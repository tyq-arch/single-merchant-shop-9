"""FastAPI 依赖：卖家鉴权。"""
from typing import Optional

from fastapi import Depends, Header

from . import database, security
from .errors import Unauthorized


def get_current_seller(authorization: Optional[str] = Header(default=None)) -> dict:
    if not authorization or not authorization.lower().startswith("bearer "):
        raise Unauthorized("未登录或凭证缺失")
    token = authorization.split(" ", 1)[1].strip()
    seller_id = security.verify_token(token)
    if not seller_id:
        raise Unauthorized("登录已失效，请重新登录")
    with database.get_conn() as conn:
        row = conn.execute("SELECT * FROM seller WHERE id = ?", (seller_id,)).fetchone()
    if not row:
        raise Unauthorized("卖家账号不存在")
    return dict(row)


CurrentSeller = Depends(get_current_seller)
