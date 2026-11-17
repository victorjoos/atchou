# Machu Pachu server

```
uv sync
MACHU_KID_NAMES="Emma,Luca" uv run uvicorn machu_pachu_server.app:create_app --factory --reload
uv run pytest
```

Env: `MACHU_DATA_DIR` (default `./data`), `MACHU_KID_NAMES` (comma-separated author suggestions).
No auth yet (MVP). API docs at `/docs`.
