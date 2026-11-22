"""Google Sign-In: verify an ID token once, then issue our own signed session token."""

import base64
import hashlib
import hmac
import json
import os
import secrets
import time
from pathlib import Path
from typing import Callable

from fastapi import APIRouter, Depends, Header, HTTPException
from pydantic import BaseModel

from .config import Config


def verify_google_token(id_token: str, client_id: str) -> dict:
    from google.auth.transport import requests as g_requests
    from google.oauth2 import id_token as g_id_token

    return g_id_token.verify_oauth2_token(id_token, g_requests.Request(), client_id)


def _b64(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode()


def _unb64(text: str) -> bytes:
    return base64.urlsafe_b64decode(text + "=" * (-len(text) % 4))


class Signer:
    """Stateless HMAC-signed tokens. The secret lives in the data dir, so tokens survive restarts."""

    def __init__(self, secret_file: Path):
        if not secret_file.exists():
            secret_file.write_text(secrets.token_hex(32))
            os.chmod(secret_file, 0o600)
        self._key = secret_file.read_text().strip().encode()

    def _sig(self, body: str) -> str:
        return _b64(hmac.new(self._key, body.encode(), hashlib.sha256).digest())

    def issue(self, claims: dict, ttl_s: int, now: float | None = None) -> str:
        body = _b64(json.dumps({**claims, "exp": int((time.time() if now is None else now) + ttl_s)}).encode())
        return f"{body}.{self._sig(body)}"

    def verify(self, token: str, now: float | None = None) -> dict | None:
        body, _, sig = token.partition(".")
        if not body or not hmac.compare_digest(sig, self._sig(body)):
            return None
        try:
            claims = json.loads(_unb64(body))
        except ValueError:
            return None
        return claims if claims.get("exp", 0) > (time.time() if now is None else now) else None


class GoogleLogin(BaseModel):
    id_token: str


def make_auth(cfg: Config, data_dir: Path, verifier: Callable[[str, str], dict] = verify_google_token):
    """Return (router, dependency). The dependency yields the user dict, or None when auth is disabled."""
    signer = Signer(data_dir / "session_secret")
    router = APIRouter(prefix="/auth")

    @router.get("/config")
    def public_config():
        return {"auth_required": cfg.auth_enabled, "google_client_id": cfg.google_client_id or None}

    @router.post("/google")
    def google_login(body: GoogleLogin):
        if not cfg.auth_enabled:
            raise HTTPException(404, "l’authentification est désactivée")
        try:
            claims = verifier(body.id_token, cfg.google_client_id)
        except ValueError:
            raise HTTPException(401, "jeton Google invalide")
        email = str(claims.get("email", "")).lower()
        if not claims.get("email_verified") or email not in cfg.allowed_emails:
            raise HTTPException(403, "ce compte n’est pas autorisé")
        name = claims.get("name") or email
        ttl = cfg.session_days * 86400
        return {"token": signer.issue({"email": email, "name": name}, ttl), "email": email, "name": name,
                "expires_in": ttl}

    def current_user(authorization: str | None = Header(None)):
        if not cfg.auth_enabled:
            return None
        scheme, _, token = (authorization or "").partition(" ")
        claims = signer.verify(token) if scheme.lower() == "bearer" else None
        # Re-check the allowlist so removing an email takes effect immediately.
        if claims is None or claims["email"] not in cfg.allowed_emails:
            raise HTTPException(401, "connexion requise")
        return claims

    return router, current_user
