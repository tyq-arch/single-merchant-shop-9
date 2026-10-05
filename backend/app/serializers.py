"""Row -> dict 序列化，统一响应结构。"""
from .enums import OrderStatus, ProductStatus
from .utils import mask_phone, price_from_cents


def product_to_dict(row) -> dict:
    if row is None:
        return None
    return {
        "id": row["id"],
        "name": row["name"],
        "description": row["description"],
        "image_url": row["image_url"],
        "price": price_from_cents(row["price_cents"]),
        "status": row["status"],
        "status_label": ProductStatus.LABELS.get(row["status"], row["status"]),
        "published_at": row["published_at"],
        "updated_at": row["updated_at"],
    }


def order_to_dict(row, mask: bool = False) -> dict:
    if row is None:
        return None
    return {
        "order_id": row["id"],
        "product_id": row["product_id"],
        "buyer_name": row["buyer_name"],
        "buyer_phone": mask_phone(row["buyer_phone"]) if mask else row["buyer_phone"],
        "status": row["status"],
        "status_label": OrderStatus.LABELS.get(row["status"], row["status"]),
        "queue_position": row["queue_position"],
        "requeue_count": row["requeue_count"],
        "command_code": row["command_code"],
        "created_at": row["created_at"],
        "updated_at": row["updated_at"],
        "product_snapshot": {
            "name": row["snapshot_name"],
            "description": row["snapshot_description"],
            "image_url": row["snapshot_image_url"],
            "price": price_from_cents(row["snapshot_price_cents"]),
        },
    }


def transaction_to_dict(row) -> dict:
    if row is None:
        return None
    return {
        "id": row["id"],
        "order_id": row["order_id"],
        "product_id": row["product_id"],
        "result": row["result"],
        "result_label": "交易成功" if row["result"] == "SUCCESS" else "交易失败",
        "note": row["note"],
        "created_at": row["created_at"],
    }
