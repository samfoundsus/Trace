package com.example.ui.theme

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Standard Material 3 Easing curves and durations.
 */
object TraceMotion {
    val EmphasizedEasing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
    val EmphasizedDecelerateEasing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)
    val EmphasizedAccelerateEasing = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)

    val StandardEasing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
    val StandardDecelerateEasing = LinearOutSlowInEasing
    val StandardAccelerateEasing = FastOutLinearInEasing

    const val DURATION_QUICK = 150
    const val DURATION_PILL = 200
    const val DURATION_MEDIUM = 250
    const val DURATION_SHARED_AXIS = 300
    const val DURATION_CONTAINER_TRANSFORM = 350
    const val DURATION_COUNTDOWN_IN = 400

    fun duration(baseMs: Int, animationsEnabled: Boolean): Int =
        if (animationsEnabled) baseMs else 0
}

/**
 * Checks whether system animator duration scale is enabled (> 0f).
 */
fun Context.areSystemAnimationsEnabled(): Boolean {
    return runCatching {
        Settings.Global.getFloat(
            contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        ) > 0f
    }.getOrDefault(true)
}

@Composable
fun rememberAnimationsEnabled(): Boolean {
    val context = LocalContext.current
    return remember(context) { context.areSystemAnimationsEnabled() }
}

/**
 * Applies a subtle press scale to 0.97f on tap down and springs back to 1f on release (no bounce),
 * Driven by an [InteractionSource].
 */
fun Modifier.pressScale(
    interactionSource: InteractionSource,
    enabled: Boolean = true,
    pressedScale: Float = 0.97f
): Modifier = composed {
    val animationsEnabled = rememberAnimationsEnabled()
    val isPressed by interactionSource.collectIsPressedAsState()
    val target = if (enabled && isPressed && animationsEnabled) pressedScale else 1f
    val scale by animateFloatAsState(
        targetValue = target,
        animationSpec = if (animationsEnabled) {
            spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        } else {
            snap()
        },
        label = "press_scale"
    )
    this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * Self-contained press scale modifier for composables that manage their own internal InteractionSource.
 * Observes pointer down/up on the Initial pass without consuming events so ripples and clicks work normally.
 */
fun Modifier.tapPressScale(
    enabled: Boolean = true,
    pressedScale: Float = 0.97f
): Modifier = composed {
    val animationsEnabled = rememberAnimationsEnabled()
    var isPressed by remember { mutableStateOf(false) }
    val target = if (enabled && isPressed && animationsEnabled) pressedScale else 1f
    val scale by animateFloatAsState(
        targetValue = target,
        animationSpec = if (animationsEnabled) {
            spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        } else {
            snap()
        },
        label = "tap_press_scale"
    )
    this
        .pointerInput(enabled) {
            if (!enabled) return@pointerInput
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val anyPressed = event.changes.any { it.pressed }
                    if (isPressed != anyPressed) {
                        isPressed = anyPressed
                    }
                }
            }
        }
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
}

/**
 * Animates slider value tooltip fade and subtle scale (0.92f -> 1f) while dragging or pressing.
 */
fun Modifier.sliderLabelTransition(
    interactionSource: InteractionSource
): Modifier = composed {
    val animationsEnabled = rememberAnimationsEnabled()
    val isDragged by interactionSource.collectIsDraggedAsState()
    val isPressed by interactionSource.collectIsPressedAsState()
    val active = isDragged || isPressed
    val dur = TraceMotion.duration(TraceMotion.DURATION_QUICK, animationsEnabled)
    val alpha by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = tween(durationMillis = dur, easing = TraceMotion.StandardEasing),
        label = "slider_label_alpha"
    )
    val scale by animateFloatAsState(
        targetValue = if (active) 1f else 0.9f,
        animationSpec = tween(durationMillis = dur, easing = TraceMotion.EmphasizedDecelerateEasing),
        label = "slider_label_scale"
    )
    this.graphicsLayer {
        this.alpha = alpha
        this.scaleX = scale
        this.scaleY = scale
    }
}

/**
 * Fades in content inside a bottom sheet 50 ms after the sheet starts moving.
 */
@Composable
fun SheetDelayedContent(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val animationsEnabled = rememberAnimationsEnabled()
    var visible by remember { mutableStateOf(!animationsEnabled) }
    LaunchedEffect(Unit) {
        visible = true
    }
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = if (animationsEnabled) {
            tween(
                durationMillis = 200,
                delayMillis = 50,
                easing = TraceMotion.StandardDecelerateEasing
            )
        } else {
            snap()
        },
        label = "sheet_content_fade"
    )
    Box(
        modifier = modifier.graphicsLayer {
            this.alpha = alpha
        }
    ) {
        content()
    }
}

/**
 * Staggered entrance animation for Settings cards: fades in from 8.dp below with a 40 ms stagger.
 */
@Composable
fun StaggeredSettingsCardEntrance(
    index: Int,
    playEntrance: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val animationsEnabled = rememberAnimationsEnabled()
    val shouldAnimate = animationsEnabled && playEntrance
    var started by remember { mutableStateOf(!shouldAnimate) }
    LaunchedEffect(Unit) {
        started = true
    }
    val offsetPx = with(LocalDensity.current) { 8.dp.toPx() }
    val delayMs = (index.coerceAtLeast(0) * 40)

    val progress by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = if (shouldAnimate) {
            tween(
                durationMillis = TraceMotion.DURATION_MEDIUM,
                delayMillis = delayMs,
                easing = TraceMotion.EmphasizedDecelerateEasing
            )
        } else {
            snap()
        },
        label = "settings_card_entrance_$index"
    )

    Box(
        modifier = modifier.graphicsLayer {
            alpha = progress
            translationY = (1f - progress) * offsetPx
        }
    ) {
        content()
    }
}

/**
 * Material dialog wrapper that scales from 0.9 to 1 + fades in over 250 ms,
 * and fades out over 150 ms when dismissed.
 */
@Composable
fun AnimatedMaterialDialog(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    content: @Composable () -> Unit
) {
    val animationsEnabled = rememberAnimationsEnabled()
    val transitionState = remember { MutableTransitionState(false) }

    LaunchedEffect(visible) {
        transitionState.targetState = visible
    }

    if (visible || transitionState.currentState || !transitionState.isIdle) {
        Dialog(
            onDismissRequest = onDismissRequest,
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            val enterDur = TraceMotion.duration(TraceMotion.DURATION_MEDIUM, animationsEnabled)
            val exitDur = TraceMotion.duration(TraceMotion.DURATION_QUICK, animationsEnabled)
            AnimatedVisibility(
                visibleState = transitionState,
                enter = fadeIn(
                    animationSpec = tween(
                        durationMillis = enterDur,
                        easing = TraceMotion.EmphasizedDecelerateEasing
                    )
                ) + scaleIn(
                    initialScale = 0.9f,
                    animationSpec = tween(
                        durationMillis = enterDur,
                        easing = TraceMotion.EmphasizedDecelerateEasing
                    )
                ),
                exit = fadeOut(
                    animationSpec = tween(
                        durationMillis = exitDur,
                        easing = TraceMotion.EmphasizedAccelerateEasing
                    )
                ) + scaleOut(
                    targetScale = 0.9f,
                    animationSpec = tween(
                        durationMillis = exitDur,
                        easing = TraceMotion.EmphasizedAccelerateEasing
                    )
                )
            ) {
                content()
            }
        }
    }
}

/**
 * Animates snackbar slide-up and fade over 250 ms.
 */
@Composable
fun AnimatedSnackbarContainer(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val animationsEnabled = rememberAnimationsEnabled()
    val dur = TraceMotion.duration(TraceMotion.DURATION_MEDIUM, animationsEnabled)
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(
            initialOffsetY = { it / 2 },
            animationSpec = tween(durationMillis = dur, easing = TraceMotion.EmphasizedDecelerateEasing)
        ) + fadeIn(
            animationSpec = tween(durationMillis = dur, easing = TraceMotion.StandardDecelerateEasing)
        ),
        exit = slideOutVertically(
            targetOffsetY = { it / 2 },
            animationSpec = tween(durationMillis = dur, easing = TraceMotion.EmphasizedAccelerateEasing)
        ) + fadeOut(
            animationSpec = tween(durationMillis = dur, easing = TraceMotion.StandardAccelerateEasing)
        ),
        modifier = modifier
    ) {
        content()
    }
}
