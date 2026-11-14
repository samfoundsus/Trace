package com.example.domain.usecase

import android.app.Activity
import android.content.Context
import com.google.ar.core.ArCoreApk
import kotlin.math.max

enum class ArSupportStatus {
    SUPPORTED_INSTALLED,
    SUPPORTED_NEEDS_INSTALL,
    UNSUPPORTED
}

/**
 * Pure calculations and runtime ARCore availability helpers for Pinned Mode.
 */
object PinnedModeLogic {
    const val DEFAULT_PAPER_WIDTH_CM = 21f
    const val MIN_PAPER_WIDTH_CM = 10f
    const val MAX_PAPER_WIDTH_CM = 50f

    /**
     * Maps an [ArCoreApk.Availability] value to [ArSupportStatus].
     * Exposed as a pure function for deterministic unit testing.
     */
    fun mapAvailability(availability: ArCoreApk.Availability): ArSupportStatus {
        return when (availability) {
            ArCoreApk.Availability.SUPPORTED_INSTALLED -> ArSupportStatus.SUPPORTED_INSTALLED
            ArCoreApk.Availability.SUPPORTED_APK_TOO_OLD,
            ArCoreApk.Availability.SUPPORTED_NOT_INSTALLED -> ArSupportStatus.SUPPORTED_NEEDS_INSTALL
            else -> ArSupportStatus.UNSUPPORTED
        }
    }

    /**
     * Queries [ArCoreApk] safely without throwing if Play Services for AR is missing.
     */
    fun checkArSupport(context: Context): ArSupportStatus {
        return try {
            val availability = ArCoreApk.getInstance().checkAvailability(context)
            mapAvailability(availability)
        } catch (_: Throwable) {
            ArSupportStatus.UNSUPPORTED
        }
    }

    /**
     * Requests installation of Google Play Services for AR when needed.
     * Returns true if ARCore is already installed and ready to create a Session.
     */
    fun ensureArCoreInstalled(
        activity: Activity,
        userRequestedInstall: Boolean
    ): Boolean {
        return try {
            when (ArCoreApk.getInstance().requestInstall(activity, userRequestedInstall)) {
                ArCoreApk.InstallStatus.INSTALLED -> true
                ArCoreApk.InstallStatus.INSTALL_REQUESTED -> false
            }
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Clamps paper width in centimetres to the valid range [10 cm, 50 cm].
     * Default is 21 cm (standard A4 width).
     */
    fun sanitizePaperWidthCm(widthCm: Float): Float {
        if (widthCm.isNaN() || widthCm.isInfinite()) return DEFAULT_PAPER_WIDTH_CM
        return widthCm.coerceIn(MIN_PAPER_WIDTH_CM, MAX_PAPER_WIDTH_CM)
    }

    /**
     * Computes the physical width and height (in metres) of the anchored 3D image quad
     * on the horizontal paper plane given [paperWidthCm], image pixel dimensions, and [scale].
     */
    fun computeQuadSizeMeters(
        paperWidthCm: Float,
        imageWidthPx: Int,
        imageHeightPx: Int,
        scale: Float
    ): Pair<Float, Float> {
        val safePaperCm = sanitizePaperWidthCm(paperWidthCm)
        val safeScale = if (scale.isNaN() || scale <= 0f) 1f else scale.coerceIn(0.15f, 8.0f)
        val baseWidthMeters = (safePaperCm / 100f) * safeScale

        val safeW = max(1, imageWidthPx).toFloat()
        val safeH = max(1, imageHeightPx).toFloat()
        val aspect = safeH / safeW
        val heightMeters = baseWidthMeters * aspect
        return baseWidthMeters to heightMeters
    }

    /**
     * Converts 2D screen-space pan offsets ([offsetX], [offsetY]) into metres along the
     * anchored horizontal plane's X and Z axes so drag gestures move the image naturally on paper.
     */
    fun computePlaneOffsetMeters(
        offsetX: Float,
        offsetY: Float,
        viewportWidthPx: Int,
        paperWidthCm: Float
    ): Pair<Float, Float> {
        val safeViewportW = max(1, viewportWidthPx).toFloat()
        val paperWidthMeters = sanitizePaperWidthCm(paperWidthCm) / 100f
        val metersPerPixel = paperWidthMeters / (safeViewportW * 0.75f)
        val deltaX = offsetX * metersPerPixel
        val deltaZ = offsetY * metersPerPixel
        return deltaX to deltaZ
    }
}
