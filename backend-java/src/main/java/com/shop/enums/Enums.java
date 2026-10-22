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
