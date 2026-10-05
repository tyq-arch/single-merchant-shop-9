"""买家意向：提交、凭口令码查询/修改/撤销，以及卖家查看意向购买人。"""
import csv
import io

from ..enums import OrderStatus, ProductStatus
from ..errors import Conflict, InvalidCode, NotFound
from ..utils import new_id, now_iso
from . import codes, queue
from ..serializers import order_to_dict

# 卖家查看列表时，终结状态或已下架商品的买家信息需脱敏（FR-019 / NFR-003）


def get_order(conn, order_id: str):
    return conn.execute("SELECT * FROM order_intent WHERE id = ?", (order_id,)).fetchone()


def _position_for(conn, order) -> int | None:
    if order["status"] in OrderStatus.WAITING:
        return queue.position_of(conn, order["product_id"], order["id"])
    if order["status"] == OrderStatus.IN_TRANSACTION:
        return 1
    return None


def submit_intent(conn, product_id: str, buyer_name: str, buyer_phone: str) -> dict:
    product = conn.execute("SELECT * FROM product WHERE id = ?", (product_id,)).fetchone()
    if not product:
        raise NotFound("商品不存在")
    if product["status"] == ProductStatus.OFF_SHELF:
        raise Conflict("商品已下架，无法提交购买意向")
    if product["status"] == ProductStatus.FROZEN:
        raise Conflict("商品交易中，暂停接收新意向")

    order_id = new_id()
    ts = now_iso()
    conn.execute(
        "INSERT INTO order_intent ("
        "id, product_id, snapshot_name, snapshot_description, snapshot_image_url, snapshot_price_cents,"
        "buyer_name, buyer_phone, status, queue_position, requeue_count, command_code, created_at, updated_at"
        ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
        (
            order_id, product_id, product["name"], product["description"], product["image_url"],
            product["price_cents"], buyer_name, buyer_phone, OrderStatus.QUEUED, None, 0, None, ts, ts,
        ),
    )
    position = queue.enqueue(conn, product_id, order_id, ts)
    code = codes.create_code_for_order(conn, order_id, product_id)
    conn.execute(
        "UPDATE order_intent SET queue_position = ?, command_code = ?, updated_at = ? WHERE id = ?",
        (position, code, ts, order_id),
    )
    order = get_order(conn, order_id)
    return {
        "order": order_to_dict(order),
        "command_code": code,
        "queue_position": position,
        "notice": "请务必自行保存口令码，这是查询、修改、撤销该意向的唯一凭证，丢失无法找回。",
    }


def query_by_code(conn, code: str) -> dict:
    code_row = codes.find_active_code(conn, code)
    if not code_row:
        raise InvalidCode()
    order = get_order(conn, code_row["order_id"])
    if not order:
        raise InvalidCode()
    product = conn.execute("SELECT * FROM product WHERE id = ?", (order["product_id"],)).fetchone()
    return {
        "order": order_to_dict(order),
        "position": _position_for(conn, order),
        "can_edit": order["status"] in OrderStatus.BUYER_EDITABLE,
        "can_cancel": order["status"] in OrderStatus.BUYER_EDITABLE,
        "product_status": product["status"] if product else None,
    }


def update_by_code(conn, code: str, buyer_name: str | None, buyer_phone: str | None) -> dict:
    code_row = codes.find_active_code(conn, code)
    if not code_row:
        raise InvalidCode()
    order = get_order(conn, code_row["order_id"])
    if not order or order["status"] not in OrderStatus.BUYER_EDITABLE:
        raise Conflict("当前状态不可修改信息（仅排队中可修改）")
    new_name = buyer_name if buyer_name is not None else order["buyer_name"]
    new_phone = buyer_phone if buyer_phone is not None else order["buyer_phone"]
    # 修改信息不改变排队位次，不重新排队
    conn.execute(
        "UPDATE order_intent SET buyer_name = ?, buyer_phone = ?, updated_at = ? WHERE id = ?",
        (new_name, new_phone, now_iso(), order["id"]),
    )
    return order_to_dict(get_order(conn, order["id"]))


def cancel_by_code(conn, code: str) -> dict:
    code_row = codes.find_active_code(conn, code)
    if not code_row:
        raise InvalidCode()
    order = get_order(conn, code_row["order_id"])
    if not order or order["status"] not in OrderStatus.BUYER_EDITABLE:
        raise Conflict("当前状态不可撤销（仅排队中可撤销）")
    product_id = order["product_id"]
    queue.remove(conn, product_id, order["id"])
    queue.renumber(conn, product_id)
    conn.execute(
        "UPDATE order_intent SET status = ?, queue_position = NULL, updated_at = ? WHERE id = ?",
        (OrderStatus.CANCELLED, now_iso(), order["id"]),
    )
    codes.invalidate_order_code(conn, order["id"])
    return {"order_id": order["id"], "status": OrderStatus.CANCELLED, "message": "意向已撤销，队列已自动补位"}


def _should_mask(product_status: str, order_status: str) -> bool:
    return product_status == ProductStatus.OFF_SHELF or order_status in OrderStatus.TERMINAL


def list_intents(conn, product_id: str, page: int, page_size: int,
                 status: str | None = None) -> dict:
    product = conn.execute("SELECT * FROM product WHERE id = ?", (product_id,)).fetchone()
    if not product:
        raise NotFound("商品不存在")
    where = "product_id = ?"
    params: list = [product_id]
    if status:
        where += " AND status = ?"
        params.append(status)
    total = conn.execute(
        f"SELECT COUNT(*) AS c FROM order_intent WHERE {where}", params
    ).fetchone()["c"]
    rows = conn.execute(
        f"SELECT * FROM order_intent WHERE {where} ORDER BY created_at ASC, rowid ASC LIMIT ? OFFSET ?",
        params + [page_size, (page - 1) * page_size],
    ).fetchall()
    items = []
    for row in rows:
        mask = _should_mask(product["status"], row["status"])
        item = order_to_dict(row, mask=mask)
        item["queue_position"] = _position_for(conn, row)
        item["submitted_at"] = row["created_at"]
        items.append(item)
    return {"items": items, "total": total, "page": page, "page_size": page_size}


def export_intents_csv(conn, product_id: str) -> str:
    product = conn.execute("SELECT * FROM product WHERE id = ?", (product_id,)).fetchone()
    if not product:
        raise NotFound("商品不存在")
    rows = conn.execute(
        "SELECT * FROM order_intent WHERE product_id = ? ORDER BY created_at ASC, rowid ASC",
        (product_id,),
    ).fetchall()
    buffer = io.StringIO()
    writer = csv.writer(buffer)
    writer.writerow(["姓名", "联系电话", "提交时间", "排队序号", "状态"])
    for row in rows:
        mask = _should_mask(product["status"], row["status"])
        item = order_to_dict(row, mask=mask)
        position = _position_for(conn, row)
        writer.writerow([
            item["buyer_name"], item["buyer_phone"], row["created_at"],
            position if position is not None else "", item["status_label"],
        ])
    return "\ufeff" + buffer.getvalue()
