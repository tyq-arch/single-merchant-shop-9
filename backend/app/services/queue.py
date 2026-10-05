"""排队队列数据访问助手：严格先到先得。"""
from ..utils import new_id


def enqueue(conn, product_id: str, order_id: str, submitted_at: str) -> int:
    """入队并返回分配到的排队序号（队尾）。"""
    row = conn.execute(
        "SELECT COALESCE(MAX(position), 0) AS max_pos FROM queue_item WHERE product_id = ?",
        (product_id,),
    ).fetchone()
    position = int(row["max_pos"]) + 1
    conn.execute(
        "INSERT INTO queue_item (id, product_id, order_id, position, submitted_at) VALUES (?, ?, ?, ?, ?)",
        (new_id(), product_id, order_id, position, submitted_at),
    )
    return position


def remove(conn, product_id: str, order_id: str) -> None:
    conn.execute(
        "DELETE FROM queue_item WHERE product_id = ? AND order_id = ?", (product_id, order_id)
    )


def renumber(conn, product_id: str) -> None:
    """移除或递补后重排序号，保证 1..n 连续、顺序不变。"""
    rows = conn.execute(
        "SELECT id FROM queue_item WHERE product_id = ? ORDER BY position ASC, submitted_at ASC",
        (product_id,),
    ).fetchall()
    for index, row in enumerate(rows, start=1):
        conn.execute("UPDATE queue_item SET position = ? WHERE id = ?", (index, row["id"]))


def head(conn, product_id: str):
    """队首（排队序号最小）。"""
    return conn.execute(
        "SELECT * FROM queue_item WHERE product_id = ? ORDER BY position ASC LIMIT 1",
        (product_id,),
    ).fetchone()


def position_of(conn, product_id: str, order_id: str):
    row = conn.execute(
        "SELECT position FROM queue_item WHERE product_id = ? AND order_id = ?",
        (product_id, order_id),
    ).fetchone()
    return row["position"] if row else None


def list_queue(conn, product_id: str):
    return conn.execute(
        "SELECT * FROM queue_item WHERE product_id = ? ORDER BY position ASC", (product_id,)
    ).fetchall()
