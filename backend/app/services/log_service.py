"""操作日志（FR-042 / NFR-010）。"""
from ..utils import new_id, now_iso


def log_action(conn, seller_id: str | None, action: str, detail: str | None = None) -> None:
    conn.execute(
        "INSERT INTO operation_log (id, seller_id, action, detail, created_at) VALUES (?, ?, ?, ?, ?)",
        (new_id(), seller_id, action, detail, now_iso()),
    )


def list_logs(conn, page: int, page_size: int) -> tuple[list, int]:
    total = conn.execute("SELECT COUNT(*) AS c FROM operation_log").fetchone()["c"]
    rows = conn.execute(
        "SELECT * FROM operation_log ORDER BY created_at DESC, rowid DESC LIMIT ? OFFSET ?",
        (page_size, (page - 1) * page_size),
    ).fetchall()
    return [dict(r) for r in rows], total
