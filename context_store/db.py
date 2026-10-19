# -*- coding: utf-8 -*-
"""
上下文存储：MySQL 连接与表结构初始化。
所有表以 ctx_ 前缀建在 farming_harness 库（可通过环境变量覆盖）。
"""
import os
import pathlib

import pymysql
from pymysql.cursors import DictCursor

BASE_DIR = pathlib.Path(__file__).resolve().parent
SCHEMA_FILE = BASE_DIR / "schema.sql"

DB_CONFIG = {
    "host": os.environ.get("CTX_DB_HOST", "127.0.0.1"),
    "port": int(os.environ.get("CTX_DB_PORT", "3306")),
    "user": os.environ.get("CTX_DB_USER", "root"),
    "password": os.environ.get("CTX_DB_PASSWORD", os.environ.get("DB_PASSWORD", "")),
    "database": os.environ.get("CTX_DB_NAME", "farming_harness"),
    "charset": "utf8mb4",
    "autocommit": True,
}

_conn = None


def connect():
    """获取（惰性建立的）全局连接。"""
    global _conn
    if _conn is None or not _conn.open:
        _conn = pymysql.connect(cursorclass=DictCursor, **DB_CONFIG)
    return _conn


def close():
    global _conn
    if _conn is not None:
        try:
            _conn.close()
        except Exception:
            pass
        _conn = None


def query(sql, params=None):
    """查询多行。"""
    with connect().cursor() as cur:
        cur.execute(sql, params)
        return list(cur.fetchall())


def query_one(sql, params=None):
    rows = query(sql, params)
    return rows[0] if rows else None


def execute(sql, params=None):
    """执行写操作，返回影响行数（autocommit 模式）。"""
    with connect().cursor() as cur:
        return cur.execute(sql, params)


def execute_returning_id(sql, params=None):
    with connect().cursor() as cur:
        cur.execute(sql, params)
        return cur.lastrowid


def ensure_schema():
    """执行 schema.sql 中全部 CREATE TABLE IF NOT EXISTS。"""
    text = SCHEMA_FILE.read_text(encoding="utf-8")
    statements = []
    buf = []
    for line in text.splitlines():
        stripped = line.strip()
        if stripped.startswith("--") or not stripped:
            continue
        buf.append(line)
        if stripped.endswith(";"):
            statements.append("\n".join(buf).rstrip(";"))
            buf = []
    conn = connect()
    with conn.cursor() as cur:
        for stmt in statements:
            cur.execute(stmt)
    return len(statements)


def tables():
    rows = query(
        "SELECT table_name AS name FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name LIKE 'ctx_%' ORDER BY table_name"
    )
    return [r["name"] for r in rows]
