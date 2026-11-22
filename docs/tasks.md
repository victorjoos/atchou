# Machu Pachu – Tasks

Order: MVP server + app first (no auth, no frame), then scheduling, auth,
deployment, firmware.

## Phase 1 – MVP (no auth, no frame)
1. ✅ **Server scaffold**: FastAPI, SQLite, config file, data dir, Dockerfile-ready layout.
2. ✅ **Image pipeline v0**: resize/crop to 800×480, Floyd–Steinberg dither to the
   6-color palette, PNG preview. CLI tool for experimenting. (Palette tuned
   against the real panel later.)
3. ✅ **Post API v0**: upload (image + author + options), list, get thumbnail/preview,
   delete, author suggestions. Open endpoints (no auth yet).
4. ✅ **Android: project + upload flow**: send screen, author field with
   suggestions, send, show server preview.
5. ✅ **Android: history** (basic grid + delete done; reschedule/pin pending editor): gallery of all posts, delete.
6. **Android: editor**: crop (5:3), rotate, brightness/contrast, author-overlay
   toggle (overlay rendered by server), "show on date", pin, exclude.
7. **Android: paint/text canvas**: 6-color palette, brush, eraser, text; uploads
   as a post (exact palette, no dithering).

4b. ✅ **Android: camera-first start screen**: CameraX viewfinder + shutter,
    flip, gallery picker (secondary), history button; capture goes to the send screen.
    Also done: dimmed overlay showing the cropped area; UI locked to portrait with
    labels and crop box rotating with the phone (like a stock camera app).
4c. ✅ **Android: drawing mode**: Photo/Drawing toggle on the camera; for
    drawings auto-detect the sheet's corners and perspective-correct it to a flat
    5:3 image (on-device, e.g. OpenCV or ML Kit Document Scanner), with manual
    corner adjustment as fallback; boost contrast/white balance for paper.
    Done with the ML Kit Document Scanner (needs Play Services; its UI covers
    auto-detect, manual corners and paper cleanup). Verified in the emulator.
7b. ✅ (existing screens) **French everywhere**: all user-facing text in French (app UI strings moved to
    `strings.xml` with a French default, server error messages, any text rendered
    on the image such as paint-canvas/text tool labels, Google sign-in prompts).
    Existing app screens and server messages are translated; new strings must go in
    `strings.xml` in French (Google sign-in and canvas tool labels still to do).

## Phase 2 – Frame-facing server logic
8. ✅ **Scheduler**: day queue (newest first, overflow to next days), decay-weighted
   rotation pool, pin/exclude; unit tests.
9. ✅ **Frame API**: device token, current image + ETag, native packed buffer,
   battery report, `next_wake_at` logic (quiet/busy/scheduled, clamps); unit tests.
10. ✅ **Frame simulator script** that polls like the firmware would.

## Phase 3 – Hardening and deployment
11. **Auth**: Google ID-token verification, email allowlist in config,
    Google Sign-In in the app.
12. **Deployment**: Docker, Caddy config, backups, HTTPS.
13. **Palette tuning** against the physical panel; optional dithering for drawings.

## Phase 4 – Firmware
14. ESP32-S3 firmware: Wi-Fi, poll, download, display, battery, deep sleep.

## Open decisions
- Final rotation decay formula and default slot times.
- Image upload size limit (the app currently downscales to 1600 px; no server cap).
- Verify the panel's native color codes (currently from memory).
- Decided: Android min SDK 28.
