package com.example.ui.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.domain.model.RecentImageItem
import com.example.domain.model.SavedProject
import com.example.ui.theme.AnimatedMaterialDialog
import com.example.ui.theme.AnimatedSnackbarContainer
import com.example.ui.theme.TraceMotion
import com.example.ui.theme.pressScale
import com.example.ui.theme.rememberAnimationsEnabled
import com.example.ui.theme.tapPressScale
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenRecentTrace: (String) -> Unit,
    onOpenProjectTrace: (String) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null
) {
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    val recentImages by viewModel.recentImages.collectAsStateWithLifecycle()
    val thumbnails by viewModel.thumbnails.collectAsStateWithLifecycle()
    val isImporting by viewModel.isImporting.collectAsStateWithLifecycle()
    val snackbarMessage by viewModel.snackbarMessage.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val animationsEnabled = rememberAnimationsEnabled()

    var projectToRename by remember { mutableStateOf<SavedProject?>(null) }
    var lastRenameProject by remember { mutableStateOf<SavedProject?>(null) }
    if (projectToRename != null) {
        lastRenameProject = projectToRename
    }

    var projectToDelete by remember { mutableStateOf<SavedProject?>(null) }
    var lastDeleteProject by remember { mutableStateOf<SavedProject?>(null) }
    if (projectToDelete != null) {
        lastDeleteProject = projectToDelete
    }

    // Track whether the user initiated trace from the "Choose image" FAB for container transform
    var fabSourceImageId by rememberSaveable { mutableStateOf("") }

    // Stagger items only on the first load, not on subsequent scrolls
    var hasCompletedFirstLoadStagger by rememberSaveable { mutableStateOf(false) }
    val hasAnyItems by remember {
        androidx.compose.runtime.derivedStateOf {
            projects.isNotEmpty() || recentImages.isNotEmpty()
        }
    }
    LaunchedEffect(hasAnyItems, animationsEnabled) {
        if (hasAnyItems && !hasCompletedFirstLoadStagger) {
            if (animationsEnabled) {
                delay(420L)
            }
            hasCompletedFirstLoadStagger = true
        }
    }

    LaunchedEffect(snackbarMessage) {
        val msg = snackbarMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(
            message = msg,
            duration = SnackbarDuration.Short
        )
        viewModel.consumeSnackbarMessage()
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.onUriPicked(uri) { newImageId ->
                fabSourceImageId = newImageId
                onOpenRecentTrace(newImageId)
            }
        }
    }

    val launchPicker = remember(isImporting, photoPickerLauncher) {
        {
            if (!isImporting) {
                photoPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            }
        }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val containerDuration = TraceMotion.duration(
        TraceMotion.DURATION_CONTAINER_TRANSFORM,
        animationsEnabled
    )

    val settingsBtnInteraction = remember { MutableInteractionSource() }
    val fabInteraction = remember { MutableInteractionSource() }

    val fabSharedModifier = if (
        sharedTransitionScope != null &&
        animatedVisibilityScope != null &&
        fabSourceImageId.isNotBlank()
    ) {
        with(sharedTransitionScope) {
            Modifier.sharedBounds(
                sharedContentState = rememberSharedContentState(
                    key = "trace_bounds_$fabSourceImageId"
                ),
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

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.widthIn(max = 480.dp)
            ) { data ->
                AnimatedSnackbarContainer(visible = true) {
                    Snackbar(snackbarData = data)
                }
            }
        },
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.home_title),
                        style = MaterialTheme.typography.headlineLarge,
                        modifier = Modifier.graphicsLayer {
                            // Read collapsedFraction inside graphicsLayer so scroll does not recompose the top bar
                            val collapsedFraction = scrollBehavior.state.collapsedFraction
                            val titleScale = 1f - (collapsedFraction * 0.04f)
                            scaleX = titleScale
                            scaleY = titleScale
                            transformOrigin = TransformOrigin(0f, 0.5f)
                        }
                    )
                },
                actions = {
                    IconButton(
                        onClick = onOpenSettings,
                        interactionSource = settingsBtnInteraction,
                        modifier = Modifier
                            .size(48.dp)
                            .pressScale(settingsBtnInteraction)
                            .testTag("home_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = stringResource(R.string.settings_title),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                scrollBehavior = scrollBehavior
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = launchPicker,
                interactionSource = fabInteraction,
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                icon = {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null
                    )
                },
                text = {
                    Text(
                        text = stringResource(R.string.home_choose_image),
                        style = MaterialTheme.typography.labelLarge
                    )
                },
                modifier = Modifier
                    .then(fabSharedModifier)
                    .pressScale(fabInteraction)
                    .testTag("choose_image_button")
            )
        }
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            val columnCount = when {
                maxWidth >= 840.dp -> 4
                maxWidth >= 600.dp -> 3
                else -> 2
            }

            if (!hasAnyItems) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 64.dp)
                        .testTag("home_empty_state"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.home_empty_state),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columnCount),
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 840.dp)
                        .testTag("home_grid"),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 0.dp,
                        bottom = 104.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (projects.isNotEmpty()) {
                        item(
                            key = "header_projects",
                            contentType = "section_header",
                            span = { GridItemSpan(maxLineSpan) }
                        ) {
                            Text(
                                text = stringResource(R.string.home_projects_section),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .animateItem(
                                        fadeInSpec = if (animationsEnabled) {
                                            tween(TraceMotion.DURATION_PILL, easing = TraceMotion.StandardDecelerateEasing)
                                        } else null,
                                        placementSpec = if (animationsEnabled) {
                                            spring(
                                                dampingRatio = Spring.DampingRatioNoBouncy,
                                                stiffness = Spring.StiffnessMediumLow
                                            )
                                        } else null,
                                        fadeOutSpec = if (animationsEnabled) {
                                            tween(TraceMotion.DURATION_QUICK, easing = TraceMotion.StandardAccelerateEasing)
                                        } else null
                                    )
                                    .padding(top = 24.dp, bottom = 4.dp)
                                    .testTag("projects_section_header")
                            )
                        }

                        itemsIndexed(
                            items = projects,
                            key = { _, project -> "project_${project.id}" },
                            contentType = { _, _ -> "project_card" }
                        ) { index, project ->
                            val sharedModifier = if (
                                sharedTransitionScope != null && animatedVisibilityScope != null
                            ) {
                                with(sharedTransitionScope) {
                                    Modifier.sharedBounds(
                                        sharedContentState = rememberSharedContentState(
                                            key = "trace_bounds_project_${project.id}"
                                        ),
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

                            val itemPlacementModifier = Modifier.animateItem(
                                fadeInSpec = if (animationsEnabled) {
                                    tween(
                                        durationMillis = TraceMotion.DURATION_MEDIUM,
                                        easing = TraceMotion.EmphasizedDecelerateEasing
                                    )
                                } else null,
                                placementSpec = if (animationsEnabled) {
                                    spring(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                    )
                                } else null,
                                fadeOutSpec = if (animationsEnabled) {
                                    tween(
                                        durationMillis = TraceMotion.DURATION_QUICK,
                                        easing = TraceMotion.EmphasizedAccelerateEasing
                                    )
                                } else null
                            )

                            StaggeredGridItem(
                                index = index,
                                playStagger = !hasCompletedFirstLoadStagger,
                                modifier = itemPlacementModifier.then(sharedModifier)
                            ) {
                                val onClickProject = remember(project.id, onOpenProjectTrace) {
                                    {
                                        fabSourceImageId = ""
                                        onOpenProjectTrace(project.id)
                                    }
                                }
                                val onRenameProject = remember(project) {
                                    { projectToRename = project }
                                }
                                val onDuplicateProject = remember(project.id) {
                                    { viewModel.duplicateProject(project.id) }
                                }
                                val onDeleteProject = remember(project) {
                                    { projectToDelete = project }
                                }

                                ProjectThumbnailCard(
                                    project = project,
                                    thumbnail = thumbnails["project_${project.id}"],
                                    onClick = onClickProject,
                                    onRename = onRenameProject,
                                    onDuplicate = onDuplicateProject,
                                    onDelete = onDeleteProject
                                )
                            }
                        }
                    }

                    if (recentImages.isNotEmpty()) {
                        item(
                            key = "header_recent",
                            contentType = "section_header",
                            span = { GridItemSpan(maxLineSpan) }
                        ) {
                            Text(
                                text = stringResource(R.string.home_recent_section),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .animateItem(
                                        fadeInSpec = if (animationsEnabled) {
                                            tween(TraceMotion.DURATION_PILL, easing = TraceMotion.StandardDecelerateEasing)
                                        } else null,
                                        placementSpec = if (animationsEnabled) {
                                            spring(
                                                dampingRatio = Spring.DampingRatioNoBouncy,
                                                stiffness = Spring.StiffnessMediumLow
                                            )
                                        } else null,
                                        fadeOutSpec = if (animationsEnabled) {
                                            tween(TraceMotion.DURATION_QUICK, easing = TraceMotion.StandardAccelerateEasing)
                                        } else null
                                    )
                                    .padding(top = 24.dp, bottom = 4.dp)
                                    .testTag("recent_section_header")
                            )
                        }

                        itemsIndexed(
                            items = recentImages,
                            key = { _, item -> "recent_${item.id}" },
                            contentType = { _, _ -> "recent_card" }
                        ) { index, item ->
                            val sharedModifier = if (
                                sharedTransitionScope != null &&
                                animatedVisibilityScope != null &&
                                fabSourceImageId != item.id
                            ) {
                                with(sharedTransitionScope) {
                                    Modifier.sharedBounds(
                                        sharedContentState = rememberSharedContentState(
                                            key = "trace_bounds_${item.id}"
                                        ),
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

                            val itemPlacementModifier = Modifier.animateItem(
                                fadeInSpec = if (animationsEnabled) {
                                    tween(
                                        durationMillis = TraceMotion.DURATION_MEDIUM,
                                        easing = TraceMotion.EmphasizedDecelerateEasing
                                    )
                                } else null,
                                placementSpec = if (animationsEnabled) {
                                    spring(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                    )
                                } else null,
                                fadeOutSpec = if (animationsEnabled) {
                                    tween(
                                        durationMillis = TraceMotion.DURATION_QUICK,
                                        easing = TraceMotion.EmphasizedAccelerateEasing
                                    )
                                } else null
                            )

                            StaggeredGridItem(
                                index = projects.size + index,
                                playStagger = !hasCompletedFirstLoadStagger,
                                modifier = itemPlacementModifier.then(sharedModifier)
                            ) {
                                val onClickRecent = remember(item.id, onOpenRecentTrace) {
                                    {
                                        fabSourceImageId = ""
                                        viewModel.onRecentImageSelected(item.id, onOpenRecentTrace)
                                    }
                                }
                                RecentThumbnailCard(
                                    item = item,
                                    thumbnail = thumbnails["recent_${item.id}"],
                                    onClick = onClickRecent
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Rename Project Dialog (scale 0.9 -> 1 + fade in 250 ms, fade out 150 ms)
    AnimatedMaterialDialog(
        visible = projectToRename != null,
        onDismissRequest = { projectToRename = null }
    ) {
        val project = projectToRename ?: lastRenameProject
        if (project != null) {
            var nameInput by rememberSaveable(project.id) { mutableStateOf(project.name) }
            val saveInteraction = remember { MutableInteractionSource() }
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
                        text = stringResource(R.string.dialog_rename_project_title),
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
                            .testTag("rename_project_input")
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = { projectToRename = null },
                            interactionSource = cancelInteraction,
                            modifier = Modifier.pressScale(cancelInteraction)
                        ) {
                            Text(text = stringResource(R.string.dialog_cancel))
                        }
                        TextButton(
                            onClick = {
                                val trimmed = nameInput.trim()
                                if (trimmed.isNotBlank()) {
                                    viewModel.renameProject(project.id, trimmed)
                                }
                                projectToRename = null
                            },
                            interactionSource = saveInteraction,
                            modifier = Modifier
                                .pressScale(saveInteraction)
                                .testTag("rename_project_confirm_button")
                        ) {
                            Text(text = stringResource(R.string.dialog_save))
                        }
                    }
                }
            }
        }
    }

    // Delete Project Confirmation Dialog (scale 0.9 -> 1 + fade in 250 ms, fade out 150 ms)
    AnimatedMaterialDialog(
        visible = projectToDelete != null,
        onDismissRequest = { projectToDelete = null }
    ) {
        val project = projectToDelete ?: lastDeleteProject
        if (project != null) {
            val deleteInteraction = remember { MutableInteractionSource() }
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
                        text = stringResource(R.string.dialog_delete_project_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.dialog_delete_project_message, project.name),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = { projectToDelete = null },
                            interactionSource = cancelInteraction,
                            modifier = Modifier.pressScale(cancelInteraction)
                        ) {
                            Text(text = stringResource(R.string.dialog_cancel))
                        }
                        TextButton(
                            onClick = {
                                viewModel.deleteProject(project.id)
                                projectToDelete = null
                            },
                            interactionSource = deleteInteraction,
                            modifier = Modifier
                                .pressScale(deleteInteraction)
                                .testTag("delete_project_confirm_button")
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
}

@Composable
private fun StaggeredGridItem(
    index: Int,
    playStagger: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val animationsEnabled = rememberAnimationsEnabled()
    val shouldStagger = animationsEnabled && playStagger
    var visible by remember { mutableStateOf(!shouldStagger) }
    LaunchedEffect(Unit) {
        visible = true
    }

    val delayMs = (index.coerceIn(0, 12) * 30)
    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = if (shouldStagger) {
            tween(
                durationMillis = TraceMotion.DURATION_MEDIUM,
                delayMillis = delayMs,
                easing = TraceMotion.EmphasizedDecelerateEasing
            )
        } else {
            snap()
        },
        label = "grid_stagger_$index"
    )

    Box(
        modifier = modifier.graphicsLayer {
            alpha = progress
            val itemScale = 0.92f + (0.08f * progress)
            scaleX = itemScale
            scaleY = itemScale
        }
    ) {
        content()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProjectThumbnailCard(
    project: SavedProject,
    thumbnail: ImageBitmap?,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    var contextMenuExpanded by remember { mutableStateOf(false) }
    var tapOffsetPx by remember { mutableStateOf(Offset.Zero) }
    var cardSize by remember { mutableStateOf(IntSize.Zero) }
    val interactionSource = remember { MutableInteractionSource() }
    val cardShape = RoundedCornerShape(20.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged { cardSize = it }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .pressScale(interactionSource)
                .testTag("project_card_${project.id}")
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(cardShape)
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val down = event.changes.firstOrNull { it.pressed }
                                if (down != null) {
                                    tapOffsetPx = down.position
                                }
                            }
                        }
                    }
                    .combinedClickable(
                        interactionSource = interactionSource,
                        indication = LocalIndication.current,
                        onClick = onClick,
                        onLongClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            contextMenuExpanded = true
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (thumbnail != null) {
                    Image(
                        bitmap = thumbnail,
                        contentDescription = stringResource(R.string.cd_project_image),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = project.name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }

        val menuOffset = with(density) {
            if (cardSize.width > 0 && cardSize.height > 0 && tapOffsetPx != Offset.Zero) {
                DpOffset(
                    x = tapOffsetPx.x.toDp(),
                    y = (tapOffsetPx.y - cardSize.height).toDp()
                )
            } else {
                DpOffset.Zero
            }
        }

        DropdownMenu(
            expanded = contextMenuExpanded,
            onDismissRequest = { contextMenuExpanded = false },
            offset = menuOffset,
            shape = RoundedCornerShape(16.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ) {
            DropdownMenuItem(
                text = {
                    Text(
                        text = stringResource(R.string.project_menu_rename),
                        style = MaterialTheme.typography.bodyLarge
                    )
                },
                onClick = {
                    contextMenuExpanded = false
                    onRename()
                },
                modifier = Modifier
                    .tapPressScale()
                    .testTag("project_menu_rename_${project.id}")
            )
            DropdownMenuItem(
                text = {
                    Text(
                        text = stringResource(R.string.project_menu_duplicate),
                        style = MaterialTheme.typography.bodyLarge
                    )
                },
                onClick = {
                    contextMenuExpanded = false
                    onDuplicate()
                },
                modifier = Modifier
                    .tapPressScale()
                    .testTag("project_menu_duplicate_${project.id}")
            )
            DropdownMenuItem(
                text = {
                    Text(
                        text = stringResource(R.string.project_menu_delete),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                },
                onClick = {
                    contextMenuExpanded = false
                    onDelete()
                },
                modifier = Modifier
                    .tapPressScale()
                    .testTag("project_menu_delete_${project.id}")
            )
        }
    }
}

@Composable
private fun RecentThumbnailCard(
    item: RecentImageItem,
    thumbnail: ImageBitmap?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val cardShape = RoundedCornerShape(20.dp)
    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = cardShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .pressScale(interactionSource)
            .testTag("recent_image_${item.id}")
    ) {
        if (thumbnail != null) {
            Image(
                bitmap = thumbnail,
                contentDescription = stringResource(R.string.cd_recent_image),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
