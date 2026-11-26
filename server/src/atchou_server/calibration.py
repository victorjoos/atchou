"""Panel color calibration: show a 6-patch chart on the frame, photograph it, measure the colors.

    uv run python -m atchou_server.calibration pattern   # frame shows the chart on its next wake
    uv run python -m atchou_server.calibration measure photo.jpg   # writes <data>/palette.json
    uv run python -m atchou_server.calibration clear     # back to normal; `reset` drops the palette
"""

import argparse
import io
import json
import os
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageOps

from . import imaging

FLAG = "calibration-pattern"
COLS, ROWS = 3, 2  # patches in PALETTE order, row by row: black white yellow / red blue green


def pattern_active(data_dir: Path) -> bool:
    return (Path(data_dir) / FLAG).exists()


def pattern_image() -> Image.Image:
    img = Image.new("RGB", (imaging.WIDTH, imaging.HEIGHT))
    cw, ch = imaging.WIDTH // COLS + 1, imaging.HEIGHT // ROWS
    for i, rgb in enumerate(imaging.THEORETICAL):
        x, y = (i % COLS) * (imaging.WIDTH // COLS), (i // COLS) * ch
        img.paste(rgb, (x, y, min(x + cw, imaging.WIDTH), y + ch))
    return img.quantize(palette=imaging._palette_image(), dither=Image.Dither.NONE)


def pattern_file(data_dir: Path, fmt: str) -> Path:
    path = Path(data_dir) / f"{FLAG}.{fmt}"
    if not path.exists():
        img = pattern_image()
        path.write_bytes(imaging.to_panel_buffer(img) if fmt == "bin" else imaging.to_png(img))
    return path


def measure(photo: bytes) -> list[tuple[int, int, int]]:
    """Median color of the middle of each patch, in PALETTE order. The photo must show only the screen."""
    img = ImageOps.exif_transpose(Image.open(io.BytesIO(photo))).convert("RGB")
    w, h = img.size
    if h > w:
        raise ValueError("la photo doit être en paysage et ne montrer que l'écran")
    arr = np.asarray(img)
    colors = []
    for i in range(COLS * ROWS):
        c, r = i % COLS, i // COLS
        x0, x1 = int((c + 0.2) * w / COLS), int((c + 0.8) * w / COLS)
        y0, y1 = int((r + 0.2) * h / ROWS), int((r + 0.8) * h / ROWS)
        patch = arr[y0:y1, x0:x1].reshape(-1, 3)
        colors.append(tuple(int(v) for v in np.median(patch, axis=0)))
    luma = [0.2126 * r + 0.7152 * g + 0.0722 * b for r, g, b in colors]
    if luma[0] != min(luma) or luma[1] != max(luma):
        raise ValueError(f"le noir doit être le plus sombre et le blanc le plus clair ({colors}) : "
                         "photo mal cadrée, à l'envers ou reflet ?")
    return colors


def save_palette(data_dir: Path, colors) -> None:
    data = {name: list(c) for (name, _, _), c in zip(imaging.PALETTE, colors)}
    (Path(data_dir) / imaging.PALETTE_FILE).write_text(json.dumps(data, indent=1) + "\n")


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--data-dir", default=os.environ.get("ATCHOU_DATA_DIR", "data"))
    sub = ap.add_subparsers(dest="cmd", required=True)
    sub.add_parser("pattern")
    sub.add_parser("clear")
    sub.add_parser("reset")
    m = sub.add_parser("measure")
    m.add_argument("photo")
    args = ap.parse_args(argv)
    d = Path(args.data_dir)

    if args.cmd == "pattern":
        (d / FLAG).touch()
        print("Le cadre affichera la mire à son prochain réveil (appui court sur KEY pour forcer).")
    elif args.cmd == "clear":
        (d / FLAG).unlink(missing_ok=True)
        print("Mire désactivée.")
    elif args.cmd == "reset":
        (d / imaging.PALETTE_FILE).unlink(missing_ok=True)
        print("Palette théorique rétablie.")
    else:
        try:
            colors = measure(Path(args.photo).read_bytes())
        except ValueError as e:
            print(f"Erreur : {e}", file=sys.stderr)
            return 1
        save_palette(d, colors)
        for (name, theory, _), c in zip(imaging.PALETTE, colors):
            print(f"{name:7} {theory} -> {c}")
        print(f"Écrit dans {d / imaging.PALETTE_FILE}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
