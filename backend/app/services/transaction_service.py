"""交易管理：进入交易（自动冻结）、标记成败、失败者作废/重新排队、自动递补。"""
from ..enums import OrderStatus, ProductStatus, TxResult
from ..errors import Conflict, NotFound
from ..utils import new_id, now_iso
from . import codes, log_service, queue
from . import product_service as ps
from ..serializers import order_to_dict, product_to_dict


def _record(conn, order_id: str, product_id: str, result: str, note: str | None) -> None:
    conn.execute(
        "INSERT INTO transaction_record (id, order_id, product_id, result, note, created_at) "
        "VALUES (?, ?, ?, ?, ?, ?)",
        (new_id(), order_id, product_id, result, note, now_iso()),
    )


def start_transaction(conn, seller_id: str, product_id: str) -> dict:
    """卖家与队列第一位进入交易：商品自动冻结、队列封口。"""
    product = ps.get_product(conn, product_id)
    if not product:
        raise NotFound("商品不存在")
    if product["status"] == ProductStatus.OFF_SHELF:
        raise Conflict("商品已下架，无法进入交易")
    in_tx = conn.execute(
        "SELECT 1 FROM order_intent WHERE product_id = ? AND status = ? LIMIT 1",
        (product_id, OrderStatus.IN_TRANSACTION),
    ).fetchone()
    if in_tx:
        raise Conflict("已有进行中的交易，请先标记交易结果")
    head = queue.head(conn, product_id)
    if not head:
        raise Conflict("当前队列为空，没有可交易的意向购买人")

    order_id = head["order_id"]
    ts = now_iso()
    conn.execute(
        "UPDATE order_intent SET status = ?, queue_position = 1, updated_at = ? WHERE id = ?",
        (OrderStatus.IN_TRANSACTION, ts, order_id),
    )
    conn.execute(
        "UPDATE product SET status = ?, updated_at = ? WHERE id = ?",
        (ProductStatus.FROZEN, ts, product_id),
    )
    log_service.log_action(conn, seller_id, "START_TRANSACTION",
                           f"与队列第 1 位进入交易，订单 {order_id}，商品自动冻结")
    return {
        "order": order_to_dict(ps_order(conn, order_id)),
        "product": product_to_dict(ps.get_product(conn, product_id)),
        "message": "已与队列第一位进入交易，商品自动冻结，队列已封口",
    }


def ps_order(conn, order_id: str):
    return conn.execute("SELECT * FROM order_intent WHERE id = ?", (order_id,)).fetchone()


def mark_transaction(conn, seller_id: str, order_id: str, result: str, note: str | None) -> dict:
    order = ps_order(conn, order_id)
    if not order:
        raise NotFound("意向单不存在")
    if order["status"] != OrderStatus.IN_TRANSACTION:
        raise Conflict("仅进行中的交易可以标记结果")
    product_id = order["product_id"]
    ts = now_iso()
    _record(conn, order_id, product_id, result, note)
    codes.invalidate_order_code(conn, order_id)
    queue.remove(conn, product_id, order_id)

    if result == TxResult.SUCCESS:
        # 交易成功 -> 商品下架，口令码失效，进入历史记录
        conn.execute(
            "UPDATE order_intent SET status = ?, updated_at = ? WHERE id = ?",
            (OrderStatus.SUCCESS, ts, order_id),
        )
        conn.execute(
            "UPDATE order_intent SET status = ?, updated_at = ? "
            "WHERE product_id = ? AND status IN (?, ?, ?)",
            (OrderStatus.VOIDED, ts, product_id,
             OrderStatus.QUEUED, OrderStatus.REQUEUED, OrderStatus.IN_TRANSACTION),
        )
        conn.execute("DELETE FROM queue_item WHERE product_id = ?", (product_id,))
        codes.invalidate_product_codes(conn, product_id)
        conn.execute(
            "UPDATE product SET status = ?, updated_at = ? WHERE id = ?",
            (ProductStatus.OFF_SHELF, ts, product_id),
        )
        log_service.log_action(conn, seller_id, "TRANSACTION_SUCCESS",
                               f"订单 {order_id} 交易成功，商品下架，口令码失效")
        message = "交易成功，商品已下架并进入历史记录，关联口令码全部失效"
    else:
        # 交易失败 -> 该失败者口令码失效，队列有人则自动递补并再次冻结，否则恢复在售
        conn.execute(
            "UPDATE order_intent SET status = ?, updated_at = ? WHERE id = ?",
            (OrderStatus.FAILED, ts, order_id),
        )
        nxt = queue.head(conn, product_id)
        if nxt:
            queue.renumber(conn, product_id)
            conn.execute(
                "UPDATE order_intent SET status = ?, queue_position = 1, updated_at = ? WHERE id = ?",
                (OrderStatus.IN_TRANSACTION, ts, nxt["order_id"]),
            )
            conn.execute(
                "UPDATE product SET status = ?, updated_at = ? WHERE id = ?",
                (ProductStatus.FROZEN, ts, product_id),
            )
            message = "交易失败，已自动让队列下一位递补进入交易，商品再次冻结"
        else:
            conn.execute("DELETE FROM queue_item WHERE product_id = ?", (product_id,))
            conn.execute(
                "UPDATE product SET status = ?, updated_at = ? WHERE id = ?",
                (ProductStatus.ON_SALE, ts, product_id),
            )
            message = "交易失败，队列已空，商品恢复在售并继续接收意向"
        log_service.log_action(conn, seller_id, "TRANSACTION_FAILED",
                               f"订单 {order_id} 交易失败：{message}")

    return {
        "order": order_to_dict(ps_order(conn, order_id)),
        "product": product_to_dict(ps.get_product(conn, product_id)),
        "message": message,
    }


def void_order(conn, seller_id: str, order_id: str) -> dict:
    order = ps_order(conn, order_id)
    if not order:
        raise NotFound("意向单不存在")
    if order["status"] != OrderStatus.FAILED:
        raise Conflict("仅交易失败的意向可以作废")
    conn.execute(
        "UPDATE order_intent SET status = ?, updated_at = ? WHERE id = ?",
        (OrderStatus.VOIDED, now_iso(), order_id),
    )
    log_service.log_action(conn, seller_id, "VOID_ORDER", f"作废交易失败的意向：订单 {order_id}")
    return {"order": order_to_dict(ps_order(conn, order_id)), "message": "该买家意向已作废"}


def requeue_order(conn, seller_id: str, order_id: str) -> dict:
    """失败者重新排队：按需求状态机，放到队尾、更新提交时间、最多 1 次。"""
    order = ps_order(conn, order_id)
    if not order:
        raise NotFound("意向单不存在")
    if order["status"] != OrderStatus.FAILED:
        raise Conflict("仅交易失败的意向可以重新排队")
    if order["requeue_count"] >= 1:
        raise Conflict("该意向已重新排队过一次，不可再次排队")
    product_id = order["product_id"]
    product = ps.get_product(conn, product_id)
    if not product or product["status"] == ProductStatus.OFF_SHELF:
        raise Conflict("商品已下架，无法重新排队")

    ts = now_iso()
    position = queue.enqueue(conn, product_id, order_id, ts)
    # 需求 TBD-03：交易失败后口令码已失效，重新排队时生成新口令码以便买家继续凭码查询
    new_code = codes.create_code_for_order(conn, order_id, product_id)
    conn.execute(
        "UPDATE order_intent SET status = ?, requeue_count = requeue_count + 1, "
        "queue_position = ?, command_code = ?, updated_at = ? WHERE id = ?",
        (OrderStatus.REQUEUED, position, new_code, ts, order_id),
    )
    log_service.log_action(conn, seller_id, "REQUEUE_ORDER",
                           f"失败者重新排队：订单 {order_id}，新排队序号 {position}")
    return {
        "order": order_to_dict(ps_order(conn, order_id)),
        "command_code": new_code,
        "queue_position": position,
        "message": "该买家已放回队尾重新排队，并生成新的口令码",
    }
