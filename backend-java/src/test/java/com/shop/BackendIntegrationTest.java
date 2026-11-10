package com.shop;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 挑战B 买家端端到端集成测试：收藏、购物车、游客临时预选与登录合并、
 * 购物车多选下单与库存占用、缺货剔除、订单历史、客户取消回补、多选转收藏。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BackendIntegrationTest {

    private static Path dataDir;

    @DynamicPropertySource
    static void dataDirProperties(DynamicPropertyRegistry registry) throws IOException {
        dataDir = Files.createTempDirectory("shop-java-test-");
        registry.add("SHOP_DATA_DIR", () -> dataDir.toString());
        registry.add("SHOP_DB_PATH", () -> dataDir.resolve("shop.db").toString());
        registry.add("SHOP_UPLOAD_DIR", () -> dataDir.resolve("uploads").toString());
    }

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private ObjectMapper json;

    // --------------------------- 请求工具 ---------------------------

    private String url(String path) {
        return "http://127.0.0.1:" + port + path;
    }

    private JsonNode call(HttpMethod method, String path, Object body, String token, String guestId) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (token != null) {
                headers.setBearerAuth(token);
            }
            if (guestId != null) {
                headers.set("X-Guest-Id", guestId);
            }
            ResponseEntity<String> response = rest.exchange(url(path), method,
                    new HttpEntity<>(body == null ? null : json.writeValueAsString(body), headers), String.class);
            return json.readTree(response.getBody() == null ? "{}" : response.getBody());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private JsonNode call(HttpMethod method, String path, Object body, String token) {
        return call(method, path, body, token, null);
    }

    private int status(HttpMethod method, String path, Object body, String token, String guestId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        if (guestId != null) {
            headers.set("X-Guest-Id", guestId);
        }
        try {
            return rest.exchange(url(path), method,
                    new HttpEntity<>(body == null ? null : json.writeValueAsString(body), headers),
                    String.class).getStatusCodeValue();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private int status(HttpMethod method, String path, Object body, String token) {
        return status(method, path, body, token, null);
    }

    private JsonNode get(String path, String token) {
        return call(HttpMethod.GET, path, null, token);
    }

    private Map<String, Object> map(Object... pairs) {
        Map<String, Object> result = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            result.put((String) pairs[i], pairs[i + 1]);
        }
        return result;
    }

    private JsonNode register(String username, String phone, String location) {
        return call(HttpMethod.POST, "/api/customer/register",
                map("username", username, "password", "Pass1234!", "phone", phone,
                        "trade_location", location), null);
    }

    private String customerToken(String username) {
        return call(HttpMethod.POST, "/api/customer/login",
                map("username", username, "password", "Pass1234!"), null).path("token").asText();
    }

    private JsonNode publish(String token, String name, double price, int stock,
                             String categoryId, List<String> images) {
        Map<String, Object> body = map("name", name, "price", price, "stock", stock,
                "category_id", categoryId, "description", "<p>" + name + " 富媒体描述</p>");
        body.put("image_urls", images);
        return call(HttpMethod.POST, "/api/seller/products", body, token);
    }

    private JsonNode addCart(String productId, Integer quantity, String token) {
        return call(HttpMethod.POST, "/api/customer/cart",
                map("product_id", productId, "quantity", quantity), token);
    }

    private JsonNode checkout(List<String> productIds, String token) {
        return call(HttpMethod.POST, "/api/customer/orders", map("product_ids", productIds), token);
    }

    // --------------------------- 测试主体 ---------------------------

    @Test
    void buyerFlowChallengeB() throws Exception {
        // 0. 服务信息与卖家登录
        assertEquals("/docs", get("/", null).path("docs").asText());
        String seller = call(HttpMethod.POST, "/api/seller/login",
                map("username", "seller", "password", "seller123"), null).path("token").asText();
        assertEquals(401, status(HttpMethod.POST, "/api/seller/login",
                map("username", "seller", "password", "bad"), null));

        // 1. 二级类别
        String topId = call(HttpMethod.POST, "/api/seller/categories",
                map("name", "厨房用品"), seller).path("category").path("id").asText();
        String leafId = call(HttpMethod.POST, "/api/seller/categories",
                map("name", "杯子", "parent_id", topId), seller).path("category").path("id").asText();

        // 2. 商品：A 库存 2、B 库存 5、C 库存 0（售罄下架）
        String imageUrl = uploadPng(seller);
        String cupA = publish(seller, "手工陶瓷杯", 99.9, 2, leafId,
                new ArrayList<>(Arrays.asList(imageUrl))).path("product").path("id").asText();
        String cupB = publish(seller, "玻璃杯", 29.9, 5, leafId, new ArrayList<String>())
                .path("product").path("id").asText();
        String cupC = publish(seller, "无货商品", 10, 0, leafId, new ArrayList<String>())
                .path("product").path("id").asText();
        assertEquals(2, get("/api/products", null).path("total").asInt());
        assertEquals(1, get("/api/products?keyword=陶瓷", null).path("total").asInt());
        JsonNode detail = get("/api/products/" + cupA, null);
        assertEquals(2, detail.path("product").path("available_stock").asInt());
        assertTrue(detail.path("can_buy").asBoolean());

        // 3. 游客临时预选：缺会话标识 422；可收藏/加购但不可下单
        assertEquals(422, status(HttpMethod.POST, "/api/guest/favorites",
                map("product_id", cupA), null, null));
        String guest = "guest-session-0001";
        assertEquals(200, status(HttpMethod.POST, "/api/guest/favorites",
                map("product_id", cupA), null, guest));
        JsonNode guestCart = call(HttpMethod.POST, "/api/guest/cart",
                map("product_id", cupB, "quantity", 2), null, guest);
        assertEquals(1, guestCart.path("item_count").asInt());
        assertEquals(2, guestCart.path("items").get(0).path("quantity").asInt());
        assertEquals("玻璃杯", guestCart.path("items").get(0).path("product").path("name").asText());
        assertEquals(2, guestCart.path("items").get(0).path("quantity").asInt());
        // 游客下单必须登录
        assertEquals(401, status(HttpMethod.POST, "/api/guest/orders", null, null, guest));

        // 4. 客户注册（密码强度校验）与登录合并游客预选
        assertEquals("注册成功，请登录", register("alice", "13800000001", "一号门").path("message").asText());
        assertEquals(422, status(HttpMethod.POST, "/api/customer/register",
                map("username", "weak", "password", "abcdefgh", "phone", "13800000005",
                        "trade_location", "五号门"), null));
        JsonNode login = call(HttpMethod.POST, "/api/customer/login",
                map("username", "alice", "password", "Pass1234!"), null, guest);
        String alice = login.path("token").asText();
        assertEquals(1, login.path("preselect_merged").path("favorites_added").asInt());
        assertEquals(1, login.path("preselect_merged").path("cart_merged").asInt());
        // 游客预选已清空
        JsonNode guestCartAfter = call(HttpMethod.GET, "/api/guest/cart", null, null, guest);
        assertEquals(0, guestCartAfter.path("item_count").asInt());
        call(HttpMethod.GET, "/api/guest/favorites", null, null, guest);

        // 5. 登录后：收藏含 A、购物车含 B×2
        JsonNode favorites = get("/api/customer/favorites", alice);
        assertEquals(1, favorites.path("total").asInt());
        assertEquals(cupA, favorites.path("items").get(0).path("product_id").asText());
        assertTrue(favorites.path("items").get(0).path("available").asBoolean());
        JsonNode cart = get("/api/customer/cart", alice);
        assertEquals(1, cart.path("item_count").asInt());
        assertEquals(2, cart.path("items").get(0).path("quantity").asInt());
        assertEquals(59.8, cart.path("items").get(0).path("subtotal").asDouble(), 0.001);

        // 6. 购物车：同商品唯一、数量累加、上限 99
        addCart(cupA, 1, alice);
        JsonNode afterAdd = addCart(cupA, 1, alice);
        assertEquals(2, afterAdd.path("item_count").asInt());
        JsonNode itemA = findItem(afterAdd.path("items"), cupA);
        assertEquals(2, itemA.path("quantity").asInt());
        assertEquals(199.8, itemA.path("subtotal").asDouble(), 0.001);
        assertEquals(422, status(HttpMethod.PATCH, "/api/customer/cart/" + cupA,
                map("quantity", 100), alice));
        assertEquals(409, status(HttpMethod.POST, "/api/customer/favorites",
                map("product_id", cupA), alice));

        // 7. 多选下单：生成含多订单项订单并占用库存，购物车项自动移除
        JsonNode order1 = checkout(new ArrayList<>(Arrays.asList(cupA, cupB)), alice).path("order");
        assertEquals("CUSTOMER_PLACED", order1.path("status").asText());
        assertEquals("客户下单", order1.path("status_label").asText());
        assertTrue(order1.path("order_no").asText().matches("\\d{8}-\\d{4}"),
                order1.path("order_no").asText());
        assertEquals(2, order1.path("item_count").asInt());
        assertEquals(259.6, order1.path("total").asDouble(), 0.001);
        assertEquals(25960, order1.path("total_cents").asInt());
        assertTrue(order1.path("can_cancel").asBoolean());
        JsonNode orderItemA = findItem(order1.path("items"), cupA);
        assertEquals("手工陶瓷杯", orderItemA.path("product_name").asText());
        assertEquals(99.9, orderItemA.path("price").asDouble(), 0.001);
        assertEquals(2, orderItemA.path("quantity").asInt());
        // 购物车已清空
        assertEquals(0, get("/api/customer/cart", alice).path("item_count").asInt());
        // 库存占用：A 可售 0（虽仍为在售态但不可买），B 可售 3
        JsonNode detailAfter = get("/api/products/" + cupA, null);
        assertEquals(0, detailAfter.path("product").path("available_stock").asInt());
        // 买家侧库存展示为剩余可售，不展示原始库存
        assertEquals(0, detailAfter.path("product").path("stock").asInt());
        assertFalse(detailAfter.path("can_buy").asBoolean());
        assertEquals(3, get("/api/products/" + cupB, null).path("product").path("available_stock").asInt());
        // 占用锁死可售：A 无法再加购
        assertEquals(409, status(HttpMethod.POST, "/api/customer/cart",
                map("product_id", cupA, "quantity", 1), alice));

        // 8. 缺货整单提示剔除：B 库存 3，加购 4 无法下单；调减后成功
        addCart(cupB, 4, alice);
        JsonNode insufficient = call(HttpMethod.POST, "/api/customer/orders",
                map("product_ids", Arrays.asList(cupB)), alice);
        assertTrue(insufficient.path("error").path("message").asText().contains("库存不足"),
                insufficient.toString());
        assertEquals(409, status(HttpMethod.POST, "/api/customer/orders",
                map("product_ids", Arrays.asList(cupB)), alice));
        assertEquals(200, status(HttpMethod.PATCH, "/api/customer/cart/" + cupB,
                map("quantity", 2), alice));
        JsonNode order2 = checkout(new ArrayList<>(Arrays.asList(cupB)), alice).path("order");
        assertEquals(59.8, order2.path("total").asDouble(), 0.001);
        assertEquals(1, get("/api/products/" + cupB, null).path("product").path("available_stock").asInt());

        // 9. 客户取消订单：占用回补、状态置已取消、重复取消 409
        JsonNode cancelled2 = call(HttpMethod.POST,
                "/api/customer/orders/" + order2.path("id").asText() + "/cancel", null, alice).path("order");
        assertEquals("CANCELLED", cancelled2.path("status").asText());
        assertEquals("已取消", cancelled2.path("status_label").asText());
        assertFalse(cancelled2.path("can_cancel").asBoolean());
        assertEquals(3, get("/api/products/" + cupB, null).path("product").path("available_stock").asInt());
        assertEquals(409, status(HttpMethod.POST,
                "/api/customer/orders/" + order2.path("id").asText() + "/cancel", null, alice));

        // 10. 取消首个订单：A/B 占用全部回补
        JsonNode cancelled1 = call(HttpMethod.POST,
                "/api/customer/orders/" + order1.path("id").asText() + "/cancel", null, alice).path("order");
        assertEquals("CANCELLED", cancelled1.path("status").asText());
        assertEquals(2, get("/api/products/" + cupA, null).path("product").path("available_stock").asInt());
        assertEquals(5, get("/api/products/" + cupB, null).path("product").path("available_stock").asInt());
        assertTrue(get("/api/products/" + cupA, null).path("can_buy").asBoolean());

        // 11. 订单历史：含已取消订单，字段齐全；状态筛选
        JsonNode history = get("/api/customer/orders?page_size=100", alice);
        assertEquals(2, history.path("total").asInt());
        assertEquals(2, get("/api/customer/orders?status=CANCELLED", alice).path("total").asInt());
        assertEquals(0, get("/api/customer/orders?status=COMPLETED", alice).path("total").asInt());
        JsonNode historyOrder = findOrder(history.path("items"), order1.path("id").asText());
        assertEquals("CANCELLED", historyOrder.path("status").asText());
        assertEquals(2, historyOrder.path("items").size());
        assertEquals(99.9, historyOrder.path("items").get(0).path("price").asDouble(), 0.001);

        // 12. 购物车多选转收藏：原购物车项移除
        addCart(cupA, 1, alice);
        addCart(cupB, 1, alice);
        JsonNode moved = call(HttpMethod.POST, "/api/customer/cart/favorites",
                map("product_ids", Arrays.asList(cupA, cupB)), alice);
        assertEquals(2, moved.path("moved_count").asInt());
        assertEquals(0, moved.path("item_count").asInt());
        JsonNode favoritesAfter = get("/api/customer/favorites", alice);
        assertEquals(2, favoritesAfter.path("total").asInt());

        // 13. 售罄商品不可收藏；取消收藏后重复取消 404
        assertEquals(409, status(HttpMethod.POST, "/api/customer/favorites",
                map("product_id", cupC), alice));
        assertEquals(200, status(HttpMethod.DELETE, "/api/customer/favorites/" + cupA, null, alice));
        assertEquals(404, status(HttpMethod.DELETE, "/api/customer/favorites/" + cupA, null, alice));

        // 14. 商品下架后收藏项保留并标注「已下架」
        call(HttpMethod.POST, "/api/seller/products/" + cupB + "/off-shelf", null, seller);
        JsonNode favoritesOffShelf = get("/api/customer/favorites", alice);
        JsonNode favoriteB = findItem(favoritesOffShelf.path("items"), cupB);
        assertFalse(favoriteB.path("available").asBoolean());
        assertEquals("已下架", favoriteB.path("unavailable_reason").asText());
        assertEquals(409, status(HttpMethod.POST, "/api/customer/cart",
                map("product_id", cupB, "quantity", 1), alice));
        call(HttpMethod.POST, "/api/seller/products/" + cupB + "/on-shelf", null, seller);

        // 15. 个人中心计数：购买（交易完成）0、收藏 1、购物车 0
        JsonNode me = get("/api/customer/me", alice);
        assertEquals(0, me.path("purchase_count").asInt());
        assertEquals(1, me.path("favorite_count").asInt());
        assertEquals(0, me.path("cart_item_count").asInt());

        // 16. 订单仅本人可见 / 可取消
        register("bob", "13800000002", "二号门");
        String bob = customerToken("bob");
        assertEquals(404, status(HttpMethod.GET,
                "/api/customer/orders/" + order1.path("id").asText(), null, bob));
        assertEquals(404, status(HttpMethod.POST,
                "/api/customer/orders/" + order1.path("id").asText() + "/cancel", null, bob));
        assertEquals(0, get("/api/customer/orders", bob).path("total").asInt());

        // 17. 角色隔离与游客不可下单
        assertEquals(401, status(HttpMethod.GET, "/api/customer/cart", null, seller));
        assertEquals(401, status(HttpMethod.GET, "/api/seller/products", null, alice));
        assertEquals(401, status(HttpMethod.POST, "/api/customer/orders",
                map("product_ids", Arrays.asList(cupA)), null));
    }

    private JsonNode findItem(JsonNode items, String productId) {
        for (JsonNode node : items) {
            if (productId.equals(node.path("product_id").asText())) {
                return node;
            }
        }
        throw new IllegalStateException("未找到商品项：" + productId);
    }

    private JsonNode findOrder(JsonNode items, String orderId) {
        for (JsonNode node : items) {
            if (orderId.equals(node.path("id").asText())) {
                return node;
            }
        }
        throw new IllegalStateException("未找到订单：" + orderId);
    }

    private String uploadPng(String token) {
        byte[] png = new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("file", new ByteArrayResource(png) {
            @Override
            public String getFilename() {
                return "cup.png";
            }
        });
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.setBearerAuth(token);
        ResponseEntity<String> response = rest.postForEntity(url("/api/files/images"),
                new HttpEntity<>(parts, headers), String.class);
        try {
            assertEquals(200, response.getStatusCodeValue());
            return json.readTree(response.getBody()).path("url").asText();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}