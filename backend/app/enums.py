"""业务枚举与状态机常量（对应需求文档第3章状态机）。"""


class ProductStatus:
    ON_SALE = "ON_SALE"      # 在售
    FROZEN = "FROZEN"        # 已冻结（手动或进入交易自动冻结）
    OFF_SHELF = "OFF_SHELF"  # 已下架（终态）

    LABELS = {
        ON_SALE: "在售",
        FROZEN: "已冻结",
        OFF_SHELF: "已下架",
    }


class OrderStatus:
    QUEUED = "QUEUED"                # 排队中
    CANCELLED = "CANCELLED"          # 已撤销
    IN_TRANSACTION = "IN_TRANSACTION"  # 已进入交易
    SUCCESS = "SUCCESS"              # 交易成功
    FAILED = "FAILED"                # 交易失败
    VOIDED = "VOIDED"                # 已作废
    REQUEUED = "REQUEUED"            # 重新排队中

    LABELS = {
        QUEUED: "排队中",
        CANCELLED: "已撤销",
        IN_TRANSACTION: "已进入交易",
        SUCCESS: "交易成功",
        FAILED: "交易失败",
        VOIDED: "已作废",
        REQUEUED: "重新排队中",
    }

    # 已终结、口令码必然失效的状态
    TERMINAL = {CANCELLED, SUCCESS, FAILED, VOIDED}
    # 允许买家凭口令码修改信息 / 撤销的状态
    BUYER_EDITABLE = {QUEUED, REQUEUED}
    # 仍在排队队列中的状态
    WAITING = {QUEUED, REQUEUED}


class CodeStatus:
    ACTIVE = "ACTIVE"
    INVALID = "INVALID"


class TxResult:
    SUCCESS = "SUCCESS"
    FAILED = "FAILED"


class ProductState:
    """买家端商品可见状态。"""
    NONE = "NONE"        # 无商品在售
    ON_SALE = "ON_SALE"  # 在售，可购买
    FROZEN = "FROZEN"    # 商品交易中，不可购买
