package com.example.domain.usecase

import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Converts a source [Bitmap] into clean line art on a transparent background:
 * 1. Grayscale conversion
 * 2. Light 3x3 Gaussian blur ([1, 2, 1; 2, 4, 2; 1, 2, 1] / 16)
 * 3. 3x3 Sobel edge detection (Gx, Gy -> normalized magnitude 0..255)
 * 4. Thresholding controlled by [strength] (0f..1f) into dark charcoal lines
 *    on a transparent background.
 *
 * Performance optimizations:
 * - Runs exclusively on [Dispatchers.Default]
 * - Reuses [IntArray] scratch buffers across invocations so GC is not pressured
 * - Precomputes both a full-resolution Sobel magnitude buffer and a reduced preview-size
 *   magnitude buffer (max [PREVIEW_MAX_LONG_SIDE] px) so dragging the strength slider
 *   renders at preview resolution immediately and full quality renders 150 ms after release.
 */
class OutlineProcessor {

    private val mutex = Mutex()

    private var cachedSourceKey: String? = null
    private var cachedWidth: Int = 0
    private var cachedHeight: Int = 0
    private var cachedEdgeMagnitudes: IntArray? = null

    private var cachedPreviewWidth: Int = 0
    private var cachedPreviewHeight: Int = 0
    private var cachedPreviewEdgeMagnitudes: IntArray? = null

    private var cachedFullStrengthBucket: Int = -1
    private var cachedFullOutlineBitmap: Bitmap? = null

    private var cachedPreviewStrengthBucket: Int = -1
    private var cachedPreviewOutlineBitmap: Bitmap? = null

    // Reusable scratch IntArrays to avoid repeated multi-megabyte heap allocations
    private var scratchArgb: IntArray = IntArray(0)
    private var scratchGray: IntArray = IntArray(0)
    private var scratchBlurred: IntArray = IntArray(0)
    private var scratchOutPixels: IntArray = IntArray(0)
    private var scratchPreviewOutPixels: IntArray = IntArray(0)

    suspend fun precomputeEdges(sourceKey: String, source: Bitmap) = withContext(Dispatchers.Default) {
        mutex.withLock {
            if (cachedSourceKey == sourceKey && cachedEdgeMagnitudes != null) {
                return@withLock
            }
            computeEdgeBuffersLocked(sourceKey, source)
        }
    }

    suspend fun getOutlineBitmap(
        sourceKey: String,
        source: Bitmap,
        strength: Float,
        isPreview: Boolean = false
    ): Bitmap = withContext(Dispatchers.Default) {
        mutex.withLock {
            if (cachedSourceKey != sourceKey || cachedEdgeMagnitudes == null) {
                computeEdgeBuffersLocked(sourceKey, source)
            }

            val bucket = (strength.coerceIn(0.05f, 0.95f) * 100f).roundToInt()
            if (isPreview) {
                val existingPreview = cachedPreviewOutlineBitmap
                if (cachedPreviewStrengthBucket == bucket &&
                    existingPreview != null &&
                    !existingPreview.isRecycled
                ) {
                    return@withLock existingPreview
                }

                val pWidth = cachedPreviewWidth
                val pHeight = cachedPreviewHeight
                val pMagnitudes = cachedPreviewEdgeMagnitudes ?: return@withLock source
                val pTotal = pWidth * pHeight
                val outPixels = ensureCapacity(scratchPreviewOutPixels, pTotal).also {
                    scratchPreviewOutPixels = it
                }

                thresholdIntoPixels(
                    magnitudes = pMagnitudes,
                    totalPixels = pTotal,
                    strength = strength,
                    outPixels = outPixels
                )

                val targetBitmap = obtainOrReuseBitmap(
                    existing = cachedPreviewOutlineBitmap,
                    width = pWidth,
                    height = pHeight
                )
                targetBitmap.setPixels(outPixels, 0, pWidth, 0, 0, pWidth, pHeight)
                cachedPreviewOutlineBitmap = targetBitmap
                cachedPreviewStrengthBucket = bucket
                return@withLock targetBitmap
            } else {
                val existingFull = cachedFullOutlineBitmap
                if (cachedFullStrengthBucket == bucket &&
                    existingFull != null &&
                    !existingFull.isRecycled
                ) {
                    return@withLock existingFull
                }

                val width = cachedWidth
                val height = cachedHeight
                val magnitudes = cachedEdgeMagnitudes ?: return@withLock source
                val totalPixels = width * height
                val outPixels = ensureCapacity(scratchOutPixels, totalPixels).also {
                    scratchOutPixels = it
                }

                thresholdIntoPixels(
                    magnitudes = magnitudes,
                    totalPixels = totalPixels,
                    strength = strength,
                    outPixels = outPixels
                )

                val targetBitmap = obtainOrReuseBitmap(
                    existing = cachedFullOutlineBitmap,
                    width = width,
                    height = height
                )
                targetBitmap.setPixels(outPixels, 0, width, 0, 0, width, height)
                cachedFullOutlineBitmap = targetBitmap
                cachedFullStrengthBucket = bucket
                return@withLock targetBitmap
            }
        }
    }

    private fun thresholdIntoPixels(
        magnitudes: IntArray,
        totalPixels: Int,
        strength: Float,
        outPixels: IntArray
    ) {
        val normalizedStrength = strength.coerceIn(0.05f, 0.95f)
        val threshold = (185f - normalizedStrength * 155f).roundToInt().coerceIn(18, 185)
        val softRange = max(24, (255 - threshold) / 2)
        val inkRgb = 0x0014161A

        for (i in 0 until totalPixels) {
            val mag = magnitudes[i]
            if (mag > threshold) {
                val excess = mag - threshold
                val alpha = min(255, 95 + (excess * 160) / softRange)
                outPixels[i] = (alpha shl 24) or inkRgb
            } else {
                outPixels[i] = 0x00000000
            }
        }
    }

    private fun computeEdgeBuffersLocked(sourceKey: String, source: Bitmap) {
        val width = source.width
        val height = source.height
        val totalPixels = width * height

        val argb = ensureCapacity(scratchArgb, totalPixels).also { scratchArgb = it }
        val gray = ensureCapacity(scratchGray, totalPixels).also { scratchGray = it }
        val blurred = ensureCapacity(scratchBlurred, totalPixels).also { scratchBlurred = it }

        val fullMagnitudes = cachedEdgeMagnitudes?.takeIf { it.size >= totalPixels }
            ?: IntArray(totalPixels)

        source.getPixels(argb, 0, width, 0, 0, width, height)
        computeSobelMagnitudes(
            argb = argb,
            gray = gray,
            blurred = blurred,
            outMagnitudes = fullMagnitudes,
            width = width,
            height = height
        )

        // Compute reduced preview buffer (max 640px on long side) for drag-time responsiveness
        val longSide = max(width, height)
        if (longSide > PREVIEW_MAX_LONG_SIDE) {
            val scale = PREVIEW_MAX_LONG_SIDE.toFloat() / longSide.toFloat()
            val pWidth = max(4, (width * scale).roundToInt())
            val pHeight = max(4, (height * scale).roundToInt())
            val pTotal = pWidth * pHeight
            val previewMagnitudes = cachedPreviewEdgeMagnitudes?.takeIf { it.size >= pTotal }
                ?: IntArray(pTotal)

            // Downsample full Sobel magnitudes using 2x2 max-pooling so thin lines remain crisp
            for (py in 0 until pHeight) {
                val sy0 = (py * height) / pHeight
                val sy1 = min(height - 1, ((py + 1) * height) / pHeight)
                val rowOffset = py * pWidth
                for (px in 0 until pWidth) {
                    val sx0 = (px * width) / pWidth
                    val sx1 = min(width - 1, ((px + 1) * width) / pWidth)
                    var maxMag = fullMagnitudes[sy0 * width + sx0]
                    val m1 = fullMagnitudes[sy0 * width + sx1]
                    if (m1 > maxMag) maxMag = m1
                    val m2 = fullMagnitudes[sy1 * width + sx0]
                    if (m2 > maxMag) maxMag = m2
                    val m3 = fullMagnitudes[sy1 * width + sx1]
                    if (m3 > maxMag) maxMag = m3
                    previewMagnitudes[rowOffset + px] = maxMag
                }
            }
            cachedPreviewWidth = pWidth
            cachedPreviewHeight = pHeight
            cachedPreviewEdgeMagnitudes = previewMagnitudes
        } else {
            cachedPreviewWidth = width
            cachedPreviewHeight = height
            cachedPreviewEdgeMagnitudes = fullMagnitudes
        }

        cachedSourceKey = sourceKey
        cachedWidth = width
        cachedHeight = height
        cachedEdgeMagnitudes = fullMagnitudes
        cachedFullStrengthBucket = -1
        cachedPreviewStrengthBucket = -1
    }

    private fun computeSobelMagnitudes(
        argb: IntArray,
        gray: IntArray,
        blurred: IntArray,
        outMagnitudes: IntArray,
        width: Int,
        height: Int
    ) {
        val totalPixels = width * height

        // Step 1: Grayscale luminance (0..255)
        for (i in 0 until totalPixels) {
            val pixel = argb[i]
            val a = (pixel ushr 24) and 0xFF
            if (a < 16) {
                gray[i] = 255
            } else {
                val r = (pixel ushr 16) and 0xFF
                val g = (pixel ushr 8) and 0xFF
                val b = pixel and 0xFF
                gray[i] = (r * 77 + g * 150 + b * 29) shr 8
            }
        }

        // Step 2: Light 3x3 Gaussian blur ([1, 2, 1; 2, 4, 2; 1, 2, 1] / 16)
        for (y in 0 until height) {
            val y0 = max(0, y - 1) * width
            val y1 = y * width
            val y2 = min(height - 1, y + 1) * width
            for (x in 0 until width) {
                val x0 = max(0, x - 1)
                val x2 = min(width - 1, x + 1)
                val sum =
                    gray[y0 + x0] + (gray[y0 + x] shl 1) + gray[y0 + x2] +
                        (gray[y1 + x0] shl 1) + (gray[y1 + x] shl 2) + (gray[y1 + x2] shl 1) +
                        gray[y2 + x0] + (gray[y2 + x] shl 1) + gray[y2 + x2]
                blurred[y1 + x] = sum shr 4
            }
        }

        // Zero border pixels in outMagnitudes
        for (x in 0 until width) {
            outMagnitudes[x] = 0
            outMagnitudes[(height - 1) * width + x] = 0
        }
        for (y in 0 until height) {
            outMagnitudes[y * width] = 0
            outMagnitudes[y * width + (width - 1)] = 0
        }

        // Step 3: 3x3 Sobel operator
        for (y in 1 until height - 1) {
            val rowAbove = (y - 1) * width
            val rowCurr = y * width
            val rowBelow = (y + 1) * width
            for (x in 1 until width - 1) {
                val p00 = blurred[rowAbove + x - 1]
                val p01 = blurred[rowAbove + x]
                val p02 = blurred[rowAbove + x + 1]
                val p10 = blurred[rowCurr + x - 1]
                val p12 = blurred[rowCurr + x + 1]
                val p20 = blurred[rowBelow + x - 1]
                val p21 = blurred[rowBelow + x]
                val p22 = blurred[rowBelow + x + 1]

                val gx = (-p00 + p02) + ((-p10 + p12) shl 1) + (-p20 + p22)
                val gy = (-p00 - (p01 shl 1) - p02) + (p20 + (p21 shl 1) + p22)

                val mag = hypot(gx.toDouble(), gy.toDouble()).roundToInt().coerceAtMost(255)
                outMagnitudes[rowCurr + x] = mag
            }
        }
    }

    private fun ensureCapacity(buffer: IntArray, requiredSize: Int): IntArray {
        return if (buffer.size >= requiredSize) buffer else IntArray(requiredSize)
    }

    private fun obtainOrReuseBitmap(existing: Bitmap?, width: Int, height: Int): Bitmap {
        if (existing != null &&
            !existing.isRecycled &&
            existing.isMutable &&
            existing.width == width &&
            existing.height == height
        ) {
            return existing
        }
        if (existing != null && !existing.isRecycled) {
            existing.recycle()
        }
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    }

    fun clear() {
        cachedSourceKey = null
        cachedEdgeMagnitudes = null
        cachedPreviewEdgeMagnitudes = null
        cachedFullStrengthBucket = -1
        cachedPreviewStrengthBucket = -1
        cachedFullOutlineBitmap?.let { if (!it.isRecycled) it.recycle() }
        cachedFullOutlineBitmap = null
        cachedPreviewOutlineBitmap?.let { if (!it.isRecycled) it.recycle() }
        cachedPreviewOutlineBitmap = null
        scratchArgb = IntArray(0)
        scratchGray = IntArray(0)
        scratchBlurred = IntArray(0)
        scratchOutPixels = IntArray(0)
        scratchPreviewOutPixels = IntArray(0)
    }

    companion object {
        const val PREVIEW_MAX_LONG_SIDE = 640
    }
}
