"""通用工具函数。"""
import uuid
from datetime import datetime


def new_id() -> str:
    return uuid.uuid4().hex


def now() -> datetime:
    return datetime.now().replace(microsecond=0)


def iso(dt: datetime | None) -> str | None:
    if dt is None:
        return None
    return dt.isoformat(sep=" ")


def now_iso() -> str:
    return iso(now())


def parse_iso(value: str | None) -> datetime | None:
    if not value:
        return None
    try:
        return datetime.fromisoformat(value)
    except ValueError:
        return None


def mask_phone(phone: str | None) -> str | None:
    """手机号脱敏：保留前 3 位与后 4 位（FR-019 / NFR-003）。"""
    if not phone:
        return phone
    if len(phone) >= 7:
        return f"{phone[:3]}****{phone[-4:]}"
    return "***"


def cents_from_price(price) -> int:
    from decimal import Decimal, ROUND_HALF_UP
    value = Decimal(str(price))
    return int((value * 100).quantize(Decimal("1"), rounding=ROUND_HALF_UP))


def price_from_cents(cents: int | None) -> float | None:
    if cents is None:
        return None
    return round(cents / 100, 2)
