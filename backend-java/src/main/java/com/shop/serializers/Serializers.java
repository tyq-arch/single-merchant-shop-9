package com.shop.serializers;

import com.shop.db.Jdbc;
import com.shop.enums.Enums;
import com.shop.util.Utils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 响应序列化：统一 snake_case 字段与中文状态标签。
 */
public final class Serializers {

    private Serializers() {
    }

    public static Map<String, Object> image(Map<String, Object> row) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", Jdbc.strOf(row, "id"));
        item.put("url", Jdbc.strOf(row, "url"));
        item.put("sort_order", Jdbc.intOf(row, "sort_order"));
        return item;
    }

    /** 商品响应；images 为空时 primary_image 为 null。 */
    public static Map<String, Object> product(Map<String, Object> row, List<Map<String, Object>> images) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", Jdbc.strOf(row, "id"));
        item.put("name", Jdbc.strOf(row, "name"));
        item.put("description", Jdbc.strOf(row, "description"));
        item.put("price", Utils.priceFromCents(Jdbc.integerOrNull(row, "price_cents")));
        item.put("price_cents", Jdbc.intOf(row, "price_cents"));
        item.put("stock", Jdbc.intOf(row, "stock"));
        item.put("status", Jdbc.strOf(row, "status"));
        item.put("status_label", Enums.ProductStatus.label(Jdbc.strOf(row, "status")));
        item.put("category_id", Jdbc.strOf(row, "category_id"));
        item.put("category_name", Jdbc.strOf(row, "category_name"));
        item.put("parent_category_id", Jdbc.strOf(row, "parent_category_id"));
        item.put("parent_category_name", Jdbc.strOf(row, "parent_category_name"));
        item.put("published_at", Jdbc.strOf(row, "published_at"));
        item.put("created_at", Jdbc.strOf(row, "created_at"));
        item.put("updated_at", Jdbc.strOf(row, "updated_at"));

        List<Map<String, Object>> serialized = new ArrayList<>();
        String primary = null;
        if (images != null) {
            for (Map<String, Object> img : images) {
                serialized.add(image(img));
                if (primary == null) {
                    primary = Jdbc.strOf(img, "url");
                }
            }
        }
        item.put("images", serialized);
        item.put("primary_image", primary);
        item.put("image_count", serialized.size());
        return item;
    }

    public static Map<String, Object> category(Map<String, Object> row) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", Jdbc.strOf(row, "id"));
        item.put("name", Jdbc.strOf(row, "name"));
        item.put("parent_id", Jdbc.strOf(row, "parent_id"));
        item.put("level", Jdbc.intOf(row, "level"));
        item.put("created_at", Jdbc.strOf(row, "created_at"));
        item.put("updated_at", Jdbc.strOf(row, "updated_at"));
        item.put("product_count", Jdbc.intOf(row, "product_count"));
        return item;
    }

    /** 客户信息；masked=true 时电话脱敏（NFR-004）。 */
    public static Map<String, Object> customer(Map<String, Object> row, boolean masked) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", Jdbc.strOf(row, "id"));
        item.put("username", Jdbc.strOf(row, "username"));
        String phone = Jdbc.strOf(row, "phone");
        item.put("phone", masked ? Utils.maskPhone(phone) : phone);
        item.put("trade_location", Jdbc.strOf(row, "trade_location"));
        item.put("registered_at", Jdbc.strOf(row, "created_at"));
        return item;
    }

    /** 意向单；customer 为空时不输出该字段。 */
    public static Map<String, Object> intent(Map<String, Object> row, Map<String, Object> customer) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", Jdbc.strOf(row, "id"));
        item.put("product_id", Jdbc.strOf(row, "product_id"));
        item.put("customer_id", Jdbc.strOf(row, "customer_id"));
        item.put("status", Jdbc.strOf(row, "status"));
        item.put("status_label", Enums.IntentStatus.label(Jdbc.strOf(row, "status")));
        item.put("queue_position", Jdbc.intOf(row, "queue_position"));
        item.put("requeue_count", Jdbc.intOf(row, "requeue_count"));
        item.put("created_at", Jdbc.strOf(row, "created_at"));
        item.put("updated_at", Jdbc.strOf(row, "updated_at"));
        if (customer != null) {
            item.put("customer", customer);
        }
        return item;
    }

    public static Map<String, Object> trade(Map<String, Object> row) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", Jdbc.strOf(row, "id"));
        item.put("intent_id", Jdbc.strOf(row, "intent_id"));
        item.put("product_id", Jdbc.strOf(row, "product_id"));
        item.put("customer_id", Jdbc.strOf(row, "customer_id"));
        item.put("result", Jdbc.strOf(row, "result"));
        item.put("result_label", Enums.TradeResult.label(Jdbc.strOf(row, "result")));
        item.put("note", Jdbc.strOf(row, "note"));
        item.put("created_at", Jdbc.strOf(row, "created_at"));
        return item;
    }

    // --------------------------- 挑战B：收藏 / 购物车 / 订单 ---------------------------

    /** 收藏项；product 由调用方补充。 */
    public static Map<String, Object> favorite(Map<String, Object> row) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", Jdbc.strOf(row, "id"));
        item.put("product_id", Jdbc.strOf(row, "product_id"));
        item.put("created_at", Jdbc.strOf(row, "created_at"));
        return item;
    }

    /** 购物车项：展示名称/单价/数量/小计（FR-020）；product 由调用方补充。 */
    public static Map<String, Object> cartItem(Map<String, Object> row, Integer priceCents) {
        int quantity = Jdbc.intOf(row, "quantity");
        Integer subtotalCents = priceCents == null ? null : priceCents * quantity;
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", Jdbc.strOf(row, "id"));
        item.put("product_id", Jdbc.strOf(row, "product_id"));
        item.put("quantity", quantity);
        item.put("price", Utils.priceFromCents(priceCents));
        item.put("price_cents", priceCents);
        item.put("subtotal", Utils.priceFromCents(subtotalCents));
        item.put("subtotal_cents", subtotalCents);
        item.put("created_at", Jdbc.strOf(row, "created_at"));
        item.put("updated_at", Jdbc.strOf(row, "updated_at"));
        return item;
    }

    /** 订单项：单价与名称为下单时快照（TBD-09）。 */
    public static Map<String, Object> orderItem(Map<String, Object> row) {
        int quantity = Jdbc.intOf(row, "quantity");
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", Jdbc.strOf(row, "id"));
        item.put("order_id", Jdbc.strOf(row, "order_id"));
        item.put("product_id", Jdbc.strOf(row, "product_id"));
        item.put("product_name", Jdbc.strOf(row, "product_name"));
        item.put("quantity", quantity);
        item.put("price", Utils.priceFromCents(Jdbc.integerOrNull(row, "price_cents")));
        item.put("price_cents", Jdbc.intOf(row, "price_cents"));
        item.put("subtotal", Utils.priceFromCents(Jdbc.integerOrNull(row, "subtotal_cents")));
        item.put("subtotal_cents", Jdbc.intOf(row, "subtotal_cents"));
        return item;
    }

    /** 订单：订单号、总金额、下单时间、状态/结果（FR-030）；items 由调用方补充。 */
    public static Map<String, Object> order(Map<String, Object> row) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", Jdbc.strOf(row, "id"));
        item.put("order_no", Jdbc.strOf(row, "order_no"));
        item.put("customer_id", Jdbc.strOf(row, "customer_id"));
        item.put("status", Jdbc.strOf(row, "status"));
        item.put("status_label", Enums.OrderStatus.label(Jdbc.strOf(row, "status")));
        item.put("can_cancel", Enums.OrderStatus.CUSTOMER_CANCELLABLE.contains(Jdbc.strOf(row, "status")));
        item.put("total", Utils.priceFromCents(Jdbc.integerOrNull(row, "total_cents")));
        item.put("total_cents", Jdbc.intOf(row, "total_cents"));
        // TBD-09：只提供下单时间一个时间戳，不为各状态单独打点
        item.put("created_at", Jdbc.strOf(row, "created_at"));
        return item;
    }

    // --------------------------- 挑战C：收货地址 / 支付 / 物流 / 售后 ---------------------------

    /** 收货地址（FR-046 / FR-047）。 */
    public static Map<String, Object> address(Map<String, Object> row, boolean masked) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", Jdbc.strOf(row, "id"));
        item.put("receiver_name", Jdbc.strOf(row, "receiver_name"));
        String phone = Jdbc.strOf(row, "receiver_phone");
        item.put("receiver_phone", masked ? Utils.maskPhone(phone) : phone);
        item.put("province", Jdbc.strOf(row, "province"));
        item.put("city", Jdbc.strOf(row, "city"));
        item.put("district", Jdbc.strOf(row, "district"));
        item.put("detail", Jdbc.strOf(row, "detail"));
        item.put("postal_code", Jdbc.strOf(row, "postal_code"));
        item.put("region", Jdbc.strOf(row, "province") + Jdbc.strOf(row, "city") + Jdbc.strOf(row, "district"));
        item.put("is_default", Jdbc.intOf(row, "is_default") == 1);
        item.put("created_at", Jdbc.strOf(row, "created_at"));
        item.put("updated_at", Jdbc.strOf(row, "updated_at"));
        return item;
    }

    /** 支付记录（FR-041 / FR-045）。 */
    public static Map<String, Object> payment(Map<String, Object> row) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", Jdbc.strOf(row, "id"));
        item.put("payment_no", Jdbc.strOf(row, "payment_no"));
        item.put("method", Jdbc.strOf(row, "method"));
        item.put("method_label", Enums.PaymentMethod.label(Jdbc.strOf(row, "method")));
        item.put("amount", Utils.priceFromCents(Jdbc.integerOrNull(row, "amount_cents")));
        item.put("amount_cents", Jdbc.intOf(row, "amount_cents"));
        item.put("status", Jdbc.strOf(row, "status"));
        item.put("status_label", Enums.PaymentStatus.label(Jdbc.strOf(row, "status")));
        item.put("transaction_no", Jdbc.strOf(row, "transaction_no"));
        item.put("failure_reason", Jdbc.strOf(row, "failure_reason"));
        item.put("paid_at", Jdbc.strOf(row, "paid_at"));
        item.put("created_at", Jdbc.strOf(row, "created_at"));
        return item;
    }

    public static Map<String, Object> refund(Map<String, Object> row) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", Jdbc.strOf(row, "id"));
        item.put("refund_no", Jdbc.strOf(row, "refund_no"));
        item.put("amount", Utils.priceFromCents(Jdbc.integerOrNull(row, "amount_cents")));
        item.put("amount_cents", Jdbc.intOf(row, "amount_cents"));
        item.put("channel", Jdbc.strOf(row, "channel"));
        item.put("channel_label", Enums.PaymentMethod.label(Jdbc.strOf(row, "channel")));
        item.put("status", Jdbc.strOf(row, "status"));
        item.put("status_label", Enums.RefundStatus.label(Jdbc.strOf(row, "status")));
        item.put("reason", Jdbc.strOf(row, "reason"));
        item.put("refunded_at", Jdbc.strOf(row, "refunded_at"));
        return item;
    }

    public static Map<String, Object> shipment(Map<String, Object> row) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", Jdbc.strOf(row, "id"));
        item.put("carrier", Jdbc.strOf(row, "carrier"));
        item.put("tracking_no", Jdbc.strOf(row, "tracking_no"));
        item.put("status", Jdbc.strOf(row, "status"));
        item.put("status_label", Enums.ShipmentStatus.label(Jdbc.strOf(row, "status")));
        item.put("shipped_at", Jdbc.strOf(row, "shipped_at"));
        item.put("updated_at", Jdbc.strOf(row, "updated_at"));
        return item;
    }

    public static Map<String, Object> shipmentTrack(Map<String, Object> row) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("status", Jdbc.strOf(row, "status"));
        item.put("status_label", Enums.ShipmentStatus.label(Jdbc.strOf(row, "status")));
        item.put("description", Jdbc.strOf(row, "description"));
        item.put("occurred_at", Jdbc.strOf(row, "occurred_at"));
        return item;
    }

    /** 售后申请（FR-051 / FR-052）；images 由调用方补充。 */
    public static Map<String, Object> afterSale(Map<String, Object> row) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", Jdbc.strOf(row, "id"));
        item.put("after_sale_no", Jdbc.strOf(row, "after_sale_no"));
        item.put("order_id", Jdbc.strOf(row, "order_id"));
        item.put("order_item_id", Jdbc.strOf(row, "order_item_id"));
        item.put("product_id", Jdbc.strOf(row, "product_id"));
        item.put("product_name", Jdbc.strOf(row, "product_name"));
        item.put("title", Jdbc.strOf(row, "title"));
        item.put("description", Jdbc.strOf(row, "description"));
        item.put("status", Jdbc.strOf(row, "status"));
        item.put("status_label", Enums.AfterSaleStatus.label(Jdbc.strOf(row, "status")));
        item.put("result", Jdbc.strOf(row, "result"));
        item.put("result_note", Jdbc.strOf(row, "result_note"));
        item.put("created_at", Jdbc.strOf(row, "created_at"));
        item.put("updated_at", Jdbc.strOf(row, "updated_at"));
        return item;
    }
}
