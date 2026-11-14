package com.example.domain.usecase

import com.example.domain.model.AppSettings
import com.example.domain.model.TimeLapseQuality
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Pure scheduling, timestamp, and resolution calculation logic for the Time-Lapse Recorder.
 *
 * - Captures 1 frame every [intervalSeconds] (1s, 2s, 5s, or 10s).
 * - Encodes output at [outputFps] (30 fps), so a 1-hour (3600s) drawing at a 2s interval
 *   produces 1800 frames = 60.0 seconds (1 minute) of smooth video.
 */
class TimeLapseFrameScheduler(
    intervalSeconds: Int,
    val outputFps: Int = DEFAULT_OUTPUT_FPS
) {
    val sanitizedIntervalSeconds: Int =
        if (intervalSeconds in AppSettings.SUPPORTED_INTERVALS_SECONDS) {
            intervalSeconds
        } else {
            2
        }

    val intervalMs: Long = sanitizedIntervalSeconds * 1000L

    private var lastCapturedElapsedMs: Long? = null
    var capturedFrameCount: Int = 0
        private set

    fun reset() {
        lastCapturedElapsedMs = null
        capturedFrameCount = 0
    }

    /**
     * Returns true if a frame should be captured at [elapsedMs] since recording started.
     */
    fun shouldCaptureFrame(elapsedMs: Long): Boolean {
        if (elapsedMs < 0L) return false
        val previous = lastCapturedElapsedMs
        return if (previous == null) {
            true
        } else {
            (elapsedMs - previous) >= intervalMs
        }
    }

    /**
     * Records that a frame was captured at [elapsedMs] and returns its 30fps presentation
     * timestamp in microseconds (`ptsUs`) for `MediaCodec` / `MediaMuxer`.
     */
    fun onFrameCaptured(elapsedMs: Long): Long {
        val prev = lastCapturedElapsedMs
        lastCapturedElapsedMs = if (prev == null) {
            elapsedMs
        } else {
            // Advance by exact multiples of intervalMs when close, preventing drift
            val steps = max(1L, (elapsedMs - prev) / intervalMs)
            prev + steps * intervalMs
        }
        val frameIndex = capturedFrameCount
        capturedFrameCount++
        return computePresentationTimeUs(frameIndex, outputFps)
    }

    companion object {
        const val DEFAULT_OUTPUT_FPS = 30

        fun computePresentationTimeUs(
            frameIndex: Int,
            outputFps: Int = DEFAULT_OUTPUT_FPS
        ): Long {
            val safeFps = max(1, outputFps)
            return (frameIndex.coerceAtLeast(0).toLong() * 1_000_000L) / safeFps.toLong()
        }

        fun calculateOutputVideoDurationSeconds(
            recordingDurationSeconds: Int,
            intervalSeconds: Int,
            outputFps: Int = DEFAULT_OUTPUT_FPS
        ): Double {
            val safeInterval = max(1, intervalSeconds)
            val safeFps = max(1, outputFps)
            val totalFrames = recordingDurationSeconds.coerceAtLeast(0).toDouble() / safeInterval
            return totalFrames / safeFps
        }

        /**
         * Computes 16-pixel-aligned H.264 encoder dimensions (width, height) that preserve
         * the portrait/landscape orientation and aspect ratio of the source frame while
         * fitting within [quality] (720p = 720x1280, 1080p = 1080x1920).
         */
        fun computeOutputDimensions(
            sourceWidth: Int,
            sourceHeight: Int,
            quality: TimeLapseQuality
        ): Pair<Int, Int> {
            if (sourceWidth <= 0 || sourceHeight <= 0) {
                return quality.shortSidePx to quality.longSidePx
            }
            val isLandscape = sourceWidth > sourceHeight
            val targetMaxW = if (isLandscape) quality.longSidePx else quality.shortSidePx
            val targetMaxH = if (isLandscape) quality.shortSidePx else quality.longSidePx

            val scale = min(
                targetMaxW.toFloat() / sourceWidth.toFloat(),
                targetMaxH.toFloat() / sourceHeight.toFloat()
            )

            val rawW = (sourceWidth * scale).roundToInt().coerceAtLeast(160)
            val rawH = (sourceHeight * scale).roundToInt().coerceAtLeast(160)

            // Round to nearest multiple of 16 for universal MediaCodec H.264 hardware compatibility
            val alignedW = ((rawW + 8) / 16) * 16
            val alignedH = ((rawH + 8) / 16) * 16
            return alignedW.coerceAtMost(targetMaxW) to alignedH.coerceAtMost(targetMaxH)
        }

        fun formatElapsedDuration(elapsedSeconds: Long): String {
            val safeSeconds = elapsedSeconds.coerceAtLeast(0L)
            val hours = safeSeconds / 3600L
            val minutes = (safeSeconds % 3600L) / 60L
            val seconds = safeSeconds % 60L
            return if (hours > 0L) {
                String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format(Locale.US, "%02d:%02d", minutes, seconds)
            }
        }
    }
}
