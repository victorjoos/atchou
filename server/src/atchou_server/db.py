import sqlite3
from pathlib import Path

SCHEMA = """
CREATE TABLE IF NOT EXISTS posts (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    author TEXT NOT NULL DEFAULT '',
    sender TEXT NOT NULL DEFAULT '',
    created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ','now')),
    show_on TEXT,
    pinned INTEGER NOT NULL DEFAULT 0,
    exclude_from_rotation INTEGER NOT NULL DEFAULT 0,
    show_author INTEGER NOT NULL DEFAULT 0,
    params TEXT NOT NULL DEFAULT '{}',
    shown_at TEXT
);
CREATE TABLE IF NOT EXISTS authors (
    name TEXT PRIMARY KEY,
    last_used TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ','now'))
);
CREATE TABLE IF NOT EXISTS devices (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    token_hash TEXT NOT NULL UNIQUE,
    created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ','now')),
    current_post_id INTEGER,
    advanced_at TEXT,
    last_seen TEXT,
    battery_pct REAL,
    battery_mv INTEGER
);
"""


def connect(path: Path) -> sqlite3.Connection:
    conn = sqlite3.connect(path, check_same_thread=False)
    conn.row_factory = sqlite3.Row
    conn.executescript(SCHEMA)
    columns = {r["name"] for r in conn.execute("PRAGMA table_info(posts)")}
    if "shown_at" not in columns:
        conn.execute("ALTER TABLE posts ADD COLUMN shown_at TEXT")
        conn.commit()
    return conn
