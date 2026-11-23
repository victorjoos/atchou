import io

from fastapi.testclient import TestClient
from PIL import Image

from atchou_server import auth
from atchou_server.app import create_app
from atchou_server.config import Config

CLAIMS = {"alice-token": {"email": "Alice@Example.com", "email_verified": True, "name": "Alice"},
          "bob-token": {"email": "bob@example.com", "email_verified": True, "name": "Bob"},
          "unverified": {"email": "alice@example.com", "email_verified": False}}


def fake_verifier(token, client_id):
    assert client_id == "cid"
    if token not in CLAIMS:
        raise ValueError("bad token")
    return CLAIMS[token]


def make(tmp_path):
    cfg = Config(google_client_id="cid", allowed_emails=("alice@example.com",), children=("Emma",))
    return TestClient(create_app(tmp_path, config=cfg, google_verifier=fake_verifier))


def login(client, token):
    return client.post("/auth/google", json={"id_token": token})


def test_endpoints_require_login_and_allowlist(tmp_path):
    c = make(tmp_path)
    assert c.get("/auth/config").json() == {"auth_required": True, "google_client_id": "cid"}
    assert c.get("/posts").status_code == 401
    assert c.get("/posts", headers={"Authorization": "Bearer junk"}).status_code == 401
    assert login(c, "bad").status_code == 401
    assert login(c, "bob-token").status_code == 403
    assert login(c, "unverified").status_code == 403

    r = login(c, "alice-token")
    assert r.status_code == 200 and r.json()["name"] == "Alice"
    h = {"Authorization": f"Bearer {r.json()['token']}"}
    assert c.get("/posts", headers=h).status_code == 200
    assert c.get("/authors", headers=h).json() == ["Emma"]
    # Frame endpoints stay on device tokens only.
    assert c.get("/frame/image").status_code == 401


def test_sender_comes_from_account_and_tokens_expire(tmp_path):
    c = make(tmp_path)
    h = {"Authorization": f"Bearer {login(c, 'alice-token').json()['token']}"}
    c.headers.update(h)
    buf = io.BytesIO()
    Image.new("RGB", (100, 60), (255, 0, 0)).save(buf, format="PNG")
    r = c.post("/posts", files={"image": ("a.png", buf.getvalue(), "image/png")}, data={"sender": "forged", "author": "Emma"})
    post_id = r.json()["id"]
    assert c.get(f"/posts/{post_id}").json()["sender"] == "Alice"

    signer = auth.Signer(tmp_path / "session_secret")
    expired = signer.issue({"email": "alice@example.com", "name": "Alice"}, ttl_s=10, now=0)
    assert c.get("/posts", headers={"Authorization": f"Bearer {expired}"}).status_code == 401


def test_auth_disabled_without_client_id(tmp_path):
    c = TestClient(create_app(tmp_path, config=Config()))
    assert c.get("/posts").status_code == 200
    assert c.get("/auth/config").json()["auth_required"] is False
