"""商品管理：发布、下架、冻结/解冻、历史查询。"""
from ..enums import OrderStatus, ProductStatus
from ..errors import Conflict, NotFound
from ..utils import new_id, now_iso
from . import codes, log_service
from ..serializers import order_to_dict, product_to_dict, transaction_to_dict

ACTIVE_STATUSES = (ProductStatus.ON_SALE, ProductStatus.FROZEN)


def get_product(conn, product_id: str):
    return conn.execute("SELECT * FROM product WHERE id = ?", (product_id,)).fetchone()


def get_active_product(conn):
    """当前唯一在售/交易中商品。"""
    return conn.execute(
        "SELECT * FROM product WHERE status IN (?, ?) ORDER BY published_at DESC LIMIT 1",
        ACTIVE_STATUSES,
    ).fetchone()


def publish_product(conn, seller_id: str, name: str, price_cents: int,
                    description: str | None, image_url: str | None):
    if get_active_product(conn):
        raise Conflict("当前已有在售或交易中的商品，需先下架后才能发布新商品")
    product_id = new_id()
    ts = now_iso()
    conn.execute(
        "INSERT INTO product (id, name, description, image_url, price_cents, status, published_at, updated_at) "
        "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
        (product_id, name, description, image_url, price_cents, ProductStatus.ON_SALE, ts, ts),
    )
    log_service.log_action(conn, seller_id, "PUBLISH_PRODUCT", f"发布商品：{name}")
    return get_product(conn, product_id)


def off_shelf_product(conn, seller_id: str, product_id: str):
    product = get_product(conn, product_id)
    if not product:
        raise NotFound("商品不存在")
    if product["status"] == ProductStatus.OFF_SHELF:
        raise Conflict("商品已下架")
    # 作废所有进行中的意向（排队中 / 重新排队中 / 已进入交易）
    conn.execute(
        "UPDATE order_intent SET status = ?, updated_at = ? "
        "WHERE product_id = ? AND status IN (?, ?, ?)",
        (OrderStatus.VOIDED, now_iso(), product_id,
         OrderStatus.QUEUED, OrderStatus.REQUEUED, OrderStatus.IN_TRANSACTION),
    )
    # 商品下架 -> 关联口令码全部失效
    codes.invalidate_product_codes(conn, product_id)
    conn.execute("DELETE FROM queue_item WHERE product_id = ?", (product_id,))
    conn.execute(
        "UPDATE product SET status = ?, updated_at = ? WHERE id = ?",
        (ProductStatus.OFF_SHELF, now_iso(), product_id),
    )
    log_service.log_action(conn, seller_id, "OFF_SHELF_PRODUCT",
                           f"下架商品：{product['name']}，关联口令码失效")
    return get_product(conn, product_id)


def freeze_product(conn, seller_id: str, product_id: str):
    product = get_product(conn, product_id)
    if not product:
        raise NotFound("商品不存在")
    if product["status"] == ProductStatus.OFF_SHELF:
        raise Conflict("商品已下架，无法冻结")
    if product["status"] == ProductStatus.FROZEN:
        raise Conflict("商品已处于冻结状态")
    conn.execute(
        "UPDATE product SET status = ?, updated_at = ? WHERE id = ?",
        (ProductStatus.FROZEN, now_iso(), product_id),
    )
    log_service.log_action(conn, seller_id, "FREEZE_PRODUCT",
                           f"手动冻结商品：{product['name']}，队列封口，不再接收新意向")
    return get_product(conn, product_id)


def unfreeze_product(conn, seller_id: str, product_id: str):
    product = get_product(conn, product_id)
    if not product:
        raise NotFound("商品不存在")
    if product["status"] != ProductStatus.FROZEN:
        raise Conflict("仅已冻结的商品可以解冻")
    in_tx = conn.execute(
        "SELECT 1 FROM order_intent WHERE product_id = ? AND status = ? LIMIT 1",
        (product_id, OrderStatus.IN_TRANSACTION),
    ).fetchone()
    if in_tx:
        raise Conflict("存在进行中的交易，不能手动解冻")
    conn.execute(
        "UPDATE product SET status = ?, updated_at = ? WHERE id = ?",
        (ProductStatus.ON_SALE, now_iso(), product_id),
    )
    log_service.log_action(conn, seller_id, "UNFREEZE_PRODUCT",
                           f"手动解冻商品：{product['name']}，恢复在售，重新接收意向")
    return get_product(conn, product_id)


def _tx_summary(conn, product_id: str) -> dict:
    row = conn.execute(
        "SELECT COUNT(*) AS failed FROM transaction_record WHERE product_id = ? AND result = 'FAILED'",
        (product_id,),
    ).fetchone()
    last = conn.execute(
        "SELECT * FROM transaction_record WHERE product_id = ? ORDER BY created_at DESC, rowid DESC LIMIT 1",
        (product_id,),
    ).fetchone()
    buyers = conn.execute(
        "SELECT COUNT(*) AS c FROM order_intent WHERE product_id = ?", (product_id,)
    ).fetchone()
    return {
        "failed_rounds": row["failed"],
        "buyer_count": buyers["c"],
        "final_result": last["result"] if last else None,
        "final_result_label": ("交易成功" if last and last["result"] == "SUCCESS"
                               else "交易失败" if last else "未成交"),
        "last_transaction_at": last["created_at"] if last else None,
    }


def list_history(conn, page: int, page_size: int, keyword: str | None = None) -> dict:
    where = "status = ?"
    params: list = [ProductStatus.OFF_SHELF]
    if keyword:
        where += " AND name LIKE ?"
        params.append(f"%{keyword}%")
    total = conn.execute(f"SELECT COUNT(*) AS c FROM product WHERE {where}", params).fetchone()["c"]
    rows = conn.execute(
        f"SELECT * FROM product WHERE {where} ORDER BY published_at DESC, rowid DESC LIMIT ? OFFSET ?",
        params + [page_size, (page - 1) * page_size],
    ).fetchall()
    items = []
    for row in rows:
        item = product_to_dict(row)
        item["transaction_summary"] = _tx_summary(conn, row["id"])
        items.append(item)
    return {"items": items, "total": total, "page": page, "page_size": page_size}


def get_history_detail(conn, product_id: str) -> dict:
    product = get_product(conn, product_id)
    if not product:
        raise NotFound("商品不存在")
    if product["status"] != ProductStatus.OFF_SHELF:
        raise Conflict("该商品尚未下架，暂不进入历史记录")
    orders = conn.execute(
        "SELECT * FROM order_intent WHERE product_id = ? ORDER BY created_at ASC, rowid ASC",
        (product_id,),
    ).fetchall()
    txs = conn.execute(
        "SELECT * FROM transaction_record WHERE product_id = ? ORDER BY created_at ASC, rowid ASC",
        (product_id,),
    ).fetchall()
    detail = product_to_dict(product)
    detail["transaction_summary"] = _tx_summary(conn, product_id)
    # 历史商品已终结，买家信息统一脱敏展示（FR-019 / NFR-003）
    detail["buyers"] = [order_to_dict(o, mask=True) for o in orders]
    detail["transactions"] = [transaction_to_dict(t) for t in txs]
    return detail
