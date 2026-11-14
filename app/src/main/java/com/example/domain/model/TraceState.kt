package com.example.domain.model

import androidx.compose.runtime.Immutable

enum class ActiveSheet {
    NONE,
    OPACITY,
    OUTLINE,
    MORE,
    TIMELAPSE,
    PINNED
}

@Immutable
data class TraceState(
    val projectId: String = "",
    val projectName: String = "",
    val imageId: String = "",
    val imagePath: String = "",
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val scale: Float = 1f,
    val rotation: Float = 0f,
    val opacity: Float = 0.55f,
    val isLocked: Boolean = false,
    val isControlsHidden: Boolean = false,
    val isOutlineEnabled: Boolean = false,
    val outlineStrength: Float = 0.5f,
    val isFlashlightOn: Boolean = false,
    val hasFlashUnit: Boolean = false,
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false,
    val isGridEnabled: Boolean = false,
    val gridCellSizeDp: Float = 48f,
    val activeSheet: ActiveSheet = ActiveSheet.NONE,
    val isProcessingOutline: Boolean = false,
    val showSaveDialog: Boolean = false,
    val suggestedProjectName: String = "Untitled 1",
    val keepScreenOn: Boolean = true,
    val showFirstRunHint: Boolean = false,
    val timeLapseIntervalSeconds: Int = 2,
    val timeLapseQuality: TimeLapseQuality = TimeLapseQuality.QUALITY_720P,
    val timeLapseIncludeOverlay: Boolean = false,
    val timeLapseCountdownSeconds: Int = 3,
    val countdownRemainingSeconds: Int = 0,
    val isArSupported: Boolean = false,
    val isPinnedMode: Boolean = false,
    val isPlaneDetected: Boolean = false,
    val isSurfaceAnchored: Boolean = false,
    val isTrackingLost: Boolean = false,
    val paperWidthCm: Float = 21f,
    val rePinTriggerCount: Int = 0,
    val snackbarMessage: String? = null
)
