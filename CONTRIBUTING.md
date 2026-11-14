# Contributing to Trace

Thank you for contributing to **Trace**. Please follow these conventions to keep the history clean and releases automated.

## Branch Naming

Create branches from `main` using descriptive prefixes:

- `feature/<short-description>` — New user-facing features or capabilities (e.g., `feature/timelapse-countdown`).
- `fix/<short-description>` — Bug fixes or edge-case corrections (e.g., `fix/arcore-camera-handover`).
- `perf/<short-description>` — Performance, memory, or startup improvements.
- `ui/<short-description>` — Visual polish, iconography, or motion refinements.
- `chore/<short-description>` — Build scripts, CI workflows, dependency updates, or documentation.

## Commit Message Prefixes

All commit messages must start with one of the following prefixes so `.github/workflows/build-apk.yml` can group them automatically in GitHub Release notes:

- `feat:` — A new feature or user-visible addition.
- `fix:` — A bug fix.
- `perf:` — A performance or memory optimization.
- `ui:` — Visual styling, layout, icon, or animation changes.
- `chore:` — Build configuration, versioning, CI/CD, or documentation updates.

Example:

```text
feat: add pre-recording countdown chips to time-lapse setup sheet
fix: await CameraX unbind before resuming ARCore session
perf: reuse scratch pixel buffers in OutlineProcessor
```

## Main Branch Policy

- **`main` is always releasable**: Every push to `main` runs unit tests, builds a signed, R8-minified release APK, and publishes a GitHub Release.
- Open a Pull Request for all changes and ensure `./gradlew testDebugUnitTest` and `./gradlew assembleDebug` pass before merging into `main`.
