package com.example

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import android.os.Build
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.TraceDatabase
import com.example.data.repository.ProjectRepository
import com.example.data.repository.SettingsRepository
import com.example.domain.model.SavedProject
import com.example.domain.model.ThemeMode
import com.example.domain.model.TimeLapseQuality
import com.example.domain.usecase.ArSupportStatus
import com.example.domain.usecase.OutlineProcessor
import com.example.domain.usecase.PinnedModeLogic
import com.example.domain.usecase.TimeLapseFrameScheduler
import com.example.ui.theme.FixedGoogleDarkColorScheme
import com.example.ui.theme.FixedGoogleLightColorScheme
import com.example.ui.theme.isDynamicColorSupported
import com.example.ui.theme.resolveTraceColorScheme
import com.example.ui.trace.TraceViewModel
import com.google.ar.core.ArCoreApk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    private lateinit var context: Context
    private lateinit var database: TraceDatabase
    private lateinit var projectRepository: ProjectRepository
    private lateinit var tempDir: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        tempDir = Files.createTempDirectory("trace_test").toFile()
        database = Room.inMemoryDatabaseBuilder(context, TraceDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        projectRepository = ProjectRepository(
            projectDao = database.projectDao(),
            projectImagesDir = File(tempDir, "project_images")
        )
    }

    @After
    fun tearDown() {
        database.close()
        tempDir.deleteRecursively()
    }

    @Test
    fun `project repository saves copies image auto-saves renames duplicates and deletes`() = runTest {
        val sourceImage = File(tempDir, "gallery_source.png")
        val bmp = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        FileOutputStream(sourceImage).use { out ->
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        bmp.recycle()

        val defaultName1 = projectRepository.nextDefaultProjectName()
        assertEquals("Untitled 1", defaultName1)

        val saved = projectRepository.saveProject(
            project = SavedProject(
                id = "",
                name = defaultName1,
                imagePath = sourceImage.absolutePath,
                offsetX = 42f,
                offsetY = -18f,
                scale = 2.2f,
                rotation = 15f,
                opacity = 0.65f,
                flipHorizontal = true,
                flipVertical = false,
                isOutlineEnabled = true,
                outlineStrength = 0.7f,
                isGridEnabled = true,
                gridCellSizeDp = 64f
            ),
            sourceImagePath = sourceImage.absolutePath
        )

        // Delete original source image; copied project image in app storage must still exist
        assertTrue(sourceImage.delete())
        assertNotEquals(sourceImage.absolutePath, saved.imagePath)
        assertTrue(File(saved.imagePath).exists())

        // Verify full restoration from Room
        val loaded = projectRepository.getProjectById(saved.id)
        assertNotNull(loaded)
        assertEquals("Untitled 1", loaded!!.name)
        assertEquals(42f, loaded.offsetX, 0.01f)
        assertEquals(-18f, loaded.offsetY, 0.01f)
        assertEquals(2.2f, loaded.scale, 0.01f)
        assertEquals(15f, loaded.rotation, 0.01f)
        assertEquals(0.65f, loaded.opacity, 0.01f)
        assertTrue(loaded.flipHorizontal)
        assertFalse(loaded.flipVertical)
        assertTrue(loaded.isOutlineEnabled)
        assertEquals(0.7f, loaded.outlineStrength, 0.01f)
        assertTrue(loaded.isGridEnabled)
        assertEquals(64f, loaded.gridCellSizeDp, 0.01f)

        // Auto-save updates existing project
        projectRepository.autoSaveExistingProject(
            projectId = saved.id,
            offsetX = 100f,
            offsetY = 50f,
            scale = 3.0f,
            rotation = -45f,
            opacity = 0.8f,
            flipHorizontal = false,
            flipVertical = true,
            isOutlineEnabled = false,
            outlineStrength = 0.4f,
            isGridEnabled = false,
            gridCellSizeDp = 48f
        )
        val afterAutoSave = projectRepository.getProjectById(saved.id)!!
        assertEquals(100f, afterAutoSave.offsetX, 0.01f)
        assertEquals(3.0f, afterAutoSave.scale, 0.01f)
        assertTrue(afterAutoSave.flipVertical)

        // Rename project
        projectRepository.renameProject(saved.id, "Portrait Sketch")
        assertEquals("Portrait Sketch", projectRepository.getProjectById(saved.id)!!.name)

        // Duplicate project
        val duplicated = projectRepository.duplicateProject(saved.id)
        assertNotNull(duplicated)
        assertEquals("Portrait Sketch (copy)", duplicated!!.name)
        assertNotEquals(saved.id, duplicated.id)
        assertTrue(File(duplicated.imagePath).exists())

        val allProjects = projectRepository.projects.first()
        assertEquals(2, allProjects.size)

        // Delete single project
        projectRepository.deleteProject(saved.id)
        assertNull(projectRepository.getProjectById(saved.id))
        assertFalse(File(saved.imagePath).exists())
        assertTrue(File(duplicated.imagePath).exists())

        // Delete all projects
        projectRepository.deleteAllProjects()
        assertTrue(projectRepository.projects.first().isEmpty())
        assertFalse(File(duplicated.imagePath).exists())
    }

    @Test
    fun `settings repository persists theme dynamic color trace preferences and time-lapse settings`() = runTest {
        val dsFile = File(tempDir, "test_settings.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { dsFile }
        )
        val settingsRepo = SettingsRepository(dataStore)

        // Verify defaults (Dark theme by default, 2s interval, 720p, overlay off)
        val initial = settingsRepo.settingsFlow.first()
        assertEquals(ThemeMode.DARK, initial.themeMode)
        assertTrue(initial.dynamicColor)
        assertTrue(initial.keepScreenOn)
        assertEquals(0.55f, initial.defaultOpacity, 0.01f)
        assertFalse(initial.hideControlsOnStart)
        assertFalse(initial.hasSeenTraceHint)
        assertEquals(2, initial.timeLapseIntervalSeconds)
        assertEquals(TimeLapseQuality.QUALITY_720P, initial.timeLapseQuality)
        assertFalse(initial.timeLapseIncludeOverlay)

        // Update settings including Phase 3 time-lapse settings
        settingsRepo.setThemeMode(ThemeMode.LIGHT)
        settingsRepo.setDynamicColor(false)
        settingsRepo.setKeepScreenOn(false)
        settingsRepo.setDefaultOpacity(0.8f)
        settingsRepo.setHideControlsOnStart(true)
        settingsRepo.markTraceHintSeen()
        settingsRepo.setTimeLapseIntervalSeconds(5)
        settingsRepo.setTimeLapseQuality(TimeLapseQuality.QUALITY_1080P)
        settingsRepo.setTimeLapseIncludeOverlay(true)

        val updated = settingsRepo.settingsFlow.first()
        assertEquals(ThemeMode.LIGHT, updated.themeMode)
        assertFalse(updated.dynamicColor)
        assertFalse(updated.keepScreenOn)
        assertEquals(0.8f, updated.defaultOpacity, 0.01f)
        assertTrue(updated.hideControlsOnStart)
        assertTrue(updated.hasSeenTraceHint)
        assertEquals(5, updated.timeLapseIntervalSeconds)
        assertEquals(TimeLapseQuality.QUALITY_1080P, updated.timeLapseQuality)
        assertTrue(updated.timeLapseIncludeOverlay)

        // Unsupported interval falls back safely to 2s
        settingsRepo.setTimeLapseIntervalSeconds(99)
        assertEquals(2, settingsRepo.settingsFlow.first().timeLapseIntervalSeconds)
    }

    @Test
    @Config(sdk = [29])
    fun `api 29 android 10 uses fixed google color scheme and adaptive icon without monochrome`() {
        assertEquals(29, Build.VERSION.SDK_INT)
        assertFalse(isDynamicColorSupported())

        val darkScheme = resolveTraceColorScheme(
            context = context,
            darkTheme = true,
            dynamicColor = true
        )
        assertEquals(FixedGoogleDarkColorScheme.primary, darkScheme.primary)
        assertEquals(FixedGoogleDarkColorScheme.surface, darkScheme.surface)
        assertEquals(FixedGoogleDarkColorScheme.surfaceContainer, darkScheme.surfaceContainer)

        val lightScheme = resolveTraceColorScheme(
            context = context,
            darkTheme = false,
            dynamicColor = true
        )
        assertEquals(FixedGoogleLightColorScheme.primary, lightScheme.primary)
        assertEquals(FixedGoogleLightColorScheme.surface, lightScheme.surface)
        assertEquals(FixedGoogleLightColorScheme.surfaceContainer, lightScheme.surfaceContainer)

        val icon = context.getDrawable(R.mipmap.ic_launcher)
        assertTrue(icon is AdaptiveIconDrawable)
    }

    @Test
    @Config(sdk = [34])
    fun `api 34 android 14 supports dynamic color and falls back to fixed google scheme when disabled`() {
        assertEquals(34, Build.VERSION.SDK_INT)
        assertTrue(isDynamicColorSupported())

        val dynamicDark = resolveTraceColorScheme(
            context = context,
            darkTheme = true,
            dynamicColor = true
        )
        assertNotNull(dynamicDark)

        val fixedDarkWhenDisabled = resolveTraceColorScheme(
            context = context,
            darkTheme = true,
            dynamicColor = false
        )
        assertEquals(FixedGoogleDarkColorScheme.primary, fixedDarkWhenDisabled.primary)
        assertEquals(FixedGoogleDarkColorScheme.secondaryContainer, fixedDarkWhenDisabled.secondaryContainer)

        val icon = context.getDrawable(R.mipmap.ic_launcher) as AdaptiveIconDrawable
        assertNotNull(icon.monochrome)
    }

    @Test
    fun `outline processor detects edges onto transparent background`() = runTest {
        val processor = OutlineProcessor()
        val bmp = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        for (y in 0 until 32) {
            for (x in 0 until 32) {
                bmp.setPixel(x, y, if (x < 16) Color.WHITE else Color.BLACK)
            }
        }

        val outline = processor.getOutlineBitmap("test_edge", bmp, strength = 0.6f)
        assertNotNull(outline)
        assertEquals(32, outline.width)
        assertEquals(32, outline.height)

        val uniformPixelAlpha = (outline.getPixel(4, 16) ushr 24) and 0xFF
        assertEquals(0, uniformPixelAlpha)

        val edgePixelAlpha = (outline.getPixel(15, 16) ushr 24) and 0xFF
        assertTrue(edgePixelAlpha > 0)
    }

    @Test
    fun `trace viewModel persists transform opacity and lock state in SavedStateHandle`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val handle = SavedStateHandle()
        val vm = TraceViewModel(app, handle)

        vm.onTransformGesture(panX = 25f, panY = -15f, zoomChange = 1.5f, rotationChange = 30f)
        vm.setOpacity(0.75f)
        vm.setLocked(true)

        vm.onTransformGesture(panX = 100f, panY = 100f, zoomChange = 2f, rotationChange = 45f)

        val recreatedVm = TraceViewModel(app, handle)
        val state = recreatedVm.uiState.value

        assertEquals(25f, state.offsetX, 0.01f)
        assertEquals(-15f, state.offsetY, 0.01f)
        assertEquals(1.5f, state.scale, 0.01f)
        assertEquals(30f, state.rotation, 0.01f)
        assertEquals(0.75f, state.opacity, 0.01f)
        assertTrue(state.isLocked)
    }

    @Test
    fun `timeLapseFrameScheduler schedules frames at configured interval and computes 30fps timestamps`() {
        val scheduler = TimeLapseFrameScheduler(intervalSeconds = 2, outputFps = 30)
        assertEquals(2000L, scheduler.intervalMs)

        // First frame at t = 0ms is immediately due
        assertTrue(scheduler.shouldCaptureFrame(0L))
        val pts0 = scheduler.onFrameCaptured(0L)
        assertEquals(0L, pts0)
        assertEquals(1, scheduler.capturedFrameCount)

        // Not due before 2000ms
        assertFalse(scheduler.shouldCaptureFrame(500L))
        assertFalse(scheduler.shouldCaptureFrame(1999L))

        // Due at 2000ms -> 2nd frame at 33_333 us (1/30s)
        assertTrue(scheduler.shouldCaptureFrame(2000L))
        val pts1 = scheduler.onFrameCaptured(2000L)
        assertEquals(33_333L, pts1)
        assertEquals(2, scheduler.capturedFrameCount)

        // Due at 4050ms -> 3rd frame at 66_666 us (2/30s), anchored without drift to 4000ms
        assertTrue(scheduler.shouldCaptureFrame(4050L))
        val pts2 = scheduler.onFrameCaptured(4050L)
        assertEquals(66_666L, pts2)
        assertFalse(scheduler.shouldCaptureFrame(5999L))
        assertTrue(scheduler.shouldCaptureFrame(6000L))
    }

    @Test
    fun `timeLapseFrameScheduler interval duration math and output dimensions for 720p and 1080p`() {
        // 1 hour (3600s) at 2s interval = 1800 frames = 60.0s (1 minute) at 30fps
        val durationSec = TimeLapseFrameScheduler.calculateOutputVideoDurationSeconds(
            recordingDurationSeconds = 3600,
            intervalSeconds = 2,
            outputFps = 30
        )
        assertEquals(60.0, durationSec, 0.001)

        // Elapsed duration formatting
        assertEquals("00:42", TimeLapseFrameScheduler.formatElapsedDuration(42L))
        assertEquals("05:09", TimeLapseFrameScheduler.formatElapsedDuration(309L))
        assertEquals("01:02:03", TimeLapseFrameScheduler.formatElapsedDuration(3723L))

        // Output dimensions: portrait 720p and 1080p
        val portrait720 = TimeLapseFrameScheduler.computeOutputDimensions(
            sourceWidth = 1080,
            sourceHeight = 1920,
            quality = TimeLapseQuality.QUALITY_720P
        )
        assertEquals(720 to 1280, portrait720)

        val portrait1080 = TimeLapseFrameScheduler.computeOutputDimensions(
            sourceWidth = 1080,
            sourceHeight = 1920,
            quality = TimeLapseQuality.QUALITY_1080P
        )
        assertEquals(1080 to 1920, portrait1080)

        // Output dimensions: landscape 720p and 1080p
        val landscape720 = TimeLapseFrameScheduler.computeOutputDimensions(
            sourceWidth = 1920,
            sourceHeight = 1080,
            quality = TimeLapseQuality.QUALITY_720P
        )
        assertEquals(1280 to 720, landscape720)

        val landscape1080 = TimeLapseFrameScheduler.computeOutputDimensions(
            sourceWidth = 3840,
            sourceHeight = 2160,
            quality = TimeLapseQuality.QUALITY_1080P
        )
        assertEquals(1920 to 1080, landscape1080)

        // 16-pixel alignment check for arbitrary camera frame sizes
        val (alignedW, alignedH) = TimeLapseFrameScheduler.computeOutputDimensions(
            sourceWidth = 1003,
            sourceHeight = 1507,
            quality = TimeLapseQuality.QUALITY_720P
        )
        assertEquals(0, alignedW % 16)
        assertEquals(0, alignedH % 16)
    }

    @Test
    fun `pinnedModeLogic maps ARCore availability and computes physical paper width scaling`() {
        // Availability mapping
        assertEquals(
            ArSupportStatus.SUPPORTED_INSTALLED,
            PinnedModeLogic.mapAvailability(ArCoreApk.Availability.SUPPORTED_INSTALLED)
        )
        assertEquals(
            ArSupportStatus.SUPPORTED_NEEDS_INSTALL,
            PinnedModeLogic.mapAvailability(ArCoreApk.Availability.SUPPORTED_NOT_INSTALLED)
        )
        assertEquals(
            ArSupportStatus.SUPPORTED_NEEDS_INSTALL,
            PinnedModeLogic.mapAvailability(ArCoreApk.Availability.SUPPORTED_APK_TOO_OLD)
        )
        assertEquals(
            ArSupportStatus.UNSUPPORTED,
            PinnedModeLogic.mapAvailability(ArCoreApk.Availability.UNSUPPORTED_DEVICE_NOT_CAPABLE)
        )
        assertEquals(
            ArSupportStatus.UNSUPPORTED,
            PinnedModeLogic.mapAvailability(ArCoreApk.Availability.UNKNOWN_ERROR)
        )

        // Default A4 paper width is 21 cm (clamped to 10..50 cm)
        assertEquals(21f, PinnedModeLogic.DEFAULT_PAPER_WIDTH_CM, 0.01f)
        assertEquals(10f, PinnedModeLogic.sanitizePaperWidthCm(2f), 0.01f)
        assertEquals(50f, PinnedModeLogic.sanitizePaperWidthCm(95f), 0.01f)
        assertEquals(21f, PinnedModeLogic.sanitizePaperWidthCm(Float.NaN), 0.01f)

        // 21 cm paper at 1.0x scale with 1000x1500 portrait image -> 0.21m wide x 0.315m tall
        val (quadW1, quadH1) = PinnedModeLogic.computeQuadSizeMeters(
            paperWidthCm = 21f,
            imageWidthPx = 1000,
            imageHeightPx = 1500,
            scale = 1.0f
        )
        assertEquals(0.21f, quadW1, 0.001f)
        assertEquals(0.315f, quadH1, 0.001f)

        // 30 cm paper at 2.0x scale with 1000x500 landscape image -> 0.60m wide x 0.30m tall
        val (quadW2, quadH2) = PinnedModeLogic.computeQuadSizeMeters(
            paperWidthCm = 30f,
            imageWidthPx = 1000,
            imageHeightPx = 500,
            scale = 2.0f
        )
        assertEquals(0.60f, quadW2, 0.001f)
        assertEquals(0.30f, quadH2, 0.001f)
    }

    @Test
    fun `trace viewModel persists pinned mode and paper width in SavedStateHandle and falls back cleanly on AR failure`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val handle = SavedStateHandle()
        val vm = TraceViewModel(app, handle)

        assertEquals(21f, vm.uiState.value.paperWidthCm, 0.01f)
        assertFalse(vm.uiState.value.isPinnedMode)

        vm.enterPinnedMode()
        vm.setPaperWidthCm(29.7f)
        vm.onSurfaceAnchored()
        vm.onTrackingLostChanged(true)

        assertTrue(vm.uiState.value.isPinnedMode)
        assertEquals(29.7f, vm.uiState.value.paperWidthCm, 0.01f)
        assertTrue(vm.uiState.value.isSurfaceAnchored)
        assertTrue(vm.uiState.value.isTrackingLost)

        // Recreate ViewModel from SavedStateHandle and verify state restoration
        val restoredVm = TraceViewModel(app, handle)
        assertTrue(restoredVm.uiState.value.isPinnedMode)
        assertEquals(29.7f, restoredVm.uiState.value.paperWidthCm, 0.01f)

        // Re-pin clears anchor and increments rePinTriggerCount
        restoredVm.rePinSurface()
        assertFalse(restoredVm.uiState.value.isSurfaceAnchored)
        assertFalse(restoredVm.uiState.value.isTrackingLost)
        assertEquals(1, restoredVm.uiState.value.rePinTriggerCount)

        // Simulate ARCore failure -> falls back to normal mode and sets snackbar message
        restoredVm.onArSessionFailure("Couldn't start pinned mode")
        assertFalse(restoredVm.uiState.value.isPinnedMode)
        assertEquals("Couldn't start pinned mode", restoredVm.uiState.value.snackbarMessage)
        restoredVm.consumeSnackbarMessage()
        assertNull(restoredVm.uiState.value.snackbarMessage)
    }

    @Test
    fun `buildConfig version fields match version properties single source of truth`() {
        assertEquals("1.1.0", BuildConfig.BASE_VERSION_NAME)
        assertTrue(BuildConfig.VERSION_NAME.startsWith("1.1.0"))
        assertTrue(BuildConfig.BUILD_NUMBER >= 1)
        assertEquals(BuildConfig.BUILD_NUMBER + 100, BuildConfig.VERSION_CODE)
        assertTrue(BuildConfig.GIT_COMMIT.isNotBlank())
    }
}
