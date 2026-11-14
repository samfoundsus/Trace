package com.example.ui.home

import android.app.Application
import android.net.Uri
import android.os.StatFs
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.data.repository.ProjectRepository
import com.example.data.repository.RecentImagesRepository
import com.example.domain.model.RecentImageItem
import com.example.domain.model.SavedProject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val recentRepository = RecentImagesRepository.getInstance(application)
    private val projectRepository = ProjectRepository.getInstance(application)

    val projects: StateFlow<List<SavedProject>> = projectRepository.projects.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val recentImages: StateFlow<List<RecentImageItem>> = recentRepository.recentImages.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    private val imageBitmapCache = ConcurrentHashMap<String, ImageBitmap>()
    private val _thumbnails = MutableStateFlow<Map<String, ImageBitmap>>(emptyMap())
    val thumbnails: StateFlow<Map<String, ImageBitmap>> = _thumbnails.asStateFlow()

    init {
        viewModelScope.launch {
            recentRepository.loadRecentImages()
        }
        viewModelScope.launch {
            combine(
                projectRepository.projects,
                recentRepository.recentImages
            ) { savedProjects, recents ->
                savedProjects to recents
            }.collect { (savedProjects, recents) ->
                refreshThumbnails(savedProjects, recents)
            }
        }
    }

    fun onUriPicked(uri: Uri, onReadyToTrace: (String) -> Unit) {
        if (_isImporting.value) return
        _isImporting.value = true
        viewModelScope.launch {
            try {
                if (!hasEnoughStorageSpaceIo()) {
                    _snackbarMessage.value = getApplication<Application>().getString(R.string.snackbar_low_storage)
                    return@launch
                }
                val item = recentRepository.importImageFromUri(uri)
                if (item != null) {
                    onReadyToTrace(item.id)
                } else {
                    _snackbarMessage.value = getApplication<Application>().getString(R.string.snackbar_image_load_error)
                }
            } finally {
                _isImporting.value = false
            }
        }
    }

    fun onRecentImageSelected(imageId: String, onReadyToTrace: (String) -> Unit) {
        viewModelScope.launch {
            recentRepository.touchRecentImage(imageId)
            onReadyToTrace(imageId)
        }
    }

    fun renameProject(projectId: String, newName: String) {
        viewModelScope.launch {
            projectRepository.renameProject(projectId, newName)
        }
    }

    fun duplicateProject(projectId: String) {
        viewModelScope.launch {
            if (!hasEnoughStorageSpaceIo()) {
                _snackbarMessage.value = getApplication<Application>().getString(R.string.snackbar_low_storage)
                return@launch
            }
            val duplicated = projectRepository.duplicateProject(projectId)
            if (duplicated == null) {
                _snackbarMessage.value = getApplication<Application>().getString(R.string.snackbar_low_storage)
            }
        }
    }

    fun deleteProject(projectId: String) {
        viewModelScope.launch {
            projectRepository.deleteProject(projectId)
        }
    }

    fun consumeSnackbarMessage() {
        _snackbarMessage.value = null
    }

    private suspend fun hasEnoughStorageSpaceIo(): Boolean = withContext(Dispatchers.IO) {
        try {
            val dir = getApplication<Application>().filesDir
            val stat = StatFs(dir.absolutePath)
            val availableBytes = stat.availableBlocksLong * stat.blockSizeLong
            availableBytes > 10L * 1024L * 1024L
        } catch (_: Throwable) {
            true
        }
    }

    private suspend fun refreshThumbnails(
        savedProjects: List<SavedProject>,
        recents: List<RecentImageItem>
    ) = withContext(Dispatchers.IO) {
        val entriesToLoad = LinkedHashMap<String, String>(savedProjects.size + recents.size)
        for (proj in savedProjects) {
            entriesToLoad["project_${proj.id}_${proj.imagePath.hashCode()}"] = proj.imagePath
        }
        for (rec in recents) {
            entriesToLoad["recent_${rec.id}"] = rec.filePath
        }

        val activeKeys = entriesToLoad.keys
        val iterator = imageBitmapCache.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.key !in activeKeys) {
                iterator.remove()
            }
        }

        val updatedMap = HashMap<String, ImageBitmap>(entriesToLoad.size)
        for (proj in savedProjects) {
            val cacheKey = "project_${proj.id}_${proj.imagePath.hashCode()}"
            val uiKey = "project_${proj.id}"
            val cachedImg = imageBitmapCache[cacheKey]
            val memBmp = recentRepository.getMemoryCachedThumbnail(cacheKey)
            if (cachedImg != null && memBmp != null && !memBmp.isRecycled) {
                updatedMap[uiKey] = cachedImg
            } else {
                val decoded = recentRepository.loadThumbnailBitmap(
                    filePath = proj.imagePath,
                    maxDimensionPx = RecentImagesRepository.THUMBNAIL_MAX_PX,
                    cacheKey = cacheKey
                )
                if (decoded != null && !decoded.isRecycled) {
                    val img = decoded.asImageBitmap()
                    imageBitmapCache[cacheKey] = img
                    updatedMap[uiKey] = img
                }
            }
        }

        for (rec in recents) {
            val cacheKey = "recent_${rec.id}"
            val cachedImg = imageBitmapCache[cacheKey]
            val memBmp = recentRepository.getMemoryCachedThumbnail(cacheKey)
            if (cachedImg != null && memBmp != null && !memBmp.isRecycled) {
                updatedMap[cacheKey] = cachedImg
            } else {
                val decoded = recentRepository.loadThumbnailBitmap(
                    filePath = rec.filePath,
                    maxDimensionPx = RecentImagesRepository.THUMBNAIL_MAX_PX,
                    cacheKey = cacheKey
                )
                if (decoded != null && !decoded.isRecycled) {
                    val img = decoded.asImageBitmap()
                    imageBitmapCache[cacheKey] = img
                    updatedMap[cacheKey] = img
                }
            }
        }

        _thumbnails.value = updatedMap
    }

    override fun onCleared() {
        super.onCleared()
        imageBitmapCache.clear()
    }
}
