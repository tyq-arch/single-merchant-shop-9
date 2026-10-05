"""口令码数据访问助手（口令码生命周期）。"""
from datetime import timedelta

from .. import config, security
from ..enums import CodeStatus
from ..utils import now, now_iso, iso, parse_iso


def _code_exists(conn):
    def _exists(code: str) -> bool:
        return conn.execute("SELECT 1 FROM command_code WHERE code = ?", (code,)).fetchone() is not None

    return _exists


def create_code_for_order(conn, order_id: str, product_id: str) -> str:
    """为一笔意向生成全局唯一口令码并置为生效。"""
    code = security.unique_command_code(_code_exists(conn))
    expires_at = iso(now() + timedelta(days=config.COMMAND_CODE_TTL_DAYS))
    conn.execute(
        "INSERT INTO command_code (code, order_id, product_id, status, created_at, expires_at) "
        "VALUES (?, ?, ?, ?, ?, ?)",
        (code, order_id, product_id, CodeStatus.ACTIVE, now_iso(), expires_at),
    )
    return code


def find_active_code(conn, code: str):
    """返回有效口令码记录；不存在 / 已失效 / 已过期返回 None。"""
    row = conn.execute("SELECT * FROM command_code WHERE code = ?", (code,)).fetchone()
    if row is None or row["status"] != CodeStatus.ACTIVE:
        return None
    expires_at = parse_iso(row["expires_at"])
    if expires_at is not None and expires_at < now():
        return None
    return row


def invalidate_order_code(conn, order_id: str) -> None:
    """交易成功/失败/撤销后，令该订单的口令码失效。"""
    conn.execute(
        "UPDATE command_code SET status = ? WHERE order_id = ? AND status = ?",
        (CodeStatus.INVALID, order_id, CodeStatus.ACTIVE),
    )


def invalidate_product_codes(conn, product_id: str) -> None:
    """商品下架时，令关联的全部口令码失效。"""
    conn.execute(
        "UPDATE command_code SET status = ? WHERE product_id = ? AND status = ?",
        (CodeStatus.INVALID, product_id, CodeStatus.ACTIVE),
    )
