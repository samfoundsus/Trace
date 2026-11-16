package com.example.ui.settings

import android.os.Build
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Label
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.BuildConfig
import com.example.R
import com.example.domain.model.AppSettings
import com.example.domain.model.ThemeMode
import com.example.domain.model.TimeLapseQuality
import com.example.ui.theme.AnimatedMaterialDialog
import com.example.ui.theme.StaggeredSettingsCardEntrance
import com.example.ui.theme.pressScale
import com.example.ui.theme.rememberAnimationsEnabled
import com.example.ui.theme.sliderLabelTransition
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

private val SettingsCardShape = RoundedCornerShape(28.dp)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val animationsEnabled = rememberAnimationsEnabled()

    var hasPlayedCardEntrance by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(animationsEnabled) {
        if (!hasPlayedCardEntrance) {
            if (animationsEnabled) {
                delay(450L)
            }
            hasPlayedCardEntrance = true
        }
    }

    var showThemeDialog by rememberSaveable { mutableStateOf(false) }
    var showIntervalDialog by rememberSaveable { mutableStateOf(false) }
    var showQualityDialog by rememberSaveable { mutableStateOf(false) }
    var showClearRecentDialog by rememberSaveable { mutableStateOf(false) }
    var showDeleteAllProjectsDialog by rememberSaveable { mutableStateOf(false) }

    val themeLabel = when (settings.themeMode) {
        ThemeMode.SYSTEM -> stringResource(R.string.settings_theme_system)
        ThemeMode.LIGHT -> stringResource(R.string.settings_theme_light)
        ThemeMode.DARK -> stringResource(R.string.settings_theme_dark)
    }

    val intervalLabel = stringResource(
        R.string.settings_timelapse_interval_value,
        settings.timeLapseIntervalSeconds
    )

    val qualityLabel = when (settings.timeLapseQuality) {
        TimeLapseQuality.QUALITY_720P -> stringResource(R.string.settings_timelapse_quality_720p)
        TimeLapseQuality.QUALITY_1080P -> stringResource(R.string.settings_timelapse_quality_1080p)
    }

    val defaultOpacityPercent = (settings.defaultOpacity * 100f).roundToInt()
    val opacityValueLabel = stringResource(R.string.sheet_opacity_value, defaultOpacityPercent)
    val showDynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val backBtnInteraction = remember { MutableInteractionSource() }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            val collapsedFraction = scrollBehavior.state.collapsedFraction
            LargeTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.headlineLarge,
                        modifier = Modifier.graphicsLayer {
                            val titleScale = 1f - (collapsedFraction * 0.04f)
                            scaleX = titleScale
                            scaleY = titleScale
                            transformOrigin = TransformOrigin(0f, 0.5f)
                        }
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        interactionSource = backBtnInteraction,
                        modifier = Modifier
                            .size(48.dp)
                            .pressScale(backBtnInteraction)
                            .testTag("settings_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp)
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 1. Appearance Group
                StaggeredSettingsCardEntrance(
                    index = 0,
                    playEntrance = !hasPlayedCardEntrance
                ) {
                    SettingsGroupCard(
                        header = stringResource(R.string.settings_section_appearance)
                    ) {
                        SettingsActionRow(
                            title = stringResource(R.string.settings_theme),
                            supportingText = themeLabel,
                            onClick = { showThemeDialog = true },
                            testTag = "settings_theme_row"
                        )

                        if (showDynamicColor) {
                            SettingsCardDivider()
                            SettingsSwitchRow(
                                title = stringResource(R.string.settings_dynamic_color),
                                supportingText = stringResource(R.string.settings_dynamic_color_supporting),
                                checked = settings.dynamicColor,
                                onCheckedChange = { enabled ->
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    viewModel.setDynamicColor(enabled)
                                },
                                testTag = "settings_dynamic_color_row"
                            )
                        }
                    }
                }

                // 2. Trace Group
                StaggeredSettingsCardEntrance(
                    index = 1,
                    playEntrance = !hasPlayedCardEntrance
                ) {
                    SettingsGroupCard(
                        header = stringResource(R.string.settings_section_trace)
                    ) {
                        SettingsSwitchRow(
                            title = stringResource(R.string.settings_keep_screen_on),
                            supportingText = stringResource(R.string.settings_keep_screen_on_supporting),
                            checked = settings.keepScreenOn,
                            onCheckedChange = { enabled ->
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.setKeepScreenOn(enabled)
                            },
                            testTag = "settings_keep_screen_on_row"
                        )

                        SettingsCardDivider()

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 64.dp)
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                                .testTag("settings_default_opacity_row")
                        ) {
                            Text(
                                text = stringResource(R.string.settings_default_opacity),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = stringResource(
                                    R.string.settings_default_opacity_supporting,
                                    defaultOpacityPercent
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            val sliderInteraction = remember { MutableInteractionSource() }
                            Slider(
                                value = settings.defaultOpacity,
                                onValueChange = viewModel::setDefaultOpacity,
                                valueRange = 0.1f..1.0f,
                                interactionSource = sliderInteraction,
                                thumb = {
                                    Label(
                                        label = {
                                            PlainTooltip(
                                                shape = RoundedCornerShape(12.dp),
                                                containerColor = MaterialTheme.colorScheme.inverseSurface,
                                                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                                                modifier = Modifier.sliderLabelTransition(sliderInteraction)
                                            ) {
                                                Text(
                                                    text = opacityValueLabel,
                                                    style = MaterialTheme.typography.labelMedium
                                                )
                                            }
                                        },
                                        interactionSource = sliderInteraction
                                    ) {
                                        SliderDefaults.Thumb(interactionSource = sliderInteraction)
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("settings_default_opacity_slider")
                            )
                        }

                        SettingsCardDivider()

                        SettingsSwitchRow(
                            title = stringResource(R.string.settings_hide_controls_on_start),
                            supportingText = stringResource(R.string.settings_hide_controls_on_start_supporting),
                            checked = settings.hideControlsOnStart,
                            onCheckedChange = { enabled ->
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.setHideControlsOnStart(enabled)
                            },
                            testTag = "settings_hide_controls_row"
                        )
                    }
                }

                // 3. Time-lapse Group
                StaggeredSettingsCardEntrance(
                    index = 2,
                    playEntrance = !hasPlayedCardEntrance
                ) {
                    SettingsGroupCard(
                        header = stringResource(R.string.settings_section_timelapse)
                    ) {
                        SettingsActionRow(
                            title = stringResource(R.string.settings_timelapse_interval),
                            supportingText = intervalLabel,
                            onClick = { showIntervalDialog = true },
                            testTag = "settings_timelapse_interval_row"
                        )

                        SettingsCardDivider()

                        SettingsActionRow(
                            title = stringResource(R.string.settings_timelapse_quality),
                            supportingText = qualityLabel,
                            onClick = { showQualityDialog = true },
                            testTag = "settings_timelapse_quality_row"
                        )

                        SettingsCardDivider()

                        SettingsSwitchRow(
                            title = stringResource(R.string.settings_timelapse_include_overlay),
                            supportingText = stringResource(R.string.settings_timelapse_include_overlay_supporting),
                            checked = settings.timeLapseIncludeOverlay,
                            onCheckedChange = { enabled ->
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.setTimeLapseIncludeOverlay(enabled)
                            },
                            testTag = "settings_timelapse_include_overlay_row"
                        )
                    }
                }

                // 4. Data Group
                StaggeredSettingsCardEntrance(
                    index = 3,
                    playEntrance = !hasPlayedCardEntrance
                ) {
                    SettingsGroupCard(
                        header = stringResource(R.string.settings_section_data)
                    ) {
                        SettingsActionRow(
                            title = stringResource(R.string.settings_clear_recent),
                            supportingText = stringResource(R.string.settings_clear_recent_supporting),
                            onClick = { showClearRecentDialog = true },
                            testTag = "settings_clear_recent_row"
                        )

                        SettingsCardDivider()

                        SettingsActionRow(
                            title = stringResource(R.string.settings_delete_all_projects),
                            supportingText = stringResource(R.string.settings_delete_all_projects_supporting),
                            onClick = { showDeleteAllProjectsDialog = true },
                            testTag = "settings_delete_all_projects_row"
                        )
                    }
                }

                // 5. About Group (Version, Build, and Commit)
                StaggeredSettingsCardEntrance(
                    index = 4,
                    playEntrance = !hasPlayedCardEntrance
                ) {
                    SettingsGroupCard(
                        header = stringResource(R.string.settings_section_about)
                    ) {
                        SettingsActionRow(
                            title = stringResource(R.string.settings_about_version),
                            supportingText = BuildConfig.BASE_VERSION_NAME,
                            onClick = {},
                            enabled = false,
                            testTag = "settings_about_version_row"
                        )

                        SettingsCardDivider()

                        SettingsActionRow(
                            title = stringResource(R.string.settings_about_build),
                            supportingText = BuildConfig.BUILD_NUMBER.toString(),
                            onClick = {},
                            enabled = false,
                            testTag = "settings_about_build_row"
                        )

                        SettingsCardDivider()

                        SettingsActionRow(
                            title = stringResource(R.string.settings_about_commit),
                            supportingText = BuildConfig.GIT_COMMIT,
                            onClick = {},
                            enabled = false,
                            testTag = "settings_about_commit_row"
                        )
                    }
                }
            }
        }
    }

    // Theme Dialog (scale 0.9 -> 1 + fade in 250 ms, fade out 150 ms)
    SingleChoiceRadioDialog(
        visible = showThemeDialog,
        title = stringResource(R.string.settings_theme),
        options = listOf(
            ThemeMode.SYSTEM to stringResource(R.string.settings_theme_system),
            ThemeMode.LIGHT to stringResource(R.string.settings_theme_light),
            ThemeMode.DARK to stringResource(R.string.settings_theme_dark)
        ),
        selected = settings.themeMode,
        onSelect = { mode ->
            viewModel.setThemeMode(mode)
            showThemeDialog = false
        },
        onDismiss = { showThemeDialog = false },
        tagPrefix = "theme_option"
    )

    // Interval Dialog
    SingleChoiceRadioDialog(
        visible = showIntervalDialog,
        title = stringResource(R.string.settings_timelapse_interval),
        options = AppSettings.SUPPORTED_INTERVALS_SECONDS.map { sec ->
            sec to stringResource(R.string.settings_timelapse_interval_value, sec)
        },
        selected = settings.timeLapseIntervalSeconds,
        onSelect = { seconds ->
            viewModel.setTimeLapseIntervalSeconds(seconds)
            showIntervalDialog = false
        },
        onDismiss = { showIntervalDialog = false },
        tagPrefix = "interval_option"
    )

    // Quality Dialog
    SingleChoiceRadioDialog(
        visible = showQualityDialog,
        title = stringResource(R.string.settings_timelapse_quality),
        options = listOf(
            TimeLapseQuality.QUALITY_720P to stringResource(R.string.settings_timelapse_quality_720p),
            TimeLapseQuality.QUALITY_1080P to stringResource(R.string.settings_timelapse_quality_1080p)
        ),
        selected = settings.timeLapseQuality,
        onSelect = { quality ->
            viewModel.setTimeLapseQuality(quality)
            showQualityDialog = false
        },
        onDismiss = { showQualityDialog = false },
        tagPrefix = "quality_option"
    )

    // Clear Recent Confirmation Dialog
    AnimatedMaterialDialog(
        visible = showClearRecentDialog,
        onDismissRequest = { showClearRecentDialog = false }
    ) {
        val confirmInteraction = remember { MutableInteractionSource() }
        val cancelInteraction = remember { MutableInteractionSource() }

        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 0.dp,
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .widthIn(min = 280.dp, max = 560.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = stringResource(R.string.dialog_clear_recent_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.dialog_clear_recent_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = { showClearRecentDialog = false },
                        interactionSource = cancelInteraction,
                        modifier = Modifier.pressScale(cancelInteraction)
                    ) {
                        Text(text = stringResource(R.string.dialog_cancel))
                    }
                    TextButton(
                        onClick = {
                            viewModel.clearRecentImages()
                            showClearRecentDialog = false
                        },
                        interactionSource = confirmInteraction,
                        modifier = Modifier
                            .pressScale(confirmInteraction)
                            .testTag("confirm_clear_recent_button")
                    ) {
                        Text(
                            text = stringResource(R.string.dialog_clear),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }

    // Delete All Projects Confirmation Dialog
    AnimatedMaterialDialog(
        visible = showDeleteAllProjectsDialog,
        onDismissRequest = { showDeleteAllProjectsDialog = false }
    ) {
        val confirmInteraction = remember { MutableInteractionSource() }
        val cancelInteraction = remember { MutableInteractionSource() }

        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 0.dp,
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .widthIn(min = 280.dp, max = 560.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = stringResource(R.string.dialog_delete_all_projects_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.dialog_delete_all_projects_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = { showDeleteAllProjectsDialog = false },
                        interactionSource = cancelInteraction,
                        modifier = Modifier.pressScale(cancelInteraction)
                    ) {
                        Text(text = stringResource(R.string.dialog_cancel))
                    }
                    TextButton(
                        onClick = {
                            viewModel.deleteAllProjects()
                            showDeleteAllProjectsDialog = false
                        },
                        interactionSource = confirmInteraction,
                        modifier = Modifier
                            .pressScale(confirmInteraction)
                            .testTag("confirm_delete_all_projects_button")
                    ) {
                        Text(
                            text = stringResource(R.string.dialog_delete),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsGroupCard(
    header: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = header,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp)
        )
        Surface(
            shape = SettingsCardShape,
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            modifier = Modifier
                .fillMaxWidth()
                .clip(SettingsCardShape)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                content = content
            )
        }
    }
}

@Composable
private fun SettingsCardDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    )
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    supportingText: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
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
            .padding(horizontal = 16.dp, vertical = 12.dp)
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
                text = title,
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
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun SettingsActionRow(
    title: String,
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
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag(testTag),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = supportingText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun <T> SingleChoiceRadioDialog(
    visible: Boolean,
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
    tagPrefix: String
) {
    AnimatedMaterialDialog(
        visible = visible,
        onDismissRequest = onDismiss
    ) {
        val cancelInteraction = remember { MutableInteractionSource() }

        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 0.dp,
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .widthIn(min = 280.dp, max = 560.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(16.dp))
                Column(modifier = Modifier.fillMaxWidth()) {
                    options.forEach { (value, label) ->
                        val rowInteraction = remember { MutableInteractionSource() }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .pressScale(rowInteraction)
                                .clickable(
                                    interactionSource = rowInteraction,
                                    indication = LocalIndication.current
                                ) { onSelect(value) }
                                .padding(vertical = 8.dp, horizontal = 4.dp)
                                .testTag("${tagPrefix}_${value.toString().lowercase()}"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selected == value,
                                onClick = { onSelect(value) }
                            )
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(start = 12.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onDismiss,
                        interactionSource = cancelInteraction,
                        modifier = Modifier.pressScale(cancelInteraction)
                    ) {
                        Text(text = stringResource(R.string.dialog_cancel))
                    }
                }
            }
        }
    }
}
