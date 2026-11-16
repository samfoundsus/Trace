package com.example.ui.theme

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.provider.Settings
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.example.R

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
 * Native Google / Material 3 dialog wrapper:
 * - Disables any platform/OEM window slide animation or lingering window dim via [DialogWindowProvider]
 *   and keeps the root [Dialog] container at constant full-screen size from frame 1 so Android's
 *   WindowManager never triggers a window-bounds relayout slide.
 * - Open: fades in (0 -> 1) + scales from 0.85 to 1.0 around the dialog's own centre over 250 ms
 *   with emphasized-decelerate easing (cubic-bezier 0.05, 0.7, 0.1, 1.0), with zero translation.
 * - Scrim: fades in over 250 ms to standard scrim colour (black, 32% alpha), no slide.
 * - Close: fades out to 0 + scales to 0.95 over 150 ms with standard accelerate easing,
 *   with the dialog and scrim disappearing together and kept in composition until exit finishes.
 */
@Composable
fun AnimatedMaterialDialog(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    content: @Composable () -> Unit
) {
    val animationsEnabled = rememberAnimationsEnabled()
    val transitionState = remember { MutableTransitionState(false) }
    // Set targetState synchronously during composition so Frame 1 immediately begins the transition
    // and never measures an empty 0x0 dialog window first.
    transitionState.targetState = visible

    if (visible || transitionState.currentState || !transitionState.isIdle) {
        Dialog(
            onDismissRequest = onDismissRequest,
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            val view = LocalView.current
            val dialogWindowProvider = view.parent as? DialogWindowProvider

            remember(dialogWindowProvider) {
                dialogWindowProvider?.window?.apply {
                    setWindowAnimations(R.style.Animation_Trace_DialogWindow)
                    setDimAmount(0f)
                    clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                    setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
                    setLayout(
                        WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.MATCH_PARENT
                    )
                }
                true
            }
            SideEffect {
                dialogWindowProvider?.window?.apply {
                    setWindowAnimations(R.style.Animation_Trace_DialogWindow)
                    setDimAmount(0f)
                    clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                }
            }

            val enterDur = TraceMotion.duration(TraceMotion.DURATION_MEDIUM, animationsEnabled)
            val exitDur = TraceMotion.duration(TraceMotion.DURATION_QUICK, animationsEnabled)

            val transition = rememberTransition(transitionState, label = "material_dialog_transition")
            val progress by transition.animateFloat(
                transitionSpec = {
                    if (targetState) {
                        if (animationsEnabled) {
                            tween(
                                durationMillis = enterDur,
                                easing = TraceMotion.EmphasizedDecelerateEasing
                            )
                        } else {
                            snap()
                        }
                    } else {
                        if (animationsEnabled) {
                            tween(
                                durationMillis = exitDur,
                                easing = TraceMotion.StandardAccelerateEasing
                            )
                        } else {
                            snap()
                        }
                    }
                },
                label = "dialog_progress"
            ) { isVisible ->
                if (isVisible) 1f else 0f
            }

            val isOpening = transition.targetState
            val scrimInteractionSource = remember { MutableInteractionSource() }

            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                // Scrim: fades in over 250 ms to black @ 32% alpha and fades out over 150 ms with the dialog
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = progress }
                        .background(Color.Black.copy(alpha = 0.32f))
                        .clickable(
                            interactionSource = scrimInteractionSource,
                            indication = null,
                            enabled = visible,
                            onClick = onDismissRequest
                        )
                )

                // Dialog card: constant layout bounds centered on screen;
                // open fade (0 -> 1) + scale (0.85 -> 1.0) around Center in 250 ms (emphasized-decelerate);
                // close fade (1 -> 0) + scale (1.0 -> 0.95) around Center in 150 ms (standard-accelerate).
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .graphicsLayer {
                            val scale = if (isOpening) {
                                0.85f + (0.15f * progress)
                            } else {
                                0.95f + (0.05f * progress)
                            }
                            alpha = progress
                            scaleX = scale
                            scaleY = scale
                            transformOrigin = TransformOrigin.Center
                        }
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Final)
                                    event.changes.forEach { it.consume() }
                                }
                            }
                        }
                ) {
                    content()
                }
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
