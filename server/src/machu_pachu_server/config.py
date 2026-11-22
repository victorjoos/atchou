"""Scheduling settings, all overridable through environment variables."""

import os
from dataclasses import dataclass
from datetime import time
from zoneinfo import ZoneInfo


def _time(value: str) -> time:
    h, _, m = value.strip().partition(":")
    return time(int(h), int(m or 0))


@dataclass(frozen=True)
class Config:
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

    @classmethod
    def from_env(cls, env=os.environ) -> "Config":
        d = cls()
        return cls(
            timezone=env.get("MACHU_TIMEZONE", d.timezone),
            slots=tuple(_time(s) for s in env["MACHU_SLOTS"].split(",")) if env.get("MACHU_SLOTS") else d.slots,
            window_start=_time(env["MACHU_WINDOW_START"]) if env.get("MACHU_WINDOW_START") else d.window_start,
            window_end=_time(env["MACHU_WINDOW_END"]) if env.get("MACHU_WINDOW_END") else d.window_end,
            busy_interval_min=int(env.get("MACHU_BUSY_INTERVAL_MIN", d.busy_interval_min)),
            min_wake_min=int(env.get("MACHU_MIN_WAKE_MIN", d.min_wake_min)),
            max_wake_min=int(env.get("MACHU_MAX_WAKE_MIN", d.max_wake_min)),
            decay_half_life_days=float(env.get("MACHU_DECAY_HALF_LIFE_DAYS", d.decay_half_life_days)),
            pin_boost=float(env.get("MACHU_PIN_BOOST", d.pin_boost)),
        )
