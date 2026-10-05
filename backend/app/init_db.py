"""数据库初始化与种子数据。"""
from . import config, database, security
from .utils import new_id, now_iso


def seed_seller() -> None:
    """写入唯一固定卖家账号（无注册流程）。"""
    with database.transaction() as conn:
        if conn.execute("SELECT id FROM seller LIMIT 1").fetchone():
            return
        conn.execute(
            "INSERT INTO seller (id, username, password_hash, created_at) VALUES (?, ?, ?, ?)",
            (
                new_id(),
                config.SELLER_USERNAME,
                security.hash_password(config.SELLER_PASSWORD),
                now_iso(),
            ),
        )


def init_db() -> None:
    config.ensure_dirs()
    database.execute_script(security.load_schema_sql())
    seed_seller()
