# Orbit

A custom, native Android TV launcher — built lightweight for older hardware (tested on a 2018 Sony Bravia, Android 9, 2 GB RAM, armeabi-v7a).

## Features

- **Rows by category** (Recent, Streaming, Media, Tools, …) with a cover-flow curved 3D tilt
- **Custom app banners** (logo + wordmark) baked in, with per-app accent colors
- **Reactive ambient glow** that follows focus and takes on each app's accent color
- **Search** — type-to-filter across all installed apps
- **App management** — show/hide apps, move between categories, set custom icons, edit/reorder/create categories
- **Appearance settings** — accent color, background (gradient or solid), card size, font size, 12/24h clock, wallpaper
- **Recents** tracking and a living (Ken Burns) wallpaper

Designed to stay smooth on weak GPUs: GPU-cheap transforms only, no runtime blur (unavailable pre–API 31), no translucent overdraw.

## Tech

- Kotlin, AndroidX Views + RecyclerView (no Compose — chosen for performance on low-end TVs)
- `minSdk 21`, `targetSdk 34`, AGP 8.5 / Gradle 8.7 / JDK 17
- Config persisted as JSON in app storage

## Build

```bash
./gradlew assembleDebug          # debug APK -> app/build/outputs/apk/debug/
./gradlew assembleRelease        # release (set AURORA_KEYSTORE/AURORA_KS_PASS/AURORA_KEY_ALIAS/AURORA_KEY_PASS env vars to sign)
```

Install and set as the home launcher:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell cmd package set-home-activity com.psst.orbit/com.psst.aurora.LauncherActivity
```

> Note: building on an ARM64 Linux host requires x86-64 emulation for AGP's bundled `aapt2` (`qemu-user-static` + an amd64 sysroot via `QEMU_LD_PREFIX`). x86-64 hosts (incl. GitHub Actions) work out of the box.
