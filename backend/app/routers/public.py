"""公开接口：买家匿名浏览商品。"""
from fastapi import APIRouter

from .. import database
from ..enums import ProductState, ProductStatus
from ..errors import NotFound
from ..serializers import product_to_dict
from ..services import product_service

router = APIRouter(prefix="/api/products", tags=["公开-商品浏览"])


@router.get("/current", summary="获取当前商品（无需登录）")
def get_current_product():
    """无商品在售返回 state=NONE；冻结商品返回商品信息并标记交易中。"""
    with database.get_conn() as conn:
        row = product_service.get_active_product(conn)
    if not row:
        return {"state": ProductState.NONE, "product": None, "message": "暂无商品在售"}
    if row["status"] == ProductStatus.FROZEN:
        return {
            "state": ProductState.FROZEN,
            "product": product_to_dict(row),
            "message": "商品交易中",
        }
    return {"state": ProductState.ON_SALE, "product": product_to_dict(row), "message": None}


@router.get("/{product_id}", summary="商品详情（无需登录）")
def get_product_detail(product_id: str):
    with database.get_conn() as conn:
        row = product_service.get_product(conn, product_id)
    if not row or row["status"] == ProductStatus.OFF_SHELF:
        raise NotFound("商品不存在或已下架")
    return {"product": product_to_dict(row)}
