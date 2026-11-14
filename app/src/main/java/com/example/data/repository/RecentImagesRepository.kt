package com.example.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.LruCache
import com.example.domain.model.RecentImageItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

class RecentImagesRepository private constructor(
    private val appContext: Context
) {
    private val mutex = Mutex()

    // Directory paths resolved lazily without synchronous disk I/O in getters
    private val imagesDir: File
        get() = File(appContext.filesDir, "recent_images")

    private val thumbnailsDir: File
        get() = File(appContext.filesDir, "thumbnails")

    private val metadataFile: File
        get() = File(appContext.filesDir, "recent_images_index.json")

    // In-memory LruCache (up to 16 MB) for ~400 px Home thumbnails so we never re-decode
    private val thumbnailMemoryCache = object : LruCache<String, Bitmap>(16 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            return (value.allocationByteCount / 1024).coerceAtLeast(1)
        }
    }

    private val _recentImages = MutableStateFlow<List<RecentImageItem>>(emptyList())
    val recentImages: StateFlow<List<RecentImageItem>> = _recentImages.asStateFlow()

    suspend fun loadRecentImages() = withContext(Dispatchers.IO) {
        mutex.withLock {
            _recentImages.value = readMetadataLocked()
        }
    }

    suspend fun clearAllRecentImages() = withContext(Dispatchers.IO) {
        mutex.withLock {
            imagesDir.listFiles()?.forEach { file ->
                runCatching { file.delete() }
            }
            thumbnailsDir.listFiles()?.forEach { file ->
                if (file.name.startsWith("thumb_recent_")) {
                    runCatching { file.delete() }
                }
            }
            synchronized(thumbnailMemoryCache) {
                thumbnailMemoryCache.evictAll()
            }
            runCatching { metadataFile.delete() }
            _recentImages.value = emptyList()
        }
    }

    /**
     * Imports a gallery [Uri], downscales it so the long side is at most [MAX_LONG_SIDE] (2048px),
     * generates a dedicated ~400px disk + memory thumbnail, applies EXIF rotation if present,
     * recycles intermediate bitmaps, saves locally in app storage, and keeps only the last [MAX_RECENT_COUNT] (10) items.
     */
    suspend fun importImageFromUri(uri: Uri): RecentImageItem? = withContext(Dispatchers.IO) {
        val downscaled = decodeAndDownscaleUri(uri, MAX_LONG_SIDE) ?: return@withContext null
        try {
            val id = UUID.randomUUID().toString()
            val dir = imagesDir.apply { if (!exists()) mkdirs() }
            val outFile = File(dir, "trace_$id.png")
            FileOutputStream(outFile).use { out ->
                downscaled.compress(Bitmap.CompressFormat.PNG, 100, out)
                out.flush()
            }

            // Pre-create the ~400px disk & memory thumbnail right away from the already-decoded bitmap
            createAndCacheThumbnailLocked(
                cacheKey = "recent_$id",
                sourceBitmap = downscaled,
                maxDimensionPx = THUMBNAIL_MAX_PX
            )

            val newItem = RecentImageItem(
                id = id,
                filePath = outFile.absolutePath,
                createdAt = System.currentTimeMillis(),
                width = downscaled.width,
                height = downscaled.height
            )

            mutex.withLock {
                val current = readMetadataLocked().toMutableList()
                current.add(0, newItem)
                val trimmed = if (current.size > MAX_RECENT_COUNT) {
                    val removed = current.subList(MAX_RECENT_COUNT, current.size)
                    removed.forEach { oldItem ->
                        runCatching { File(oldItem.filePath).delete() }
                        runCatching { File(thumbnailsDir, "thumb_recent_${oldItem.id}.jpg").delete() }
                        synchronized(thumbnailMemoryCache) {
                            thumbnailMemoryCache.remove("recent_${oldItem.id}")
                        }
                    }
                    current.take(MAX_RECENT_COUNT)
                } else {
                    current
                }
                writeMetadataLocked(trimmed)
                _recentImages.value = trimmed
            }
            newItem
        } finally {
            if (!downscaled.isRecycled) {
                downscaled.recycle()
            }
        }
    }

    suspend fun touchRecentImage(imageId: String): RecentImageItem? = withContext(Dispatchers.IO) {
        mutex.withLock {
            val current = readMetadataLocked().toMutableList()
            val index = current.indexOfFirst { it.id == imageId }
            if (index == -1) return@withLock null
            val item = current.removeAt(index).copy(createdAt = System.currentTimeMillis())
            current.add(0, item)
            writeMetadataLocked(current)
            _recentImages.value = current
            item
        }
    }

    suspend fun getRecentItemById(imageId: String): RecentImageItem? = withContext(Dispatchers.IO) {
        mutex.withLock {
            val cached = _recentImages.value.firstOrNull { it.id == imageId }
            if (cached != null && File(cached.filePath).exists()) {
                return@withLock cached
            }
            val loaded = readMetadataLocked()
            _recentImages.value = loaded
            loaded.firstOrNull { it.id == imageId }
        }
    }

    suspend fun loadTraceBitmap(filePath: String): Bitmap? = withContext(Dispatchers.IO) {
        val file = File(filePath)
        if (!file.exists()) return@withContext null
        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        BitmapFactory.decodeFile(file.absolutePath, options)
    }

    fun getMemoryCachedThumbnail(cacheKey: String): Bitmap? {
        synchronized(thumbnailMemoryCache) {
            val cached = thumbnailMemoryCache.get(cacheKey)
            if (cached != null && !cached.isRecycled) {
                return cached
            }
        }
        return null
    }

    /**
     * Loads a small (~400px) thumbnail for Home using a two-tier cache:
     * 1. In-memory [LruCache]
     * 2. Dedicated small disk thumbnail in `filesDir/thumbnails`
     * 3. Downsampled decode of [filePath] (persisting the small thumbnail to disk for subsequent launches)
     */
    suspend fun loadThumbnailBitmap(
        filePath: String,
        maxDimensionPx: Int = THUMBNAIL_MAX_PX,
        cacheKey: String = filePath
    ): Bitmap? = withContext(Dispatchers.IO) {
        getMemoryCachedThumbnail(cacheKey)?.let { return@withContext it }

        val safeKey = cacheKey.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val thumbDir = thumbnailsDir.apply { if (!exists()) mkdirs() }
        val diskThumbFile = File(thumbDir, "thumb_$safeKey.png")

        if (diskThumbFile.exists() && diskThumbFile.length() > 0L) {
            val diskDecoded = runCatching {
                val opts = BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                BitmapFactory.decodeFile(diskThumbFile.absolutePath, opts)
            }.getOrNull()
            if (diskDecoded != null) {
                synchronized(thumbnailMemoryCache) {
                    thumbnailMemoryCache.put(cacheKey, diskDecoded)
                }
                return@withContext diskDecoded
            }
        }

        val sourceFile = File(filePath)
        if (!sourceFile.exists()) return@withContext null

        val boundsOptions = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(sourceFile.absolutePath, boundsOptions)
        val srcW = boundsOptions.outWidth
        val srcH = boundsOptions.outHeight
        if (srcW <= 0 || srcH <= 0) return@withContext null

        var sampleSize = 1
        val longSide = max(srcW, srcH)
        while (longSide / (sampleSize * 2) >= maxDimensionPx) {
            sampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val sampled = BitmapFactory.decodeFile(sourceFile.absolutePath, decodeOptions)
            ?: return@withContext null

        val sampledLongSide = max(sampled.width, sampled.height)
        val thumbBitmap = if (sampledLongSide > maxDimensionPx) {
            val scale = maxDimensionPx.toFloat() / sampledLongSide.toFloat()
            val targetW = max(1, (sampled.width * scale).roundToInt())
            val targetH = max(1, (sampled.height * scale).roundToInt())
            val scaled = Bitmap.createScaledBitmap(sampled, targetW, targetH, true)
            if (scaled !== sampled && !sampled.isRecycled) {
                sampled.recycle()
            }
            scaled
        } else {
            sampled
        }

        runCatching {
            FileOutputStream(diskThumbFile).use { out ->
                thumbBitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
                out.flush()
            }
        }

        synchronized(thumbnailMemoryCache) {
            thumbnailMemoryCache.put(cacheKey, thumbBitmap)
        }
        thumbBitmap
    }

    private fun createAndCacheThumbnailLocked(
        cacheKey: String,
        sourceBitmap: Bitmap,
        maxDimensionPx: Int
    ) {
        if (sourceBitmap.isRecycled) return
        val longSide = max(sourceBitmap.width, sourceBitmap.height)
        val thumbBitmap = if (longSide > maxDimensionPx) {
            val ratio = maxDimensionPx.toFloat() / longSide.toFloat()
            val targetW = max(1, (sourceBitmap.width * ratio).roundToInt())
            val targetH = max(1, (sourceBitmap.height * ratio).roundToInt())
            Bitmap.createScaledBitmap(sourceBitmap, targetW, targetH, true)
        } else {
            sourceBitmap.copy(Bitmap.Config.ARGB_8888, false)
        } ?: return

        val safeKey = cacheKey.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val thumbDir = thumbnailsDir.apply { if (!exists()) mkdirs() }
        val diskThumbFile = File(thumbDir, "thumb_$safeKey.png")
        runCatching {
            FileOutputStream(diskThumbFile).use { out ->
                thumbBitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
                out.flush()
            }
        }
        synchronized(thumbnailMemoryCache) {
            thumbnailMemoryCache.put(cacheKey, thumbBitmap)
        }
    }

    private fun decodeAndDownscaleUri(uri: Uri, maxLongSide: Int): Bitmap? {
        val contentResolver = appContext.contentResolver

        val boundsOptions = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, boundsOptions)
        }
        val rawWidth = boundsOptions.outWidth
        val rawHeight = boundsOptions.outHeight
        if (rawWidth <= 0 || rawHeight <= 0) return null

        var sampleSize = 1
        val rawLongSide = max(rawWidth, rawHeight)
        while (rawLongSide / (sampleSize * 2) >= maxLongSide) {
            sampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        val sampledBitmap = contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOptions)
        } ?: return null

        val exifRotation = runCatching {
            contentResolver.openInputStream(uri)?.use { exifStream ->
                val exif = ExifInterface(exifStream)
                when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
        }.getOrDefault(0f)

        val rotatedW = if (exifRotation == 90f || exifRotation == 270f) sampledBitmap.height else sampledBitmap.width
        val rotatedH = if (exifRotation == 90f || exifRotation == 270f) sampledBitmap.width else sampledBitmap.height
        val currentLongSide = max(rotatedW, rotatedH)
        val exactScale = if (currentLongSide > maxLongSide) {
            maxLongSide.toFloat() / currentLongSide.toFloat()
        } else {
            1f
        }

        if (exactScale == 1f && exifRotation == 0f) {
            return sampledBitmap
        }

        val matrix = Matrix().apply {
            if (exifRotation != 0f) {
                postRotate(exifRotation)
            }
            if (exactScale != 1f) {
                postScale(exactScale, exactScale)
            }
        }

        val transformed = Bitmap.createBitmap(
            sampledBitmap,
            0,
            0,
            sampledBitmap.width,
            sampledBitmap.height,
            matrix,
            true
        )
        if (transformed !== sampledBitmap && !sampledBitmap.isRecycled) {
            sampledBitmap.recycle()
        }

        val finalLongSide = max(transformed.width, transformed.height)
        if (finalLongSide <= maxLongSide) {
            return transformed
        }
        val ratio = maxLongSide.toFloat() / finalLongSide.toFloat()
        val targetW = max(1, (transformed.width * ratio).roundToInt())
        val targetH = max(1, (transformed.height * ratio).roundToInt())
        val scaled = Bitmap.createScaledBitmap(transformed, targetW, targetH, true)
        if (scaled !== transformed && !transformed.isRecycled) {
            transformed.recycle()
        }
        return scaled
    }

    private fun readMetadataLocked(): List<RecentImageItem> {
        if (!metadataFile.exists()) return emptyList()
        return runCatching {
            val raw = metadataFile.readText()
            val array = JSONArray(raw)
            val result = mutableListOf<RecentImageItem>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val filePath = obj.optString("filePath")
                if (filePath.isNotBlank() && File(filePath).exists()) {
                    result.add(
                        RecentImageItem(
                            id = obj.getString("id"),
                            filePath = filePath,
                            createdAt = obj.optLong("createdAt", 0L),
                            width = obj.optInt("width", 0),
                            height = obj.optInt("height", 0)
                        )
                    )
                }
            }
            result.take(MAX_RECENT_COUNT)
        }.getOrDefault(emptyList())
    }

    private fun writeMetadataLocked(items: List<RecentImageItem>) {
        runCatching {
            val array = JSONArray()
            items.take(MAX_RECENT_COUNT).forEach { item ->
                val obj = JSONObject().apply {
                    put("id", item.id)
                    put("filePath", item.filePath)
                    put("createdAt", item.createdAt)
                    put("width", item.width)
                    put("height", item.height)
                }
                array.put(obj)
            }
            metadataFile.writeText(array.toString())
        }
    }

    companion object {
        const val MAX_LONG_SIDE = 2048
        const val THUMBNAIL_MAX_PX = 400
        const val MAX_RECENT_COUNT = 10

        @Volatile
        private var instance: RecentImagesRepository? = null

        fun getInstance(context: Context): RecentImagesRepository {
            return instance ?: synchronized(this) {
                instance ?: RecentImagesRepository(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }
}
