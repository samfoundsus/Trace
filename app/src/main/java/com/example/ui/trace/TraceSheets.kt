package com.example.ui.trace

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Label
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.AppSettings
import com.example.domain.model.TimeLapseQuality
import com.example.domain.model.TraceState
import com.example.domain.usecase.PinnedModeLogic
import com.example.ui.theme.SheetDelayedContent
import com.example.ui.theme.TraceMotion
import com.example.ui.theme.pressScale
import com.example.ui.theme.rememberAnimationsEnabled
import com.example.ui.theme.sliderLabelTransition
import kotlin.math.roundToInt

private val SheetCornerShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpacityBottomSheet(
    opacity: Float,
    onOpacityChange: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val percentageText = stringResource(
        R.string.sheet_opacity_value,
        (opacity * 100f).roundToInt()
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = SheetCornerShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f),
        tonalElevation = 0.dp,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        SheetDelayedContent {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 24.dp)
                    .testTag("opacity_sheet")
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.sheet_opacity_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = percentageText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                LabeledMaterialSlider(
                    value = opacity,
                    onValueChange = onOpacityChange,
                    valueRange = 0.1f..1.0f,
                    labelText = percentageText,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("opacity_slider")
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutlineBottomSheet(
    isOutlineEnabled: Boolean,
    outlineStrength: Float,
    onOutlineToggle: (Boolean) -> Unit,
    onStrengthChange: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptics = LocalHapticFeedback.current
    val strengthPercentText = stringResource(
        R.string.sheet_outline_strength_value,
        (outlineStrength * 100f).roundToInt()
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = SheetCornerShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f),
        tonalElevation = 0.dp,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        SheetDelayedContent {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = 24.dp)
                    .testTag("outline_sheet")
            ) {
                Text(
                    text = stringResource(R.string.sheet_outline_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 12.dp)
                )

                TextSwitchRow(
                    label = stringResource(R.string.sheet_outline_toggle),
                    supportingText = stringResource(R.string.sheet_outline_toggle_supporting),
                    checked = isOutlineEnabled,
                    onCheckedChange = { enabled ->
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onOutlineToggle(enabled)
                    },
                    testTag = "outline_toggle_row",
                    switchTestTag = "outline_switch"
                )

                Spacer(modifier = Modifier.height(12.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.sheet_outline_strength),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = strengthPercentText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    LabeledMaterialSlider(
                        value = outlineStrength,
                        onValueChange = onStrengthChange,
                        valueRange = 0.05f..0.95f,
                        enabled = isOutlineEnabled,
                        labelText = strengthPercentText,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("outline_strength_slider")
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeLapseBottomSheet(
    intervalSeconds: Int,
    quality: TimeLapseQuality,
    includeOverlay: Boolean,
    countdownSeconds: Int,
    isRecording: Boolean,
    onIntervalChange: (Int) -> Unit,
    onQualityChange: (TimeLapseQuality) -> Unit,
    onIncludeOverlayChange: (Boolean) -> Unit,
    onCountdownChange: (Int) -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptics = LocalHapticFeedback.current
    val controlsEnabled = !isRecording
    val labelColor = if (controlsEnabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
    }
    val supportingColor = if (controlsEnabled) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    }

    val intervalResultText = when (intervalSeconds) {
        1 -> stringResource(R.string.sheet_timelapse_interval_1s_result)
        2 -> stringResource(R.string.sheet_timelapse_interval_2s_result)
        5 -> stringResource(R.string.sheet_timelapse_interval_5s_result)
        10 -> stringResource(R.string.sheet_timelapse_interval_10s_result)
        else -> stringResource(R.string.sheet_timelapse_interval_2s_result)
    }

    val overlayRowInteraction = remember { MutableInteractionSource() }
    val startBtnInteraction = remember { MutableInteractionSource() }
    val cancelBtnInteraction = remember { MutableInteractionSource() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = SheetCornerShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f),
        tonalElevation = 0.dp,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        SheetDelayedContent {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(24.dp)
                    .testTag("timelapse_sheet")
            ) {
                Text(
                    text = stringResource(R.string.sheet_timelapse_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(20.dp))

                // 1. Interval
                Text(
                    text = stringResource(R.string.settings_timelapse_interval),
                    style = MaterialTheme.typography.bodyLarge,
                    color = labelColor
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AppSettings.SUPPORTED_INTERVALS_SECONDS.forEach { seconds ->
                        AnimatedPillFilterChip(
                            selected = intervalSeconds == seconds,
                            enabled = controlsEnabled,
                            label = stringResource(R.string.settings_timelapse_interval_value, seconds),
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onIntervalChange(seconds)
                            },
                            testTag = "timelapse_sheet_interval_chip_${seconds}s"
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = intervalResultText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = supportingColor,
                    modifier = Modifier.testTag("timelapse_interval_info_text")
                )

                Spacer(modifier = Modifier.height(20.dp))

                // 2. Quality
                Text(
                    text = stringResource(R.string.settings_timelapse_quality),
                    style = MaterialTheme.typography.bodyLarge,
                    color = labelColor
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TimeLapseQuality.entries.forEach { option ->
                        AnimatedPillFilterChip(
                            selected = quality == option,
                            enabled = controlsEnabled,
                            label = when (option) {
                                TimeLapseQuality.QUALITY_720P -> stringResource(R.string.settings_timelapse_quality_720p)
                                TimeLapseQuality.QUALITY_1080P -> stringResource(R.string.settings_timelapse_quality_1080p)
                            },
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onQualityChange(option)
                            },
                            testTag = "timelapse_sheet_quality_chip_${option.label}"
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // 3. Include overlay image
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .pressScale(overlayRowInteraction, enabled = controlsEnabled)
                        .then(
                            if (controlsEnabled) {
                                Modifier.clickable(
                                    interactionSource = overlayRowInteraction,
                                    indication = LocalIndication.current
                                ) {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onIncludeOverlayChange(!includeOverlay)
                                }
                            } else {
                                Modifier
                            }
                        )
                        .testTag("timelapse_sheet_include_overlay_row"),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.settings_timelapse_include_overlay),
                            style = MaterialTheme.typography.bodyLarge,
                            color = labelColor
                        )
                        Text(
                            text = stringResource(R.string.sheet_timelapse_include_overlay_supporting),
                            style = MaterialTheme.typography.bodyMedium,
                            color = supportingColor
                        )
                    }
                    Switch(
                        checked = includeOverlay,
                        enabled = controlsEnabled,
                        onCheckedChange = { enabled ->
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onIncludeOverlayChange(enabled)
                        },
                        modifier = Modifier.testTag("timelapse_sheet_include_overlay_switch")
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // 4. Countdown before start
                Text(
                    text = stringResource(R.string.sheet_timelapse_countdown_label),
                    style = MaterialTheme.typography.bodyLarge,
                    color = labelColor
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AppSettings.SUPPORTED_COUNTDOWNS_SECONDS.forEach { seconds ->
                        val chipLabel = if (seconds == 0) {
                            stringResource(R.string.sheet_timelapse_countdown_off)
                        } else {
                            stringResource(R.string.settings_timelapse_interval_value, seconds)
                        }
                        val chipTagSuffix = if (seconds == 0) "off" else "${seconds}s"
                        AnimatedPillFilterChip(
                            selected = countdownSeconds == seconds,
                            enabled = controlsEnabled,
                            label = chipLabel,
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onCountdownChange(seconds)
                            },
                            testTag = "timelapse_sheet_countdown_chip_$chipTagSuffix"
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 5. Large filled "Start" (or "Stop") button + quiet text "Cancel" button under it
                Button(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        if (isRecording) {
                            onStopRecording()
                        } else {
                            onStartRecording()
                        }
                    },
                    interactionSource = startBtnInteraction,
                    shape = CircleShape,
                    colors = if (isRecording) {
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        )
                    } else {
                        ButtonDefaults.buttonColors()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .pressScale(startBtnInteraction)
                        .testTag("timelapse_start_button")
                ) {
                    if (!isRecording) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    shape = CircleShape
                                )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = stringResource(
                            if (isRecording) {
                                R.string.sheet_timelapse_stop
                            } else {
                                R.string.sheet_timelapse_start
                            }
                        ),
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                TextButton(
                    onClick = onDismiss,
                    interactionSource = cancelBtnInteraction,
                    shape = CircleShape,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .pressScale(cancelBtnInteraction)
                        .testTag("timelapse_cancel_button")
                ) {
                    Text(
                        text = stringResource(R.string.dialog_cancel),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun AnimatedPillFilterChip(
    selected: Boolean,
    enabled: Boolean,
    label: String,
    onClick: () -> Unit,
    testTag: String
) {
    val animationsEnabled = rememberAnimationsEnabled()
    val interactionSource = remember { MutableInteractionSource() }
    val dur = TraceMotion.duration(TraceMotion.DURATION_QUICK, animationsEnabled)

    val containerColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            Color.Transparent
        },
        animationSpec = tween(durationMillis = dur, easing = TraceMotion.StandardEasing),
        label = "chip_container_color"
    )
    val labelColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(durationMillis = dur, easing = TraceMotion.StandardEasing),
        label = "chip_label_color"
    )

    FilterChip(
        selected = selected,
        enabled = enabled,
        onClick = onClick,
        interactionSource = interactionSource,
        label = { Text(text = label) },
        shape = CircleShape,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = containerColor,
            selectedContainerColor = containerColor,
            labelColor = labelColor,
            selectedLabelColor = labelColor
        ),
        modifier = Modifier
            .pressScale(interactionSource, enabled = enabled)
            .testTag(testTag)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PinToSurfaceBottomSheet(
    paperWidthCm: Float,
    onPaperWidthChange: (Float) -> Unit,
    onRePin: () -> Unit,
    onBackToNormal: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptics = LocalHapticFeedback.current
    val paperWidthLabel = stringResource(
        R.string.sheet_pin_paper_width_value,
        paperWidthCm.roundToInt()
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = SheetCornerShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f),
        tonalElevation = 0.dp,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        SheetDelayedContent {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = 24.dp)
                    .testTag("pin_sheet")
            ) {
                Text(
                    text = stringResource(R.string.sheet_pin_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 12.dp)
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.sheet_pin_paper_width),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = paperWidthLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    LabeledMaterialSlider(
                        value = paperWidthCm,
                        onValueChange = onPaperWidthChange,
                        valueRange = PinnedModeLogic.MIN_PAPER_WIDTH_CM..PinnedModeLogic.MAX_PAPER_WIDTH_CM,
                        labelText = paperWidthLabel,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("pin_paper_width_slider")
                    )
                }

                TextActionRow(
                    label = stringResource(R.string.sheet_pin_repin),
                    supportingText = stringResource(R.string.sheet_pin_repin_supporting),
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onRePin()
                    },
                    testTag = "pin_repin_row"
                )

                TextActionRow(
                    label = stringResource(R.string.sheet_pin_back_to_normal),
                    supportingText = stringResource(R.string.sheet_pin_back_to_normal_supporting),
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onBackToNormal()
                    },
                    testTag = "pin_back_to_normal_row"
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreBottomSheet(
    state: TraceState,
    onPinToSurfaceClick: () -> Unit,
    onOpenTimeLapseSheet: () -> Unit,
    onSaveProject: () -> Unit,
    onFlashlightToggle: (Boolean) -> Unit,
    onFlipHorizontal: () -> Unit,
    onFlipVertical: () -> Unit,
    onGridToggle: (Boolean) -> Unit,
    onGridCellSizeChange: (Float) -> Unit,
    onResetPosition: () -> Unit,
    onChangeImage: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptics = LocalHapticFeedback.current
    val gridSizeLabel = stringResource(
        R.string.more_grid_size_value,
        state.gridCellSizeDp.roundToInt()
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = SheetCornerShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f),
        tonalElevation = 0.dp,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        SheetDelayedContent {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(bottom = 24.dp)
                    .testTag("more_sheet")
            ) {
                Text(
                    text = stringResource(R.string.sheet_more_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 12.dp)
                )

                // 1. Pin to surface (shown only if ARCore is supported on this device)
                if (state.isArSupported) {
                    TextActionRow(
                        label = stringResource(R.string.more_pin_to_surface),
                        supportingText = stringResource(
                            if (state.isPinnedMode) {
                                R.string.more_pin_active_supporting
                            } else {
                                R.string.more_pin_to_surface_supporting
                            }
                        ),
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onPinToSurfaceClick()
                        },
                        testTag = "more_pin_to_surface_row"
                    )
                }

                // 2. Time-lapse (disabled with supporting line when in pinned mode)
                if (state.isPinnedMode) {
                    TextActionRow(
                        label = stringResource(R.string.more_timelapse),
                        supportingText = stringResource(R.string.more_timelapse_pinned_unavailable),
                        onClick = {},
                        enabled = false,
                        testTag = "more_timelapse_unavailable_row"
                    )
                } else {
                    TextActionRow(
                        label = stringResource(R.string.more_timelapse),
                        supportingText = stringResource(R.string.more_timelapse_supporting),
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onOpenTimeLapseSheet()
                        },
                        testTag = "more_timelapse_row"
                    )
                }

                // 3. Save project (text only, no icon)
                TextActionRow(
                    label = stringResource(R.string.more_save_project),
                    supportingText = stringResource(R.string.more_save_project_supporting),
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSaveProject()
                    },
                    testTag = "more_save_project_row"
                )

                // 4. Flashlight (hidden if device has no flash unit)
                if (state.hasFlashUnit) {
                    TextSwitchRow(
                        label = stringResource(R.string.more_flashlight),
                        supportingText = stringResource(R.string.more_flashlight_supporting),
                        checked = state.isFlashlightOn,
                        onCheckedChange = { enabled ->
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onFlashlightToggle(enabled)
                        },
                        testTag = "more_flashlight_row"
                    )
                }

                // 5. Flip horizontal
                TextSwitchRow(
                    label = stringResource(R.string.more_flip_horizontal),
                    supportingText = stringResource(R.string.more_flip_horizontal_supporting),
                    checked = state.flipHorizontal,
                    onCheckedChange = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onFlipHorizontal()
                    },
                    testTag = "more_flip_horizontal_row"
                )

                // 6. Flip vertical
                TextSwitchRow(
                    label = stringResource(R.string.more_flip_vertical),
                    supportingText = stringResource(R.string.more_flip_vertical_supporting),
                    checked = state.flipVertical,
                    onCheckedChange = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onFlipVertical()
                    },
                    testTag = "more_flip_vertical_row"
                )

                // 7. Grid (on/off + cell size slider)
                TextSwitchRow(
                    label = stringResource(R.string.more_grid),
                    supportingText = stringResource(R.string.more_grid_supporting),
                    checked = state.isGridEnabled,
                    onCheckedChange = { enabled ->
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onGridToggle(enabled)
                    },
                    testTag = "more_grid_row"
                )

                if (state.isGridEnabled) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.more_grid_size),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = gridSizeLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        LabeledMaterialSlider(
                            value = state.gridCellSizeDp,
                            onValueChange = onGridCellSizeChange,
                            valueRange = 24f..120f,
                            labelText = gridSizeLabel,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("more_grid_size_slider")
                        )
                    }
                }

                // 8. Reset position (text only, no icon)
                TextActionRow(
                    label = stringResource(R.string.more_reset_position),
                    supportingText = stringResource(R.string.more_reset_position_supporting),
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onResetPosition()
                        onDismiss()
                    },
                    testTag = "more_reset_position_row"
                )

                // 9. Change image (text only, no icon)
                TextActionRow(
                    label = stringResource(R.string.more_change_image),
                    supportingText = stringResource(R.string.more_change_image_supporting),
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onChangeImage()
                    },
                    testTag = "more_change_image_row"
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LabeledMaterialSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    labelText: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        enabled = enabled,
        interactionSource = interactionSource,
        thumb = {
            Label(
                label = {
                    PlainTooltip(
                        shape = RoundedCornerShape(12.dp),
                        containerColor = MaterialTheme.colorScheme.inverseSurface,
                        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                        modifier = Modifier.sliderLabelTransition(interactionSource)
                    ) {
                        Text(
                            text = labelText,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                },
                interactionSource = interactionSource
            ) {
                SliderDefaults.Thumb(
                    interactionSource = interactionSource,
                    enabled = enabled
                )
            }
        },
        modifier = modifier
    )
}

@Composable
private fun TextSwitchRow(
    label: String,
    supportingText: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String,
    switchTestTag: String? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .pressScale(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current
            ) { onCheckedChange(!checked) }
            .padding(horizontal = 24.dp, vertical = 12.dp)
            .testTag(testTag),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = supportingText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = if (switchTestTag != null) {
                Modifier.testTag(switchTestTag)
            } else {
                Modifier
            }
        )
    }
}

@Composable
private fun TextActionRow(
    label: String,
    supportingText: String,
    onClick: () -> Unit,
    testTag: String,
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .pressScale(interactionSource, enabled = enabled)
            .then(
                if (enabled) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = LocalIndication.current,
                        onClick = onClick
                    )
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 24.dp, vertical = 12.dp)
            .testTag(testTag),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            }
        )
        Text(
            text = supportingText,
            style = MaterialTheme.typography.bodyMedium,
            color = if (enabled) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
            }
        )
    }
}
