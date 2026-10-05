"""买家接口：提交意向、凭口令码查询/修改/撤销。"""
from fastapi import APIRouter

from .. import database
from ..schemas import BuyerCodeRequest, BuyerIntentCreateRequest, BuyerIntentUpdateRequest
from ..services import intent_service

router = APIRouter(prefix="/api/buyer", tags=["买家"])


@router.post("/intents", summary="提交购买意向并获取口令码")
def submit_intent(payload: BuyerIntentCreateRequest):
    with database.transaction() as conn:
        result = intent_service.submit_intent(
            conn, payload.product_id, payload.buyer_name, payload.buyer_phone
        )
    return result


@router.get("/intents/{code}", summary="凭口令码查询意向状态与排队位置")
def query_intent(code: str):
    with database.get_conn() as conn:
        return intent_service.query_by_code(conn, code)


@router.patch("/intents", summary="凭口令码修改姓名/联系电话（仅排队中）")
def update_intent(payload: BuyerIntentUpdateRequest):
    with database.transaction() as conn:
        return intent_service.update_by_code(
            conn, payload.code, payload.buyer_name, payload.buyer_phone
        )


@router.post("/intents/cancel", summary="凭口令码撤销排队意向（仅排队中）")
def cancel_intent(payload: BuyerCodeRequest):
    with database.transaction() as conn:
        return intent_service.cancel_by_code(conn, payload.code)
