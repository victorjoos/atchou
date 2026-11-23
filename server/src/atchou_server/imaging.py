"""Image pipeline: crop/rotate/adjust -> 800x480 six-color image."""

import io
from dataclasses import dataclass
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


def _palette_image() -> Image.Image:
    flat: list[int] = []
    for _, rgb, _ in PALETTE:
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


def render(img: Image.Image, params: EditParams) -> Image.Image:
    """Return an 800x480 'P' image using only the six panel colors."""
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
    dither = Image.Dither.FLOYDSTEINBERG if params.mode == "dither" else Image.Dither.NONE
    out = img.quantize(palette=_palette_image(), dither=dither)
    if params.show_author and params.author and params.author.strip():
        # Draw after quantizing so the overlay stays crisp and in-palette.
        rgb = out.convert("RGB")
        _draw_author(rgb, params.author)
        out = rgb.quantize(palette=_palette_image(), dither=Image.Dither.NONE)
    return out


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
