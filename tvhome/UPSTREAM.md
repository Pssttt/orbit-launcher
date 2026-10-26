# Upstream attribution

Source: [Gathin23/cleanyourtv](https://github.com/Gathin23/cleanyourtv)

Revision: `b06ab21105c49900cb1ed76b24414dba1e21d0db`

- `tvhome/` vendors upstream `launcher/app/`, its launcher build/install script,
  MIT license and Outfit SIL Open Font License.
- `tools/` vendors `tv.py`, `b64push.py`, `theme.sh`, `theme_restart.py`, and
  `requirements.txt`. The scripts retain upstream's validation and error handling.
- Scenes illustrations and the Glass wallpaper come from upstream `themes/` and
  retain the upstream MIT license in `themes/LICENSE`.
- Orbit's existing PNG banners were copied from this repository's original app.

Local changes: integrate the launcher as the `tvhome` Gradle module, adapt the
installer's build and APK paths, configure Sony inputs and a six-app dock, provide
three personalized themes, and add native XML wrappers for larger banner artwork.
The upstream launcher Java code and unit tests are unchanged.

Copyright (c) 2026 Gathin. See [LICENSE](LICENSE).
