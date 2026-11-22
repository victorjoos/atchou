# Project status

_Last updated: 2026-11-20_

## Done

**Planning**
- `docs/requirements.md` and `docs/tasks.md` (phased task list).

**Server** (`server/`, Python + uv, FastAPI, SQLite)
- Image pipeline: EXIF transpose, rotate, crop (5:3), resize to 800×480, brightness/contrast, quantize to the 6 panel colors (dither or exact palette), author overlay.
- API: preview, create, list, get, image, patch, delete, authors.
- Writes a packed `panel.bin` for the frame.
- Scheduler (day queue newest-first with carry-over, weighted rotation pool with configurable decay, pin boost, 06:00–22:00 window) and frame API (`/frame/image`, per-device tokens in DB, ETag, battery, `X-Next-Wake-At`); `scripts/frame_sim.py` simulator. See `server/README.md`.
- 9 passing tests.

**Android app** (`app/`, Kotlin + Compose)
- Opens on the camera, with a dimmed overlay showing what gets cropped.
- Camera screen is portrait-locked; labels and crop box rotate with the phone.
- Gallery picker as a secondary option.
- Photo/Dessin toggle: Dessin launches the ML Kit Document Scanner (auto-detect, manual corners, flatten); result goes to the send screen with dithering off and the server's `drawing` mode (exact palette, paper levelled to white). Verified in the emulator (before the `drawing` mode was added).
- Send screen: server-rendered preview, author chips + free text, author-overlay and dither toggles.
- History screen: grid, delete, pull-to-refresh, server-URL setting.
- Tested end to end in the emulator against the local server.

## Next

1. **11. Google login** with an email allowlist (protects app endpoints; frame endpoints use device tokens only).
2. **6. Editor:** adjustable crop, rotate, brightness/contrast, show-on date, pin, exclude from rotation (needs PATCH in the app's API client and history controls).
3. **7. Paint/text canvas** limited to the 6 colors.
4. **12. Deployment:** Docker + Caddy on a VPS.
5. **13. Palette tuning** against the real panel.
6. **14. ESP32-S3 firmware.**

## Open decisions
- Tune `MACHU_DECAY_HALF_LIFE_DAYS` (default 365) once the app is in use.
- Upload size limit.
- Verify the panel's native color codes (currently from memory: black 0, white 1, yellow 2, red 3, blue 5, green 6).

## Dev notes
- Server: `cd server && MACHU_KID_NAMES="Emma,Luca" uv run uvicorn machu_pachu_server.app:create_app --factory --host 0.0.0.0 --port 8000`
- App build: `cd app && ./gradlew --no-daemon -q assembleDebug`
- Emulator reaches the host server at `http://10.0.2.2:8000` (default in the app).
