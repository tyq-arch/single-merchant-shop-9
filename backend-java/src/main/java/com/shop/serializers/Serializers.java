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
}
