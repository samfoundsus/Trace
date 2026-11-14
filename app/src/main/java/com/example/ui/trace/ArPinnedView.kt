package com.example.ui.trace

import android.content.Context
import android.graphics.Bitmap
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.opengl.Matrix
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.Surface
import android.view.WindowManager
import androidx.camera.core.Camera
import androidx.camera.core.CameraState
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.domain.model.TraceState
import com.example.domain.usecase.PinnedModeLogic
import com.google.ar.core.Anchor
import com.google.ar.core.Config
import com.google.ar.core.Coordinates2d
import com.google.ar.core.Frame
import com.google.ar.core.Plane
import com.google.ar.core.Point
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import com.google.ar.core.exceptions.CameraNotAvailableException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.atomic.AtomicReference
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.coroutines.resume
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Coordinates camera hardware handover between CameraX (normal mode) and ARCore (pinned mode)
 * so neither framework opens the camera until the other has completely released it.
 */
internal object CameraHandoverCoordinator {
    private val _isCameraXClosed = MutableStateFlow(true)
    private val _isArSessionClosed = MutableStateFlow(true)

    private var trackedCamera: Camera? = null
    private var stateObserver: Observer<CameraState>? = null

    fun onCameraXBound(camera: Camera) {
        clearCameraObserver()
        trackedCamera = camera
        val currentType = camera.cameraInfo.cameraState.value?.type
        _isCameraXClosed.value = (currentType == CameraState.Type.CLOSED)

        val observer = Observer<CameraState> { state ->
            val closed = state.type == CameraState.Type.CLOSED
            _isCameraXClosed.value = closed
        }
        stateObserver = observer
        camera.cameraInfo.cameraState.observeForever(observer)
    }

    fun releaseCameraXAndTrackState(context: Context) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        if (providerFuture.isDone) {
            runCatching {
                providerFuture.get().unbindAll()
            }
            val cam = trackedCamera
            val currentType = cam?.cameraInfo?.cameraState?.value?.type
            if (cam == null || currentType == null || currentType == CameraState.Type.CLOSED) {
                clearCameraObserver()
                _isCameraXClosed.value = true
            }
        } else {
            providerFuture.addListener(
                {
                    runCatching { providerFuture.get().unbindAll() }
                    clearCameraObserver()
                    _isCameraXClosed.value = true
                },
                ContextCompat.getMainExecutor(context)
            )
        }
    }

    suspend fun awaitCameraXReleased(context: Context) {
        withContext(Dispatchers.Main.immediate) {
            val provider = suspendCancellableCoroutine<ProcessCameraProvider?> { cont ->
                val future = ProcessCameraProvider.getInstance(context)
                if (future.isDone) {
                    cont.resume(runCatching { future.get() }.getOrNull())
                } else {
                    future.addListener(
                        {
                            if (cont.isActive) {
                                cont.resume(runCatching { future.get() }.getOrNull())
                            }
                        },
                        ContextCompat.getMainExecutor(context)
                    )
                }
            }
            runCatching { provider?.unbindAll() }

            val cam = trackedCamera
            val currentType = cam?.cameraInfo?.cameraState?.value?.type
            if (cam == null || currentType == null || currentType == CameraState.Type.CLOSED) {
                clearCameraObserver()
                _isCameraXClosed.value = true
            } else {
                withTimeoutOrNull(2_000L) {
                    _isCameraXClosed.first { it }
                }
                clearCameraObserver()
                _isCameraXClosed.value = true
            }
        }
    }

    fun markArSessionActive() {
        _isArSessionClosed.value = false
    }

    fun markArSessionClosed() {
        _isArSessionClosed.value = true
    }

    suspend fun awaitArSessionClosed() {
        withTimeoutOrNull(2_000L) {
            _isArSessionClosed.first { it }
        }
        _isArSessionClosed.value = true
    }

    private fun clearCameraObserver() {
        val cam = trackedCamera
        val obs = stateObserver
        if (cam != null && obs != null) {
            runCatching { cam.cameraInfo.cameraState.removeObserver(obs) }
        }
        trackedCamera = null
        stateObserver = null
    }
}

/**
 * ARCore-backed GLSurfaceView composable for Pinned Mode.
 * Owns the camera exclusively while active and releases the ARCore Session cleanly on exit.
 */
@Composable
fun ArPinnedSurfaceLayer(
    state: TraceState,
    activeBitmap: Bitmap?,
    rePinTriggerCount: Int,
    onPlaneDetectedChanged: (Boolean) -> Unit,
    onSurfaceAnchored: () -> Unit,
    onTrackingLostChanged: (Boolean) -> Unit,
    onArSessionFailure: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    val currentOnPlaneDetected by rememberUpdatedState(onPlaneDetectedChanged)
    val currentOnSurfaceAnchored by rememberUpdatedState(onSurfaceAnchored)
    val currentOnTrackingLost by rememberUpdatedState(onTrackingLostChanged)
    val currentOnFailure by rememberUpdatedState(onArSessionFailure)

    val controller = remember {
        ArPinnedGlController(
            context = context,
            onPlaneDetected = { detected ->
                mainHandler.post { currentOnPlaneDetected(detected) }
            },
            onAnchored = {
                mainHandler.post { currentOnSurfaceAnchored() }
            },
            onTrackingLost = { lost ->
                mainHandler.post { currentOnTrackingLost(lost) }
            },
            onFailure = {
                mainHandler.post { currentOnFailure() }
            }
        )
    }

    LaunchedEffect(
        activeBitmap,
        state.opacity,
        state.scale,
        state.rotation,
        state.offsetX,
        state.offsetY,
        state.flipHorizontal,
        state.flipVertical,
        state.paperWidthCm,
        state.isFlashlightOn
    ) {
        controller.updateRenderParams(
            bitmap = activeBitmap,
            opacity = state.opacity,
            scale = state.scale,
            rotationDeg = state.rotation,
            offsetX = state.offsetX,
            offsetY = state.offsetY,
            flipHorizontal = state.flipHorizontal,
            flipVertical = state.flipVertical,
            paperWidthCm = state.paperWidthCm,
            flashlightOn = state.isFlashlightOn
        )
    }

    LaunchedEffect(rePinTriggerCount) {
        if (rePinTriggerCount > 0) {
            controller.clearAnchor()
        }
    }

    DisposableEffect(lifecycleOwner, controller) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> controller.onResume()
                Lifecycle.Event.ON_PAUSE -> controller.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            controller.onResume()
        }

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            controller.onDestroy()
        }
    }

    AndroidView(
        factory = { controller.glSurfaceView },
        modifier = modifier.testTag("ar_pinned_surface_view")
    )
}

private data class QuadRenderParams(
    val bitmap: Bitmap? = null,
    val opacity: Float = 0.55f,
    val scale: Float = 1f,
    val rotationDeg: Float = 0f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false,
    val paperWidthCm: Float = PinnedModeLogic.DEFAULT_PAPER_WIDTH_CM,
    val flashlightOn: Boolean = false
)

private class ArPinnedGlController(
    private val context: Context,
    private val onPlaneDetected: (Boolean) -> Unit,
    private val onAnchored: () -> Unit,
    private val onTrackingLost: (Boolean) -> Unit,
    private val onFailure: () -> Unit
) : GLSurfaceView.Renderer {

    private val controllerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var resumeJob: Job? = null

    @Volatile
    private var isResumed = false

    private val renderParamsRef = AtomicReference(QuadRenderParams())
    private val pendingTapRef = AtomicReference<Pair<Float, Float>?>(null)

    @Volatile
    private var clearAnchorRequested = false

    @Volatile
    private var session: Session? = null

    @Volatile
    private var isAnchored = false

    private var lastReportedPlaneDetected = false
    private var lastReportedTrackingLost = false

    private var viewportWidth = 1
    private var viewportHeight = 1

    private var cameraTextureId = 0
    private var imageTextureId = 0
    private var uploadedBitmapRef: Bitmap? = null
    private var uploadedBitmapGenerationId: Int = -1
    private var uploadedBitmapWidth = 1
    private var uploadedBitmapHeight = 1

    private var backgroundProgram = 0
    private var backgroundPositionAttr = 0
    private var backgroundTexCoordAttr = 0
    private var backgroundTextureUniform = 0

    private var planeDotsProgram = 0
    private var planeDotsMvpUniform = 0
    private var planeDotsPositionAttr = 0
    private var planeDotsColorUniform = 0

    private var quadProgram = 0
    private var quadMvpUniform = 0
    private var quadPositionAttr = 0
    private var quadTexCoordAttr = 0
    private var quadTextureUniform = 0
    private var quadOpacityUniform = 0

    private var currentAnchor: Anchor? = null
    private val lastValidAnchorMatrix = FloatArray(16).apply {
        Matrix.setIdentityM(this, 0)
    }
    private var hasValidAnchorPose = false

    private val viewMatrix = FloatArray(16)
    private val projectionMatrix = FloatArray(16)
    private val viewProjectionMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)

    private val ndcQuadBuffer: FloatBuffer = createFloatBuffer(
        floatArrayOf(
            -1f, -1f,
            1f, -1f,
            -1f, 1f,
            1f, 1f
        )
    )
    private val cameraTexCoordBuffer: FloatBuffer = createFloatBuffer(
        floatArrayOf(
            0f, 1f,
            1f, 1f,
            0f, 0f,
            1f, 0f
        )
    )
    private val quadVertexBuffer: FloatBuffer = createFloatBuffer(FloatArray(12))
    private val quadUvBuffer: FloatBuffer = createFloatBuffer(FloatArray(8))
    private val planeDotsBuffer: FloatBuffer = ByteBuffer
        .allocateDirect(MAX_PLANE_DOTS * 3 * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()

    val glSurfaceView: GLSurfaceView = object : GLSurfaceView(context) {
        private var downX = 0f
        private var downY = 0f
        private var downTime = 0L
        private var maxPointers = 0

        override fun onTouchEvent(event: MotionEvent): Boolean {
            // Only intercept single-finger quick taps when not yet anchored;
            // let multi-touch and drags pass through to the Compose gesture layer.
            if (isAnchored) return false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    downY = event.y
                    downTime = event.eventTime
                    maxPointers = 1
                    return true
                }
                MotionEvent.ACTION_POINTER_DOWN -> {
                    maxPointers = maxOf(maxPointers, event.pointerCount)
                }
                MotionEvent.ACTION_UP -> {
                    val dt = event.eventTime - downTime
                    val dx = event.x - downX
                    val dy = event.y - downY
                    if (maxPointers == 1 && dt < 350L && (dx * dx + dy * dy) < 48f * 48f) {
                        pendingTapRef.set(event.x to event.y)
                        return true
                    }
                }
            }
            return false
        }
    }.apply {
        preserveEGLContextOnPause = true
        setEGLContextClientVersion(2)
        setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        setRenderer(this@ArPinnedGlController)
        renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
    }

    fun updateRenderParams(
        bitmap: Bitmap?,
        opacity: Float,
        scale: Float,
        rotationDeg: Float,
        offsetX: Float,
        offsetY: Float,
        flipHorizontal: Boolean,
        flipVertical: Boolean,
        paperWidthCm: Float,
        flashlightOn: Boolean
    ) {
        val prev = renderParamsRef.get()
        renderParamsRef.set(
            QuadRenderParams(
                bitmap = bitmap,
                opacity = opacity,
                scale = scale,
                rotationDeg = rotationDeg,
                offsetX = offsetX,
                offsetY = offsetY,
                flipHorizontal = flipHorizontal,
                flipVertical = flipVertical,
                paperWidthCm = paperWidthCm,
                flashlightOn = flashlightOn
            )
        )
        if (prev.flashlightOn != flashlightOn) {
            applyTorchConfig(flashlightOn)
        }
    }

    fun clearAnchor() {
        clearAnchorRequested = true
        isAnchored = false
        lastReportedTrackingLost = false
        onTrackingLost(false)
    }

    fun onResume() {
        isResumed = true
        resumeJob?.cancel()
        resumeJob = controllerScope.launch {
            try {
                // 1. Wait until CameraX has unbound all use cases and CameraState reaches CLOSED
                CameraHandoverCoordinator.awaitCameraXReleased(context)
                if (!isResumed) return@launch

                CameraHandoverCoordinator.markArSessionActive()

                var activeSession = session
                if (activeSession == null) {
                    activeSession = Session(context)
                    val config = Config(activeSession).apply {
                        planeFindingMode = Config.PlaneFindingMode.HORIZONTAL
                        lightEstimationMode = Config.LightEstimationMode.DISABLED
                        updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                        focusMode = Config.FocusMode.AUTO
                    }
                    activeSession.configure(config)
                    session = activeSession
                }

                // 2. Resume the ARCore Session, retrying up to 3 times (300 ms apart) on CameraNotAvailableException
                var retriesRemaining = 3
                var sessionResumed = false
                while (!sessionResumed && isResumed) {
                    try {
                        activeSession.resume()
                        sessionResumed = true
                    } catch (e: CameraNotAvailableException) {
                        if (retriesRemaining > 0 && isResumed) {
                            retriesRemaining--
                            delay(300L)
                        } else {
                            throw e
                        }
                    }
                }

                if (!isResumed) {
                    runCatching { activeSession.pause() }
                    return@launch
                }

                glSurfaceView.onResume()
                applyTorchConfig(renderParamsRef.get().flashlightOn)
            } catch (_: Throwable) {
                closeSessionSafely()
                onFailure()
            }
        }
    }

    fun onPause() {
        isResumed = false
        resumeJob?.cancel()
        resumeJob = null
        runCatching {
            glSurfaceView.onPause()
            session?.pause()
        }
    }

    fun onDestroy() {
        isResumed = false
        resumeJob?.cancel()
        resumeJob = null
        controllerScope.cancel()
        runCatching {
            glSurfaceView.onPause()
        }
        closeSessionSafely()
    }

    private fun closeSessionSafely() {
        runCatching {
            currentAnchor?.detach()
            currentAnchor = null
        }
        runCatching {
            session?.pause()
        }
        runCatching {
            session?.close()
        }
        session = null
        CameraHandoverCoordinator.markArSessionClosed()
    }

    private fun applyTorchConfig(torchOn: Boolean) {
        val activeSession = session ?: return
        runCatching {
            val config = activeSession.config
            config.flashMode = if (torchOn) Config.FlashMode.TORCH else Config.FlashMode.OFF
            activeSession.configure(config)
        }
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.07f, 0.07f, 0.08f, 1.0f)

        val textures = IntArray(2)
        GLES20.glGenTextures(2, textures, 0)
        cameraTextureId = textures[0]
        imageTextureId = textures[1]

        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, cameraTextureId)
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_WRAP_S,
            GLES20.GL_CLAMP_TO_EDGE
        )
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_WRAP_T,
            GLES20.GL_CLAMP_TO_EDGE
        )
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_MIN_FILTER,
            GLES20.GL_LINEAR
        )
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_MAG_FILTER,
            GLES20.GL_LINEAR
        )

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, imageTextureId)
        GLES20.glTexParameteri(
            GLES20.GL_TEXTURE_2D,
            GLES20.GL_TEXTURE_WRAP_S,
            GLES20.GL_CLAMP_TO_EDGE
        )
        GLES20.glTexParameteri(
            GLES20.GL_TEXTURE_2D,
            GLES20.GL_TEXTURE_WRAP_T,
            GLES20.GL_CLAMP_TO_EDGE
        )
        GLES20.glTexParameteri(
            GLES20.GL_TEXTURE_2D,
            GLES20.GL_TEXTURE_MIN_FILTER,
            GLES20.GL_LINEAR
        )
        GLES20.glTexParameteri(
            GLES20.GL_TEXTURE_2D,
            GLES20.GL_TEXTURE_MAG_FILTER,
            GLES20.GL_LINEAR
        )

        uploadedBitmapRef = null
        uploadedBitmapGenerationId = -1

        backgroundProgram = createProgram(CAMERA_VERTEX_SHADER, CAMERA_FRAGMENT_SHADER)
        backgroundPositionAttr = GLES20.glGetAttribLocation(backgroundProgram, "aPosition")
        backgroundTexCoordAttr = GLES20.glGetAttribLocation(backgroundProgram, "aTexCoord")
        backgroundTextureUniform = GLES20.glGetUniformLocation(backgroundProgram, "sTexture")

        planeDotsProgram = createProgram(DOTS_VERTEX_SHADER, DOTS_FRAGMENT_SHADER)
        planeDotsMvpUniform = GLES20.glGetUniformLocation(planeDotsProgram, "uMvpMatrix")
        planeDotsPositionAttr = GLES20.glGetAttribLocation(planeDotsProgram, "aPosition")
        planeDotsColorUniform = GLES20.glGetUniformLocation(planeDotsProgram, "uColor")

        quadProgram = createProgram(QUAD_VERTEX_SHADER, QUAD_FRAGMENT_SHADER)
        quadMvpUniform = GLES20.glGetUniformLocation(quadProgram, "uMvpMatrix")
        quadPositionAttr = GLES20.glGetAttribLocation(quadProgram, "aPosition")
        quadTexCoordAttr = GLES20.glGetAttribLocation(quadProgram, "aTexCoord")
        quadTextureUniform = GLES20.glGetUniformLocation(quadProgram, "sTexture")
        quadOpacityUniform = GLES20.glGetUniformLocation(quadProgram, "uOpacity")
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        viewportWidth = width.coerceAtLeast(1)
        viewportHeight = height.coerceAtLeast(1)
        GLES20.glViewport(0, 0, viewportWidth, viewportHeight)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        val activeSession = session ?: return
        val frame: Frame = try {
            activeSession.setCameraTextureName(cameraTextureId)
            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            @Suppress("DEPRECATION")
            val rotation = windowManager?.defaultDisplay?.rotation ?: Surface.ROTATION_0
            activeSession.setDisplayGeometry(rotation, viewportWidth, viewportHeight)
            activeSession.update()
        } catch (_: Throwable) {
            return
        }

        if (clearAnchorRequested) {
            clearAnchorRequested = false
            runCatching { currentAnchor?.detach() }
            currentAnchor = null
            hasValidAnchorPose = false
            isAnchored = false
        }

        // 1. Draw live camera feed background
        drawCameraBackground(frame)

        val camera = frame.camera
        camera.getViewMatrix(viewMatrix, 0)
        camera.getProjectionMatrix(projectionMatrix, 0, 0.05f, 20.0f)
        Matrix.multiplyMM(viewProjectionMatrix, 0, projectionMatrix, 0, viewMatrix, 0)

        // 2. Handle single tap to anchor onto detected horizontal paper surface
        val pendingTap = pendingTapRef.getAndSet(null)
        if (pendingTap != null && !isAnchored && camera.trackingState == TrackingState.TRACKING) {
            val (tapX, tapY) = pendingTap
            val hits = runCatching { frame.hitTest(tapX, tapY) }.getOrNull().orEmpty()
            val planeHit = hits.firstOrNull { hit ->
                val trackable = hit.trackable
                (trackable is Plane &&
                    trackable.type == Plane.Type.HORIZONTAL_UPWARD_FACING &&
                    trackable.isPoseInPolygon(hit.hitPose)) ||
                    (trackable is Point &&
                        trackable.orientationMode == Point.OrientationMode.ESTIMATED_SURFACE_NORMAL)
            } ?: hits.firstOrNull { it.trackable is Plane }

            if (planeHit != null) {
                runCatching {
                    currentAnchor?.detach()
                    val newAnchor = planeHit.createAnchor()
                    currentAnchor = newAnchor
                    newAnchor.pose.toMatrix(lastValidAnchorMatrix, 0)
                    hasValidAnchorPose = true
                    isAnchored = true
                    onAnchored()
                }
            }
        }

        // 3. If not yet anchored, draw subtle dotted grid on detected horizontal planes
        if (!isAnchored) {
            val planes = runCatching {
                activeSession.getAllTrackables(Plane::class.java)
            }.getOrNull().orEmpty()

            var anyHorizontalPlane = false
            for (plane in planes) {
                if (plane.type == Plane.Type.HORIZONTAL_UPWARD_FACING &&
                    plane.trackingState == TrackingState.TRACKING &&
                    plane.subsumedBy == null
                ) {
                    anyHorizontalPlane = true
                    drawSubtlePlaneDots(plane)
                }
            }
            if (anyHorizontalPlane != lastReportedPlaneDetected) {
                lastReportedPlaneDetected = anyHorizontalPlane
                onPlaneDetected(anyHorizontalPlane)
            }
            return
        }

        // 4. When anchored, update pose or keep last pose if tracking is lost
        val anchor = currentAnchor
        val currentlyTracking = camera.trackingState == TrackingState.TRACKING &&
            anchor != null &&
            anchor.trackingState == TrackingState.TRACKING

        if (currentlyTracking && anchor != null) {
            anchor.pose.toMatrix(lastValidAnchorMatrix, 0)
            hasValidAnchorPose = true
        }

        val trackingLost = !currentlyTracking
        if (trackingLost != lastReportedTrackingLost) {
            lastReportedTrackingLost = trackingLost
            onTrackingLost(trackingLost)
        }

        if (hasValidAnchorPose) {
            drawAnchoredImageQuad(renderParamsRef.get())
        }
    }

    private fun drawCameraBackground(frame: Frame) {
        if (frame.hasDisplayGeometryChanged()) {
            frame.transformCoordinates2d(
                Coordinates2d.OPENGL_NORMALIZED_DEVICE_COORDINATES,
                ndcQuadBuffer,
                Coordinates2d.TEXTURE_NORMALIZED,
                cameraTexCoordBuffer
            )
        }
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthMask(false)

        GLES20.glUseProgram(backgroundProgram)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, cameraTextureId)
        GLES20.glUniform1i(backgroundTextureUniform, 0)

        ndcQuadBuffer.position(0)
        GLES20.glVertexAttribPointer(
            backgroundPositionAttr,
            2,
            GLES20.GL_FLOAT,
            false,
            0,
            ndcQuadBuffer
        )
        GLES20.glEnableVertexAttribArray(backgroundPositionAttr)

        cameraTexCoordBuffer.position(0)
        GLES20.glVertexAttribPointer(
            backgroundTexCoordAttr,
            2,
            GLES20.GL_FLOAT,
            false,
            0,
            cameraTexCoordBuffer
        )
        GLES20.glEnableVertexAttribArray(backgroundTexCoordAttr)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        GLES20.glDisableVertexAttribArray(backgroundPositionAttr)
        GLES20.glDisableVertexAttribArray(backgroundTexCoordAttr)
        GLES20.glDepthMask(true)
    }

    private fun drawSubtlePlaneDots(plane: Plane) {
        val extentX = (plane.extentX * 0.5f).coerceAtMost(0.6f)
        val extentZ = (plane.extentZ * 0.5f).coerceAtMost(0.6f)
        if (extentX <= 0.02f || extentZ <= 0.02f) return

        val stepMeters = 0.025f
        planeDotsBuffer.clear()
        var dotCount = 0

        // Local coordinates relative to plane.centerPose; transformed on the GPU via modelMatrix
        var x = -extentX
        while (x <= extentX && dotCount < MAX_PLANE_DOTS) {
            var z = -extentZ
            while (z <= extentZ && dotCount < MAX_PLANE_DOTS) {
                planeDotsBuffer.put(x)
                planeDotsBuffer.put(0.001f)
                planeDotsBuffer.put(z)
                dotCount++
                z += stepMeters
            }
            x += stepMeters
        }
        if (dotCount == 0) return
        planeDotsBuffer.position(0)

        plane.centerPose.toMatrix(modelMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, viewProjectionMatrix, 0, modelMatrix, 0)

        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        GLES20.glUseProgram(planeDotsProgram)
        GLES20.glUniformMatrix4fv(planeDotsMvpUniform, 1, false, mvpMatrix, 0)
        // Subtle neutral white/grey dots with low opacity, no bright colours
        GLES20.glUniform4f(planeDotsColorUniform, 0.92f, 0.94f, 0.96f, 0.42f)

        GLES20.glVertexAttribPointer(
            planeDotsPositionAttr,
            3,
            GLES20.GL_FLOAT,
            false,
            0,
            planeDotsBuffer
        )
        GLES20.glEnableVertexAttribArray(planeDotsPositionAttr)

        GLES20.glDrawArrays(GLES20.GL_POINTS, 0, dotCount)

        GLES20.glDisableVertexAttribArray(planeDotsPositionAttr)
        GLES20.glDisable(GLES20.GL_BLEND)
    }

    private fun drawAnchoredImageQuad(params: QuadRenderParams) {
        val bmp = params.bitmap ?: return
        if (bmp.isRecycled) return

        if (bmp !== uploadedBitmapRef || bmp.generationId != uploadedBitmapGenerationId) {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, imageTextureId)
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0)
            uploadedBitmapRef = bmp
            uploadedBitmapGenerationId = bmp.generationId
            uploadedBitmapWidth = bmp.width.coerceAtLeast(1)
            uploadedBitmapHeight = bmp.height.coerceAtLeast(1)
        }

        val (quadWidthM, quadHeightM) = PinnedModeLogic.computeQuadSizeMeters(
            paperWidthCm = params.paperWidthCm,
            imageWidthPx = uploadedBitmapWidth,
            imageHeightPx = uploadedBitmapHeight,
            scale = params.scale
        )
        val (panMetersX, panMetersZ) = PinnedModeLogic.computePlaneOffsetMeters(
            offsetX = params.offsetX,
            offsetY = params.offsetY,
            viewportWidthPx = viewportWidth,
            paperWidthCm = params.paperWidthCm
        )

        val halfW = quadWidthM * 0.5f
        val halfH = quadHeightM * 0.5f
        val rad = Math.toRadians(params.rotationDeg.toDouble())
        val cosR = cos(rad).toFloat()
        val sinR = sin(rad).toFloat()

        // Four corners on the horizontal X-Z plane around (panMetersX, 0.002f, panMetersZ), zero heap allocations
        val wCos = halfW * cosR
        val wSin = halfW * sinR
        val hCos = halfH * cosR
        val hSin = halfH * sinR

        quadVertexBuffer.clear()
        // Top-Left (-halfW, -halfH)
        quadVertexBuffer.put(-wCos + hSin + panMetersX).put(0.002f).put(-wSin - hCos + panMetersZ)
        // Top-Right (halfW, -halfH)
        quadVertexBuffer.put(wCos + hSin + panMetersX).put(0.002f).put(wSin - hCos + panMetersZ)
        // Bottom-Left (-halfW, halfH)
        quadVertexBuffer.put(-wCos - hSin + panMetersX).put(0.002f).put(-wSin + hCos + panMetersZ)
        // Bottom-Right (halfW, halfH)
        quadVertexBuffer.put(wCos - hSin + panMetersX).put(0.002f).put(wSin + hCos + panMetersZ)
        quadVertexBuffer.position(0)

        val uLeft = if (params.flipHorizontal) 1f else 0f
        val uRight = if (params.flipHorizontal) 0f else 1f
        val vTop = if (params.flipVertical) 1f else 0f
        val vBottom = if (params.flipVertical) 0f else 1f

        quadUvBuffer.clear()
        quadUvBuffer.put(uLeft).put(vTop)
        quadUvBuffer.put(uRight).put(vTop)
        quadUvBuffer.put(uLeft).put(vBottom)
        quadUvBuffer.put(uRight).put(vBottom)
        quadUvBuffer.position(0)

        Matrix.multiplyMM(mvpMatrix, 0, viewProjectionMatrix, 0, lastValidAnchorMatrix, 0)

        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        GLES20.glUseProgram(quadProgram)
        GLES20.glUniformMatrix4fv(quadMvpUniform, 1, false, mvpMatrix, 0)
        GLES20.glUniform1f(quadOpacityUniform, params.opacity.coerceIn(0.1f, 1.0f))

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, imageTextureId)
        GLES20.glUniform1i(quadTextureUniform, 0)

        GLES20.glVertexAttribPointer(
            quadPositionAttr,
            3,
            GLES20.GL_FLOAT,
            false,
            0,
            quadVertexBuffer
        )
        GLES20.glEnableVertexAttribArray(quadPositionAttr)

        GLES20.glVertexAttribPointer(
            quadTexCoordAttr,
            2,
            GLES20.GL_FLOAT,
            false,
            0,
            quadUvBuffer
        )
        GLES20.glEnableVertexAttribArray(quadTexCoordAttr)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        GLES20.glDisableVertexAttribArray(quadPositionAttr)
        GLES20.glDisableVertexAttribArray(quadTexCoordAttr)
        GLES20.glDisable(GLES20.GL_BLEND)
    }

    companion object {
        private const val MAX_PLANE_DOTS = 600

        private const val CAMERA_VERTEX_SHADER = """
            attribute vec4 aPosition;
            attribute vec2 aTexCoord;
            varying vec2 vTexCoord;
            void main() {
                gl_Position = aPosition;
                vTexCoord = aTexCoord;
            }
        """

        private const val CAMERA_FRAGMENT_SHADER = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            varying vec2 vTexCoord;
            uniform samplerExternalOES sTexture;
            void main() {
                gl_FragColor = texture2D(sTexture, vTexCoord);
            }
        """

        private const val DOTS_VERTEX_SHADER = """
            uniform mat4 uMvpMatrix;
            attribute vec4 aPosition;
            void main() {
                gl_Position = uMvpMatrix * aPosition;
                gl_PointSize = 7.0;
            }
        """

        private const val DOTS_FRAGMENT_SHADER = """
            precision mediump float;
            uniform vec4 uColor;
            void main() {
                vec2 coord = gl_PointCoord - vec2(0.5);
                if (length(coord) > 0.5) {
                    discard;
                }
                gl_FragColor = uColor;
            }
        """

        private const val QUAD_VERTEX_SHADER = """
            uniform mat4 uMvpMatrix;
            attribute vec4 aPosition;
            attribute vec2 aTexCoord;
            varying vec2 vTexCoord;
            void main() {
                gl_Position = uMvpMatrix * aPosition;
                vTexCoord = aTexCoord;
            }
        """

        private const val QUAD_FRAGMENT_SHADER = """
            precision mediump float;
            varying vec2 vTexCoord;
            uniform sampler2D sTexture;
            uniform float uOpacity;
            void main() {
                vec4 tex = texture2D(sTexture, vTexCoord);
                gl_FragColor = vec4(tex.rgb, tex.a * uOpacity);
            }
        """

        private fun createFloatBuffer(data: FloatArray): FloatBuffer {
            return ByteBuffer
                .allocateDirect(data.size * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .apply {
                    put(data)
                    position(0)
                }
        }

        private fun compileShader(type: Int, source: String): Int {
            val shader = GLES20.glCreateShader(type)
            GLES20.glShaderSource(shader, source)
            GLES20.glCompileShader(shader)
            return shader
        }

        private fun createProgram(vertexSource: String, fragmentSource: String): Int {
            val vertexShader = compileShader(GLES20.GL_VERTEX_SHADER, vertexSource)
            val fragmentShader = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
            val program = GLES20.glCreateProgram()
            GLES20.glAttachShader(program, vertexShader)
            GLES20.glAttachShader(program, fragmentShader)
            GLES20.glLinkProgram(program)
            return program
        }
    }
}
