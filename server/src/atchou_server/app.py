import json
import os
from dataclasses import asdict
import random
from datetime import date, datetime, timezone
from pathlib import Path

from fastapi import Depends, FastAPI, File, Form, HTTPException, UploadFile
from fastapi.responses import FileResponse, Response
from PIL import UnidentifiedImageError
from pydantic import BaseModel

from . import auth, db, frame, imaging
from .config import Config

class PostUpdate(BaseModel):
    author: str | None = None
    show_on: str | None = None
    pinned: bool | None = None
    exclude_from_rotation: bool | None = None


def _parse_crop(crop: str | None):
    if not crop:
        return None
    try:
        x, y, w, h = (float(v) for v in crop.split(","))
    except ValueError:
        raise HTTPException(422, "le recadrage doit être « x,y,l,h » (valeurs normalisées 0..1)")
    if not (0 <= x < 1 and 0 <= y < 1 and 0 < w <= 1 and 0 < h <= 1 and x + w <= 1.0001 and y + h <= 1.0001):
        raise HTTPException(422, "recadrage hors limites")
    return (x, y, w, h)


def _build_params(crop, rotate, brightness, contrast, author, show_author, mode) -> imaging.EditParams:
    if rotate % 90:
        raise HTTPException(422, "la rotation doit être un multiple de 90")
    if mode not in ("dither", "exact", "drawing"):
        raise HTTPException(422, "le mode doit être « dither », « exact » ou « drawing »")
    return imaging.EditParams(
        crop=_parse_crop(crop), rotate=rotate, brightness=brightness, contrast=contrast,
        author=author, show_author=show_author, mode=mode,
    )


def _load(upload_bytes: bytes):
    try:
        return imaging.load_image(upload_bytes)
    except (UnidentifiedImageError, OSError):
        raise HTTPException(400, "image invalide")


def create_app(data_dir: Path | None = None, config: Config | None = None, now_fn=None, rng=None, google_verifier=None) -> FastAPI:
    data_dir = Path(data_dir or os.environ.get("ATCHOU_DATA_DIR", "data"))
    (data_dir / "posts").mkdir(parents=True, exist_ok=True)
    conn = db.connect(data_dir / "atchou.sqlite")
    cfg = config or Config.load(Path(os.environ.get("ATCHOU_CONFIG", data_dir / "config.toml")))
    kids = list(cfg.children)
    app = FastAPI(title="Atchou")
    auth_router, require_user = auth.make_auth(cfg, data_dir, google_verifier or auth.verify_google_token)
    app.include_router(auth_router)
    app.include_router(frame.make_router(
        conn, data_dir, cfg,
        now_fn or (lambda: datetime.now(timezone.utc)), rng or random.Random(),
    ))

    def post_dir(post_id: int) -> Path:
        return data_dir / "posts" / str(post_id)

    def row_to_dict(row) -> dict:
        d = dict(row)
        for k in ("pinned", "exclude_from_rotation", "show_author"):
            d[k] = bool(d[k])
        d["params"] = json.loads(d["params"])
        d["image_url"] = f"/posts/{d['id']}/image"
        return d

    def get_row(post_id: int):
        row = conn.execute("SELECT * FROM posts WHERE id = ?", (post_id,)).fetchone()
        if row is None:
            raise HTTPException(404, "publication introuvable")
        return row

    @app.post("/preview", dependencies=[Depends(require_user)])
    async def preview(
        image: UploadFile = File(...),
        crop: str | None = Form(None),
        rotate: int = Form(0),
        brightness: float = Form(1.0),
        contrast: float = Form(1.0),
        author: str | None = Form(None),
        show_author: bool = Form(False),
        mode: str = Form("dither"),
    ):
        params = _build_params(crop, rotate, brightness, contrast, author, show_author, mode)
        out = imaging.render(_load(await image.read()), params)
        return Response(imaging.to_png(out), media_type="image/png")

    @app.post("/posts", status_code=201, dependencies=[Depends(require_user)])
    async def create_post(
        image: UploadFile = File(...),
        author: str = Form(""),
        sender: str = Form(""),
        show_on: str | None = Form(None),
        pinned: bool = Form(False),
        exclude_from_rotation: bool = Form(False),
        crop: str | None = Form(None),
        rotate: int = Form(0),
        brightness: float = Form(1.0),
        contrast: float = Form(1.0),
        show_author: bool = Form(False),
        mode: str = Form("dither"),
        user=Depends(require_user),
    ):
        # The sender is the signed-in account (history metadata); `author` is the child who made it.
        if user:
            sender = user["name"]
        if show_on:
            try:
                date.fromisoformat(show_on)
            except ValueError:
                raise HTTPException(422, "show_on doit être au format AAAA-MM-JJ")
        params = _build_params(crop, rotate, brightness, contrast, author, show_author, mode)
        original = await image.read()
        rendered = imaging.render(_load(original), params)

        cur = conn.execute(
            "INSERT INTO posts (author, sender, show_on, pinned, exclude_from_rotation, show_author, params)"
            " VALUES (?,?,?,?,?,?,?)",
            (author.strip(), sender, show_on or None, pinned, exclude_from_rotation, show_author,
             json.dumps(asdict(params))),
        )
        post_id = cur.lastrowid
        if author.strip():
            conn.execute(
                "INSERT INTO authors (name) VALUES (?) ON CONFLICT(name) DO UPDATE SET last_used = strftime('%Y-%m-%dT%H:%M:%SZ','now')",
                (author.strip(),),
            )
        conn.commit()

        d = post_dir(post_id)
        d.mkdir(parents=True)
        (d / "original").write_bytes(original)
        (d / "rendered.png").write_bytes(imaging.to_png(rendered))
        (d / "panel.bin").write_bytes(imaging.to_panel_buffer(rendered))
        return row_to_dict(get_row(post_id))

    @app.get("/posts", dependencies=[Depends(require_user)])
    def list_posts():
        rows = conn.execute("SELECT * FROM posts ORDER BY id DESC").fetchall()
        return [row_to_dict(r) for r in rows]

    @app.get("/posts/{post_id}", dependencies=[Depends(require_user)])
    def get_post(post_id: int):
        return row_to_dict(get_row(post_id))

    @app.get("/posts/{post_id}/image", dependencies=[Depends(require_user)])
    def get_image(post_id: int):
        get_row(post_id)
        return FileResponse(post_dir(post_id) / "rendered.png", media_type="image/png")

    @app.patch("/posts/{post_id}", dependencies=[Depends(require_user)])
    def update_post(post_id: int, body: PostUpdate):
        get_row(post_id)
        fields = body.model_dump(exclude_unset=True)
        if "show_on" in fields and fields["show_on"]:
            try:
                date.fromisoformat(fields["show_on"])
            except ValueError:
                raise HTTPException(422, "show_on doit être au format AAAA-MM-JJ")
        if fields:
            sets = ", ".join(f"{k} = ?" for k in fields)
            conn.execute(f"UPDATE posts SET {sets} WHERE id = ?", (*fields.values(), post_id))
            conn.commit()
        return row_to_dict(get_row(post_id))

    @app.delete("/posts/{post_id}", status_code=204, dependencies=[Depends(require_user)])
    def delete_post(post_id: int):
        get_row(post_id)
        conn.execute("DELETE FROM posts WHERE id = ?", (post_id,))
        conn.commit()
        d = post_dir(post_id)
        for f in d.glob("*"):
            f.unlink()
        d.rmdir()

    @app.get("/authors", dependencies=[Depends(require_user)])
    def authors():
        used = [r["name"] for r in conn.execute("SELECT name FROM authors ORDER BY last_used DESC")]
        return kids + [a for a in used if a not in kids]

    return app

