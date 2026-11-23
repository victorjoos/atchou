"""Pure scheduling logic: day queue, rotation pool and next wake time."""

import random
from datetime import date, datetime, timedelta, timezone

from .config import Config


def parse_utc(value: str) -> datetime:
    return datetime.strptime(value, "%Y-%m-%dT%H:%M:%SZ").replace(tzinfo=timezone.utc)


def effective_day(post: dict, cfg: Config) -> date:
    """Day the post is meant to be shown (default: the local day it was sent)."""
    if post.get("show_on"):
        return date.fromisoformat(post["show_on"])
    return parse_utc(post["created_at"]).astimezone(cfg.tz).date()


def in_window(now: datetime, cfg: Config) -> bool:
    t = now.astimezone(cfg.tz).time()
    return cfg.window_start <= t < cfg.window_end


def queued(posts: list[dict], now: datetime, cfg: Config) -> list[dict]:
    """Not-yet-shown posts due today or earlier (missed days carry over), today's first, newest first."""
    today = now.astimezone(cfg.tz).date()
    due = [p for p in posts if not p["shown_at"] and effective_day(p, cfg) <= today]
    return sorted(due, key=lambda p: (effective_day(p, cfg), p["id"]), reverse=True)


def weight(post: dict, now: datetime, cfg: Config) -> float:
    age_days = max(0.0, (now.astimezone(cfg.tz).date() - effective_day(post, cfg)).days)
    w = 0.5 ** (age_days / cfg.decay_half_life_days)
    return w * cfg.pin_boost if post["pinned"] else w


def rotation_pick(posts: list[dict], current_id: int | None, now: datetime, cfg: Config, rng: random.Random) -> dict | None:
    pool = [p for p in posts if p["shown_at"] and not p["exclude_from_rotation"]]
    if len(pool) > 1:
        pool = [p for p in pool if p["id"] != current_id]
    if not pool:
        return None
    return rng.choices(pool, weights=[weight(p, now, cfg) for p in pool])[0]


def pick_next(posts: list[dict], current_id: int | None, now: datetime, cfg: Config, rng: random.Random) -> dict | None:
    q = queued(posts, now, cfg)
    return q[0] if q else rotation_pick(posts, current_id, now, cfg, rng)


def _at(day: date, t, cfg: Config) -> datetime:
    return datetime.combine(day, t, tzinfo=cfg.tz).astimezone(timezone.utc)


def next_wake(now: datetime, posts: list[dict], cfg: Config) -> datetime:
    """When the frame should wake next, clamped to [min_wake, max_wake] from now."""
    local_today = now.astimezone(cfg.tz).date()
    candidates: list[datetime] = []

    if queued(posts, now, cfg):
        busy = now + timedelta(minutes=cfg.busy_interval_min)
        local = busy.astimezone(cfg.tz)
        if local.time() >= cfg.window_end:
            busy = _at(local.date() + timedelta(days=1), cfg.window_start, cfg)
        elif local.time() < cfg.window_start:
            busy = _at(local.date(), cfg.window_start, cfg)
        candidates.append(busy)
    else:
        slots = [_at(local_today + timedelta(days=d), s, cfg) for d in (0, 1) for s in sorted(cfg.slots)]
        candidates.append(next(s for s in slots if s > now))

    # A post scheduled for a future day appears as soon as that day's window opens.
    future = [effective_day(p, cfg) for p in posts if not p["shown_at"] and effective_day(p, cfg) > local_today]
    if future:
        candidates.append(_at(min(future), cfg.window_start, cfg))

    wake = min(candidates)
    return min(max(wake, now + timedelta(minutes=cfg.min_wake_min)), now + timedelta(minutes=cfg.max_wake_min))
