"""卖家接口：登录、商品管理、冻结/解冻、进入交易、意向购买人、历史、改密、日志。"""
from fastapi import APIRouter, Depends, Query, Response

from .. import config, database, security
from ..deps import get_current_seller
from ..errors import Unauthorized
from ..schemas import (
    ChangePasswordRequest,
    ProductCreateRequest,
    SellerLoginRequest,
    TransactionMarkRequest,
)
from ..services import intent_service, log_service, product_service, transaction_service
from ..serializers import product_to_dict
from ..utils import cents_from_price

router = APIRouter(prefix="/api/seller", tags=["卖家"])

PageQuery = Query(1, ge=1, description="页码，从 1 开始")
SizeQuery = Query(config.DEFAULT_PAGE_SIZE, ge=1, le=config.MAX_PAGE_SIZE, description="每页条数")


@router.post("/login", summary="卖家登录（固定账号，无注册）")
def login(payload: SellerLoginRequest):
    with database.get_conn() as conn:
        seller = conn.execute(
            "SELECT * FROM seller WHERE username = ?", (payload.username,)
        ).fetchone()
    if not seller or not security.verify_password(payload.password, seller["password_hash"]):
        raise Unauthorized("用户名或密码错误")
    token, expires_at = security.create_token(seller["id"])
    with database.transaction() as conn:
        log_service.log_action(conn, seller["id"], "LOGIN", f"卖家 {seller['username']} 登录后台")
    return {
        "token": token,
        "token_type": "Bearer",
        "expires_at": expires_at,
        "seller": {"id": seller["id"], "username": seller["username"]},
    }


@router.post("/password", summary="修改密码（校验旧密码）")
def change_password(payload: ChangePasswordRequest, seller: dict = Depends(get_current_seller)):
    if not security.verify_password(payload.old_password, seller["password_hash"]):
        raise Unauthorized("旧密码不正确")
    with database.transaction() as conn:
        conn.execute(
            "UPDATE seller SET password_hash = ? WHERE id = ?",
            (security.hash_password(payload.new_password), seller["id"]),
        )
        log_service.log_action(conn, seller["id"], "CHANGE_PASSWORD", "修改登录密码")
    return {"message": "密码修改成功"}


@router.get("/product/current", summary="查看当前商品")
def current_product(seller: dict = Depends(get_current_seller)):
    with database.get_conn() as conn:
        row = product_service.get_active_product(conn)
    if not row:
        return {"product": None, "message": "当前无在售/交易中的商品"}
    return {"product": product_to_dict(row)}


@router.post("/products", summary="发布商品（唯一在售约束）")
def publish_product(payload: ProductCreateRequest, seller: dict = Depends(get_current_seller)):
    with database.transaction() as conn:
        product = product_service.publish_product(
            conn, seller["id"], payload.name, cents_from_price(payload.price),
            payload.description, payload.image_url,
        )
    return {"product": product_to_dict(product), "message": "商品发布成功"}


@router.post("/products/{product_id}/off-shelf", summary="下架商品")
def off_shelf(product_id: str, seller: dict = Depends(get_current_seller)):
    with database.transaction() as conn:
        product = product_service.off_shelf_product(conn, seller["id"], product_id)
    return {"product": product_to_dict(product),
            "message": "商品已下架，关联口令码全部失效"}


@router.post("/products/{product_id}/freeze", summary="手动冻结商品")
def freeze(product_id: str, seller: dict = Depends(get_current_seller)):
    with database.transaction() as conn:
        product = product_service.freeze_product(conn, seller["id"], product_id)
    return {"product": product_to_dict(product),
            "message": "商品已冻结，不再接收新意向"}


@router.post("/products/{product_id}/unfreeze", summary="手动解冻商品")
def unfreeze(product_id: str, seller: dict = Depends(get_current_seller)):
    with database.transaction() as conn:
        product = product_service.unfreeze_product(conn, seller["id"], product_id)
    return {"product": product_to_dict(product),
            "message": "商品已恢复在售，重新接收意向"}


@router.post("/products/{product_id}/start-transaction", summary="与队列第一位进入交易")
def start_transaction(product_id: str, seller: dict = Depends(get_current_seller)):
    with database.transaction() as conn:
        return transaction_service.start_transaction(conn, seller["id"], product_id)


@router.get("/products/{product_id}/intents", summary="查看意向购买人（分页/筛选）")
def list_intents(
    product_id: str,
    page: int = PageQuery,
    page_size: int = SizeQuery,
    status: str | None = Query(None, description="按意向状态筛选"),
    seller: dict = Depends(get_current_seller),
):
    with database.get_conn() as conn:
        return intent_service.list_intents(conn, product_id, page, page_size, status)


@router.get("/products/{product_id}/intents/export", summary="导出意向购买人 CSV")
def export_intents(product_id: str, seller: dict = Depends(get_current_seller)):
    with database.get_conn() as conn:
        csv_text = intent_service.export_intents_csv(conn, product_id)
    return Response(
        content=csv_text,
        media_type="text/csv; charset=utf-8",
        headers={"Content-Disposition": f'attachment; filename="intents_{product_id}.csv"'},
    )


@router.get("/history", summary="历史商品列表（时间倒序、分页）")
def history(
    page: int = PageQuery,
    page_size: int = SizeQuery,
    keyword: str | None = Query(None, description="按商品名称模糊搜索"),
    seller: dict = Depends(get_current_seller),
):
    with database.get_conn() as conn:
        return product_service.list_history(conn, page, page_size, keyword)


@router.get("/history/{product_id}", summary="历史商品详情（含完整交易过程）")
def history_detail(product_id: str, seller: dict = Depends(get_current_seller)):
    with database.get_conn() as conn:
        return product_service.get_history_detail(conn, product_id)


@router.post("/orders/{order_id}/transaction", summary="标记交易成功/失败")
def mark_transaction(
    order_id: str, payload: TransactionMarkRequest, seller: dict = Depends(get_current_seller)
):
    with database.transaction() as conn:
        return transaction_service.mark_transaction(
            conn, seller["id"], order_id, payload.result, payload.note
        )


@router.post("/orders/{order_id}/void", summary="作废交易失败的意向")
def void_order(order_id: str, seller: dict = Depends(get_current_seller)):
    with database.transaction() as conn:
        return transaction_service.void_order(conn, seller["id"], order_id)


@router.post("/orders/{order_id}/requeue", summary="失败者重新排队（最多 1 次）")
def requeue_order(order_id: str, seller: dict = Depends(get_current_seller)):
    with database.transaction() as conn:
        return transaction_service.requeue_order(conn, seller["id"], order_id)


@router.get("/logs", summary="操作日志（可追溯）")
def logs(
    page: int = PageQuery,
    page_size: int = SizeQuery,
    seller: dict = Depends(get_current_seller),
):
    with database.get_conn() as conn:
        items, total = log_service.list_logs(conn, page, page_size)
    return {"items": items, "total": total, "page": page, "page_size": page_size}
