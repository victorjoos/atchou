"""Low-battery screens, drawn in exact panel colors on top of the cached panel image."""

import io
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

from . import imaging

BADGE_BELOW = 15  # percent: small badge in a corner
CHARGE_BELOW = 5  # percent: full "charge me" screen instead of the photo
BLACK, WHITE, RED = 0, 1, 3  # PALETTE indices


def _indexed(img: Image.Image) -> Image.Image:
    return img.convert("RGB").quantize(palette=imaging._palette_image(), dither=Image.Dither.NONE)


def _battery_icon(draw: ImageDraw.ImageDraw, x: int, y: int, w: int, h: int, color: int, fill: float) -> None:
    draw.rectangle((x, y, x + w, y + h), outline=color, width=max(3, h // 12))
    draw.rectangle((x + w, y + h // 3, x + w + max(4, w // 12), y + 2 * h // 3), fill=color)
    inner = int((w - 8) * fill)
    if inner > 0:
        draw.rectangle((x + 4, y + 4, x + 4 + inner, y + h - 4), fill=color)


def with_badge(panel: Image.Image, pct: float) -> Image.Image:
    img = _indexed(panel)
    draw = ImageDraw.Draw(img)
    text = f"{round(pct)} %"
    font = ImageFont.load_default(size=28)
    tw = draw.textlength(text, font=font)
    w, h = 54, 28
    box_w = int(tw) + w + 40
    x0, y0 = imaging.WIDTH - box_w - 10, 10
    draw.rounded_rectangle((x0, y0, x0 + box_w, y0 + 48), radius=8, fill=WHITE, outline=BLACK, width=2)
    _battery_icon(draw, x0 + 12, y0 + 10, w, h, RED, max(pct, 0) / 100)
    draw.text((x0 + 12 + w + 22, y0 + 24), text, font=font, fill=BLACK, anchor="lm")
    return img


def charge_screen(pct: float | None) -> Image.Image:
    img = Image.new("P", (imaging.WIDTH, imaging.HEIGHT), WHITE)
    img.putpalette([c for rgb in imaging.THEORETICAL for c in rgb])
    draw = ImageDraw.Draw(img)
    _battery_icon(draw, 300, 110, 200, 100, RED, 0.04)
    draw.text((400, 290), "Batterie presque vide", font=ImageFont.load_default(size=44), fill=BLACK, anchor="mm")
    draw.text((400, 350), "Merci de recharger le cadre", font=ImageFont.load_default(size=30), fill=BLACK, anchor="mm")
    return img


def apply(panel_png: Path, fmt: str, pct: float) -> bytes:
    img = with_badge(Image.open(io.BytesIO(panel_png.read_bytes())), pct)
    return imaging.to_panel_buffer(img) if fmt == "bin" else imaging.to_png(img)


def charge_bytes(fmt: str, pct: float | None) -> bytes:
    img = charge_screen(pct)
    return imaging.to_panel_buffer(img) if fmt == "bin" else imaging.to_png(img)
