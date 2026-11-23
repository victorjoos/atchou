"""Scheduling settings, all overridable through environment variables."""

import os
import tomllib
from dataclasses import dataclass
from datetime import time
from pathlib import Path
from zoneinfo import ZoneInfo


def _time(value: str) -> time:
    h, _, m = value.strip().partition(":")
    return time(int(h), int(m or 0))


def _csv(value: str) -> tuple[str, ...]:
    return tuple(v.strip() for v in value.split(",") if v.strip())


@dataclass(frozen=True)
class Config:
    # Author suggestions (the children's names).
    children: tuple[str, ...] = ()
    # Google Sign-In is enforced only when a client ID is set (leave empty for local dev).
    google_client_id: str = ""
    allowed_emails: tuple[str, ...] = ()
    session_days: int = 30
    timezone: str = "Europe/Brussels"
    # Quiet-day rotation slots (local time).
    slots: tuple[time, ...] = (time(6, 0), time(15, 0))
    # Items are only shown between these local times; scheduled items start at window_start.
    window_start: time = time(6, 0)
    window_end: time = time(22, 0)
    # Busy day (queued, not yet shown items): wake this often.
    busy_interval_min: int = 60
    min_wake_min: int = 15
    max_wake_min: int = 24 * 60
    # Rotation weight = pin_boost (if pinned) * 0.5 ** (age_days / decay_half_life_days).
    # 365 keeps even year-old items fairly likely; lower it to favor recent items.
    decay_half_life_days: float = 365.0
    pin_boost: float = 3.0

    @property
    def tz(self) -> ZoneInfo:
        return ZoneInfo(self.timezone)

    @property
    def auth_enabled(self) -> bool:
        return bool(self.google_client_id)

    @classmethod
    def load(cls, path: Path | None = None, env=os.environ) -> "Config":
        """Read config.toml (if present), then let ATCHOU_* environment variables override it."""
        raw: dict = {}
        if path is not None and path.is_file():
            raw = tomllib.loads(path.read_text(encoding="utf-8"))
        auth, sched = raw.get("auth", {}), raw.get("schedule", {})
        d = cls()

        def pick(env_key, toml_value, default):
            return env[env_key] if env.get(env_key) else (default if toml_value is None else toml_value)

        slots = pick("ATCHOU_SLOTS", sched.get("slots"), None)
        if isinstance(slots, str):
            slots = slots.split(",")
        return cls(
            children=_csv(env["ATCHOU_KID_NAMES"]) if env.get("ATCHOU_KID_NAMES") else tuple(raw.get("children", d.children)),
            google_client_id=pick("ATCHOU_GOOGLE_CLIENT_ID", auth.get("google_client_id"), d.google_client_id),
            allowed_emails=tuple(e.strip().lower() for e in (_csv(env["ATCHOU_ALLOWED_EMAILS"]) if env.get("ATCHOU_ALLOWED_EMAILS") else auth.get("allowed_emails", d.allowed_emails))),
            session_days=int(auth.get("session_days", d.session_days)),
            timezone=pick("ATCHOU_TIMEZONE", raw.get("timezone"), d.timezone),
            slots=tuple(_time(t) for t in slots) if slots else d.slots,
            window_start=_time(pick("ATCHOU_WINDOW_START", sched.get("window_start"), "06:00")),
            window_end=_time(pick("ATCHOU_WINDOW_END", sched.get("window_end"), "22:00")),
            busy_interval_min=int(pick("ATCHOU_BUSY_INTERVAL_MIN", sched.get("busy_interval_min"), d.busy_interval_min)),
            min_wake_min=int(pick("ATCHOU_MIN_WAKE_MIN", sched.get("min_wake_min"), d.min_wake_min)),
            max_wake_min=int(pick("ATCHOU_MAX_WAKE_MIN", sched.get("max_wake_min"), d.max_wake_min)),
            decay_half_life_days=float(pick("ATCHOU_DECAY_HALF_LIFE_DAYS", sched.get("decay_half_life_days"), d.decay_half_life_days)),
            pin_boost=float(pick("ATCHOU_PIN_BOOST", sched.get("pin_boost"), d.pin_boost)),
        )
