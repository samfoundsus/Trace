package com.example.ui.trace

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.os.StatFs
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.data.repository.ProjectRepository
import com.example.data.repository.RecentImagesRepository
import com.example.data.repository.SettingsRepository
import com.example.data.timelapse.OverlayCompositeSpec
import com.example.data.timelapse.TimeLapseRecordingStatus
import com.example.data.timelapse.TimeLapseSessionManager
import com.example.domain.model.ActiveSheet
import com.example.domain.model.AppSettings
import com.example.domain.model.SavedProject
import com.example.domain.model.TimeLapseQuality
import com.example.domain.model.TraceState
import com.example.domain.usecase.ArSupportStatus
import com.example.domain.usecase.OutlineProcessor
import com.example.domain.usecase.PinnedModeLogic
import com.example.service.TimeLapseRecordingService
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TraceViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    private val recentRepository = RecentImagesRepository.getInstance(application)
    private val projectRepository = ProjectRepository.getInstance(application)
    private val settingsRepository = SettingsRepository.getInstance(application)
    private val outlineProcessor = OutlineProcessor()

    private var sourceBitmap: Bitmap? = null
    private var currentOutlineRawBitmap: Bitmap? = null
    private var outlineJob: Job? = null
    private var autoSaveJob: Job? = null
    private var countdownJob: Job? = null
    private var initializedSession = false

    private var referenceViewWidth: Int = 1080
    private var referenceViewHeight: Int = 1920

    private val _sourceImageBitmap = MutableStateFlow<ImageBitmap?>(null)
    val sourceImageBitmap: StateFlow<ImageBitmap?> = _sourceImageBitmap.asStateFlow()

    private val _outlineImageBitmap = MutableStateFlow<ImageBitmap?>(null)
    val outlineImageBitmap: StateFlow<ImageBitmap?> = _outlineImageBitmap.asStateFlow()

    private val _activeRawBitmap = MutableStateFlow<Bitmap?>(null)
    val activeRawBitmap: StateFlow<Bitmap?> = _activeRawBitmap.asStateFlow()

    val timeLapseStatus: StateFlow<TimeLapseRecordingStatus> = TimeLapseSessionManager.status

    private val _uiState = MutableStateFlow(
        TraceState(
            projectId = savedStateHandle[KEY_PROJECT_ID] ?: "",
            projectName = savedStateHandle[KEY_PROJECT_NAME] ?: "",
            imageId = savedStateHandle[KEY_IMAGE_ID] ?: "",
            imagePath = savedStateHandle[KEY_IMAGE_PATH] ?: "",
            offsetX = savedStateHandle[KEY_OFFSET_X] ?: 0f,
            offsetY = savedStateHandle[KEY_OFFSET_Y] ?: 0f,
            scale = savedStateHandle[KEY_SCALE] ?: 1f,
            rotation = savedStateHandle[KEY_ROTATION] ?: 0f,
            opacity = savedStateHandle[KEY_OPACITY] ?: 0.55f,
            isLocked = savedStateHandle[KEY_IS_LOCKED] ?: false,
            isControlsHidden = savedStateHandle[KEY_IS_CONTROLS_HIDDEN] ?: false,
            isOutlineEnabled = savedStateHandle[KEY_IS_OUTLINE_ENABLED] ?: false,
            outlineStrength = savedStateHandle[KEY_OUTLINE_STRENGTH] ?: 0.5f,
            isFlashlightOn = savedStateHandle[KEY_IS_FLASHLIGHT_ON] ?: false,
            flipHorizontal = savedStateHandle[KEY_FLIP_HORIZONTAL] ?: false,
            flipVertical = savedStateHandle[KEY_FLIP_VERTICAL] ?: false,
            isGridEnabled = savedStateHandle[KEY_IS_GRID_ENABLED] ?: false,
            gridCellSizeDp = savedStateHandle[KEY_GRID_CELL_SIZE_DP] ?: 48f,
            isPinnedMode = savedStateHandle[KEY_IS_PINNED_MODE] ?: false,
            paperWidthCm = PinnedModeLogic.sanitizePaperWidthCm(
                savedStateHandle[KEY_PAPER_WIDTH_CM] ?: PinnedModeLogic.DEFAULT_PAPER_WIDTH_CM
            )
        )
    )
    val uiState: StateFlow<TraceState> = _uiState.asStateFlow()

    init {
        refreshArSupport()

        viewModelScope.launch {
            settingsRepository.settingsFlow.collect { appSettings ->
                _uiState.update { current ->
                    current.copy(
                        keepScreenOn = appSettings.keepScreenOn,
                        showFirstRunHint = !appSettings.hasSeenTraceHint,
                        timeLapseIntervalSeconds = appSettings.timeLapseIntervalSeconds,
                        timeLapseQuality = appSettings.timeLapseQuality,
                        timeLapseIncludeOverlay = appSettings.timeLapseIncludeOverlay,
                        timeLapseCountdownSeconds = appSettings.timeLapseCountdownSeconds
                    )
                }
                syncOverlaySpecToSessionManager()
            }
        }
        viewModelScope.launch {
            TimeLapseSessionManager.hasFlashUnit.collect { hasFlash ->
                if (hasFlash != _uiState.value.hasFlashUnit) {
                    setHasFlashUnit(hasFlash)
                }
            }
        }
    }

    fun refreshArSupport() {
        val status = PinnedModeLogic.checkArSupport(getApplication())
        val supported = status != ArSupportStatus.UNSUPPORTED
        _uiState.update { it.copy(isArSupported = supported) }
    }

    fun updateViewportSize(width: Int, height: Int) {
        if (width > 0 && height > 0) {
            referenceViewWidth = width
            referenceViewHeight = height
            syncOverlaySpecToSessionManager()
        }
    }

    fun initializeSession(imageId: String, projectId: String = "") {
        refreshArSupport()
        if (initializedSession &&
            _uiState.value.imageId == imageId &&
            _uiState.value.projectId == projectId &&
            _sourceImageBitmap.value != null
        ) {
            return
        }
        val restoredFromSavedState = savedStateHandle.contains(KEY_SESSION_INITIALIZED)
        initializedSession = true
        savedStateHandle[KEY_SESSION_INITIALIZED] = true

        viewModelScope.launch {
            val appSettings = settingsRepository.settingsFlow.first()

            if (projectId.isNotBlank()) {
                val savedProject = projectRepository.getProjectById(projectId)
                if (savedProject != null) {
                    if (!restoredFromSavedState) {
                        applyProjectToState(savedProject, appSettings.hideControlsOnStart)
                    } else {
                        _uiState.update {
                            it.copy(
                                projectId = savedProject.id,
                                projectName = savedProject.name,
                                imagePath = savedProject.imagePath
                            )
                        }
                    }
                    loadBitmapFromPath(
                        sourceKey = "project_${savedProject.id}",
                        filePath = _uiState.value.imagePath.ifBlank { savedProject.imagePath }
                    )
                    return@launch
                }
            }

            if (imageId.isNotBlank()) {
                if (!restoredFromSavedState) {
                    val defaultOpacity = appSettings.defaultOpacity.coerceIn(0.1f, 1.0f)
                    val hideOnStart = appSettings.hideControlsOnStart
                    savedStateHandle[KEY_IMAGE_ID] = imageId
                    savedStateHandle[KEY_OPACITY] = defaultOpacity
                    savedStateHandle[KEY_IS_CONTROLS_HIDDEN] = hideOnStart
                    _uiState.update {
                        it.copy(
                            imageId = imageId,
                            opacity = defaultOpacity,
                            isControlsHidden = hideOnStart
                        )
                    }
                } else {
                    savedStateHandle[KEY_IMAGE_ID] = imageId
                    _uiState.update { it.copy(imageId = imageId) }
                }

                val item = recentRepository.getRecentItemById(imageId) ?: return@launch
                savedStateHandle[KEY_IMAGE_PATH] = item.filePath
                _uiState.update { it.copy(imagePath = item.filePath) }
                loadBitmapFromPath(sourceKey = item.id, filePath = item.filePath)
            }
        }
    }

    fun initializeWithImageId(imageId: String) {
        initializeSession(imageId = imageId, projectId = "")
    }

    private fun applyProjectToState(project: SavedProject, hideControlsOnStart: Boolean) {
        savedStateHandle[KEY_PROJECT_ID] = project.id
        savedStateHandle[KEY_PROJECT_NAME] = project.name
        savedStateHandle[KEY_IMAGE_PATH] = project.imagePath
        savedStateHandle[KEY_OFFSET_X] = project.offsetX
        savedStateHandle[KEY_OFFSET_Y] = project.offsetY
        savedStateHandle[KEY_SCALE] = project.scale
        savedStateHandle[KEY_ROTATION] = project.rotation
        savedStateHandle[KEY_OPACITY] = project.opacity
        savedStateHandle[KEY_FLIP_HORIZONTAL] = project.flipHorizontal
        savedStateHandle[KEY_FLIP_VERTICAL] = project.flipVertical
        savedStateHandle[KEY_IS_OUTLINE_ENABLED] = project.isOutlineEnabled
        savedStateHandle[KEY_OUTLINE_STRENGTH] = project.outlineStrength
        savedStateHandle[KEY_IS_GRID_ENABLED] = project.isGridEnabled
        savedStateHandle[KEY_GRID_CELL_SIZE_DP] = project.gridCellSizeDp
        savedStateHandle[KEY_IS_CONTROLS_HIDDEN] = hideControlsOnStart

        _uiState.update {
            it.copy(
                projectId = project.id,
                projectName = project.name,
                imagePath = project.imagePath,
                offsetX = project.offsetX,
                offsetY = project.offsetY,
                scale = project.scale,
                rotation = project.rotation,
                opacity = project.opacity,
                flipHorizontal = project.flipHorizontal,
                flipVertical = project.flipVertical,
                isOutlineEnabled = project.isOutlineEnabled,
                outlineStrength = project.outlineStrength,
                isGridEnabled = project.isGridEnabled,
                gridCellSizeDp = project.gridCellSizeDp,
                isControlsHidden = hideControlsOnStart
            )
        }
    }

    private suspend fun loadBitmapFromPath(sourceKey: String, filePath: String) {
        val decoded = recentRepository.loadTraceBitmap(filePath)
        if (decoded == null) {
            showSnackbarMessage(getApplication<Application>().getString(R.string.snackbar_image_load_error))
            return
        }
        val previous = sourceBitmap
        sourceBitmap = decoded
        _sourceImageBitmap.value = decoded.asImageBitmap()

        if (previous != null && previous !== decoded && !previous.isRecycled) {
            previous.recycle()
        }

        outlineProcessor.precomputeEdges(sourceKey, decoded)
        val outlineBmp = outlineProcessor.getOutlineBitmap(
            sourceKey = sourceKey,
            source = decoded,
            strength = _uiState.value.outlineStrength
        )
        currentOutlineRawBitmap = outlineBmp
        _outlineImageBitmap.value = outlineBmp.asImageBitmap()
        syncOverlaySpecToSessionManager()
    }

    private fun syncOverlaySpecToSessionManager() {
        val current = _uiState.value
        val activeBmp = if (current.isOutlineEnabled && currentOutlineRawBitmap != null) {
            currentOutlineRawBitmap
        } else {
            sourceBitmap
        }
        _activeRawBitmap.value = activeBmp?.takeIf { !it.isRecycled }

        if (activeBmp != null && !activeBmp.isRecycled) {
            TimeLapseSessionManager.updateOverlaySpec(
                OverlayCompositeSpec(
                    bitmap = activeBmp,
                    offsetX = current.offsetX,
                    offsetY = current.offsetY,
                    scale = current.scale,
                    rotation = current.rotation,
                    opacity = current.opacity,
                    flipHorizontal = current.flipHorizontal,
                    flipVertical = current.flipVertical,
                    referenceViewWidth = referenceViewWidth,
                    referenceViewHeight = referenceViewHeight
                )
            )
        } else {
            TimeLapseSessionManager.updateOverlaySpec(null)
        }
    }

    fun onFirstTouch() {
        if (_uiState.value.showFirstRunHint) {
            _uiState.update { it.copy(showFirstRunHint = false) }
            viewModelScope.launch {
                settingsRepository.markTraceHintSeen()
            }
        }
    }

    fun onTransformGesture(panX: Float, panY: Float, zoomChange: Float, rotationChange: Float) {
        val current = _uiState.value
        if (current.isLocked) return

        val newScale = (current.scale * zoomChange).coerceIn(0.15f, 8.0f)
        val newOffsetX = current.offsetX + panX
        val newOffsetY = current.offsetY + panY
        var newRotation = (current.rotation + rotationChange) % 360f
        if (newRotation > 180f) newRotation -= 360f
        if (newRotation < -180f) newRotation += 360f

        commitTransformState(
            offsetX = newOffsetX,
            offsetY = newOffsetY,
            scale = newScale,
            rotation = newRotation
        )
    }

    /**
     * Commits absolute transform values from the UI gesture layer (called throttled to once per 200ms
     * during gestures and once when the gesture ends) so pointer moves never trigger per-frame recomposition.
     */
    fun commitTransformState(
        offsetX: Float,
        offsetY: Float,
        scale: Float,
        rotation: Float
    ) {
        val current = _uiState.value
        if (current.isLocked) return

        val clampedScale = scale.coerceIn(0.15f, 8.0f)
        var normalizedRotation = rotation % 360f
        if (normalizedRotation > 180f) normalizedRotation -= 360f
        if (normalizedRotation < -180f) normalizedRotation += 360f

        savedStateHandle[KEY_OFFSET_X] = offsetX
        savedStateHandle[KEY_OFFSET_Y] = offsetY
        savedStateHandle[KEY_SCALE] = clampedScale
        savedStateHandle[KEY_ROTATION] = normalizedRotation

        _uiState.update {
            it.copy(
                offsetX = offsetX,
                offsetY = offsetY,
                scale = clampedScale,
                rotation = normalizedRotation
            )
        }
        syncOverlaySpecToSessionManager()
        scheduleAutoSaveIfSavedProject()
    }

    fun setOpacity(opacity: Float) {
        val clamped = opacity.coerceIn(0.1f, 1.0f)
        savedStateHandle[KEY_OPACITY] = clamped
        _uiState.update { it.copy(opacity = clamped) }
        syncOverlaySpecToSessionManager()
        scheduleAutoSaveIfSavedProject()
    }

    fun setLocked(locked: Boolean) {
        savedStateHandle[KEY_IS_LOCKED] = locked
        _uiState.update {
            it.copy(
                isLocked = locked,
                activeSheet = if (locked) ActiveSheet.NONE else it.activeSheet
            )
        }
    }

    fun toggleControlsHidden() {
        val next = !_uiState.value.isControlsHidden
        savedStateHandle[KEY_IS_CONTROLS_HIDDEN] = next
        _uiState.update {
            it.copy(
                isControlsHidden = next,
                activeSheet = if (next) ActiveSheet.NONE else it.activeSheet
            )
        }
    }

    fun setControlsHidden(hidden: Boolean) {
        savedStateHandle[KEY_IS_CONTROLS_HIDDEN] = hidden
        _uiState.update {
            it.copy(
                isControlsHidden = hidden,
                activeSheet = if (hidden) ActiveSheet.NONE else it.activeSheet
            )
        }
    }

    fun setOutlineEnabled(enabled: Boolean) {
        savedStateHandle[KEY_IS_OUTLINE_ENABLED] = enabled
        _uiState.update { it.copy(isOutlineEnabled = enabled) }
        if (enabled && _outlineImageBitmap.value == null) {
            refreshOutlineBitmap(_uiState.value.outlineStrength, debounceFullQuality = false)
        }
        syncOverlaySpecToSessionManager()
        scheduleAutoSaveIfSavedProject()
    }

    fun setOutlineStrength(strength: Float) {
        val clamped = strength.coerceIn(0.05f, 0.95f)
        savedStateHandle[KEY_OUTLINE_STRENGTH] = clamped
        _uiState.update { it.copy(outlineStrength = clamped) }
        // Process at reduced preview size immediately while the slider is dragged,
        // then re-render at full quality 150 ms after the slider stops.
        refreshOutlineBitmap(clamped, debounceFullQuality = true)
        scheduleAutoSaveIfSavedProject()
    }

    private fun refreshOutlineBitmap(strength: Float, debounceFullQuality: Boolean = false) {
        val bmp = sourceBitmap ?: return
        val current = _uiState.value
        val sourceKey = when {
            current.projectId.isNotBlank() -> "project_${current.projectId}"
            current.imageId.isNotBlank() -> current.imageId
            else -> current.imagePath
        }
        if (bmp.isRecycled || sourceKey.isBlank()) return

        outlineJob?.cancel()
        outlineJob = viewModelScope.launch {
            _uiState.update { it.copy(isProcessingOutline = true) }
            try {
                if (debounceFullQuality) {
                    val previewBmp = outlineProcessor.getOutlineBitmap(
                        sourceKey = sourceKey,
                        source = bmp,
                        strength = strength,
                        isPreview = true
                    )
                    currentOutlineRawBitmap = previewBmp
                    _outlineImageBitmap.value = previewBmp.asImageBitmap()
                    syncOverlaySpecToSessionManager()
                    delay(150L)
                }

                val fullBmp = outlineProcessor.getOutlineBitmap(
                    sourceKey = sourceKey,
                    source = bmp,
                    strength = strength,
                    isPreview = false
                )
                currentOutlineRawBitmap = fullBmp
                _outlineImageBitmap.value = fullBmp.asImageBitmap()
                syncOverlaySpecToSessionManager()
            } finally {
                _uiState.update { it.copy(isProcessingOutline = false) }
            }
        }
    }

    fun setHasFlashUnit(hasFlash: Boolean) {
        TimeLapseSessionManager.setHasFlashUnit(hasFlash)
        _uiState.update {
            it.copy(
                hasFlashUnit = hasFlash,
                isFlashlightOn = if (!hasFlash) false else it.isFlashlightOn
            )
        }
        if (!hasFlash) {
            savedStateHandle[KEY_IS_FLASHLIGHT_ON] = false
            TimeLapseSessionManager.setFlashlightRequested(false)
        }
    }

    fun setFlashlightOn(enabled: Boolean) {
        if (!_uiState.value.hasFlashUnit) return
        savedStateHandle[KEY_IS_FLASHLIGHT_ON] = enabled
        _uiState.update { it.copy(isFlashlightOn = enabled) }
        TimeLapseSessionManager.setFlashlightRequested(enabled)
    }

    fun toggleFlipHorizontal() {
        val next = !_uiState.value.flipHorizontal
        savedStateHandle[KEY_FLIP_HORIZONTAL] = next
        _uiState.update { it.copy(flipHorizontal = next) }
        syncOverlaySpecToSessionManager()
        scheduleAutoSaveIfSavedProject()
    }

    fun toggleFlipVertical() {
        val next = !_uiState.value.flipVertical
        savedStateHandle[KEY_FLIP_VERTICAL] = next
        _uiState.update { it.copy(flipVertical = next) }
        syncOverlaySpecToSessionManager()
        scheduleAutoSaveIfSavedProject()
    }

    fun setGridEnabled(enabled: Boolean) {
        savedStateHandle[KEY_IS_GRID_ENABLED] = enabled
        _uiState.update { it.copy(isGridEnabled = enabled) }
        scheduleAutoSaveIfSavedProject()
    }

    fun setGridCellSizeDp(sizeDp: Float) {
        val clamped = sizeDp.coerceIn(24f, 120f)
        savedStateHandle[KEY_GRID_CELL_SIZE_DP] = clamped
        _uiState.update { it.copy(gridCellSizeDp = clamped) }
        scheduleAutoSaveIfSavedProject()
    }

    fun resetPosition() {
        savedStateHandle[KEY_OFFSET_X] = 0f
        savedStateHandle[KEY_OFFSET_Y] = 0f
        savedStateHandle[KEY_SCALE] = 1f
        savedStateHandle[KEY_ROTATION] = 0f
        savedStateHandle[KEY_FLIP_HORIZONTAL] = false
        savedStateHandle[KEY_FLIP_VERTICAL] = false

        _uiState.update {
            it.copy(
                offsetX = 0f,
                offsetY = 0f,
                scale = 1f,
                rotation = 0f,
                flipHorizontal = false,
                flipVertical = false
            )
        }
        syncOverlaySpecToSessionManager()
        scheduleAutoSaveIfSavedProject()
    }

    fun openSheet(sheet: ActiveSheet) {
        _uiState.update { it.copy(activeSheet = sheet) }
    }

    fun dismissSheet() {
        _uiState.update { it.copy(activeSheet = ActiveSheet.NONE) }
    }

    // --- Pinned Mode (ARCore) Actions ---

    fun enterPinnedMode() {
        cancelTimeLapseCountdown()
        if (TimeLapseSessionManager.status.value.isRecording) {
            TimeLapseRecordingService.stopRecording(getApplication())
        }
        savedStateHandle[KEY_IS_PINNED_MODE] = true
        _uiState.update {
            it.copy(
                isPinnedMode = true,
                isPlaneDetected = false,
                isSurfaceAnchored = false,
                isTrackingLost = false,
                activeSheet = ActiveSheet.NONE
            )
        }
    }

    fun exitPinnedMode() {
        savedStateHandle[KEY_IS_PINNED_MODE] = false
        _uiState.update {
            it.copy(
                isPinnedMode = false,
                isPlaneDetected = false,
                isSurfaceAnchored = false,
                isTrackingLost = false,
                activeSheet = ActiveSheet.NONE
            )
        }
    }

    fun setPaperWidthCm(widthCm: Float) {
        val sanitized = PinnedModeLogic.sanitizePaperWidthCm(widthCm)
        savedStateHandle[KEY_PAPER_WIDTH_CM] = sanitized
        _uiState.update { it.copy(paperWidthCm = sanitized) }
    }

    fun rePinSurface() {
        _uiState.update {
            it.copy(
                isSurfaceAnchored = false,
                isTrackingLost = false,
                rePinTriggerCount = it.rePinTriggerCount + 1,
                activeSheet = ActiveSheet.NONE
            )
        }
    }

    fun onPlaneDetectedChanged(detected: Boolean) {
        _uiState.update { it.copy(isPlaneDetected = detected) }
    }

    fun onSurfaceAnchored() {
        _uiState.update {
            it.copy(
                isSurfaceAnchored = true,
                isTrackingLost = false
            )
        }
    }

    fun onTrackingLostChanged(lost: Boolean) {
        _uiState.update { it.copy(isTrackingLost = lost) }
    }

    fun onArSessionFailure(fallbackMessage: String) {
        savedStateHandle[KEY_IS_PINNED_MODE] = false
        _uiState.update {
            it.copy(
                isPinnedMode = false,
                isPlaneDetected = false,
                isSurfaceAnchored = false,
                isTrackingLost = false,
                activeSheet = ActiveSheet.NONE,
                snackbarMessage = fallbackMessage
            )
        }
    }

    fun showSnackbarMessage(message: String) {
        _uiState.update { it.copy(snackbarMessage = message) }
    }

    fun consumeSnackbarMessage() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    // --- Time-Lapse Actions ---

    fun setTimeLapseIntervalSeconds(seconds: Int) {
        val valid = if (seconds in AppSettings.SUPPORTED_INTERVALS_SECONDS) seconds else 2
        _uiState.update { it.copy(timeLapseIntervalSeconds = valid) }
        viewModelScope.launch {
            settingsRepository.setTimeLapseIntervalSeconds(valid)
        }
    }

    fun setTimeLapseQuality(quality: TimeLapseQuality) {
        _uiState.update { it.copy(timeLapseQuality = quality) }
        viewModelScope.launch {
            settingsRepository.setTimeLapseQuality(quality)
        }
    }

    fun setTimeLapseIncludeOverlay(enabled: Boolean) {
        _uiState.update { it.copy(timeLapseIncludeOverlay = enabled) }
        viewModelScope.launch {
            settingsRepository.setTimeLapseIncludeOverlay(enabled)
        }
    }

    fun setTimeLapseCountdownSeconds(seconds: Int) {
        val valid = if (seconds in AppSettings.SUPPORTED_COUNTDOWNS_SECONDS) seconds else 3
        _uiState.update { it.copy(timeLapseCountdownSeconds = valid) }
        viewModelScope.launch {
            settingsRepository.setTimeLapseCountdownSeconds(valid)
        }
    }

    fun startTimeLapseRecording() {
        if (_uiState.value.isPinnedMode) return
        viewModelScope.launch {
            if (!hasEnoughStorageSpaceIo()) {
                _uiState.update {
                    it.copy(
                        activeSheet = ActiveSheet.NONE,
                        snackbarMessage = getApplication<Application>().getString(R.string.snackbar_low_storage)
                    )
                }
                return@launch
            }
            syncOverlaySpecToSessionManager()
            countdownJob?.cancel()
            val countdownSeconds = _uiState.value.timeLapseCountdownSeconds
            if (countdownSeconds <= 0) {
                _uiState.update {
                    it.copy(
                        activeSheet = ActiveSheet.NONE,
                        countdownRemainingSeconds = 0
                    )
                }
                TimeLapseRecordingService.startRecording(getApplication())
            } else {
                _uiState.update {
                    it.copy(
                        activeSheet = ActiveSheet.NONE,
                        countdownRemainingSeconds = countdownSeconds
                    )
                }
                countdownJob = viewModelScope.launch {
                    for (remaining in countdownSeconds downTo 1) {
                        _uiState.update { it.copy(countdownRemainingSeconds = remaining) }
                        delay(1_000L)
                    }
                    _uiState.update { it.copy(countdownRemainingSeconds = 0) }
                    if (!_uiState.value.isPinnedMode) {
                        syncOverlaySpecToSessionManager()
                        TimeLapseRecordingService.startRecording(getApplication())
                    }
                }
            }
        }
    }

    fun cancelTimeLapseCountdown() {
        countdownJob?.cancel()
        countdownJob = null
        _uiState.update { it.copy(countdownRemainingSeconds = 0) }
    }

    fun stopTimeLapseRecording() {
        cancelTimeLapseCountdown()
        _uiState.update { it.copy(activeSheet = ActiveSheet.NONE) }
        TimeLapseRecordingService.stopRecording(getApplication())
    }

    fun consumeSavedVideoUri() {
        TimeLapseSessionManager.consumeSavedVideoUri()
    }

    fun requestSaveProject() {
        viewModelScope.launch {
            if (!hasEnoughStorageSpaceIo()) {
                _uiState.update {
                    it.copy(
                        activeSheet = ActiveSheet.NONE,
                        snackbarMessage = getApplication<Application>().getString(R.string.snackbar_low_storage)
                    )
                }
                return@launch
            }
            val currentName = _uiState.value.projectName
            val suggested = if (currentName.isNotBlank()) {
                currentName
            } else {
                projectRepository.nextDefaultProjectName()
            }
            _uiState.update {
                it.copy(
                    activeSheet = ActiveSheet.NONE,
                    suggestedProjectName = suggested,
                    showSaveDialog = true
                )
            }
        }
    }

    fun dismissSaveDialog() {
        _uiState.update { it.copy(showSaveDialog = false) }
    }

    fun confirmSaveProject(name: String) {
        val current = _uiState.value
        val sourcePath = current.imagePath
        if (sourcePath.isBlank()) {
            _uiState.update { it.copy(showSaveDialog = false) }
            return
        }
        _uiState.update { it.copy(showSaveDialog = false) }

        viewModelScope.launch {
            runCatching {
                val saved = projectRepository.saveProject(
                    project = SavedProject(
                        id = current.projectId,
                        name = name.trim().ifBlank { current.suggestedProjectName },
                        imagePath = sourcePath,
                        offsetX = current.offsetX,
                        offsetY = current.offsetY,
                        scale = current.scale,
                        rotation = current.rotation,
                        opacity = current.opacity,
                        flipHorizontal = current.flipHorizontal,
                        flipVertical = current.flipVertical,
                        isOutlineEnabled = current.isOutlineEnabled,
                        outlineStrength = current.outlineStrength,
                        isGridEnabled = current.isGridEnabled,
                        gridCellSizeDp = current.gridCellSizeDp
                    ),
                    sourceImagePath = sourcePath
                )

                savedStateHandle[KEY_PROJECT_ID] = saved.id
                savedStateHandle[KEY_PROJECT_NAME] = saved.name
                savedStateHandle[KEY_IMAGE_PATH] = saved.imagePath

                _uiState.update {
                    it.copy(
                        projectId = saved.id,
                        projectName = saved.name,
                        imagePath = saved.imagePath
                    )
                }
            }.onFailure {
                showSnackbarMessage(getApplication<Application>().getString(R.string.snackbar_low_storage))
            }
        }
    }

    private fun scheduleAutoSaveIfSavedProject() {
        val current = _uiState.value
        if (current.projectId.isBlank()) return
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch {
            persistSavedProjectState(current)
        }
    }

    fun autoSaveOnExitIfProjectSaved() {
        val current = _uiState.value
        if (current.projectId.isBlank()) return
        viewModelScope.launch {
            withContext(NonCancellable) {
                persistSavedProjectState(current)
            }
        }
    }

    private suspend fun persistSavedProjectState(state: TraceState) {
        if (state.projectId.isBlank()) return
        projectRepository.autoSaveExistingProject(
            projectId = state.projectId,
            offsetX = state.offsetX,
            offsetY = state.offsetY,
            scale = state.scale,
            rotation = state.rotation,
            opacity = state.opacity,
            flipHorizontal = state.flipHorizontal,
            flipVertical = state.flipVertical,
            isOutlineEnabled = state.isOutlineEnabled,
            outlineStrength = state.outlineStrength,
            isGridEnabled = state.isGridEnabled,
            gridCellSizeDp = state.gridCellSizeDp,
            currentImagePath = state.imagePath
        )
    }

    fun onNewImagePicked(uri: Uri) {
        viewModelScope.launch {
            if (!hasEnoughStorageSpaceIo()) {
                showSnackbarMessage(getApplication<Application>().getString(R.string.snackbar_low_storage))
                return@launch
            }
            val newItem = recentRepository.importImageFromUri(uri)
            if (newItem == null) {
                showSnackbarMessage(getApplication<Application>().getString(R.string.snackbar_image_load_error))
                return@launch
            }
            savedStateHandle[KEY_IMAGE_ID] = newItem.id
            savedStateHandle[KEY_IMAGE_PATH] = newItem.filePath
            resetPosition()
            _uiState.update {
                it.copy(
                    imageId = newItem.id,
                    imagePath = newItem.filePath,
                    activeSheet = ActiveSheet.NONE
                )
            }
            loadBitmapFromPath(sourceKey = newItem.id, filePath = newItem.filePath)
            scheduleAutoSaveIfSavedProject()
        }
    }

    private suspend fun hasEnoughStorageSpaceIo(): Boolean = withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val dir = getApplication<Application>().filesDir
            val stat = StatFs(dir.absolutePath)
            val availableBytes = stat.availableBlocksLong * stat.blockSizeLong
            availableBytes > 15L * 1024L * 1024L
        } catch (_: Throwable) {
            true
        }
    }

    override fun onCleared() {
        super.onCleared()
        outlineJob?.cancel()
        countdownJob?.cancel()
        outlineProcessor.clear()
        currentOutlineRawBitmap = null
        _activeRawBitmap.value = null
        sourceBitmap?.let { bmp ->
            if (!bmp.isRecycled && !TimeLapseSessionManager.status.value.isRecording) {
                bmp.recycle()
            }
        }
        sourceBitmap = null
    }

    companion object {
        private const val KEY_SESSION_INITIALIZED = "trace_session_initialized"
        private const val KEY_PROJECT_ID = "trace_project_id"
        private const val KEY_PROJECT_NAME = "trace_project_name"
        private const val KEY_IMAGE_ID = "trace_image_id"
        private const val KEY_IMAGE_PATH = "trace_image_path"
        private const val KEY_OFFSET_X = "trace_offset_x"
        private const val KEY_OFFSET_Y = "trace_offset_y"
        private const val KEY_SCALE = "trace_scale"
        private const val KEY_ROTATION = "trace_rotation"
        private const val KEY_OPACITY = "trace_opacity"
        private const val KEY_IS_LOCKED = "trace_is_locked"
        private const val KEY_IS_CONTROLS_HIDDEN = "trace_is_controls_hidden"
        private const val KEY_IS_OUTLINE_ENABLED = "trace_is_outline_enabled"
        private const val KEY_OUTLINE_STRENGTH = "trace_outline_strength"
        private const val KEY_IS_FLASHLIGHT_ON = "trace_is_flashlight_on"
        private const val KEY_FLIP_HORIZONTAL = "trace_flip_horizontal"
        private const val KEY_FLIP_VERTICAL = "trace_flip_vertical"
        private const val KEY_IS_GRID_ENABLED = "trace_is_grid_enabled"
        private const val KEY_GRID_CELL_SIZE_DP = "trace_grid_cell_size_dp"
        private const val KEY_IS_PINNED_MODE = "trace_is_pinned_mode"
        private const val KEY_PAPER_WIDTH_CM = "trace_paper_width_cm"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application
                val savedStateHandle = createSavedStateHandle()
                TraceViewModel(app, savedStateHandle)
            }
        }
    }
}
