# Machu Pachu server

```
uv sync
MACHU_KID_NAMES="Emma,Luca" uv run uvicorn machu_pachu_server.app:create_app --factory --reload
uv run pytest
```

Env: `MACHU_DATA_DIR` (default `./data`), `MACHU_KID_NAMES` (comma-separated author suggestions).
No auth yet (MVP). API docs at `/docs`.

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
