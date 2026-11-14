package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.example.MainActivity
import com.example.R
import com.example.data.repository.SettingsRepository
import com.example.data.timelapse.TimeLapseSessionManager
import com.example.data.timelapse.TimeLapseVideoEncoder
import com.example.domain.usecase.TimeLapseFrameScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class TimeLapseRecordingService : Service(), LifecycleOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val analysisExecutor = Executors.newSingleThreadExecutor()

    private var encoder: TimeLapseVideoEncoder? = null
    private var scheduler: TimeLapseFrameScheduler? = null
    private var timerJob: Job? = null
    private var surfaceObserverJob: Job? = null
    private var torchObserverJob: Job? = null

    private var cameraProvider: ProcessCameraProvider? = null
    private var previewUseCase: Preview? = null
    private var imageAnalysisUseCase: ImageAnalysis? = null
    private var boundCamera: Camera? = null

    private val isRecordingActive = AtomicBoolean(false)
    private val isEncodingFrame = AtomicBoolean(false)
    private val isFinalizing = AtomicBoolean(false)
    private var startRealtimeMs: Long = 0L
    private var includeOverlay: Boolean = false
    private var currentIntervalSeconds: Int = 2

    override fun onCreate() {
        super.onCreate()
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        ensureNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_RECORDING -> {
                stopAndFinalizeRecording()
                return START_NOT_STICKY
            }
            ACTION_START_RECORDING, null -> {
                if (!isRecordingActive.getAndSet(true)) {
                    startRecordingInternal()
                }
            }
        }
        return START_STICKY
    }

    private fun startRecordingInternal() {
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED

        val initialNotification = buildNotification(elapsedSeconds = 0L, intervalSeconds = 2)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                initialNotification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            )
        } else {
            startForeground(NOTIFICATION_ID, initialNotification)
        }

        serviceScope.launch {
            val settings = SettingsRepository.getInstance(applicationContext).settingsFlow.first()
            currentIntervalSeconds = settings.timeLapseIntervalSeconds
            includeOverlay = settings.timeLapseIncludeOverlay

            val frameScheduler = TimeLapseFrameScheduler(intervalSeconds = currentIntervalSeconds)
            scheduler = frameScheduler
            encoder = TimeLapseVideoEncoder(
                context = applicationContext,
                quality = settings.timeLapseQuality
            )
            startRealtimeMs = SystemClock.elapsedRealtime()

            TimeLapseSessionManager.onRecordingStarted(currentIntervalSeconds)
            updateNotification(elapsedSeconds = 0L)

            bindCameraForRecording()
            startElapsedTicker()
        }
    }

    private fun bindCameraForRecording() {
        val providerFuture = ProcessCameraProvider.getInstance(applicationContext)
        providerFuture.addListener(
            {
                if (!isRecordingActive.get()) return@addListener
                runCatching {
                    val provider = providerFuture.get()
                    cameraProvider = provider

                    val preview = Preview.Builder().build().also { prev ->
                        prev.surfaceProvider = TimeLapseSessionManager.activeSurfaceProvider.value
                    }
                    previewUseCase = preview

                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()

                    analysis.setAnalyzer(analysisExecutor) { imageProxy ->
                        try {
                            if (!isRecordingActive.get() || isFinalizing.get()) {
                                return@setAnalyzer
                            }
                            val activeScheduler = scheduler ?: return@setAnalyzer
                            val elapsedMs = SystemClock.elapsedRealtime() - startRealtimeMs
                            if (!activeScheduler.shouldCaptureFrame(elapsedMs)) {
                                return@setAnalyzer
                            }
                            if (!isEncodingFrame.compareAndSet(false, true)) {
                                return@setAnalyzer
                            }

                            try {
                                activeScheduler.onFrameCaptured(elapsedMs)
                                val rawBitmap = imageProxy.toBitmap()
                                val rotatedBitmap = TimeLapseSessionManager.rotateBitmapIfNeeded(
                                    source = rawBitmap,
                                    rotationDegrees = imageProxy.imageInfo.rotationDegrees
                                )
                                try {
                                    val overlaySpec = if (includeOverlay) {
                                        TimeLapseSessionManager.currentOverlaySpec()
                                    } else {
                                        null
                                    }
                                    val ok = encoder?.encodeFrame(rotatedBitmap, overlaySpec) ?: false
                                    if (!ok) {
                                        serviceScope.launch {
                                            stopAndFinalizeRecording()
                                        }
                                    }
                                } finally {
                                    if (!rotatedBitmap.isRecycled) {
                                        rotatedBitmap.recycle()
                                    }
                                }
                            } finally {
                                isEncodingFrame.set(false)
                            }
                        } catch (_: Throwable) {
                            // Never crash on frame conversion or encoder state transitions
                        } finally {
                            imageProxy.close()
                        }
                    }
                    imageAnalysisUseCase = analysis

                    val selector = when {
                        provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) ->
                            CameraSelector.DEFAULT_BACK_CAMERA
                        provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) ->
                            CameraSelector.DEFAULT_FRONT_CAMERA
                        else -> CameraSelector.DEFAULT_BACK_CAMERA
                    }

                    provider.unbindAll()
                    val camera = provider.bindToLifecycle(
                        this@TimeLapseRecordingService,
                        selector,
                        preview,
                        analysis
                    )
                    boundCamera = camera
                    val hasFlash = camera.cameraInfo.hasFlashUnit()
                    TimeLapseSessionManager.setHasFlashUnit(hasFlash)
                    if (hasFlash && TimeLapseSessionManager.flashlightRequested.value) {
                        camera.cameraControl.enableTorch(true)
                    }
                }
            },
            ContextCompat.getMainExecutor(applicationContext)
        )

        surfaceObserverJob?.cancel()
        surfaceObserverJob = serviceScope.launch {
            TimeLapseSessionManager.activeSurfaceProvider.collect { provider ->
                previewUseCase?.surfaceProvider = provider
            }
        }

        torchObserverJob?.cancel()
        torchObserverJob = serviceScope.launch {
            TimeLapseSessionManager.flashlightRequested.collect { enabled ->
                val cam = boundCamera ?: return@collect
                if (cam.cameraInfo.hasFlashUnit()) {
                    runCatching { cam.cameraControl.enableTorch(enabled) }
                }
            }
        }
    }

    private fun startElapsedTicker() {
        timerJob?.cancel()
        timerJob = serviceScope.launch {
            while (isActive && isRecordingActive.get()) {
                val elapsedSec = ((SystemClock.elapsedRealtime() - startRealtimeMs) / 1000L)
                    .coerceAtLeast(0L)
                TimeLapseSessionManager.onRecordingTick(elapsedSec)
                updateNotification(elapsedSec)
                delay(1000L)
            }
        }
    }

    private fun stopAndFinalizeRecording() {
        if (!isFinalizing.compareAndSet(false, true)) return
        isRecordingActive.set(false)
        timerJob?.cancel()
        surfaceObserverJob?.cancel()
        torchObserverJob?.cancel()

        runCatching {
            imageAnalysisUseCase?.clearAnalyzer()
            cameraProvider?.unbindAll()
        }

        val activeEncoder = encoder
        encoder = null

        analysisExecutor.execute {
            val savedUri = runCatching {
                activeEncoder?.finalizeAndSaveToMediaStore()
            }.getOrNull()

            serviceScope.launch {
                TimeLapseSessionManager.onRecordingStopped(savedUri)
                lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Finalize whatever was encoded so far if the app task is swiped away
        if (isRecordingActive.get()) {
            stopAndFinalizeRecording()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        if (isRecordingActive.get() && !isFinalizing.get()) {
            val activeEncoder = encoder
            encoder = null
            runCatching {
                val uri = activeEncoder?.finalizeAndSaveToMediaStore()
                TimeLapseSessionManager.onRecordingStopped(uri)
            }
        }
        timerJob?.cancel()
        surfaceObserverJob?.cancel()
        torchObserverJob?.cancel()
        serviceScope.cancel()
        analysisExecutor.shutdown()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun ensureNotificationChannel() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notif_channel_timelapse),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun updateNotification(elapsedSeconds: Long) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(
            NOTIFICATION_ID,
            buildNotification(elapsedSeconds, currentIntervalSeconds)
        )
    }

    private fun buildNotification(elapsedSeconds: Long, intervalSeconds: Int): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            100,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, TimeLapseRecordingService::class.java).apply {
            action = ACTION_STOP_RECORDING
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            101,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val formattedTime = TimeLapseFrameScheduler.formatElapsedDuration(elapsedSeconds)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(getString(R.string.notif_timelapse_title))
            .setContentText(
                getString(R.string.notif_timelapse_text, formattedTime, intervalSeconds)
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(contentPendingIntent)
            .addAction(
                0,
                getString(R.string.notif_action_stop),
                stopPendingIntent
            )
            .build()
    }

    companion object {
        const val CHANNEL_ID = "trace_timelapse_channel"
        const val NOTIFICATION_ID = 4021
        const val ACTION_START_RECORDING = "com.example.action.START_TIMELAPSE"
        const val ACTION_STOP_RECORDING = "com.example.action.STOP_TIMELAPSE"

        fun startRecording(context: Context) {
            val intent = Intent(context, TimeLapseRecordingService::class.java).apply {
                action = ACTION_START_RECORDING
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stopRecording(context: Context) {
            val intent = Intent(context, TimeLapseRecordingService::class.java).apply {
                action = ACTION_STOP_RECORDING
            }
            context.startService(intent)
        }
    }
}
