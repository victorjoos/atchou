"""Simulates the frame firmware: polls the server, decodes the packed buffer, sleeps until next_wake_at.

    uv run python scripts/frame_sim.py --token TOKEN [--url http://localhost:8000] [--once] [--out frame.png]
"""

import argparse
import sys
import time
import urllib.error
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

import numpy as np
from PIL import Image

from machu_pachu_server.imaging import HEIGHT, PALETTE, WIDTH

FALLBACK_SLEEP_S = 6 * 3600  # what the firmware does when the request fails


def decode_panel(buf: bytes) -> Image.Image:
    packed = np.frombuffer(buf, dtype=np.uint8).reshape(HEIGHT, WIDTH // 2)
    nat = np.empty((HEIGHT, WIDTH), dtype=np.uint8)
    nat[:, 0::2], nat[:, 1::2] = packed >> 4, packed & 0xF
    lut = {code: rgb for _, rgb, code in PALETTE}
    rgb = np.zeros((HEIGHT, WIDTH, 3), dtype=np.uint8)
    for code, color in lut.items():
        rgb[nat == code] = color
    return Image.fromarray(rgb)


def poll(url: str, token: str, etag: str | None, battery: int) -> tuple[int, bytes, dict]:
    req = urllib.request.Request(f"{url}/frame/image?battery={battery}", headers={"Authorization": f"Bearer {token}"})
    if etag:
        req.add_header("If-None-Match", etag)
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            return r.status, r.read(), dict(r.headers)
    except urllib.error.HTTPError as e:
        return e.code, b"", dict(e.headers)


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--url", default="http://localhost:8000")
    ap.add_argument("--token", required=True)
    ap.add_argument("--out", default="frame.png")
    ap.add_argument("--battery", type=int, default=100)
    ap.add_argument("--once", action="store_true", help="poll once and exit")
    args = ap.parse_args()

    etag = None
    while True:
        sleep_s = FALLBACK_SLEEP_S
        try:
            status, body, headers = poll(args.url, args.token, etag, args.battery)
            h = {k.lower(): v for k, v in headers.items()}
            print(f"{datetime.now():%H:%M:%S} HTTP {status}", end="")
            if status == 200:
                etag = h["etag"]
                decode_panel(body).save(Path(args.out))
                print(f" new image -> {args.out}", end="")
            elif status == 304:
                print(" unchanged", end="")
            elif status == 204:
                print(" nothing to show", end="")
            if "x-next-wake-at" in h:
                wake = datetime.strptime(h["x-next-wake-at"], "%Y-%m-%dT%H:%M:%SZ").replace(tzinfo=timezone.utc)
                server_now = datetime.strptime(h["x-server-time"], "%Y-%m-%dT%H:%M:%SZ").replace(tzinfo=timezone.utc)
                # Server time is the reference, so clock drift doesn't accumulate.
                sleep_s = max(0, (wake - server_now).total_seconds())
                print(f"; next wake {wake.astimezone():%Y-%m-%d %H:%M} (in {sleep_s / 60:.0f} min)", end="")
            print()
        except OSError as e:
            print(f"request failed ({e}); retrying in {FALLBACK_SLEEP_S // 3600} h")
        if args.once:
            return 0
        time.sleep(sleep_s)


if __name__ == "__main__":
    sys.exit(main())
