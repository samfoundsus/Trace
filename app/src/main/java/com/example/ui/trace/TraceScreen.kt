package com.example.ui.trace

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.Crossfade
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Opacity
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.timelapse.TimeLapseSessionManager
import com.example.domain.model.ActiveSheet
import com.example.domain.usecase.PinnedModeLogic
import com.example.domain.usecase.TimeLapseFrameScheduler
import com.example.ui.theme.AnimatedMaterialDialog
import com.example.ui.theme.AnimatedSnackbarContainer
import com.example.ui.theme.TraceMotion
import com.example.ui.theme.pressScale
import com.example.ui.theme.rememberAnimationsEnabled
import kotlin.math.PI
import kotlin.math.abs
import kotlinx.coroutines.launch

@Stable
internal class LiveTraceTransformState(
    initialOffsetX: Float,
    initialOffsetY: Float,
    initialScale: Float,
    initialRotation: Float,
    initialOpacity: Float
) {
    var offsetX by mutableFloatStateOf(initialOffsetX)
    var offsetY by mutableFloatStateOf(initialOffsetY)
    var scale by mutableFloatStateOf(initialScale)
    var rotation by mutableFloatStateOf(initialRotation)
    var opacity by mutableFloatStateOf(initialOpacity)

    var isGestureActive: Boolean = false
    var lastCommitUptimeMs: Long = 0L
    var lastOpacityCommitUptimeMs: Long = 0L

    fun applyDelta(panX: Float, panY: Float, zoomChange: Float, rotationChange: Float) {
        offsetX += panX
        offsetY += panY
        scale = (scale * zoomChange).coerceIn(0.15f, 8.0f)
        var nextRotation = (rotation + rotationChange) % 360f
        if (nextRotation > 180f) nextRotation -= 360f
        if (nextRotation < -180f) nextRotation += 360f
        rotation = nextRotation
    }

    fun syncFromState(
        stateOffsetX: Float,
        stateOffsetY: Float,
        stateScale: Float,
        stateRotation: Float,
        stateOpacity: Float
    ) {
        if (!isGestureActive) {
            offsetX = stateOffsetX
            offsetY = stateOffsetY
            scale = stateScale
            rotation = stateRotation
            opacity = stateOpacity
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun TraceScreen(
    imageId: String,
    viewModel: TraceViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    projectId: String = "",
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val sourceBitmap by viewModel.sourceImageBitmap.collectAsStateWithLifecycle()
    val outlineBitmap by viewModel.outlineImageBitmap.collectAsStateWithLifecycle()
    val activeRawBitmap by viewModel.activeRawBitmap.collectAsStateWithLifecycle()
    val timeLapseStatus by viewModel.timeLapseStatus.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current
    val snackbarHostState = remember { SnackbarHostState() }
    val animationsEnabled = rememberAnimationsEnabled()

    // Mutable transform state read ONLY inside graphicsLayer { } so drag/pinch/twist/opacity
    // never trigger recomposition of TraceScreen on every frame.
    val liveTransform = remember {
        LiveTraceTransformState(
            initialOffsetX = state.offsetX,
            initialOffsetY = state.offsetY,
            initialScale = state.scale,
            initialRotation = state.rotation,
            initialOpacity = state.opacity
        )
    }

    LaunchedEffect(
        state.offsetX,
        state.offsetY,
        state.scale,
        state.rotation,
        state.opacity
    ) {
        liveTransform.syncFromState(
            stateOffsetX = state.offsetX,
            stateOffsetY = state.offsetY,
            stateScale = state.scale,
            stateRotation = state.rotation,
            stateOpacity = state.opacity
        )
    }

    // Retain last elapsed seconds so the pill does not flash "00:00" during its quick fade-out
    var displayedElapsedSeconds by remember { mutableLongStateOf(0L) }
    if (timeLapseStatus.isRecording) {
        displayedElapsedSeconds = timeLapseStatus.elapsedSeconds
    }

    LaunchedEffect(imageId, projectId) {
        viewModel.initializeSession(imageId = imageId, projectId = projectId)
    }

    val context = LocalContext.current
    val view = LocalView.current

    val savedSnackMessage = stringResource(R.string.snackbar_timelapse_saved)
    val openSnackAction = stringResource(R.string.snackbar_timelapse_open)
    val arUnavailableMessage = stringResource(R.string.snackbar_ar_unavailable)

    // Show "Time-lapse saved" snackbar with "Open" action when a recording finishes
    LaunchedEffect(timeLapseStatus.savedVideoUri) {
        val uri = timeLapseStatus.savedVideoUri ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = savedSnackMessage,
            actionLabel = openSnackAction,
            duration = SnackbarDuration.Long
        )
        viewModel.consumeSavedVideoUri()
        if (result == SnackbarResult.ActionPerformed) {
            runCatching {
                val openIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "video/mp4")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(openIntent)
            }
        }
    }

    // Show friendly snackbar messages (ARCore fallback, low storage, image errors)
    LaunchedEffect(state.snackbarMessage) {
        val msg = state.snackbarMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(
            message = msg,
            duration = SnackbarDuration.Short
        )
        viewModel.consumeSnackbarMessage()
    }

    // Request POST_NOTIFICATIONS only on Android 13+ (API 33+) before starting foreground service
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        viewModel.startTimeLapseRecording()
    }

    val handleStartTimeLapse = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (granted) {
                viewModel.startTimeLapseRecording()
            } else {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            viewModel.startTimeLapseRecording()
        }
    }

    val handlePinToSurfaceClick = {
        if (state.isPinnedMode) {
            viewModel.openSheet(ActiveSheet.PINNED)
        } else {
            val activity = context.findActivity()
            if (activity != null) {
                val ready = PinnedModeLogic.ensureArCoreInstalled(
                    activity = activity,
                    userRequestedInstall = true
                )
                if (ready) {
                    viewModel.enterPinnedMode()
                } else {
                    viewModel.dismissSheet()
                }
            } else {
                viewModel.onArSessionFailure(arUnavailableMessage)
            }
        }
    }

    // Immersive system bars & dynamic keepScreenOn while Trace screen is open
    DisposableEffect(view, context, state.keepScreenOn) {
        val window = context.findActivity()?.window
        val insetsController = window?.let { WindowCompat.getInsetsController(it, view) }

        if (state.keepScreenOn) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        insetsController?.apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }

        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            insetsController?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    // Auto-save changes when leaving the trace screen, ONLY for projects that were already saved
    DisposableEffect(Unit) {
        onDispose {
            viewModel.autoSaveOnExitIfProjectSaved()
        }
    }

    val handleNavigateBack = {
        viewModel.autoSaveOnExitIfProjectSaved()
        onNavigateBack()
    }

    BackHandler {
        when {
            state.showSaveDialog -> viewModel.dismissSaveDialog()
            state.activeSheet != ActiveSheet.NONE -> viewModel.dismissSheet()
            state.countdownRemainingSeconds > 0 -> viewModel.cancelTimeLapseCountdown()
            state.isLocked -> {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                viewModel.setLocked(false)
            }
            state.isControlsHidden -> viewModel.setControlsHidden(false)
            state.isPinnedMode -> viewModel.exitPinnedMode()
            else -> handleNavigateBack()
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.onNewImagePicked(uri)
        }
    }

    val currentIsLocked by rememberUpdatedState(state.isLocked)
    val currentIsPinnedMode by rememberUpdatedState(state.isPinnedMode)
    val onFirstTouch by rememberUpdatedState(viewModel::onFirstTouch)
    val onTwoFingerTap by rememberUpdatedState {
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        viewModel.toggleControlsHidden()
    }

    val sharedKey = when {
        projectId.isNotBlank() -> "trace_bounds_project_$projectId"
        imageId.isNotBlank() -> "trace_bounds_$imageId"
        else -> ""
    }

    val containerDuration = TraceMotion.duration(
        TraceMotion.DURATION_CONTAINER_TRANSFORM,
        animationsEnabled
    )
    val pillDuration = TraceMotion.duration(TraceMotion.DURATION_PILL, animationsEnabled)
    val toolbarSlideDuration = TraceMotion.duration(TraceMotion.DURATION_MEDIUM, animationsEnabled)
    val quickDuration = TraceMotion.duration(TraceMotion.DURATION_QUICK, animationsEnabled)
    val countdownInDuration = TraceMotion.duration(TraceMotion.DURATION_COUNTDOWN_IN, animationsEnabled)

    val containerSharedModifier = if (
        sharedTransitionScope != null && animatedVisibilityScope != null && sharedKey.isNotBlank()
    ) {
        with(sharedTransitionScope) {
            Modifier.sharedBounds(
                sharedContentState = rememberSharedContentState(key = sharedKey),
                animatedVisibilityScope = animatedVisibilityScope,
                boundsTransform = { _, _ ->
                    tween(
                        durationMillis = containerDuration,
                        easing = TraceMotion.EmphasizedEasing
                    )
                }
            )
        }
    } else {
        Modifier
    }

    val hintInteraction = remember { MutableInteractionSource() }
    val lockedPillInteraction = remember { MutableInteractionSource() }
    val backBtnInteraction = remember { MutableInteractionSource() }
    val hideControlsInteraction = remember { MutableInteractionSource() }
    val countdownInteraction = remember { MutableInteractionSource() }

    val showFirstRunHintBanner by remember {
        derivedStateOf {
            state.showFirstRunHint &&
                !state.isLocked &&
                !timeLapseStatus.isRecording &&
                !state.isPinnedMode
        }
    }
    val showUnlockedControls by remember {
        derivedStateOf { !state.isLocked && !state.isControlsHidden }
    }
    val showChevronButton by remember {
        derivedStateOf { !state.isLocked && !(state.isControlsHidden && timeLapseStatus.isRecording) }
    }

    Box(
        modifier = modifier
            .then(containerSharedModifier)
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .onSizeChanged { size ->
                viewModel.updateViewportSize(size.width, size.height)
            }
            .pointerInput(Unit) {
                detectTraceGesturesAndTwoFingerTap(
                    isLocked = { currentIsLocked },
                    onFirstTouch = { onFirstTouch() },
                    onGesture = { pan, zoom, rotation, uptimeMs ->
                        liveTransform.isGestureActive = true
                        liveTransform.applyDelta(pan.x, pan.y, zoom, rotation)
                        // Throttle ViewModel/SavedStateHandle commits to once per 200ms during active gesture
                        if (currentIsPinnedMode || (uptimeMs - liveTransform.lastCommitUptimeMs >= 200L)) {
                            liveTransform.lastCommitUptimeMs = uptimeMs
                            viewModel.commitTransformState(
                                offsetX = liveTransform.offsetX,
                                offsetY = liveTransform.offsetY,
                                scale = liveTransform.scale,
                                rotation = liveTransform.rotation
                            )
                        }
                    },
                    onGestureEnd = {
                        if (liveTransform.isGestureActive) {
                            liveTransform.isGestureActive = false
                            viewModel.commitTransformState(
                                offsetX = liveTransform.offsetX,
                                offsetY = liveTransform.offsetY,
                                scale = liveTransform.scale,
                                rotation = liveTransform.rotation
                            )
                        }
                    },
                    onTwoFingerTap = {
                        onTwoFingerTap()
                    }
                )
            }
            .testTag("trace_root")
    ) {
        if (state.isPinnedMode) {
            // 1A. Pinned Mode: ARCore Session owns the camera & renders the 3D anchored quad (never animated)
            ArPinnedSurfaceLayer(
                state = state,
                activeBitmap = activeRawBitmap,
                rePinTriggerCount = state.rePinTriggerCount,
                onPlaneDetectedChanged = viewModel::onPlaneDetectedChanged,
                onSurfaceAnchored = viewModel::onSurfaceAnchored,
                onTrackingLostChanged = viewModel::onTrackingLostChanged,
                onArSessionFailure = {
                    viewModel.onArSessionFailure(arUnavailableMessage)
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // 1B. Standard Mode: Live CameraX Preview + 2D Transformed Overlay Image (zero recomposition per frame)
            CameraPreviewLayer(
                isRecordingTimeLapse = timeLapseStatus.isRecording,
                isFlashlightOn = state.isFlashlightOn,
                onFlashUnitAvailable = viewModel::setHasFlashUnit,
                modifier = Modifier.fillMaxSize()
            )

            val activeBitmap = if (state.isOutlineEnabled && outlineBitmap != null) {
                outlineBitmap
            } else {
                sourceBitmap
            }

            if (activeBitmap != null) {
                val flipHSign = if (state.flipHorizontal) -1f else 1f
                val flipVSign = if (state.flipVertical) -1f else 1f

                Image(
                    bitmap = activeBitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .graphicsLayer {
                            val currentScale = liveTransform.scale
                            translationX = liveTransform.offsetX
                            translationY = liveTransform.offsetY
                            scaleX = currentScale * flipHSign
                            scaleY = currentScale * flipVSign
                            rotationZ = liveTransform.rotation
                            alpha = liveTransform.opacity
                        }
                        .testTag("trace_image_overlay")
                )
            }
        }

        // 3. Optional Grid Overlay
        if (state.isGridEnabled) {
            TraceGridOverlay(
                cellSizeDp = state.gridCellSizeDp,
                lineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.36f),
                shadowColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.28f),
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("trace_grid_overlay")
            )
        }

        // 4. First-Run Hint Card (disappears on the first touch and never shows again)
        AnimatedVisibility(
            visible = showFirstRunHintBanner,
            enter = fadeIn(animationSpec = tween(pillDuration, easing = TraceMotion.StandardDecelerateEasing)) +
                scaleIn(
                    initialScale = 0.92f,
                    animationSpec = tween(pillDuration, easing = TraceMotion.EmphasizedDecelerateEasing)
                ),
            exit = fadeOut(animationSpec = tween(quickDuration, easing = TraceMotion.StandardAccelerateEasing)) +
                scaleOut(
                    targetScale = 0.92f,
                    animationSpec = tween(quickDuration, easing = TraceMotion.EmphasizedAccelerateEasing)
                ),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(start = 72.dp, end = 72.dp, top = 16.dp)
        ) {
            Surface(
                onClick = { viewModel.onFirstTouch() },
                interactionSource = hintInteraction,
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f),
                contentColor = MaterialTheme.colorScheme.onSurface,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
                modifier = Modifier
                    .widthIn(max = 360.dp)
                    .pressScale(hintInteraction)
                    .testTag("first_run_hint_card")
            ) {
                Text(
                    text = stringResource(R.string.trace_first_run_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
        }

        // 5. Top-Centre Status Pills: Lock pill and recording pill fade and scale in/out (200 ms)
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 16.dp, start = 68.dp, end = 68.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AnimatedVisibility(
                visible = state.isLocked,
                enter = fadeIn(animationSpec = tween(pillDuration, easing = TraceMotion.StandardDecelerateEasing)) +
                    scaleIn(
                        initialScale = 0.9f,
                        animationSpec = tween(pillDuration, easing = TraceMotion.EmphasizedDecelerateEasing)
                    ),
                exit = fadeOut(animationSpec = tween(pillDuration, easing = TraceMotion.StandardAccelerateEasing)) +
                    scaleOut(
                        targetScale = 0.9f,
                        animationSpec = tween(pillDuration, easing = TraceMotion.EmphasizedAccelerateEasing)
                    )
            ) {
                Surface(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        viewModel.setLocked(false)
                    },
                    interactionSource = lockedPillInteraction,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.90f),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                    modifier = Modifier
                        .pressScale(lockedPillInteraction)
                        .testTag("locked_pill")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Crossfade(
                            targetState = state.isLocked,
                            animationSpec = tween(
                                durationMillis = quickDuration,
                                easing = TraceMotion.StandardEasing
                            ),
                            label = "locked_pill_icon_crossfade"
                        ) { locked ->
                            Icon(
                                imageVector = if (locked) Icons.Filled.Lock else Icons.Outlined.Lock,
                                contentDescription = stringResource(R.string.cd_unlock),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Text(
                            text = stringResource(R.string.trace_locked_pill),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = timeLapseStatus.isRecording,
                enter = fadeIn(animationSpec = tween(pillDuration, easing = TraceMotion.StandardDecelerateEasing)) +
                    scaleIn(
                        initialScale = 0.9f,
                        animationSpec = tween(pillDuration, easing = TraceMotion.EmphasizedDecelerateEasing)
                    ),
                exit = fadeOut(animationSpec = tween(pillDuration, easing = TraceMotion.StandardAccelerateEasing)) +
                    scaleOut(
                        targetScale = 0.9f,
                        animationSpec = tween(pillDuration, easing = TraceMotion.EmphasizedAccelerateEasing)
                    )
            ) {
                TimeLapseRecordingPill(
                    elapsedSeconds = displayedElapsedSeconds,
                    isRecording = timeLapseStatus.isRecording,
                    onStopClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        viewModel.stopTimeLapseRecording()
                    }
                )
            }

            AnimatedVisibility(
                visible = state.isPinnedMode && (!state.isSurfaceAnchored || state.isTrackingLost),
                enter = fadeIn(animationSpec = tween(pillDuration, easing = TraceMotion.StandardDecelerateEasing)) +
                    scaleIn(
                        initialScale = 0.9f,
                        animationSpec = tween(pillDuration, easing = TraceMotion.EmphasizedDecelerateEasing)
                    ),
                exit = fadeOut(animationSpec = tween(pillDuration, easing = TraceMotion.StandardAccelerateEasing)) +
                    scaleOut(
                        targetScale = 0.9f,
                        animationSpec = tween(pillDuration, easing = TraceMotion.EmphasizedAccelerateEasing)
                    )
            ) {
                val pillText = when {
                    state.isSurfaceAnchored && state.isTrackingLost ->
                        stringResource(R.string.trace_pin_tracking_lost_pill)
                    else ->
                        stringResource(R.string.trace_pin_move_slowly_pill)
                }
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.90f),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                    modifier = Modifier.testTag("pinned_status_pill")
                ) {
                    Text(
                        text = pillText,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
            }
        }

        // 5B. Centred Countdown Overlay: each number scales from 1.2 to 1 while fading in (400 ms), fading out in 150 ms
        AnimatedVisibility(
            visible = state.countdownRemainingSeconds > 0,
            enter = fadeIn(animationSpec = tween(quickDuration, easing = TraceMotion.StandardDecelerateEasing)) +
                scaleIn(
                    initialScale = 0.9f,
                    animationSpec = tween(quickDuration, easing = TraceMotion.EmphasizedDecelerateEasing)
                ),
            exit = fadeOut(animationSpec = tween(quickDuration, easing = TraceMotion.StandardAccelerateEasing)) +
                scaleOut(
                    targetScale = 0.9f,
                    animationSpec = tween(quickDuration, easing = TraceMotion.EmphasizedAccelerateEasing)
                ),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Surface(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    viewModel.cancelTimeLapseCountdown()
                },
                interactionSource = countdownInteraction,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.90f),
                contentColor = MaterialTheme.colorScheme.onSurface,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
                modifier = Modifier
                    .size(120.dp)
                    .pressScale(countdownInteraction)
                    .testTag("timelapse_countdown_overlay")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    AnimatedContent(
                        targetState = state.countdownRemainingSeconds.coerceAtLeast(1),
                        transitionSpec = {
                            (fadeIn(
                                animationSpec = tween(
                                    durationMillis = countdownInDuration,
                                    easing = TraceMotion.EmphasizedDecelerateEasing
                                )
                            ) + scaleIn(
                                initialScale = 1.2f,
                                animationSpec = tween(
                                    durationMillis = countdownInDuration,
                                    easing = TraceMotion.EmphasizedDecelerateEasing
                                )
                            )) togetherWith fadeOut(
                                animationSpec = tween(
                                    durationMillis = quickDuration,
                                    easing = TraceMotion.EmphasizedAccelerateEasing
                                )
                            )
                        },
                        label = "countdown_number_transition"
                    ) { number ->
                        Text(
                            text = number.toString(),
                            style = MaterialTheme.typography.displayLarge.copy(
                                fontFeatureSettings = "tnum"
                            ),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.testTag("timelapse_countdown_text")
                        )
                    }
                }
            }
        }

        // 6. Unlocked Top Bar: small round tonal back button (top-left) and chevron hide/show button (top-right)
        AnimatedVisibility(
            visible = showUnlockedControls,
            enter = fadeIn(animationSpec = tween(pillDuration, easing = TraceMotion.StandardDecelerateEasing)) +
                scaleIn(
                    initialScale = 0.9f,
                    animationSpec = tween(pillDuration, easing = TraceMotion.EmphasizedDecelerateEasing)
                ),
            exit = fadeOut(animationSpec = tween(quickDuration, easing = TraceMotion.StandardAccelerateEasing)) +
                scaleOut(
                    targetScale = 0.9f,
                    animationSpec = tween(quickDuration, easing = TraceMotion.EmphasizedAccelerateEasing)
                ),
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(16.dp)
        ) {
            FilledTonalIconButton(
                onClick = handleNavigateBack,
                interactionSource = backBtnInteraction,
                shape = CircleShape,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.88f),
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier
                    .size(48.dp)
                    .pressScale(backBtnInteraction)
                    .testTag("trace_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.cd_back),
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        AnimatedVisibility(
            visible = showChevronButton,
            enter = fadeIn(animationSpec = tween(pillDuration, easing = TraceMotion.StandardDecelerateEasing)) +
                scaleIn(
                    initialScale = 0.9f,
                    animationSpec = tween(pillDuration, easing = TraceMotion.EmphasizedDecelerateEasing)
                ),
            exit = fadeOut(animationSpec = tween(quickDuration, easing = TraceMotion.StandardAccelerateEasing)) +
                scaleOut(
                    targetScale = 0.9f,
                    animationSpec = tween(quickDuration, easing = TraceMotion.EmphasizedAccelerateEasing)
                ),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(16.dp)
        ) {
            FilledTonalIconButton(
                onClick = {
                    viewModel.onFirstTouch()
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    viewModel.toggleControlsHidden()
                },
                interactionSource = hideControlsInteraction,
                shape = CircleShape,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.88f),
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier
                    .size(48.dp)
                    .pressScale(hideControlsInteraction)
                    .testTag("trace_hide_controls_button")
            ) {
                Crossfade(
                    targetState = state.isControlsHidden,
                    animationSpec = tween(
                        durationMillis = quickDuration,
                        easing = TraceMotion.StandardEasing
                    ),
                    label = "chevron_icon_crossfade"
                ) { hidden ->
                    Icon(
                        imageVector = if (hidden) {
                            Icons.Default.KeyboardArrowUp
                        } else {
                            Icons.Default.KeyboardArrowDown
                        },
                        contentDescription = stringResource(
                            if (hidden) {
                                R.string.cd_show_controls
                            } else {
                                R.string.cd_hide_controls
                            }
                        ),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        // 7. Floating Bottom Toolbar Pill (slides down & fades out in 250 ms, comes back with reverse) & SnackbarHost
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.widthIn(max = 420.dp)
            ) { data ->
                AnimatedSnackbarContainer(visible = true) {
                    Snackbar(snackbarData = data)
                }
            }

            AnimatedVisibility(
                visible = showUnlockedControls,
                enter = slideInVertically(
                    initialOffsetY = { fullHeight -> fullHeight },
                    animationSpec = tween(
                        durationMillis = toolbarSlideDuration,
                        easing = TraceMotion.EmphasizedDecelerateEasing
                    )
                ) + fadeIn(
                    animationSpec = tween(
                        durationMillis = toolbarSlideDuration,
                        easing = TraceMotion.StandardDecelerateEasing
                    )
                ),
                exit = slideOutVertically(
                    targetOffsetY = { fullHeight -> fullHeight },
                    animationSpec = tween(
                        durationMillis = toolbarSlideDuration,
                        easing = TraceMotion.EmphasizedAccelerateEasing
                    )
                ) + fadeOut(
                    animationSpec = tween(
                        durationMillis = toolbarSlideDuration,
                        easing = TraceMotion.StandardAccelerateEasing
                    )
                )
            ) {
                FloatingTraceToolbar(
                    activeSheet = state.activeSheet,
                    isOutlineActive = state.isOutlineEnabled,
                    isLocked = state.isLocked,
                    onOpacityClick = {
                        viewModel.onFirstTouch()
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        viewModel.openSheet(ActiveSheet.OPACITY)
                    },
                    onLockClick = {
                        viewModel.onFirstTouch()
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        viewModel.setLocked(true)
                    },
                    onOutlineClick = {
                        viewModel.onFirstTouch()
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        viewModel.openSheet(ActiveSheet.OUTLINE)
                    },
                    onMoreClick = {
                        viewModel.onFirstTouch()
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        viewModel.openSheet(ActiveSheet.MORE)
                    }
                )
            }
        }

        // 8. Bottom Sheets (Opacity, Outline, More, Time-lapse, Pin to surface)
        when (state.activeSheet) {
            ActiveSheet.OPACITY -> {
                OpacitySheetHost(
                    liveTransform = liveTransform,
                    isPinnedMode = state.isPinnedMode,
                    onCommitOpacity = viewModel::setOpacity,
                    onDismiss = viewModel::dismissSheet
                )
            }
            ActiveSheet.OUTLINE -> {
                OutlineBottomSheet(
                    isOutlineEnabled = state.isOutlineEnabled,
                    outlineStrength = state.outlineStrength,
                    onOutlineToggle = viewModel::setOutlineEnabled,
                    onStrengthChange = viewModel::setOutlineStrength,
                    onDismiss = viewModel::dismissSheet
                )
            }
            ActiveSheet.MORE -> {
                MoreBottomSheet(
                    state = state,
                    onPinToSurfaceClick = handlePinToSurfaceClick,
                    onOpenTimeLapseSheet = { viewModel.openSheet(ActiveSheet.TIMELAPSE) },
                    onSaveProject = viewModel::requestSaveProject,
                    onFlashlightToggle = viewModel::setFlashlightOn,
                    onFlipHorizontal = viewModel::toggleFlipHorizontal,
                    onFlipVertical = viewModel::toggleFlipVertical,
                    onGridToggle = viewModel::setGridEnabled,
                    onGridCellSizeChange = viewModel::setGridCellSizeDp,
                    onResetPosition = viewModel::resetPosition,
                    onChangeImage = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onDismiss = viewModel::dismissSheet
                )
            }
            ActiveSheet.TIMELAPSE -> {
                TimeLapseBottomSheet(
                    intervalSeconds = state.timeLapseIntervalSeconds,
                    quality = state.timeLapseQuality,
                    includeOverlay = state.timeLapseIncludeOverlay,
                    countdownSeconds = state.timeLapseCountdownSeconds,
                    isRecording = timeLapseStatus.isRecording,
                    onIntervalChange = viewModel::setTimeLapseIntervalSeconds,
                    onQualityChange = viewModel::setTimeLapseQuality,
                    onIncludeOverlayChange = viewModel::setTimeLapseIncludeOverlay,
                    onCountdownChange = viewModel::setTimeLapseCountdownSeconds,
                    onStartRecording = handleStartTimeLapse,
                    onStopRecording = viewModel::stopTimeLapseRecording,
                    onDismiss = viewModel::dismissSheet
                )
            }
            ActiveSheet.PINNED -> {
                PinToSurfaceBottomSheet(
                    paperWidthCm = state.paperWidthCm,
                    onPaperWidthChange = viewModel::setPaperWidthCm,
                    onRePin = viewModel::rePinSurface,
                    onBackToNormal = viewModel::exitPinnedMode,
                    onDismiss = viewModel::dismissSheet
                )
            }
            ActiveSheet.NONE -> Unit
        }
    }

    // 9. Save Project Material Dialog (scale 0.9 -> 1 + fade in 250 ms, fade out 150 ms)
    AnimatedMaterialDialog(
        visible = state.showSaveDialog,
        onDismissRequest = viewModel::dismissSaveDialog
    ) {
        var nameInput by rememberSaveable(state.suggestedProjectName) {
            mutableStateOf(state.suggestedProjectName)
        }
        val saveBtnInteraction = remember { MutableInteractionSource() }
        val cancelBtnInteraction = remember { MutableInteractionSource() }

        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 0.dp,
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .widthIn(min = 280.dp, max = 400.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = stringResource(R.string.dialog_save_project_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.dialog_project_name_label)) },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("save_project_name_input")
                )
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = viewModel::dismissSaveDialog,
                        interactionSource = cancelBtnInteraction,
                        modifier = Modifier
                            .pressScale(cancelBtnInteraction)
                            .testTag("save_project_cancel_button")
                    ) {
                        Text(text = stringResource(R.string.dialog_cancel))
                    }
                    TextButton(
                        onClick = {
                            viewModel.confirmSaveProject(nameInput)
                        },
                        interactionSource = saveBtnInteraction,
                        modifier = Modifier
                            .pressScale(saveBtnInteraction)
                            .testTag("save_project_confirm_button")
                    ) {
                        Text(text = stringResource(R.string.dialog_save))
                    }
                }
            }
        }
    }
}

@Composable
private fun OpacitySheetHost(
    liveTransform: LiveTraceTransformState,
    isPinnedMode: Boolean,
    onCommitOpacity: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    OpacityBottomSheet(
        opacity = liveTransform.opacity,
        onOpacityChange = { newOpacity ->
            val clamped = newOpacity.coerceIn(0.1f, 1.0f)
            liveTransform.opacity = clamped
            val now = SystemClock.uptimeMillis()
            if (isPinnedMode || (now - liveTransform.lastOpacityCommitUptimeMs >= 200L)) {
                liveTransform.lastOpacityCommitUptimeMs = now
                onCommitOpacity(clamped)
            }
        },
        onDismiss = {
            onCommitOpacity(liveTransform.opacity)
            onDismiss()
        }
    )
}

@Composable
private fun TimeLapseRecordingPill(
    elapsedSeconds: Long,
    isRecording: Boolean,
    onStopClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val animationsEnabled = rememberAnimationsEnabled()
    val formattedElapsed = TimeLapseFrameScheduler.formatElapsedDuration(elapsedSeconds)
    val stopBtnInteraction = remember { MutableInteractionSource() }

    // Single infinite transition that runs only while recording is active and animations are enabled
    val dotAlpha = if (isRecording && animationsEnabled) {
        val infiniteTransition = rememberInfiniteTransition(label = "recording_dot_pulse")
        val animatedAlpha by infiniteTransition.animateFloat(
            initialValue = 1.0f,
            targetValue = 0.4f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "recording_dot_alpha"
        )
        animatedAlpha
    } else {
        1.0f
    }

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.90f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = modifier.testTag("recording_pill")
    ) {
        Row(
            modifier = Modifier.padding(start = 18.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .graphicsLayer { alpha = dotAlpha }
                    .background(
                        color = MaterialTheme.colorScheme.error,
                        shape = CircleShape
                    )
            )

            Text(
                text = formattedElapsed,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFeatureSettings = "tnum"
                ),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.testTag("recording_elapsed_text")
            )

            Surface(
                onClick = onStopClick,
                interactionSource = stopBtnInteraction,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = Color.White,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
                modifier = Modifier
                    .size(48.dp)
                    .pressScale(stopBtnInteraction)
                    .testTag("stop_timelapse_button")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = RoundedStopIcon,
                        contentDescription = stringResource(R.string.cd_stop_timelapse),
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

private val RoundedStopIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "RoundedStop",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = SolidColor(Color.White),
            pathFillType = PathFillType.NonZero
        ) {
            moveTo(9f, 6f)
            lineTo(15f, 6f)
            quadTo(18f, 6f, 18f, 9f)
            lineTo(18f, 15f)
            quadTo(18f, 18f, 15f, 18f)
            lineTo(9f, 18f)
            quadTo(6f, 18f, 6f, 15f)
            lineTo(6f, 9f)
            quadTo(6f, 6f, 9f, 6f)
            close()
        }
    }.build()
}

@Composable
private fun FloatingTraceToolbar(
    activeSheet: ActiveSheet,
    isOutlineActive: Boolean,
    isLocked: Boolean,
    onOpacityClick: () -> Unit,
    onLockClick: () -> Unit,
    onOutlineClick: () -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = modifier
            .widthIn(max = 420.dp)
            .testTag("floating_bottom_toolbar")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ToolbarPillButton(
                label = stringResource(R.string.toolbar_opacity),
                icon = Icons.Outlined.Opacity,
                isActive = activeSheet == ActiveSheet.OPACITY,
                onClick = onOpacityClick,
                testTag = "toolbar_opacity_button"
            )
            ToolbarPillButton(
                label = stringResource(R.string.toolbar_lock),
                icon = if (isLocked) Icons.Filled.Lock else Icons.Outlined.Lock,
                isActive = isLocked,
                onClick = onLockClick,
                testTag = "toolbar_lock_button"
            )
            ToolbarPillButton(
                label = stringResource(R.string.toolbar_outline),
                icon = Icons.Outlined.GridOn,
                isActive = isOutlineActive || activeSheet == ActiveSheet.OUTLINE,
                onClick = onOutlineClick,
                testTag = "toolbar_outline_button"
            )
            ToolbarPillButton(
                label = stringResource(R.string.toolbar_more),
                icon = Icons.Outlined.MoreHoriz,
                isActive = activeSheet == ActiveSheet.MORE ||
                    activeSheet == ActiveSheet.TIMELAPSE ||
                    activeSheet == ActiveSheet.PINNED,
                onClick = onMoreClick,
                testTag = "toolbar_more_button"
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ToolbarPillButton(
    label: String,
    icon: ImageVector,
    isActive: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    val animationsEnabled = rememberAnimationsEnabled()
    val interactionSource = remember { MutableInteractionSource() }
    val fillDuration = TraceMotion.duration(TraceMotion.DURATION_QUICK, animationsEnabled)

    val containerColor by animateColorAsState(
        targetValue = if (isActive) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            Color.Transparent
        },
        animationSpec = tween(durationMillis = fillDuration, easing = TraceMotion.StandardEasing),
        label = "toolbar_pill_container_color"
    )
    val contentColor by animateColorAsState(
        targetValue = if (isActive) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        animationSpec = tween(durationMillis = fillDuration, easing = TraceMotion.StandardEasing),
        label = "toolbar_pill_content_color"
    )

    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = {
            PlainTooltip(shape = RoundedCornerShape(12.dp)) {
                Text(text = label, style = MaterialTheme.typography.labelSmall)
            }
        },
        state = rememberTooltipState()
    ) {
        Surface(
            onClick = onClick,
            interactionSource = interactionSource,
            shape = CircleShape,
            color = containerColor,
            contentColor = contentColor,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            modifier = Modifier
                .defaultMinSize(minWidth = 72.dp, minHeight = 52.dp)
                .pressScale(interactionSource)
                .testTag(testTag)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Crossfade(
                    targetState = icon,
                    animationSpec = tween(
                        durationMillis = fillDuration,
                        easing = TraceMotion.StandardEasing
                    ),
                    label = "toolbar_icon_crossfade"
                ) { targetIcon ->
                    Icon(
                        imageVector = targetIcon,
                        contentDescription = label,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

@Composable
private fun TraceGridOverlay(
    cellSizeDp: Float,
    lineColor: Color,
    shadowColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val stepPx = cellSizeDp.dp.toPx().coerceAtLeast(16f)
        val width = size.width
        val height = size.height
        val strokePx = 1.dp.toPx()

        var x = stepPx
        while (x < width) {
            drawLine(
                color = shadowColor,
                start = Offset(x + strokePx, 0f),
                end = Offset(x + strokePx, height),
                strokeWidth = strokePx
            )
            drawLine(
                color = lineColor,
                start = Offset(x, 0f),
                end = Offset(x, height),
                strokeWidth = strokePx
            )
            x += stepPx
        }

        var y = stepPx
        while (y < height) {
            drawLine(
                color = shadowColor,
                start = Offset(0f, y + strokePx),
                end = Offset(width, y + strokePx),
                strokeWidth = strokePx
            )
            drawLine(
                color = lineColor,
                start = Offset(0f, y),
                end = Offset(width, y),
                strokeWidth = strokePx
            )
            y += stepPx
        }
    }
}

@Composable
private fun CameraPreviewLayer(
    isRecordingTimeLapse: Boolean,
    isFlashlightOn: Boolean,
    onFlashUnitAvailable: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    var camera by remember { mutableStateOf<Camera?>(null) }

    val previewView = remember {
        PreviewView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    // Share the PreviewView surface provider with TimeLapseRecordingService when active
    DisposableEffect(previewView) {
        TimeLapseSessionManager.attachSurfaceProvider(previewView.surfaceProvider)
        onDispose {
            TimeLapseSessionManager.attachSurfaceProvider(null)
        }
    }

    // Bind camera directly to the UI lifecycle when TimeLapseRecordingService is NOT active
    DisposableEffect(lifecycleOwner, previewView, isRecordingTimeLapse) {
        if (isRecordingTimeLapse) {
            camera = null
            return@DisposableEffect onDispose {}
        }

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val executor = ContextCompat.getMainExecutor(context)
        var disposed = false

        val bindJob = coroutineScope.launch {
            // Ensure any active ARCore session is closed first before rebinding CameraX
            CameraHandoverCoordinator.awaitArSessionClosed()
            if (disposed) return@launch

            val listener = Runnable {
                if (disposed) return@Runnable
                runCatching {
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }

                    val selector = when {
                        cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) ->
                            CameraSelector.DEFAULT_BACK_CAMERA
                        cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) ->
                            CameraSelector.DEFAULT_FRONT_CAMERA
                        else -> CameraSelector.DEFAULT_BACK_CAMERA
                    }

                    cameraProvider.unbindAll()
                    val boundCamera = cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        selector,
                        preview
                    )
                    camera = boundCamera
                    CameraHandoverCoordinator.onCameraXBound(boundCamera)
                    val hasFlash = boundCamera.cameraInfo.hasFlashUnit()
                    onFlashUnitAvailable(hasFlash)
                    if (hasFlash && isFlashlightOn) {
                        boundCamera.cameraControl.enableTorch(true)
                    }
                }
            }

            cameraProviderFuture.addListener(listener, executor)
        }

        onDispose {
            disposed = true
            bindJob.cancel()
            if (!TimeLapseSessionManager.status.value.isRecording) {
                runCatching {
                    camera?.cameraControl?.enableTorch(false)
                }
                CameraHandoverCoordinator.releaseCameraXAndTrackState(context)
            }
            camera = null
        }
    }

    LaunchedEffect(camera, isFlashlightOn) {
        val activeCamera = camera ?: return@LaunchedEffect
        if (activeCamera.cameraInfo.hasFlashUnit()) {
            runCatching {
                activeCamera.cameraControl.enableTorch(isFlashlightOn)
            }
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = modifier.testTag("camera_preview_view")
    )
}

/**
 * Handles smooth 1-finger drag, 2-finger pinch zoom, 2-finger twist rotation,
 * quick 2-finger tap to hide/show controls, and notifies on the very first touch.
 */
private suspend fun PointerInputScope.detectTraceGesturesAndTwoFingerTap(
    isLocked: () -> Boolean,
    onFirstTouch: () -> Unit,
    onGesture: (pan: Offset, zoom: Float, rotation: Float, uptimeMillis: Long) -> Unit,
    onGestureEnd: () -> Unit,
    onTwoFingerTap: () -> Unit
) {
    awaitEachGesture {
        var rotation = 0f
        var zoom = 1f
        var pan = Offset.Zero
        var pastTouchSlop = false
        val touchSlop = viewConfiguration.touchSlop

        val firstDown = awaitFirstDown(requireUnconsumed = false)
        onFirstTouch()
        val gestureStartTime = firstDown.uptimeMillis
        var lastUptimeMillis = gestureStartTime
        var maxPointersSeen = 1
        var totalMovementPx = 0f

        do {
            val event = awaitPointerEvent()
            val canceled = event.changes.any { it.isConsumed }
            event.changes.maxOfOrNull { it.uptimeMillis }?.let { lastUptimeMillis = it }

            val pressedCount = event.changes.count { it.pressed }
            if (pressedCount > maxPointersSeen) {
                maxPointersSeen = pressedCount
            }

            if (!canceled && !isLocked()) {
                val zoomChange = event.calculateZoom()
                val rotationChange = event.calculateRotation()
                val panChange = event.calculatePan()

                if (!pastTouchSlop) {
                    zoom *= zoomChange
                    rotation += rotationChange
                    pan += panChange

                    val centroidSize = event.calculateCentroidSize(useCurrent = false)
                    val zoomMotion = abs(1 - zoom) * centroidSize
                    val rotationMotion = abs(rotation * PI.toFloat() * centroidSize / 180f)
                    val panMotion = pan.getDistance()
                    totalMovementPx = maxOf(totalMovementPx, panMotion, zoomMotion, rotationMotion)

                    if (zoomMotion > touchSlop ||
                        rotationMotion > touchSlop ||
                        panMotion > touchSlop
                    ) {
                        pastTouchSlop = true
                    }
                }

                if (pastTouchSlop) {
                    val effectiveRotation = if (pressedCount >= 2) rotationChange else 0f
                    val effectiveZoom = if (pressedCount >= 2) zoomChange else 1f
                    if (effectiveRotation != 0f ||
                        effectiveZoom != 1f ||
                        panChange != Offset.Zero
                    ) {
                        onGesture(panChange, effectiveZoom, effectiveRotation, lastUptimeMillis)
                    }
                    event.changes.forEach { change ->
                        if (change.positionChanged()) {
                            change.consume()
                        }
                    }
                }
            }
        } while (!canceled && event.changes.any { it.pressed })

        if (pastTouchSlop) {
            onGestureEnd()
        }

        val gestureDurationMs = lastUptimeMillis - gestureStartTime
        if (!isLocked() &&
            maxPointersSeen == 2 &&
            !pastTouchSlop &&
            totalMovementPx < touchSlop &&
            gestureDurationMs in 1L..320L
        ) {
            onTwoFingerTap()
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
