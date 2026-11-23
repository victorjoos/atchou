import io
import random
from datetime import datetime, timedelta, timezone
from zoneinfo import ZoneInfo

from fastapi.testclient import TestClient
from PIL import Image

from atchou_server import db, frame, scheduler
from atchou_server.app import create_app
from atchou_server.config import Config

CFG = Config()
BRU = ZoneInfo("Europe/Brussels")


def local(y, mo, d, h=0, mi=0):
    return datetime(y, mo, d, h, mi, tzinfo=BRU).astimezone(timezone.utc)


def post(id, created, shown=None, show_on=None, pinned=False, excluded=False):
    return {"id": id, "created_at": created.strftime("%Y-%m-%dT%H:%M:%SZ"), "show_on": show_on,
            "shown_at": shown, "pinned": pinned, "exclude_from_rotation": excluded}


def test_queue_is_newest_first_and_carries_over():
    now = local(2026, 12, 3, 10)
    posts = [
        post(1, local(2026, 12, 2, 9)),  # missed yesterday
        post(2, local(2026, 12, 3, 8)),
        post(3, local(2026, 12, 3, 9)),
        post(4, local(2026, 12, 1, 9), shown="x"),
        post(5, local(2026, 12, 3, 9), show_on="2026-12-10"),
    ]
    assert [p["id"] for p in scheduler.queued(posts, now, CFG)] == [3, 2, 1]


def test_rotation_excludes_current_and_excluded_and_decays():
    now = local(2027, 12, 3, 10)
    old = post(1, local(2026, 12, 3, 9), shown="x")
    recent = post(2, local(2027, 12, 2, 9), shown="x")
    assert scheduler.weight(recent, now, CFG) > scheduler.weight(old, now, CFG) > 0.4  # a year old is still ~50%
    pool = [old, recent, post(3, local(2027, 1, 1), shown="x", excluded=True)]
    rng = random.Random(0)
    picks = {scheduler.rotation_pick(pool, 2, now, CFG, rng)["id"] for _ in range(20)}
    assert picks == {1}


def test_next_wake_quiet_busy_scheduled_and_clamped():
    shown = post(1, local(2026, 12, 1), shown="x")
    assert scheduler.next_wake(local(2026, 12, 3, 7), [shown], CFG) == local(2026, 12, 3, 15)
    assert scheduler.next_wake(local(2026, 12, 3, 16), [shown], CFG) == local(2026, 12, 4, 6)
    busy = [shown, post(2, local(2026, 12, 3, 9))]
    assert scheduler.next_wake(local(2026, 12, 3, 10), busy, CFG) == local(2026, 12, 3, 11)
    assert scheduler.next_wake(local(2026, 12, 3, 21, 30), busy, CFG) == local(2026, 12, 4, 6)
    future = [shown, post(3, local(2026, 12, 3, 9), show_on="2026-12-04")]
    assert scheduler.next_wake(local(2026, 12, 3, 7), future, CFG) == local(2026, 12, 3, 15)
    assert scheduler.next_wake(local(2026, 12, 3, 16), future, CFG) == local(2026, 12, 4, 6)
    assert scheduler.next_wake(local(2026, 12, 3, 5, 55), [shown], CFG) == local(2026, 12, 3, 6, 10)  # min 15 min


def make_client(tmp_path):
    clock = {"now": local(2026, 12, 3, 10)}
    app = create_app(tmp_path, now_fn=lambda: clock["now"], rng=random.Random(1))
    client = TestClient(app)
    token = frame.add_device(db.connect(tmp_path / "atchou.sqlite"), "salon")
    client.headers["Authorization"] = f"Bearer {token}"
    return client, clock


def upload(client, color, **data):
    buf = io.BytesIO()
    Image.new("RGB", (100, 60), color).save(buf, format="PNG")
    r = client.post("/posts", files={"image": ("a.png", buf.getvalue(), "image/png")}, data=data)
    assert r.status_code == 201
    return r.json()["id"]


def test_frame_requires_token(tmp_path):
    client, _ = make_client(tmp_path)
    assert client.get("/frame/image", headers={"Authorization": "Bearer nope"}).status_code == 401
    assert client.get("/frame/image", headers={"Authorization": ""}).status_code == 401


def test_frame_flow(tmp_path):
    client, clock = make_client(tmp_path)
    r = client.get("/frame/image")
    assert r.status_code == 204 and "x-next-wake-at" in r.headers  # nothing posted yet

    upload(client, (255, 0, 0))
    r = client.get("/frame/image", params={"battery": 87, "battery_mv": 3900})
    assert r.status_code == 200 and len(r.content) == 192000
    assert r.headers["x-next-wake-at"] == "2026-12-03T14:00:00Z"  # quiet day: 15:00 Brussels slot
    etag = r.headers["etag"]
    assert client.get("/frame/image", headers={"If-None-Match": etag}).status_code == 304

    # A second poll within the minimum interval does not advance.
    b = upload(client, (0, 0, 255))
    clock["now"] += timedelta(minutes=5)
    assert client.get("/frame/image", headers={"If-None-Match": etag}).status_code == 304

    # Later, the queued post is shown.
    clock["now"] += timedelta(hours=1)
    r = client.get("/frame/image", params={"format": "png"})
    assert r.status_code == 200 and r.headers["content-type"] == "image/png"
    assert Image.open(io.BytesIO(r.content)).convert("RGB").getpixel((0, 0)) == (0, 0, 255)

    # Night: the picture does not change; deleting the current one forces a new pick.
    clock["now"] = local(2026, 12, 3, 23)
    client.delete(f"/posts/{b}")
    r = client.get("/frame/image", params={"format": "png"})
    assert Image.open(io.BytesIO(r.content)).convert("RGB").getpixel((0, 0)) == (255, 0, 0)
