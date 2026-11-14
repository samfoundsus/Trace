package com.example.data.timelapse

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.view.Surface
import com.example.domain.model.TimeLapseQuality
import com.example.domain.usecase.TimeLapseFrameScheduler
import java.io.File
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

data class OverlayCompositeSpec(
    val bitmap: Bitmap,
    val offsetX: Float,
    val offsetY: Float,
    val scale: Float,
    val rotation: Float,
    val opacity: Float,
    val flipHorizontal: Boolean,
    val flipVertical: Boolean,
    val referenceViewWidth: Int,
    val referenceViewHeight: Int
)

/**
 * Low-RAM streaming H.264 MP4 encoder using [MediaCodec] with an input [Surface] and [MediaMuxer].
 * Encodes each captured frame immediately and writes sample data directly to a temporary MP4 file,
 * then publishes the finalized MP4 to `Movies/Trace` via [MediaStore] (no storage permission needed on API 29+).
 */
class TimeLapseVideoEncoder(
    private val context: Context,
    private val quality: TimeLapseQuality,
    private val outputFps: Int = TimeLapseFrameScheduler.DEFAULT_OUTPUT_FPS
) {
    private val lock = Any()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val cameraMatrix = Matrix()
    private val overlayMatrix = Matrix()
    private val bufferInfo = MediaCodec.BufferInfo()

    private var encoder: MediaCodec? = null
    private var inputSurface: Surface? = null
    private var muxer: MediaMuxer? = null
    private var tempOutputFile: File? = null

    private var videoWidth: Int = 0
    private var videoHeight: Int = 0
    private var trackIndex: Int = -1
    private var muxerStarted: Boolean = false
    private var isReleased: Boolean = false
    private var encodedFrameCount: Int = 0
    private var lastPtsUs: Long = -1L

    /**
     * Encodes a single camera [frameBitmap] (and optional [overlaySpec]) into the MP4 stream.
     * Returns `false` if free disk space is critically low and recording should be finalized.
     */
    fun encodeFrame(
        frameBitmap: Bitmap,
        overlaySpec: OverlayCompositeSpec?
    ): Boolean = synchronized(lock) {
        if (isReleased || frameBitmap.isRecycled) return false

        if (encoder == null) {
            val (w, h) = TimeLapseFrameScheduler.computeOutputDimensions(
                sourceWidth = frameBitmap.width,
                sourceHeight = frameBitmap.height,
                quality = quality
            )
            if (!initializeEncoderLocked(w, h)) {
                return false
            }
        }

        val outFile = tempOutputFile ?: return false
        if (outFile.usableSpace in 1 until MIN_FREE_STORAGE_BYTES) {
            return false
        }

        val surface = inputSurface ?: return false
        return runCatching {
            val canvas = surface.lockCanvas(null)
            try {
                drawFrameToCanvas(canvas, frameBitmap, overlaySpec)
            } finally {
                surface.unlockCanvasAndPost(canvas)
            }
            drainEncoderLocked(endOfStream = false)
            true
        }.getOrDefault(false)
    }

    /**
     * Finalizes the H.264 stream and muxer, publishes the MP4 to `Movies/Trace` in [MediaStore],
     * and cleans up all native resources and temporary files.
     */
    fun finalizeAndSaveToMediaStore(): Uri? = synchronized(lock) {
        if (isReleased) return null
        isReleased = true

        // If 1 frame was encoded, duplicate it once so all video players render a valid duration
        if (encodedFrameCount == 1 && inputSurface != null) {
            runCatching {
                drainEncoderLocked(endOfStream = false)
            }
        }

        runCatching {
            drainEncoderLocked(endOfStream = true)
        }

        runCatching {
            encoder?.stop()
        }
        runCatching {
            encoder?.release()
        }
        encoder = null

        runCatching {
            inputSurface?.release()
        }
        inputSurface = null

        var muxerSuccess = false
        if (muxerStarted) {
            muxerSuccess = runCatching {
                muxer?.stop()
                true
            }.getOrDefault(false)
        }
        runCatching {
            muxer?.release()
        }
        muxer = null
        muxerStarted = false

        val tempFile = tempOutputFile
        tempOutputFile = null

        if (tempFile == null || !tempFile.exists() || tempFile.length() == 0L || encodedFrameCount == 0 || !muxerSuccess) {
            runCatching { tempFile?.delete() }
            return null
        }

        val publishedUri = publishFileToMoviesTrace(tempFile)
        runCatching { tempFile.delete() }
        return publishedUri
    }

    private fun initializeEncoderLocked(width: Int, height: Int): Boolean {
        return runCatching {
            videoWidth = width
            videoHeight = height

            val cacheDir = File(context.cacheDir, "timelapse_temp").apply {
                if (!exists()) mkdirs()
            }
            val tempFile = File(cacheDir, "timelapse_active_${System.currentTimeMillis()}.mp4")
            tempOutputFile = tempFile

            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
                setInteger(
                    MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
                )
                setInteger(MediaFormat.KEY_BIT_RATE, quality.bitrateBps)
                setInteger(MediaFormat.KEY_FRAME_RATE, outputFps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }

            val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val surface = codec.createInputSurface()
            codec.start()

            val mediaMuxer = MediaMuxer(
                tempFile.absolutePath,
                MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
            )

            encoder = codec
            inputSurface = surface
            muxer = mediaMuxer
            trackIndex = -1
            muxerStarted = false
            encodedFrameCount = 0
            lastPtsUs = -1L
            true
        }.getOrElse {
            releaseQuietlyLocked()
            false
        }
    }

    private fun drawFrameToCanvas(
        canvas: Canvas,
        cameraBitmap: Bitmap,
        overlaySpec: OverlayCompositeSpec?
    ) {
        canvas.drawColor(Color.BLACK)

        val dstW = videoWidth.toFloat()
        val dstH = videoHeight.toFloat()
        val srcW = cameraBitmap.width.toFloat()
        val srcH = cameraBitmap.height.toFloat()

        if (srcW > 0f && srcH > 0f) {
            val scale = max(dstW / srcW, dstH / srcH)
            val dx = (dstW - srcW * scale) * 0.5f
            val dy = (dstH - srcH * scale) * 0.5f
            cameraMatrix.reset()
            cameraMatrix.postScale(scale, scale)
            cameraMatrix.postTranslate(dx, dy)
            canvas.drawBitmap(cameraBitmap, cameraMatrix, paint)
        }

        if (overlaySpec != null && !overlaySpec.bitmap.isRecycled) {
            val ovBmp = overlaySpec.bitmap
            val ovW = ovBmp.width.toFloat()
            val ovH = ovBmp.height.toFloat()
            if (ovW > 0f && ovH > 0f) {
                val refW = if (overlaySpec.referenceViewWidth > 0) {
                    overlaySpec.referenceViewWidth.toFloat()
                } else {
                    dstW
                }
                val refH = if (overlaySpec.referenceViewHeight > 0) {
                    overlaySpec.referenceViewHeight.toFloat()
                } else {
                    dstH
                }

                // Replicate ContentScale.Fit inside the canvas with user's transform
                val fitScale = min((dstW * 0.9f) / ovW, (dstH * 0.9f) / ovH)
                val centerX = dstW * 0.5f
                val centerY = dstH * 0.5f
                val scaledOffsetX = overlaySpec.offsetX * (dstW / refW)
                val scaledOffsetY = overlaySpec.offsetY * (dstH / refH)

                val sx = fitScale * overlaySpec.scale * if (overlaySpec.flipHorizontal) -1f else 1f
                val sy = fitScale * overlaySpec.scale * if (overlaySpec.flipVertical) -1f else 1f

                overlayMatrix.reset()
                overlayMatrix.postTranslate(-ovW * 0.5f, -ovH * 0.5f)
                overlayMatrix.postScale(sx, sy)
                overlayMatrix.postRotate(overlaySpec.rotation)
                overlayMatrix.postTranslate(centerX + scaledOffsetX, centerY + scaledOffsetY)

                overlayPaint.alpha = (overlaySpec.opacity.coerceIn(0.1f, 1.0f) * 255f).toInt()
                canvas.drawBitmap(ovBmp, overlayMatrix, overlayPaint)
            }
        }
    }

    private fun drainEncoderLocked(endOfStream: Boolean) {
        val codec = encoder ?: return
        val mediaMuxer = muxer ?: return

        if (endOfStream) {
            runCatching { codec.signalEndOfInputStream() }
        }

        var spinCount = 0
        val maxSpins = if (endOfStream) 80 else 25

        while (spinCount < maxSpins) {
            val outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000L)
            if (outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (!endOfStream) break
                spinCount++
            } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                if (!muxerStarted) {
                    val newFormat = codec.outputFormat
                    trackIndex = mediaMuxer.addTrack(newFormat)
                    mediaMuxer.start()
                    muxerStarted = true
                }
            } else if (outputBufferIndex >= 0) {
                val encodedData = codec.getOutputBuffer(outputBufferIndex)
                if (encodedData != null) {
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        bufferInfo.size = 0
                    }

                    if (bufferInfo.size > 0 && muxerStarted && trackIndex >= 0) {
                        encodedData.position(bufferInfo.offset)
                        encodedData.limit(bufferInfo.offset + bufferInfo.size)

                        // Assign 30fps presentation timestamp so playback is smooth 30 fps
                        val targetPtsUs = TimeLapseFrameScheduler.computePresentationTimeUs(
                            frameIndex = encodedFrameCount,
                            outputFps = outputFps
                        )
                        val monotonicPtsUs = max(lastPtsUs + 1_000L, targetPtsUs)
                        bufferInfo.presentationTimeUs = monotonicPtsUs
                        lastPtsUs = monotonicPtsUs

                        mediaMuxer.writeSampleData(trackIndex, encodedData, bufferInfo)
                        encodedFrameCount++
                    }
                }
                codec.releaseOutputBuffer(outputBufferIndex, false)

                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    break
                }
            }
        }
    }

    private fun publishFileToMoviesTrace(tempFile: File): Uri? {
        return runCatching {
            val resolver = context.contentResolver
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val fileName = "Trace_$timestamp.mp4"
            val relativePath = "${Environment.DIRECTORY_MOVIES}/Trace"

            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, relativePath)
                put(MediaStore.Video.Media.DATE_ADDED, System.currentTimeMillis() / 1000L)
                put(MediaStore.Video.Media.DATE_MODIFIED, System.currentTimeMillis() / 1000L)
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }

            val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val itemUri = resolver.insert(collection, values) ?: return null

            resolver.openOutputStream(itemUri)?.use { outputStream ->
                FileInputStream(tempFile).use { inputStream ->
                    inputStream.copyTo(outputStream, bufferSize = 64 * 1024)
                    outputStream.flush()
                }
            }

            val completedValues = ContentValues().apply {
                put(MediaStore.Video.Media.IS_PENDING, 0)
            }
            resolver.update(itemUri, completedValues, null, null)
            itemUri
        }.getOrNull()
    }

    private fun releaseQuietlyLocked() {
        runCatching { encoder?.release() }
        encoder = null
        runCatching { inputSurface?.release() }
        inputSurface = null
        runCatching { muxer?.release() }
        muxer = null
        runCatching { tempOutputFile?.delete() }
        tempOutputFile = null
    }

    companion object {
        private const val MIN_FREE_STORAGE_BYTES = 15L * 1024L * 1024L // 15 MB safety floor
    }
}
