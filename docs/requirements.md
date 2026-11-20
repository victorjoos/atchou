# Machu Pachu – Functional Requirements

A family app to send drawings, photos and text to a 7" Waveshare PhotoPainter
(ESP32-S3, 800×480, 6-color e-paper). v1 targets a single frame.

## Components
- **Android app** (Kotlin, Jetpack Compose)
- **Server** (Python, FastAPI, SQLite + files on disk); does all image processing
- **Frame firmware** (ESP32-S3) – later, after app and server work

## Android app
1. **Login** via Google Sign-In (added after the MVP; see tasks).
1b. **Camera-first**: the app opens on a camera view (photos and drawings);
    gallery picking is a secondary option. The viewfinder dims the area that will
    be cropped; the camera UI stays portrait-locked and its labels and crop box
    rotate with the phone. For drawings, the sheet is auto-detected
    and flattened (perspective-corrected) before sending.
2. **Send a picture**: then an in-app editor:
   crop to the panel aspect ratio (5:3), rotate, brightness/contrast,
   toggle "show author overlay".
3. **Preview**: server-rendered 6-color dithered preview before sending.
4. **Draw/text canvas**: basic paint tool restricted to the 6 panel colors
   (black, white, red, yellow, green, blue) with brush, eraser and text.
   Rendered with the exact palette first; optional dithering later.
5. **Metadata**: free-text author with suggestions (kid names, stored on the
   server and shared); optional "show on date"; optional "pin / keep forever"
   (e.g. birthdays); optional "exclude from rotation".
6. **History**: gallery of all items from all users (author, sender, date,
   status). Everyone can delete, reschedule, pin or exclude any item.

## Server
1. Store originals, edit parameters and metadata (SQLite + filesystem).
2. **Image pipeline**: crop/rotate/adjust, author overlay, 6-color dithering
   (palette tuned to the real panel), PNG preview and native packed buffer for
   the firmware. Drawing posts use the exact palette (no dithering) initially.
3. **Scheduling**
   - An item shows on its scheduled day (default: the day it is sent). If
     several items target one day, newest first, overflow goes to following days.
   - Once shown, items stay in a rotation pool (unless excluded), selected by
     weighted random with age decay; pinned items get a boost.
   - Configurable: rotation slots per day (default 1–2), busy-day interval,
     timezone, decay parameters.
4. **Frame API**: device-token auth, returns the current image (ETag support),
   accepts battery reports, and returns `next_wake_at`.
5. **Adaptive wake-up**: the server tells the frame when to wake next.
   - Quiet day: next regular rotation slot (e.g. 08:00 / 18:00).
   - Busy day (queued, not yet shown items): ~1 hour.
   - Scheduled item: exactly when its day starts.
   - Clamped to min ~15 min / max ~24 h; frame falls back to a default
     (~6 h) if the request fails. Absolute server time is used so clock drift
     doesn't accumulate. Low battery can lengthen intervals.
6. **Auth** (after MVP): verify Google ID tokens; only emails in a manually
   maintained server-config allowlist are admitted.
7. Deployable on a VPS, or a home server behind Caddy on a VPS (Docker).

## Frame firmware (later)
Wake, connect Wi-Fi, poll, download buffer, refresh panel, report battery,
deep sleep until `next_wake_at`.

## Non-functional
- **Language: French everywhere.** All user-facing text (app UI, server error
  messages, defaults like date formats) is in French; no English strings shown to users.
- One frame in v1; data model should not preclude several.
- Phone sends original + edit parameters (downscaled to a sane size).
- No moderation beyond "everyone can delete".
- No frame needed during app/server development: use a simulator script and
  PNG previews.
