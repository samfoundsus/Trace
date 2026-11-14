# Trace

**Trace** is a minimal, first-party Pixel-style Android app for tracing gallery images onto paper over a live camera feed. Built with Kotlin, Jetpack Compose, Material 3 (Material You dynamic colour on Android 12+ and a fixed Google palette on Android 10–11), CameraX, Room, DataStore, MediaCodec/MediaMuxer, and optional ARCore surface pinning.

## Features

- **Live Camera Overlay**: Pick any image from the zero-permission Android Photo Picker and view it semi-transparently over the live camera feed (`10%`–`100%` opacity).
- **Smooth Multi-Touch & Lock Mode**: 1-finger pan, 2-finger pinch zoom (`0.15x`–`8x`), 2-finger twist rotation, horizontal/vertical flip, quick 2-finger tap (or top-right chevron) to hide toolbars, and a **Lock** mode that freezes touch gestures while drawing.
- **Line-Art Outline & Alignment Grid**: On-device Sobel edge detection converts photos into high-contrast line art on a transparent background with adjustable edge strength (`5%`–`95%`), plus an optional reference grid (`24`–`120 dp`).
- **Saved Projects & Auto-Save**: Save projects locally in Room with a dedicated copy of the source image so every transform, opacity, flip, outline, and grid setting is restored and auto-saved.
- **Low-RAM Time-Lapse Recorder**: Captures one frame every `1 s`, `2 s`, `5 s`, or `10 s` and streams directly into a hardware H.264 MP4 (`MediaCodec` + `MediaMuxer`) at `30 fps` (`720p` or `1080p`), saving to `Movies/Trace` via `MediaStore` and continuing in a camera foreground service if the screen locks.
- **Pinned Mode (ARCore, Optional)**: On ARCore-supported devices, **Pin to surface** detects horizontal paper surfaces and anchors the image as a 3D textured quad on your paper with configurable physical **Paper width** (`10`–`50 cm`, default `21 cm` for A4).

## Known Limits of Pinned Mode

- **Device Support**: Pinned mode requires an ARCore-certified device with Google Play Services for AR installed. On unsupported devices, the **Pin to surface** option is hidden and the app uses standard CameraX mode.
- **Lighting & Surface Texture**: ARCore relies on visual feature points to track horizontal surfaces. Purely blank white paper on an unpatterned desk or dim lighting can reduce tracking stability; keeping a textured desk edge, ruler, or pencil case in view improves plane detection and anchor stability.
- **Single Active Camera Pipeline**: Pinned mode owns the camera exclusively through its ARCore `Session` and `GLSurfaceView`. Time-lapse recording is disabled while pinned (`Not available in pinned mode`).
- **Hand Occlusion**: The anchored image is rendered as a semi-transparent overlay plane on the detected paper surface and does not perform depth occlusion over your hand or pencil.

## Building

No signing keys, keystore files, or GitHub secrets are needed to build or release the app—release builds are minified and shrunk with R8 and signed automatically with the default debug signing config.
Because the auto-generated debug signing key differs per build environment, updates cannot install directly over an older build; uninstall the previous build first and use the in-app backup to keep your projects.

```bash
gradle assembleDebug
gradle assembleRelease
gradle testDebugUnitTest
```

## Versioning and releases

### Single Source of Truth (`version.properties`)

All version numbers live in `version.properties` at the repository root:

```properties
VERSION_MAJOR=1
VERSION_MINOR=1
VERSION_PATCH=0
VERSION_CODE_OFFSET=100
```

`app/build.gradle.kts` reads `version.properties` at build time:

- **`versionName`**: Set to `MAJOR.MINOR.PATCH` (e.g. `1.1.0`) for local builds, and `MAJOR.MINOR.PATCH (build <run_number>)` (e.g. `1.1.0 (build 57)`) for CI builds.
- **`versionCode`**: Computed as `buildNumber + VERSION_CODE_OFFSET` (where `buildNumber` is passed via `-PbuildNumber` from `github.run_number` in CI, or `1` for local builds).
- **Why `VERSION_CODE_OFFSET` exists**: Android requires `versionCode` to strictly increase for every update. If the GitHub Actions workflow is ever recreated and `github.run_number` resets to `1`, increase `VERSION_CODE_OFFSET` in `version.properties` above the last published `versionCode` so future builds never collide or regress.
- **Settings → About Card**: Displays **Version** (`1.1.0`), **Build** (`buildNumber`), and **Commit** (short 7-character Git SHA injected via `-PgitCommit` in CI, or `local` on local builds).

### How to Cut a Release

1. **Automatic Release on Push to `main`**:
   - Pushing or merging a pull request to `main` runs `gradle testDebugUnitTest`, builds the R8-minified release APK (`trace-<MAJOR.MINOR.PATCH>-b<run_number>.apk`), generates a SHA-256 checksum file (`.apk.sha256`), and publishes a GitHub Release tagged `v<MAJOR.MINOR.PATCH>-b<run_number>` titled `Trace <versionName>`.
   - Release notes are automatically generated from commit messages since the previous release tag and grouped by prefix (`feat`, `fix`, `perf`, `ui`, `chore`).
   - Pull requests and pushes to any branch other than `main` run unit tests and upload a debug APK artifact without creating a release.
2. **Manual Version Bump via GitHub Actions (`workflow_dispatch`)**:
   - Open **Actions → Build and Release APK → Run workflow** on `main`.
   - Choose **`release_type`**:
     - `none` — Build and release the current version in `version.properties`.
     - `patch` — Increment `VERSION_PATCH` (e.g. `1.1.0` → `1.1.1`).
     - `minor` — Increment `VERSION_MINOR` and reset `VERSION_PATCH=0` (e.g. `1.1.0` → `1.2.0`).
     - `major` — Increment `VERSION_MAJOR` and reset `VERSION_MINOR=0, VERSION_PATCH=0` (e.g. `1.1.0` → `2.0.0`).
   - When `patch`, `minor`, or `major` is selected, the workflow updates `version.properties`, commits it back to `main` as `chore: bump version to X.Y.Z`, and immediately builds and publishes the new release.
