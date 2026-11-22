import io

import pytest
from fastapi.testclient import TestClient
from PIL import Image

from machu_pachu_server import imaging
from machu_pachu_server.app import create_app


@pytest.fixture
def client(tmp_path):
    return TestClient(create_app(tmp_path))


def photo(w=1200, h=900) -> bytes:
    img = Image.new("RGB", (w, h))
    for x in range(w):
        for y in range(0, h, 10):
            img.putpixel((x, y), (x * 255 // w, y * 255 // h, 128))
    buf = io.BytesIO()
    img.save(buf, format="JPEG")
    return buf.getvalue()


def test_render_uses_only_palette_colors():
    out = imaging.render(imaging.load_image(photo()), imaging.EditParams(author="Emma", show_author=True))
    assert out.size == (800, 480)
    allowed = {rgb for _, rgb, _ in imaging.PALETTE}
    assert {c for _, c in out.convert("RGB").getcolors()} <= allowed
    assert len(imaging.to_panel_buffer(out)) == 800 * 480 // 2


def test_drawing_mode_whitens_gray_paper():
    paper = Image.new("RGB", (1000, 600), (190, 185, 175))
    paper.paste((20, 20, 20), (100, 100, 500, 140))
    out = imaging.render(paper, imaging.EditParams(mode="drawing")).convert("RGB")
    assert out.getpixel((790, 470)) == (255, 255, 255)
    assert out.getpixel((300, 95)) == (0, 0, 0)


def test_post_lifecycle(client):
    r = client.post("/posts", files={"image": ("a.jpg", photo(), "image/jpeg")},
                    data={"author": "Emma", "show_author": "true", "show_on": "2026-12-01"})
    assert r.status_code == 201
    post = r.json()
    assert post["author"] == "Emma" and post["show_author"] is True

    img = client.get(post["image_url"])
    assert Image.open(io.BytesIO(img.content)).size == (800, 480)

    assert client.patch(f"/posts/{post['id']}", json={"pinned": True}).json()["pinned"] is True
    assert [p["id"] for p in client.get("/posts").json()] == [post["id"]]
    assert "Emma" in client.get("/authors").json()

    assert client.delete(f"/posts/{post['id']}").status_code == 204
    assert client.get(f"/posts/{post['id']}").status_code == 404


def test_preview_and_validation(client):
    f = {"image": ("a.jpg", photo(), "image/jpeg")}
    assert client.post("/preview", files=f, data={"crop": "0,0,0.5,0.5", "rotate": "90"}).status_code == 200
    assert client.post("/preview", files=f, data={"rotate": "45"}).status_code == 422
    assert client.post("/preview", files={"image": ("a.txt", b"nope", "text/plain")}).status_code == 400
    assert client.post("/posts", files=f, data={"show_on": "bad"}).status_code == 422
