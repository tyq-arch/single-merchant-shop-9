package com.shop.enums;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 业务状态与枚举（挑战A升级版）。
 */
public final class Enums {

    private Enums() {
    }

    /** 商品状态：在售 / 冻结 / 售罄下架 / 手动下架。 */
    public static final class ProductStatus {
        public static final String ON_SALE = "ON_SALE";
        public static final String FROZEN = "FROZEN";
        public static final String SOLD_OUT = "SOLD_OUT";
        public static final String OFF_SHELF = "OFF_SHELF";

        /** 公开可见（可浏览/搜索/详情）。 */
        public static final Set<String> VISIBLE = Collections.unmodifiableSet(
                new HashSet<>(Arrays.asList(ON_SALE, FROZEN)));
        /** 进入历史记录：已售罄下架 / 手动下架。 */
        public static final Set<String> HISTORY = Collections.unmodifiableSet(
                new HashSet<>(Arrays.asList(SOLD_OUT, OFF_SHELF)));

        public static String label(String status) {
            if (ON_SALE.equals(status)) {
                return "在售";
            }
            if (FROZEN.equals(status)) {
                return "冻结";
            }
            if (SOLD_OUT.equals(status)) {
                return "售罄下架";
            }
            if (OFF_SHELF.equals(status)) {
                return "手动下架";
            }
            return status;
        }
    }

    /** 意向状态。 */
    public static final class IntentStatus {
        public static final String QUEUED = "QUEUED";
        public static final String REQUEUED = "REQUEUED";
        public static final String IN_TRANSACTION = "IN_TRANSACTION";
        public static final String SUCCESS = "SUCCESS";
        public static final String FAILED = "FAILED";
        public static final String VOIDED = "VOIDED";
        public static final String SOLD_OUT = "SOLD_OUT";

        /** 当前仍占用排队资格的有效意向。 */
        public static final Set<String> ACTIVE = Collections.unmodifiableSet(
                new HashSet<>(Arrays.asList(QUEUED, REQUEUED, IN_TRANSACTION)));
        /** 仍在队列中等待的意向。 */
        public static final Set<String> WAITING = Collections.unmodifiableSet(
                new HashSet<>(Arrays.asList(QUEUED, REQUEUED)));

        public static String label(String status) {
            if (QUEUED.equals(status)) {
                return "排队中";
            }
            if (REQUEUED.equals(status)) {
                return "重新排队中";
            }
            if (IN_TRANSACTION.equals(status)) {
                return "进入交易";
            }
            if (SUCCESS.equals(status)) {
                return "交易成功";
            }
            if (FAILED.equals(status)) {
                return "交易失败";
            }
            if (VOIDED.equals(status)) {
                return "已作废";
            }
            if (SOLD_OUT.equals(status)) {
                return "售罄作废";
            }
            return status;
        }
    }

    /** 挑战C 订单状态：待支付/已支付 + 商家推进 4 态 + 已取消（业务流见 3.4）。 */
    public static final class OrderStatus {
        public static final String PENDING_PAYMENT = "PENDING_PAYMENT";
        public static final String PAID = "PAID";
        public static final String SELLER_CONFIRMED = "SELLER_CONFIRMED";
        public static final String STOCK_READY = "STOCK_READY";
        public static final String SHIPPING = "SHIPPING";
        public static final String COMPLETED = "COMPLETED";
        public static final String CANCELLED = "CANCELLED";

        /** 客户可取消：尚未到「开始发货」（FR-028）。 */
        public static final Set<String> CUSTOMER_CANCELLABLE = Collections.unmodifiableSet(
                new HashSet<>(Arrays.asList(PENDING_PAYMENT, PAID, SELLER_CONFIRMED, STOCK_READY)));
        /** 取消需退款：已支付及之后、开始发货之前（FR-044）。 */
        public static final Set<String> REFUNDABLE = Collections.unmodifiableSet(
                new HashSet<>(Arrays.asList(PAID, SELLER_CONFIRMED, STOCK_READY)));

        public static String label(String status) {
            if (PENDING_PAYMENT.equals(status)) {
                return "待支付";
            }
            if (PAID.equals(status)) {
                return "已支付";
            }
            if (SELLER_CONFIRMED.equals(status)) {
                return "商家确认";
            }
            if (STOCK_READY.equals(status)) {
                return "备货完成";
            }
            if (SHIPPING.equals(status)) {
                return "开始发货";
            }
            if (COMPLETED.equals(status)) {
                return "交易完成";
            }
            if (CANCELLED.equals(status)) {
                return "已取消";
            }
            return status;
        }
    }

    /** 支付方式（FR-040）：银行卡 / 信用卡 / 支付宝 / 微信。 */
    public static final class PaymentMethod {
        public static final String BANK_CARD = "BANK_CARD";
        public static final String CREDIT_CARD = "CREDIT_CARD";
        public static final String ALIPAY = "ALIPAY";
        public static final String WECHAT = "WECHAT";

        public static final Set<String> ALL = Collections.unmodifiableSet(
                new HashSet<>(Arrays.asList(BANK_CARD, CREDIT_CARD, ALIPAY, WECHAT)));

        public static String label(String method) {
            if (BANK_CARD.equals(method)) {
                return "银行卡";
            }
            if (CREDIT_CARD.equals(method)) {
                return "信用卡";
            }
            if (ALIPAY.equals(method)) {
                return "支付宝";
            }
            if (WECHAT.equals(method)) {
                return "微信支付";
            }
            return method;
        }
    }

    /** 支付状态：成功 / 失败（失败可重新发起，FR-042）。 */
    public static final class PaymentStatus {
        public static final String SUCCESS = "SUCCESS";
        public static final String FAILED = "FAILED";

        public static String label(String status) {
            return SUCCESS.equals(status) ? "支付成功" : "支付失败";
        }
    }

    /** 退款状态：原路退回成功（FR-045）。 */
    public static final class RefundStatus {
        public static final String SUCCESS = "SUCCESS";

        public static String label(String status) {
            return SUCCESS.equals(status) ? "退款成功" : status;
        }
    }

    /** 物流状态：由商家手动更新（TBD-09）。 */
    public static final class ShipmentStatus {
        public static final String SHIPPED = "SHIPPED";
        public static final String IN_TRANSIT = "IN_TRANSIT";
        public static final String DELIVERING = "DELIVERING";
        public static final String SIGNED = "SIGNED";

        public static String label(String status) {
            if (SHIPPED.equals(status)) {
                return "已发货";
            }
            if (IN_TRANSIT.equals(status)) {
                return "运输中";
            }
            if (DELIVERING.equals(status)) {
                return "派送中";
            }
            if (SIGNED.equals(status)) {
                return "已签收";
            }
            return status;
        }
    }

    /** 售后申请内部追溯状态（TBD-13）。 */
    public static final class AfterSaleStatus {
        public static final String PENDING = "PENDING";
        public static final String NEGOTIATING = "NEGOTIATING";
        public static final String RESOLVED = "RESOLVED";
        public static final String REJECTED = "REJECTED";

        public static String label(String status) {
            if (PENDING.equals(status)) {
                return "待处理";
            }
            if (NEGOTIATING.equals(status)) {
                return "协商中";
            }
            if (RESOLVED.equals(status)) {
                return "已解决";
            }
            if (REJECTED.equals(status)) {
                return "已拒绝";
            }
            return status;
        }
    }

    /** 库存占用状态：占用中 / 已回补 / 已核销。 */
    public static final class ReservationStatus {
        public static final String ACTIVE = "ACTIVE";
        public static final String RELEASED = "RELEASED";
        public static final String CONSUMED = "CONSUMED";
    }

    /** 交易结果。 */
    public static final class TradeResult {
        public static final String SUCCESS = "SUCCESS";
        public static final String FAILED = "FAILED";

        public static String label(String result) {
            return SUCCESS.equals(result) ? "交易成功" : "交易失败";
        }
    }

    /** 交易失败时对失败者的处理动作（与标记失败同一动作完成）。 */
    public static final class FailedAction {
        public static final String VOID = "VOID";
        public static final String REQUEUE = "REQUEUE";
    }

    /** 操作日志动作。 */
    public static final class LogAction {
        public static final String LOGIN = "LOGIN";
        public static final String CHANGE_PASSWORD = "CHANGE_PASSWORD";
        public static final String CREATE_CATEGORY = "CREATE_CATEGORY";
        public static final String RENAME_CATEGORY = "RENAME_CATEGORY";
        public static final String DELETE_CATEGORY = "DELETE_CATEGORY";
        public static final String PUBLISH_PRODUCT = "PUBLISH_PRODUCT";
        public static final String ADJUST_STOCK = "ADJUST_STOCK";
        public static final String OFF_SHELF_PRODUCT = "OFF_SHELF_PRODUCT";
        public static final String ON_SHELF_PRODUCT = "ON_SHELF_PRODUCT";
        public static final String FREEZE_PRODUCT = "FREEZE_PRODUCT";
        public static final String UNFREEZE_PRODUCT = "UNFREEZE_PRODUCT";
        public static final String START_TRANSACTION = "START_TRANSACTION";
        public static final String TRANSACTION_SUCCESS = "TRANSACTION_SUCCESS";
        public static final String TRANSACTION_FAILED = "TRANSACTION_FAILED";
    }
}
