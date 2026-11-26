"""Frame-facing API: device token auth, current image with ETag, battery report, next wake time."""

import hashlib
import random
import secrets
import sqlite3
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Callable

from fastapi import APIRouter, Depends, Header, HTTPException, Request, Response
from fastapi.responses import FileResponse

from . import calibration, panels, scheduler
from .config import Config

TS = "%Y-%m-%dT%H:%M:%SZ"


def hash_token(token: str) -> str:
    return hashlib.sha256(token.encode()).hexdigest()


def add_device(conn: sqlite3.Connection, name: str) -> str:
    """Register a frame and return its token (shown once; only the hash is stored)."""
    token = secrets.token_urlsafe(32)
    conn.execute("INSERT INTO devices (name, token_hash) VALUES (?, ?)", (name, hash_token(token)))
    conn.commit()
    return token


def make_router(
    conn: sqlite3.Connection,
    data_dir: Path,
    cfg: Config,
    now_fn: Callable[[], datetime],
    rng: random.Random,
) -> APIRouter:
    router = APIRouter(prefix="/frame")

    def device(authorization: str | None = Header(None)):
        scheme, _, token = (authorization or "").partition(" ")
        row = conn.execute("SELECT * FROM devices WHERE token_hash = ?", (hash_token(token),)).fetchone() if scheme.lower() == "bearer" else None
        if row is None:
            raise HTTPException(401, "jeton d’appareil invalide")
        return row

    def all_posts() -> list[dict]:
        return [dict(r) for r in conn.execute("SELECT * FROM posts")]

    def advance(dev, posts: list[dict], now: datetime) -> int | None:
        current = dev["current_post_id"]
        current_exists = any(p["id"] == current for p in posts)
        advanced_at = scheduler.parse_utc(dev["advanced_at"]) if dev["advanced_at"] else None
        due = advanced_at is None or now >= advanced_at + timedelta(minutes=cfg.min_wake_min)
        # Never change the picture at night, unless the current one was deleted.
        if current_exists and not (due and scheduler.in_window(now, cfg)):
            return current
        pick = scheduler.pick_next(posts, current if current_exists else None, now, cfg, rng)
        if pick is None:
            return current if current_exists else None
        stamp = now.strftime(TS)
        conn.execute("UPDATE posts SET shown_at = COALESCE(shown_at, ?) WHERE id = ?", (stamp, pick["id"]))
        conn.execute("UPDATE devices SET current_post_id = ?, advanced_at = ? WHERE id = ?", (pick["id"], stamp, dev["id"]))
        pick["shown_at"] = pick["shown_at"] or stamp
        return pick["id"]

    @router.get("/image")
    def image(
        request: Request,
        dev=Depends(device),
        battery: float | None = None,
        battery_mv: int | None = None,
        format: str = "bin",
    ):
        if format not in ("bin", "png"):
            raise HTTPException(422, "le format doit être « bin » ou « png »")
        now = now_fn().astimezone(timezone.utc).replace(microsecond=0)
        conn.execute(
            "UPDATE devices SET last_seen = ?, battery_pct = COALESCE(?, battery_pct), battery_mv = COALESCE(?, battery_mv) WHERE id = ?",
            (now.strftime(TS), battery, battery_mv, dev["id"]),
        )
        if calibration.pattern_active(data_dir):
            conn.commit()
            return serve(request, format, calibration.pattern_file(data_dir, format), '"calibration-pattern"',
                         {"X-Next-Wake-At": (now + timedelta(minutes=cfg.min_wake_min)).strftime(TS),
                          "X-Server-Time": now.strftime(TS)})
        posts = all_posts()
        post_id = advance(dev, posts, now)
        conn.commit()
        headers = {
            "X-Next-Wake-At": scheduler.next_wake(now, posts, cfg).strftime(TS),
            "X-Server-Time": now.strftime(TS),
        }
        if post_id is None:
            return Response(status_code=204, headers=headers)

        path, version = panels.panel_file(data_dir, conn, post_id, format)
        return serve(request, format, path, f'"{post_id}-{format}-{version}"', headers)

    def serve(request: Request, format: str, path: Path, etag: str, headers: dict):
        headers["ETag"] = etag
        if request.headers.get("if-none-match") == etag:
            return Response(status_code=304, headers=headers)
        media = "application/octet-stream" if format == "bin" else "image/png"
        return FileResponse(path, media_type=media, headers=headers)

    return router
