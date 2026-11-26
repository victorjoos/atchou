"""Image pipeline: crop/rotate/adjust -> 800x480 six-color image."""

import hashlib
import io
import json
from dataclasses import dataclass
from pathlib import Path
from typing import Literal

import numpy as np
from PIL import Image, ImageDraw, ImageEnhance, ImageFont, ImageOps

WIDTH, HEIGHT = 800, 480

# (name, RGB, native panel color code). Native codes follow the Waveshare
# 7.3" six-color (Spectra 6) driver; verify against the real panel.
PALETTE = [
    ("black", (0, 0, 0), 0x0),
    ("white", (255, 255, 255), 0x1),
    ("yellow", (255, 255, 0), 0x2),
    ("red", (255, 0, 0), 0x3),
    ("blue", (0, 0, 255), 0x5),
    ("green", (0, 255, 0), 0x6),
]

THEORETICAL = [rgb for _, rgb, _ in PALETTE]
PALETTE_FILE = "palette.json"

# "drawing" = exact palette (no dithering) after levelling the paper to white.
Mode = Literal["dither", "exact", "drawing"]


@dataclass
class EditParams:
    # Normalized crop box on the (EXIF-corrected, rotated) image; None = centered cover crop.
    crop: tuple[float, float, float, float] | None = None  # x, y, w, h in 0..1
    rotate: int = 0  # degrees clockwise, multiple of 90
    brightness: float = 1.0
    contrast: float = 1.0
    author: str | None = None
    show_author: bool = False
    mode: Mode = "dither"


def load_calibration(data_dir: Path) -> tuple[list[tuple[int, int, int]] | None, str]:
    """Measured panel colors (PALETTE order) and a version that changes whenever they do."""
    path = Path(data_dir) / PALETTE_FILE
    if not path.exists():
        return None, "std"
    raw = path.read_bytes()
    data = json.loads(raw)
    colors = [tuple(int(v) for v in data[name]) for name, _, _ in PALETTE]
    return colors, "cal" + hashlib.sha1(raw).hexdigest()[:8]


def _palette_image(colors=None) -> Image.Image:
    flat: list[int] = []
    for rgb in colors or THEORETICAL:
        flat.extend(rgb)
    flat.extend([0] * (768 - len(flat)))
    img = Image.new("P", (1, 1))
    img.putpalette(flat)
    return img


def _crop_to_panel(img: Image.Image, crop) -> Image.Image:
    w, h = img.size
    if crop is None:
        target = WIDTH / HEIGHT
        if w / h > target:
            cw, ch = round(h * target), h
        else:
            cw, ch = w, round(w / target)
        box = ((w - cw) // 2, (h - ch) // 2, (w - cw) // 2 + cw, (h - ch) // 2 + ch)
    else:
        x, y, cw, ch = crop
        box = (round(x * w), round(y * h), round((x + cw) * w), round((y + ch) * h))
    return img.crop(box).resize((WIDTH, HEIGHT), Image.LANCZOS)


def _draw_author(img: Image.Image, author: str) -> None:
    draw = ImageDraw.Draw(img)
    font = ImageFont.load_default(size=28)
    text = author.strip()
    left, top, right, bottom = draw.textbbox((0, 0), text, font=font)
    pad, margin = 8, 12
    tw, th = right - left, bottom - top
    x1, y1 = margin, HEIGHT - margin - th - 2 * pad
    draw.rectangle((x1, y1, x1 + tw + 2 * pad, y1 + th + 2 * pad), fill=(255, 255, 255))
    draw.text((x1 + pad - left, y1 + pad - top), text, font=font, fill=(0, 0, 0))


def load_image(data: bytes) -> Image.Image:
    img = ImageOps.exif_transpose(Image.open(io.BytesIO(data)))
    return img.convert("RGB")


def adjust(img: Image.Image, params: EditParams) -> Image.Image:
    """Rotate, crop to 800x480 and apply brightness/contrast. Full color, not yet quantized."""
    img = img.convert("RGB")
    if params.rotate % 360:
        img = img.rotate(-params.rotate, expand=True)
    img = _crop_to_panel(img, params.crop)
    if params.brightness != 1.0:
        img = ImageEnhance.Brightness(img).enhance(params.brightness)
    if params.contrast != 1.0:
        img = ImageEnhance.Contrast(img).enhance(params.contrast)
    if params.mode == "drawing":
        # Per-channel stretch: paper (the brightest bulk) -> white, also fixing color casts; ink stays dark.
        img = ImageOps.autocontrast(img, cutoff=(1, 5))
    return img


def show(adjusted: Image.Image, params: EditParams) -> Image.Image:
    """What the app displays: the adjusted picture, with the author label if requested."""
    out = adjusted.copy()
    if params.show_author and params.author and params.author.strip():
        _draw_author(out, params.author)
    return out


def _compress_to_panel_range(img: Image.Image, measured) -> Image.Image:
    """Map 0..255 onto what the panel can show between its measured black and white."""
    black = np.array(measured[0], dtype=np.float32)
    white = np.array(measured[1], dtype=np.float32)
    arr = np.asarray(img, dtype=np.float32) / 255.0
    return Image.fromarray(np.rint(black + arr * (white - black)).astype(np.uint8), "RGB")


def quantize(adjusted: Image.Image, params: EditParams, measured=None) -> Image.Image:
    """Return an 800x480 'P' image whose indices follow PALETTE.

    With a measured palette, photos are dithered against the colors the panel really
    shows, so error diffusion matches the output. "exact" and "drawing" keep the
    theoretical palette because their pixels are meant to be palette colors already.
    """
    if measured and params.mode == "dither":
        img = _compress_to_panel_range(adjusted, measured)
        out = img.quantize(palette=_palette_image(measured), dither=Image.Dither.FLOYDSTEINBERG)
    else:
        dither = Image.Dither.FLOYDSTEINBERG if params.mode == "dither" else Image.Dither.NONE
        out = adjusted.quantize(palette=_palette_image(), dither=dither)
    out.putpalette(_palette_image().getpalette())  # indices are the contract; show theoretical colors
    if params.show_author and params.author and params.author.strip():
        # Draw after quantizing so the overlay stays crisp and in-palette.
        rgb = out.convert("RGB")
        _draw_author(rgb, params.author)
        out = rgb.quantize(palette=_palette_image(), dither=Image.Dither.NONE)
    return out


def render(img: Image.Image, params: EditParams, measured=None) -> Image.Image:
    """Return an 800x480 'P' image using only the six panel colors."""
    return quantize(adjust(img, params), params, measured)


def to_png(img: Image.Image) -> bytes:
    buf = io.BytesIO()
    img.convert("RGB").save(buf, format="PNG")
    return buf.getvalue()


def to_panel_buffer(img: Image.Image) -> bytes:
    """Pack to 4 bits/pixel (high nibble = left pixel), row-major, 192000 bytes."""
    codes = np.array([c for _, _, c in PALETTE], dtype=np.uint8)
    idx = np.asarray(img.convert("P"), dtype=np.uint8)
    nat = codes[idx]
    return ((nat[:, 0::2] << 4) | nat[:, 1::2]).tobytes()
