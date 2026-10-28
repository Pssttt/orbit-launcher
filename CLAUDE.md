# CLAUDE.md

Guidance for working in this repository.

## What this is

**Orbit** — a custom, native Android TV launcher built for low-end hardware (a 2018 Sony Bravia: Android 9 / API 28, 2 GB RAM, armeabi-v7a). Performance on a weak GPU is the overriding constraint.

The migration branch adds `tvhome/`, a separately buildable Java CleanYourTV module
with application ID `com.gathin.tvhome`. The original Orbit app and its package identity
remain in `app/`. See `tvhome/README.md` for installation and `tvhome/UPSTREAM.md` for
provenance. Personalized themes are in `themes/`; upstream theme tools are in `tools/`.
Keep signing keys and TV maintenance backups outside Git. Test the new module with
`./gradlew :tvhome:testReleaseUnitTest :tvhome:lintRelease :tvhome:assembleRelease`.
TV Home's app area uses `OrbitRows` (pure grouping rules) and `OrbitRowsView`
(recycled horizontal shelves), backed by the existing `AppAdapter`. The header and
wallpaper remain in `MainActivity`; the original grid is retained for ordering only.
Preserve stable app-key lookup in callbacks, focus memory, app-library/menu behavior,
background animation cancellation and memory-pressure release when editing the shelves.

## Key facts

- **Language/UI:** Kotlin + AndroidX Views + RecyclerView. **No Jetpack Compose** — deliberately, for performance on old TVs. Don't introduce Compose.
- **Versions:** `minSdk 21`, `targetSdk 34`, AGP 8.5.2, Gradle 8.7, JDK 17.
- **Package vs id:** Kotlin package / namespace is `com.psst.aurora` (historical); installed `applicationId` is `com.psst.orbit`. The home component is `com.psst.orbit/com.psst.aurora.LauncherActivity`. Don't "fix" this mismatch — it's intentional.
- **No real blur:** `RenderEffect` is API 31+ and RenderScript janks on this hardware. Glassmorphism/blur was tried and removed. Use GPU-cheap transforms (scale/rotation/alpha) and translucent fills only — never runtime blur or heavy translucent overdraw.

## Architecture (all in `app/src/main/java/com/psst/aurora/`)

- `LauncherActivity` — the HOME activity; builds category rows, reactive glow, wallpaper, clock; reloads `ConfigStore` on every `onResume` so Settings changes apply.
- `AppRepository` — discovers launchable apps (leanback first, then standard launcher).
- `ConfigStore` — JSON-persisted user config in app filesDir (hidden apps, custom icons, categories + order, recents, accent, background, card/font scale, wallpaper).
- `AppCardAdapter` / `CategoryAdapter` — horizontal cards / vertical category rows.
- `CoverFlow` — rotationY tilt by distance from row centre (focused card stays flat).
- `RowLayoutManager` — blocks LEFT/RIGHT focus from escaping a row to other rows.
- `AccentColors`, `Banners`, `DefaultCategories` — per-app accent/banner/category maps.
- `SearchActivity`, `SettingsActivity`, `ManageAppsActivity`, `CategoriesActivity`.
- Bundled banners + wallpaper live in `res/drawable-nodpi/`.

## Build & deploy

```bash
./gradlew :app:assembleDebug   # app/build/outputs/apk/debug/
./gradlew :app:assembleRelease # needs signing env vars (below)
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell cmd package set-home-activity com.psst.orbit/com.psst.aurora.LauncherActivity
```

Release signing reads env vars: `AURORA_KEYSTORE` (path), `AURORA_KS_PASS`, `AURORA_KEY_ALIAS`, `AURORA_KEY_PASS`. Never commit the keystore or passwords (`.gitignore` covers `*.jks`/`*.keystore`). Lint aborts release builds (`lintVitalRelease`) and `lintDebug` aborts on errors in CI — keep code lint-clean.

### Building on ARM64 Linux

AGP ships an x86-64 `aapt2` that can't run on ARM64. To build there: install `qemu-user-static`, create an amd64 sysroot (extract `libc6`/`libstdc++6`/`libgcc-s1`/`zlib1g` amd64 debs + `lib64`/`lib` usrmerge symlinks), and build with `QEMU_LD_PREFIX=<sysroot>`. x86-64 hosts (incl. GitHub Actions) need none of this.

## CI/CD

- `.github/workflows/android.yml` — pushes to `main`/`migration/cleanyourtv`, PRs to `main`: original Orbit lint/debug APK plus TV Home release tests/lint/APK; both APKs uploaded as artifacts.
- `.github/workflows/release.yml` — push a `v*` tag: builds a signed release APK from secrets and attaches it to a GitHub Release.

## Conventions

- Conventional commits with a scope, e.g. `feat(launcher):`, `fix(settings):`, `ci:`, `docs:`, `chore:`. Concise single-line messages. **No `Co-Authored-By` line.** Group related changes into separate commits by concern.
