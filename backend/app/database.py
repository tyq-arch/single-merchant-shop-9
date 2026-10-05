"""SQLite 数据库访问层。

并发策略（NFR-013 / NFR-014）：
- 读操作使用独立连接，开启 WAL 模式，读写互不阻塞；
- 写操作统一走 transaction()，内部用全局可重入锁 + BEGIN IMMEDIATE，
  保证排队、冻结、递补在并发下严格串行，先到先得不被破坏。
"""
import sqlite3
import threading
from contextlib import contextmanager

from . import config


def connect() -> sqlite3.Connection:
    conn = sqlite3.connect(
        str(config.DB_PATH),
        check_same_thread=False,
        timeout=30,
        isolation_level=None,  # 关闭隐式事务，由我们显式控制 BEGIN/COMMIT
    )
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA foreign_keys = ON")
    conn.execute("PRAGMA busy_timeout = 30000")
    conn.execute("PRAGMA journal_mode = WAL")
    return conn


@contextmanager
def get_conn():
    """只读 / 自动提交连接，用完即关。"""
    conn = connect()
    try:
        yield conn
    finally:
        conn.close()


_write_lock = threading.RLock()


@contextmanager
def transaction():
    """串行化写事务，保证业务状态机变更的原子性与顺序一致性。"""
    with _write_lock:
        conn = connect()
        try:
            conn.execute("BEGIN IMMEDIATE")
            yield conn
            conn.commit()
        except Exception:
            conn.rollback()
            raise
        finally:
            conn.close()


def execute_script(sql: str) -> None:
    with get_conn() as conn:
        conn.executescript(sql)
