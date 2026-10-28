# TV Home migration

A separately buildable CleanYourTV launcher, customized for the Sony BRAVIA ATV3
platform running Android 9 with 2 GB RAM. The original Kotlin Orbit implementation
remains in `app/`. TV Home retains application ID `com.gathin.tvhome`, so it can
update the existing TV installation without replacing Orbit or its private data.

The greeting, clock, date, wallpaper and top controls retain CleanYourTV's design.
The app area uses Orbit-style horizontal shelves: Favorites, Recent, then categories.
Cards are 240×135 dp, with a colored focus ring and brief, interruptible scale/lift
feedback. Reduced motion uses a smaller scale, no lift and immediate scrolling.
The shelves leave a 64 dp gap below the header. Apps without banners use a horizontal
icon-and-name card, including SportzX.
Animations stop in the background and nothing animates continuously on idle Home.
Recent contains up to six apps opened from these shelves; it does not read system usage.

## Build

Use JDK 17, Android SDK platform 34 and build-tools 34.0.0. Set `ANDROID_HOME` or
the root `local.properties` to your SDK, as for the original Orbit app.

```sh
./gradlew :tvhome:testReleaseUnitTest :tvhome:lintRelease :tvhome:assembleRelease
```

APK: `tvhome/build/outputs/apk/release/tvhome-release.apk`.
The optimized release is signed with the local Android debug key, matching upstream.
Keep the same key when updating an existing installation; a different key causes a
signature mismatch. Keep keys outside Git. `ANDROID_USER_HOME` can point to the
existing Android user directory containing that key.
CI artifacts use the runner's temporary debug key, so they may not update a locally
signed installation. Build locally with the existing key for updates to your TV.

Build original Orbit separately with `./gradlew :app:assembleDebug`. Its release
signing and tag-based publication remain unchanged.

## Install and apply a theme

Enable debugging on the TV and accept its authorization prompt. Set up the upstream
Python tools once:

```sh
python3 -m venv tools/venv
tools/venv/bin/pip install -r tools/requirements.txt
```

These commands use fish syntax. Replace `YOUR_TV_IP` with the TV's address. Run them
in a terminal with local-network permission. The ADB forward carries the Python
client over the working ADB connection, including on Macs where direct access fails.

```fish
set tv_serial YOUR_TV_IP:5555
adb connect $tv_serial
adb -s $tv_serial forward tcp:15556 tcp:5555
env TV_HOST=127.0.0.1 TV_PORT=15556 STOCK_LAUNCHER=none bash tvhome/build-and-install.sh
env TV_HOST=127.0.0.1 TV_PORT=15556 bash tools/theme.sh themes/sony-scenes
```

The installer checks that Home starts and resolves correctly. `STOCK_LAUNCHER=none`
keeps the previous launcher enabled as a fallback. This installer does not apply a
debloat list. Device backups and package-recovery records are kept separately.

When finished, turn USB/network debugging off in the TV's Developer options.

## Themes and artwork

All three themes default to six favorites, in order: SmartTube (YouTube),
Stremio, Jellyfin, SportzX, Xtra TV, and AIOStreams.

| Folder | Appearance |
|---|---|
| `themes/sony-scenes` | Fourteen static illustrated landscapes, changing once an hour, with icon buttons |
| `themes/sony-glass` | Static navy/teal wallpaper and app-colored focus |
| `themes/sony-light` | Plain dark background |

Reduced motion is enabled; advanced effects, row fades and continuous Home stats
are off. The app library and tile menus are available. An idle clock starts after
ten minutes on Home. Themes only change appearance and launcher configuration.

Edit a theme's `config.json`, then apply it with `tools/theme.sh`. `dock_apps` sets
the default favorites and their order; an order saved on the TV takes precedence.
`columns` controls the existing ordering editor, which returns to shelves when done.
Left/Right stay inside a shelf, Up/Down change shelves and remember their last tile,
and Up from Favorites goes to the top controls. Hold OK on a tile for its menu or
on Settings for launcher ordering tools. The app library remains available.
The [upstream configuration reference](https://github.com/Gathin23/cleanyourtv/blob/b06ab21105c49900cb1ed76b24414dba1e21d0db/themes/README.md)
documents other available keys.

Eleven original Orbit PNG banners are bundled in `tvhome/src/main/res/drawable-nodpi`.
The themes now select the original artwork scale because the shelf cards are larger.
The earlier `banner_*_large.xml` wrappers remain available for compact layouts.
Replace a PNG and rebuild to change it; there is no on-TV custom-image picker.
Other apps use their own artwork.

## Return to Orbit

With Orbit still installed and debugging enabled:

```fish
adb -s $tv_serial shell cmd package set-home-activity --user 0 com.psst.orbit/com.psst.aurora.LauncherActivity
adb -s $tv_serial shell input keyevent KEYCODE_HOME
```

This switches Home only. The original Orbit categories, hidden-app choices, recents,
and user-selected icons remain in Orbit's private storage and were not imported.

## Provenance and validation

See [UPSTREAM.md](UPSTREAM.md), [MIT license](LICENSE), and
[Outfit font license](FONT-LICENSE-OFL.txt). The launcher sources, themes and helper
tools are vendored from a pinned upstream revision, so building does not depend on
another Git checkout. The optional Capacity Stats app installed on the TV is a
separate upstream add-on and is not required by this launcher.

The Sony setup was verified on the real TV: preferred Home, six favorites, Scenes,
custom banners, Inputs menu, Sony Settings, and returning from SmartTube. The initial
setup survived reboot. Physical HDMI video/audio and every remote key were not tested.
Short idle samples showed low launcher CPU use; they are not a controlled benchmark
against Orbit. CI runs TV Home's release unit tests and lint and builds both apps.
