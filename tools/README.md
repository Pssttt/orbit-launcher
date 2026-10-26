# CleanYourTV theme tools

These five files are vendored from CleanYourTV revision
`b06ab21105c49900cb1ed76b24414dba1e21d0db`; see [license](LICENSE) and
[provenance](../tvhome/UPSTREAM.md). They are unmodified.

`tv.py` is the ADB client, `b64push.py` verifies chunked file transfers,
`theme.sh` applies a theme, and `theme_restart.py` safely restarts Home while
preserving any enabled accessibility helpers. Install Python requirements in
`tools/venv`, which is ignored by Git. Setup and usage are in
[TV Home's README](../tvhome/README.md).
