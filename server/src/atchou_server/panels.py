"""Panel images built on demand from the stored original, so a new calibration applies everywhere."""

import json
import os
import sqlite3
from pathlib import Path

from . import imaging


def _write_atomic(path: Path, data: bytes) -> None:
    tmp = path.with_suffix(path.suffix + ".tmp")
    tmp.write_bytes(data)
    os.replace(tmp, path)


def _params(conn: sqlite3.Connection, post_id: int) -> imaging.EditParams:
    row = conn.execute("SELECT params FROM posts WHERE id = ?", (post_id,)).fetchone()
    return imaging.EditParams(**json.loads(row["params"]))


def adjusted_image(data_dir: Path, conn: sqlite3.Connection, post_id: int):
    d = data_dir / "posts" / str(post_id)
    return imaging.adjust(imaging.load_image((d / "original").read_bytes()), _params(conn, post_id))


def shown_png(data_dir: Path, conn: sqlite3.Connection, post_id: int) -> Path:
    """The cropped, adjusted picture as the app shows it (not quantized)."""
    path = data_dir / "posts" / str(post_id) / "shown.png"
    if not path.exists():
        img = imaging.show(adjusted_image(data_dir, conn, post_id), _params(conn, post_id))
        _write_atomic(path, imaging.to_png(img))
    return path


def panel_file(data_dir: Path, conn: sqlite3.Connection, post_id: int, fmt: str) -> tuple[Path, str]:
    """Return the quantized panel file ("bin" or "png") for the current calibration, plus its version."""
    measured, version = imaging.load_calibration(data_dir)
    d = data_dir / "posts" / str(post_id)
    path = d / f"panel-{version}.{fmt}"
    if not path.exists():
        params = _params(conn, post_id)
        out = imaging.quantize(adjusted_image(data_dir, conn, post_id), params, measured)
        for stale in d.glob("panel-*"):
            stale.unlink(missing_ok=True)
        _write_atomic(d / f"panel-{version}.bin", imaging.to_panel_buffer(out))
        _write_atomic(d / f"panel-{version}.png", imaging.to_png(out))
    return path, version
