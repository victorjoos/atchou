# Machu Pachu server

```
uv sync
MACHU_KID_NAMES="Emma,Luca" uv run uvicorn machu_pachu_server.app:create_app --factory --reload
uv run pytest
```

Env: `MACHU_DATA_DIR` (default `./data`), `MACHU_CONFIG` (default `<data dir>/config.toml`). API docs at `/docs`.

## Configuration

Copy `config.example.toml` to `<data dir>/config.toml`: children's names (author suggestions), Google client ID,
email allowlist and scheduling options. `MACHU_*` env vars (e.g. `MACHU_KID_NAMES`, `MACHU_ALLOWED_EMAILS`,
`MACHU_GOOGLE_CLIENT_ID`) override the file.

## Auth

Auth is enforced only when `google_client_id` is set; without it everything is open (local dev). The app signs in
with Google, sends the ID token to `POST /auth/google`, and gets back our own signed session token
(`session_days`, default 30; secret in `<data dir>/session_secret`) that it sends as `Authorization: Bearer`.
Only emails in `allowed_emails` are admitted; removing one locks that account out immediately. The post's `sender`
is the signed-in account's name; `author` is the child who made the picture. `/frame/*` uses device tokens only.

Creating the Google client ID (Google Cloud Console, one project):
1. APIs & Services > OAuth consent screen: type External, add the family emails as test users.
2. Credentials > Create credentials > OAuth client ID > **Web application**: its client ID is `google_client_id`
   (the app fetches it from `/auth/config`).
3. Create a second client ID of type **Android**: package `com.example.machupachu` and the SHA-1 of the signing key
   (debug: `keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android`;
   add the release key's SHA-1 later). Nothing from this one goes in the code.

## Frame API and scheduling

Register a frame (prints its token once; only a hash is stored):
`uv run python -m machu_pachu_server.cli add-device salon`

The frame calls `GET /frame/image?battery=87&battery_mv=3900[&format=png]` with `Authorization: Bearer <token>`
and `If-None-Match`. Responses: `200` packed 4-bit buffer (or PNG), `304`, or `204` (nothing posted yet); all carry
`X-Next-Wake-At` and `X-Server-Time` (UTC). Simulator: `uv run python scripts/frame_sim.py --token TOKEN [--once]`.

Scheduling env vars (defaults): `MACHU_TIMEZONE` (Europe/Brussels), `MACHU_SLOTS` (06:00,15:00),
`MACHU_WINDOW_START`/`MACHU_WINDOW_END` (06:00/22:00), `MACHU_BUSY_INTERVAL_MIN` (60),
`MACHU_MIN_WAKE_MIN`/`MACHU_MAX_WAKE_MIN` (15/1440), `MACHU_DECAY_HALF_LIFE_DAYS` (365: rotation weight halves
every N days of age), `MACHU_PIN_BOOST` (3: weight multiplier for pinned posts).
