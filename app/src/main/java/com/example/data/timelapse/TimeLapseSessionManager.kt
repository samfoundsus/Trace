package com.example.data.timelapse

import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import androidx.camera.core.Preview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicReference

data class TimeLapseRecordingStatus(
    val isRecording: Boolean = false,
    val elapsedSeconds: Long = 0L,
    val intervalSeconds: Int = 2,
    val savedVideoUri: Uri? = null
)

/**
 * Coordinates the live camera preview surface, current overlay state, and recording status
 * between `TraceScreen` / `TraceViewModel` and `TimeLapseRecordingService`.
 */
object TimeLapseSessionManager {

    private val _status = MutableStateFlow(TimeLapseRecordingStatus())
    val status: StateFlow<TimeLapseRecordingStatus> = _status.asStateFlow()

    private val _activeSurfaceProvider = MutableStateFlow<Preview.SurfaceProvider?>(null)
    val activeSurfaceProvider: StateFlow<Preview.SurfaceProvider?> = _activeSurfaceProvider.asStateFlow()

    private val _flashlightRequested = MutableStateFlow(false)
    val flashlightRequested: StateFlow<Boolean> = _flashlightRequested.asStateFlow()

    private val _hasFlashUnit = MutableStateFlow(false)
    val hasFlashUnit: StateFlow<Boolean> = _hasFlashUnit.asStateFlow()

    private val overlaySpecRef = AtomicReference<OverlayCompositeSpec?>(null)

    fun attachSurfaceProvider(provider: Preview.SurfaceProvider?) {
        _activeSurfaceProvider.value = provider
    }

    fun updateOverlaySpec(spec: OverlayCompositeSpec?) {
        overlaySpecRef.set(spec)
    }

    fun currentOverlaySpec(): OverlayCompositeSpec? = overlaySpecRef.get()

    fun setFlashlightRequested(enabled: Boolean) {
        _flashlightRequested.value = enabled
    }

    fun setHasFlashUnit(hasFlash: Boolean) {
        _hasFlashUnit.value = hasFlash
    }

    fun onRecordingStarted(intervalSeconds: Int) {
        _status.update {
            it.copy(
                isRecording = true,
                elapsedSeconds = 0L,
                intervalSeconds = intervalSeconds
            )
        }
    }

    fun onRecordingTick(elapsedSeconds: Long) {
        _status.update {
            if (it.isRecording) it.copy(elapsedSeconds = elapsedSeconds) else it
        }
    }

    fun onRecordingStopped(savedUri: Uri?) {
        _status.update {
            it.copy(
                isRecording = false,
                elapsedSeconds = 0L,
                savedVideoUri = savedUri ?: it.savedVideoUri
            )
        }
    }

    fun consumeSavedVideoUri() {
        _status.update { it.copy(savedVideoUri = null) }
    }

    fun rotateBitmapIfNeeded(source: Bitmap, rotationDegrees: Int): Bitmap {
        if (rotationDegrees % 360 == 0) return source
        val matrix = Matrix().apply {
            postRotate(rotationDegrees.toFloat())
        }
        val rotated = Bitmap.createBitmap(
            source,
            0,
            0,
            source.width,
            source.height,
            matrix,
            true
        )
        if (rotated !== source && !source.isRecycled) {
            source.recycle()
        }
        return rotated
    }
}
