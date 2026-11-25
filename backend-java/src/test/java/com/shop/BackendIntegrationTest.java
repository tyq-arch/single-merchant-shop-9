package com.shop;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shop.db.Db;
import com.shop.db.Jdbc;
import com.shop.security.SecurityService;
import com.shop.services.OrderService;
import com.shop.util.Utils;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
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
 * 买家端端到端集成测试：
 * <ol>
 *   <li>挑战B：收藏、购物车、游客临时预选与登录合并、多选下单与库存占用、缺货剔除、取消回补；</li>
 *   <li>挑战C：收货地址、线上支付与失败重试、回调验签幂等、取消退款、超时取消、物流查询、售后申请。</li>
 * </ol>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
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

    @Autowired
    private Db db;

    @Autowired
    private SecurityService security;

    @Autowired
    private OrderService orderService;

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
    @Order(1)
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

        // 5. 收货地址：首条自动设为默认（挑战C 下单前置条件）
        JsonNode firstAddress = call(HttpMethod.POST, "/api/customer/addresses",
                map("receiver_name", "爱丽丝", "receiver_phone", "13800000001",
                        "province", "江苏省", "city", "南京市", "district", "鼓楼区",
                        "detail", "一号楼 101", "postal_code", "210000"), alice).path("address");
        assertTrue(firstAddress.path("is_default").asBoolean());
        assertEquals("江苏省南京市鼓楼区", firstAddress.path("region").asText());
        assertEquals(422, status(HttpMethod.POST, "/api/customer/addresses",
                map("receiver_name", "错号码", "receiver_phone", "123",
                        "province", "江苏省", "city", "南京市", "district", "鼓楼区",
                        "detail", "二号楼 202"), alice));

        // 6. 登录后：收藏含 A、购物车含 B×2
        JsonNode favorites = get("/api/customer/favorites", alice);
        assertEquals(1, favorites.path("total").asInt());
        assertEquals(cupA, favorites.path("items").get(0).path("product_id").asText());
        assertTrue(favorites.path("items").get(0).path("available").asBoolean());
        JsonNode cart = get("/api/customer/cart", alice);
        assertEquals(1, cart.path("item_count").asInt());
        assertEquals(2, cart.path("items").get(0).path("quantity").asInt());
        assertEquals(59.8, cart.path("items").get(0).path("subtotal").asDouble(), 0.001);

        // 7. 购物车：同商品唯一、数量累加、上限 99
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

        // 8. 多选下单：未传地址时带出默认地址，订单进入待支付并占用库存
        JsonNode order1 = checkout(new ArrayList<>(Arrays.asList(cupA, cupB)), alice).path("order");
        assertEquals("PENDING_PAYMENT", order1.path("status").asText());
        assertEquals("待支付", order1.path("status_label").asText());
        assertTrue(order1.path("can_pay").asBoolean());
        assertEquals("一号楼 101", order1.path("address").path("detail").asText());
        assertEquals("UNPAID", order1.path("payment_status").asText());
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

    @Test
    @Order(2)
    void buyerFlowChallengeC() throws Exception {
        String seller = call(HttpMethod.POST, "/api/seller/login",
                map("username", "seller", "password", "seller123"), null).path("token").asText();
        String topId = call(HttpMethod.POST, "/api/seller/categories",
                map("name", "生活电器"), seller).path("category").path("id").asText();
        String leafId = call(HttpMethod.POST, "/api/seller/categories",
                map("name", "电水壶", "parent_id", topId), seller).path("category").path("id").asText();
        String kettle = publish(seller, "不锈钢电水壶", 50, 10, leafId, new ArrayList<String>())
                .path("product").path("id").asText();

        register("carol", "13800000003", "三号门");
        String carol = customerToken("carol");

        // 1. 收货地址为空时下单被拒（FR-048）
        assertEquals(0, get("/api/customer/addresses", carol).path("total").asInt());
        addCart(kettle, 1, carol);
        JsonNode noAddress = call(HttpMethod.POST, "/api/customer/orders",
                map("product_ids", Arrays.asList(kettle)), carol);
        assertEquals(409, status(HttpMethod.POST, "/api/customer/orders",
                map("product_ids", Arrays.asList(kettle)), carol));
        assertTrue(noAddress.path("error").path("message").asText().contains("收货地址"),
                noAddress.toString());

        // 2. 地址簿：首条自动默认、可切换默认、可修改、可删除
        String home = createAddress(carol, "张卡罗", "13800000003", "上海市", "上海市", "徐汇区",
                "漕溪路 1 号", true);
        String office = createAddress(carol, "张卡罗", "13800000003", "上海市", "上海市", "浦东新区",
                "世纪大道 2 号", false);
        assertEquals(200, status(HttpMethod.POST, "/api/customer/addresses/" + office + "/default",
                null, carol));
        assertEquals(200, status(HttpMethod.PATCH, "/api/customer/addresses/" + home,
                map("receiver_name", "张卡罗", "receiver_phone", "13800000003",
                        "province", "上海市", "city", "上海市", "district", "徐汇区",
                        "detail", "漕溪路 9 号"), carol));
        JsonNode addressList = get("/api/customer/addresses", carol);
        assertEquals(2, addressList.path("total").asInt());
        assertTrue(findAddress(addressList.path("items"), office).path("is_default").asBoolean());
        assertFalse(findAddress(addressList.path("items"), home).path("is_default").asBoolean());
        assertEquals(422, status(HttpMethod.POST, "/api/customer/addresses",
                map("receiver_name", "张卡罗", "receiver_phone", "13800000003",
                        "province", "上海市", "city", "上海市", "district", "徐汇区",
                        "detail", "漕溪路 1 号", "postal_code", "12345"), carol));

        // 3. 下单固化默认地址快照，订单进入待支付
        JsonNode order = checkout(Arrays.asList(kettle), carol).path("order");
        assertEquals("PENDING_PAYMENT", order.path("status").asText());
        assertEquals("浦东新区", order.path("address").path("district").asText());
        assertEquals("世纪大道 2 号", order.path("address").path("detail").asText());
        String orderId = order.path("id").asText();
        String orderItemId = order.path("items").get(0).path("id").asText();
        assertEquals(9, available(kettle));

        // 4. 支付：非法方式 422；失败保持待支付；成功后转已支付；重复支付 409
        assertEquals(422, status(HttpMethod.POST, "/api/customer/orders/" + orderId + "/pay",
                map("payment_method", "CASH"), carol));
        JsonNode failed = call(HttpMethod.POST, "/api/customer/orders/" + orderId + "/pay",
                map("payment_method", "ALIPAY", "simulate_result", "FAILED"), carol);
        assertEquals("FAILED", failed.path("payment").path("status").asText());
        assertEquals("PENDING_PAYMENT", failed.path("order").path("status").asText());
        assertEquals("UNPAID", failed.path("order").path("payment_status").asText());
        JsonNode paid = call(HttpMethod.POST, "/api/customer/orders/" + orderId + "/pay",
                map("payment_method", "ALIPAY"), carol);
        assertEquals("PAID", paid.path("order").path("status").asText());
        assertEquals("已支付", paid.path("order").path("status_label").asText());
        assertEquals("PAID", paid.path("order").path("payment_status").asText());
        assertFalse(paid.path("order").path("can_pay").asBoolean());
        assertEquals(50, paid.path("payment").path("amount").asDouble(), 0.001);
        assertTrue(paid.path("payment").path("payment_no").asText().startsWith("PAY"));
        assertTrue(paid.path("payment").path("transaction_no").asText().startsWith("TXN"));
        assertEquals(409, status(HttpMethod.POST, "/api/customer/orders/" + orderId + "/pay",
                map("payment_method", "WECHAT"), carol));

        // 5. 支付回调：签名错误 401，签名正确幂等（NFR-013）
        String paymentId = paid.path("payment").path("id").asText();
        assertEquals(401, status(HttpMethod.POST, "/api/payments/callback",
                map("payment_id", paymentId, "transaction_no", "TXN-REPLAY",
                        "status", "SUCCESS", "signature", "deadbeef"), null));
        JsonNode callback = call(HttpMethod.POST, "/api/payments/callback",
                map("payment_id", paymentId, "transaction_no", "TXN-REPLAY", "status", "SUCCESS",
                        "signature", security.hmacHex(paymentId + "|SUCCESS|TXN-REPLAY")), null);
        assertTrue(callback.path("idempotent").asBoolean());

        // 6. 已支付订单取消：自动原路退款，占用库存回补（FR-044 / FR-045）
        JsonNode cancelled = call(HttpMethod.POST, "/api/customer/orders/" + orderId + "/cancel",
                null, carol).path("order");
        assertEquals("CANCELLED", cancelled.path("status").asText());
        assertEquals("REFUNDED", cancelled.path("payment_status").asText());
        assertEquals("已退款", cancelled.path("payment_status_label").asText());
        assertEquals("SUCCESS", cancelled.path("refund").path("status").asText());
        assertEquals(50, cancelled.path("refund").path("amount").asDouble(), 0.001);
        assertEquals("ALIPAY", cancelled.path("refund").path("channel").asText());
        assertTrue(cancelled.path("refund").path("refund_no").asText().startsWith("RF"));
        assertEquals(10, available(kettle));

        // 7. 待支付超过 24 小时：系统自动取消并回补库存（FR-043）
        addCart(kettle, 2, carol);
        String pendingId = checkout(Arrays.asList(kettle), carol).path("order").path("id").asText();
        assertEquals(8, available(kettle));
        db.transaction(conn -> {
            Jdbc.update(conn, "UPDATE shop_order SET created_at = ? WHERE id = ?",
                    Utils.toIso(Utils.now().minusHours(25)), pendingId);
            return null;
        });
        assertEquals(1, orderService.expirePendingOrders());
        JsonNode expired = get("/api/customer/orders/" + pendingId, carol).path("order");
        assertEquals("CANCELLED", expired.path("status").asText());
        assertEquals("SYSTEM", expired.path("cancel_by").asText());
        assertTrue(expired.path("cancel_reason").asText().contains("24"));
        assertEquals(10, available(kettle));

        // 8. 物流查询：未发货 404；发货后可查服务商、运单号与轨迹（FR-050）
        addCart(kettle, 1, carol);
        String shippingOrderId = checkout(Arrays.asList(kettle), carol).path("order").path("id").asText();
        assertEquals(404, status(HttpMethod.GET,
                "/api/customer/orders/" + shippingOrderId + "/shipment", null, carol));
        String now = Utils.nowIso();
        db.transaction(conn -> {
            Jdbc.update(conn, "UPDATE shop_order SET status = ? WHERE id = ?",
                    "SHIPPING", shippingOrderId);
            String shipmentId = Utils.newId();
            Jdbc.update(conn,
                    "INSERT INTO shipment (id, order_id, carrier, tracking_no, status, shipped_at, updated_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                    shipmentId, shippingOrderId, "顺丰速运", "SF1234567890", "IN_TRANSIT", now, now);
            Jdbc.update(conn,
                    "INSERT INTO shipment_track (id, shipment_id, status, description, occurred_at) "
                            + "VALUES (?, ?, ?, ?, ?)",
                    Utils.newId(), shipmentId, "SHIPPED", "商家已发货", now);
            Jdbc.update(conn,
                    "INSERT INTO shipment_track (id, shipment_id, status, description, occurred_at) "
                            + "VALUES (?, ?, ?, ?, ?)",
                    Utils.newId(), shipmentId, "IN_TRANSIT", "快件已到达转运中心", Utils.toIso(Utils.now().plusHours(1)));
            return null;
        });
        JsonNode shipment = get("/api/customer/orders/" + shippingOrderId + "/shipment", carol)
                .path("shipment");
        assertEquals("顺丰速运", shipment.path("carrier").asText());
        assertEquals("SF1234567890", shipment.path("tracking_no").asText());
        assertEquals("运输中", shipment.path("status_label").asText());
        assertEquals(2, shipment.path("tracks").size());
        JsonNode shippedOrder = get("/api/customer/orders/" + shippingOrderId, carol).path("order");
        assertEquals("IN_TRANSIT", shippedOrder.path("shipment_status").asText());
        assertFalse(shippedOrder.path("can_cancel").asBoolean());
        assertEquals(409, status(HttpMethod.POST,
                "/api/customer/orders/" + shippingOrderId + "/cancel", null, carol));

        // 9. 售后：仅交易完成可申请；图片格式与数量校验；他人不可见（FR-051 / FR-052）
        assertEquals(409, status(HttpMethod.POST, "/api/customer/after-sales",
                map("order_item_id", orderItemId, "title", "漏水", "description", "壶嘴漏水"), carol));
        db.transaction(conn -> {
            Jdbc.update(conn, "UPDATE shop_order SET status = ? WHERE id = ?", "COMPLETED", shippingOrderId);
            return null;
        });
        String itemId = get("/api/customer/orders/" + shippingOrderId, carol)
                .path("order").path("items").get(0).path("id").asText();
        assertEquals(422, status(HttpMethod.POST, "/api/customer/after-sales",
                map("order_item_id", itemId, "title", "漏水", "description", "壶嘴漏水",
                        "image_urls", Arrays.asList("/uploads/a.pdf")), carol));
        JsonNode afterSale = call(HttpMethod.POST, "/api/customer/after-sales",
                map("order_item_id", itemId, "title", "漏水", "description", "壶嘴漏水，申请换货",
                        "image_urls", Arrays.asList("/uploads/a.png")), carol).path("after_sale");
        assertEquals("PENDING", afterSale.path("status").asText());
        assertEquals("待处理", afterSale.path("status_label").asText());
        assertTrue(afterSale.path("after_sale_no").asText().startsWith("AS"));
        assertEquals("不锈钢电水壶", afterSale.path("product_name").asText());
        assertEquals(1, afterSale.path("images").size());
        assertEquals(1, get("/api/customer/after-sales", carol).path("total").asInt());
        assertEquals(200, status(HttpMethod.GET,
                "/api/customer/after-sales/" + afterSale.path("id").asText(), null, carol));

        register("dave", "13800000004", "四号门");
        String dave = customerToken("dave");
        assertEquals(404, status(HttpMethod.GET,
                "/api/customer/after-sales/" + afterSale.path("id").asText(), null, dave));
        assertEquals(409, status(HttpMethod.POST,
                "/api/customer/orders/" + shippingOrderId + "/cancel", null, carol));
    }

    private String createAddress(String token, String name, String phone, String province, String city,
                                 String district, String detail, boolean isDefault) {
        return call(HttpMethod.POST, "/api/customer/addresses",
                map("receiver_name", name, "receiver_phone", phone, "province", province, "city", city,
                        "district", district, "detail", detail, "is_default", isDefault), token)
                .path("address").path("id").asText();
    }

    private JsonNode findAddress(JsonNode items, String addressId) {
        for (JsonNode node : items) {
            if (addressId.equals(node.path("id").asText())) {
                return node;
            }
        }
        throw new IllegalStateException("未找到地址：" + addressId);
    }

    private int available(String productId) {
        return get("/api/products/" + productId, null).path("product").path("available_stock").asInt();
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
        if (token != null) {
            headers.setBearerAuth(token);
        }
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