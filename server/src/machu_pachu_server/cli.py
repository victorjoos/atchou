"""Admin commands: `python -m machu_pachu_server.cli add-device NAME`."""

import os
import sys
from pathlib import Path

from . import db, frame


def main(argv: list[str]) -> int:
    if len(argv) != 2 or argv[0] != "add-device":
        print("usage: python -m machu_pachu_server.cli add-device NAME", file=sys.stderr)
        return 2
    data_dir = Path(os.environ.get("MACHU_DATA_DIR", "data"))
    data_dir.mkdir(parents=True, exist_ok=True)
    conn = db.connect(data_dir / "machu.sqlite")
    print(frame.add_device(conn, argv[1]))
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
