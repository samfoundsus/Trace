# Changelog

All notable changes to **Trace** are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Root `version.properties` as the single source of truth for `VERSION_MAJOR`, `VERSION_MINOR`, `VERSION_PATCH`, and `VERSION_CODE_OFFSET`.
- Automated GitHub Actions release workflow supporting manual `release_type` (`none`, `patch`, `minor`, `major`) version bumps, categorized release notes (`feat`, `fix`, `perf`, `ui`, `chore`), and `.apk.sha256` checksum files.
- Settings **About** card rows showing **Version**, **Build**, and **Commit** (`BuildConfig.GIT_COMMIT`).

## [1.1.0] - 2026-11-14

### Added
- **Saved Projects & Auto-Save**: Room-backed project persistence (`Projects` section on Home) with dedicated app-private image copies, project naming, renaming, duplication (`(copy)`), deletion, and automatic transform/filter saving.
- **Settings Screen**: Pixel Settings-style grouped cards (**Appearance**, **Trace**, **Time-lapse**, **Data**, **About**) backed by Jetpack DataStore Preferences (`ThemeMode`, `dynamicColor`, `keepScreenOn`, `defaultOpacity`, `hideControlsOnStart`, and data management actions).
- **Time-Lapse Recorder**: Hardware-encoded H.264 MP4 recording (`MediaCodec` + `MediaMuxer` at `30 fps`, `720p` or `1080p`) capturing one frame every `1 s`, `2 s`, `5 s`, or `10 s` with optional overlay compositing, pre-recording setup sheet with countdown (`Off`, `3 s`, `5 s`, `10 s`), foreground service continuation (`TimeLapseRecordingService`), and `MediaStore` export to `Movies/Trace`.
- **Pinned Mode (ARCore)**: Optional surface-anchored AR tracing (`Pin to surface`) using an ARCore `Session`, OpenGL ES 2.0 camera background and plane dot grid, horizontal plane tap-to-anchor, and physical paper width scaling (`10`–`50 cm`, default `21 cm` A4).
- **Backup & Restore Support**: Configured Android Auto Backup rules (`backup_rules.xml` and `data_extraction_rules.xml`) preserving user projects, project images, and DataStore preferences while excluding temporary recent cache files.
- **Baseline Profile & StrictMode**: Added `baseline-prof.txt` with `androidx.profileinstaller` for ahead-of-time startup optimization and debug `StrictMode` checks.

### Changed
- **Launcher Icon Redesign**: Replaced the launcher icon with a clean, flat vector adaptive icon (`#DBE7FD` full-bleed background, `#1B66E8` 45-degree pencil and line, `#88B0FC` pencil tip, plus Android 13+ monochrome layer).
- **Material Motion Pass**: Added container transform transitions from Home cards/FAB to Trace, shared X-axis transitions to Settings, fade-through from Camera Permission to Trace, predictive back gestures, spring sheet/toolbar transitions, and staggered entrance animations that respect system animator duration scale.
- **Performance Pass**: Moved trace gesture and opacity reads strictly into `graphicsLayer` lambdas with `200 ms` throttled ViewModel updates, added a `16 MB` `LruCache` and `400 px` disk thumbnails for the Home grid, reused scratch buffers and `640 px` live previews in `OutlineProcessor`, and enabled Compose strong skipping and R8 full mode.

### Fixed
- **Always-Visible Time-Lapse Stop Control**: Replaced the recording indicator with a Google Recorder-style tonal pill featuring a pulsing red dot, `titleMedium` elapsed timer, and a `48 dp` round stop button that remains visible even when toolbars are hidden or Lock mode is active.
- **CameraX / ARCore Handover Synchronization**: Awaited CameraX `unbindAll()` completion on the main executor before resuming the ARCore session, added `CameraNotAvailableException` retry backoff (`3` attempts, `300 ms` delay), and closed the ARCore session cleanly before rebinding CameraX.

## [1.0.0] - 2026-10-01

### Added
- **Trace Screen & Live Camera Overlay**: Full-screen CameraX preview with a semi-transparent gallery image overlay (`10%`–`100%` opacity).
- **Multi-Touch Gestures & Lock Mode**: Smooth 1-finger pan, 2-finger pinch-to-zoom (`0.15x`–`8.0x`), 2-finger twist rotation, horizontal/vertical mirroring, 2-finger tap to toggle controls, and a **Lock** mode with a persistent top pill to freeze gestures while drawing.
- **Line-Art Outline & Alignment Grid**: On-device Sobel edge detector converting reference photos into line art on a transparent background with adjustable strength, plus a customizable alignment grid (`24`–`120 dp`).
- **Home Screen & Zero-Permission Photo Picker**: Recent images grid using the Android Photo Picker (`PickVisualMedia`) with Google/Pixel Material 3 dark/light and dynamic colour theming.
