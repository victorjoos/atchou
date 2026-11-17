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
    params TEXT NOT NULL DEFAULT '{}'
);
CREATE TABLE IF NOT EXISTS authors (
    name TEXT PRIMARY KEY,
    last_used TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ','now'))
);
"""


def connect(path: Path) -> sqlite3.Connection:
    conn = sqlite3.connect(path, check_same_thread=False)
    conn.row_factory = sqlite3.Row
    conn.executescript(SCHEMA)
    return conn
