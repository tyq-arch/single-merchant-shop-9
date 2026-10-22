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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 挑战A升级版端到端集成测试：多商品、二级类别、客户账号、库存驱动上下架、
 * 每商品独立队列、单动作失败处理、售罄作废、客户历史与商家台账。
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

    private JsonNode call(HttpMethod method, String path, Object body, String token) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (token != null) {
                headers.setBearerAuth(token);
            }
            ResponseEntity<String> response = rest.exchange(url(path), method,
                    new HttpEntity<>(body == null ? null : json.writeValueAsString(body), headers), String.class);
            return json.readTree(response.getBody() == null ? "{}" : response.getBody());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private int status(HttpMethod method, String path, Object body, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        try {
            return rest.exchange(url(path), method,
                    new HttpEntity<>(body == null ? null : json.writeValueAsString(body), headers),
                    String.class).getStatusCodeValue();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private JsonNode get(String path, String token) {
        return call(HttpMethod.GET, path, null, token);
    }

    private HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
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
                map("username", username, "password", "pass1234", "phone", phone,
                        "trade_location", location), null);
    }

    private String customerToken(String username) {
        return call(HttpMethod.POST, "/api/customer/login",
                map("username", username, "password", "pass1234"), null).path("token").asText();
    }

    private JsonNode publish(String token, String name, double price, int stock,
                             String categoryId, List<String> images) {
        Map<String, Object> body = map("name", name, "price", price, "stock", stock,
                "category_id", categoryId, "description", "<p>" + name + " 富媒体描述</p>");
        body.put("image_urls", images);
        return call(HttpMethod.POST, "/api/seller/products", body, token);
    }

    private JsonNode submitIntent(String productId, String token) {
        return call(HttpMethod.POST, "/api/customer/intents", map("product_id", productId), token);
    }

    private JsonNode startTransaction(String productId, String seller) {
        return call(HttpMethod.POST, "/api/seller/products/" + productId + "/start-transaction", null, seller);
    }

    private JsonNode mark(String intentId, String result, String failedAction, String seller) {
        Map<String, Object> body = map("result", result);
        if (failedAction != null) {
            body.put("failed_action", failedAction);
        }
        return call(HttpMethod.POST, "/api/seller/intents/" + intentId + "/transaction", body, seller);
    }

    // --------------------------- 测试主体 ---------------------------

    @Test
    void upgradedBusinessFlow() throws Exception {
        // 0. 服务信息与卖家登录
        assertEquals("/docs", get("/", null).path("docs").asText());
        String seller = call(HttpMethod.POST, "/api/seller/login",
                map("username", "seller", "password", "seller123"), null).path("token").asText();
        assertTrue(seller.length() > 0);
        assertEquals(401, status(HttpMethod.POST, "/api/seller/login",
                map("username", "seller", "password", "bad"), null));

        // 1. 二级类别：仅两级、同级不可重名
        String topId = call(HttpMethod.POST, "/api/seller/categories",
                map("name", "厨房用品"), seller).path("category").path("id").asText();
        JsonNode leaf = call(HttpMethod.POST, "/api/seller/categories",
                map("name", "杯子", "parent_id", topId), seller);
        String leafId = leaf.path("category").path("id").asText();
        assertEquals(2, leaf.path("category").path("level").asInt());
        assertEquals(422, status(HttpMethod.POST, "/api/seller/categories",
                map("name", "陶瓷杯", "parent_id", leafId), seller));
        assertEquals(409, status(HttpMethod.POST, "/api/seller/categories",
                map("name", "杯子", "parent_id", topId), seller));
        assertEquals(1, get("/api/categories", null).path("items").size());
        assertEquals(1, get("/api/categories", null).path("items").get(0).path("children").size());

        // 2. 客户注册 / 登录 / 重名
        assertEquals("注册成功，请登录", register("alice", "13800000001", "一号门").path("message").asText());
        assertTrue(register("alice", "13800000002", "二号门").path("error").path("message").asText()
                .contains("已被注册"));
        register("bob", "13800000002", "二号门");
        register("carol", "13800000003", "三号门");
        register("dave", "13800000004", "四号门");
        String alice = customerToken("alice");
        String bob = customerToken("bob");
        String carol = customerToken("carol");
        String dave = customerToken("dave");
        assertEquals(401, status(HttpMethod.POST, "/api/customer/login",
                map("username", "alice", "password", "wrong"), null));

        // 3. 买卖双方角色隔离
        assertEquals(401, status(HttpMethod.GET, "/api/seller/products", null, alice));
        assertEquals(401, status(HttpMethod.GET, "/api/customer/me", null, seller));

        // 4. 多图上传 + 多商品同时在售
        String imageUrl = uploadPng();
        JsonNode ceramic = publish(seller, "手工陶瓷杯", 99.9, 2, leafId,
                new ArrayList<>(Arrays.asList(imageUrl)));
        String ceramicId = ceramic.path("product").path("id").asText();
        assertEquals("ON_SALE", ceramic.path("product").path("status").asText());
        String cupId = publish(seller, "玻璃杯", 29.9, 5, leafId, new ArrayList<String>())
                .path("product").path("id").asText();
        assertEquals(2, get("/api/products", null).path("total").asInt());
        assertEquals(1, get("/api/products?keyword=陶瓷", null).path("total").asInt());
        assertEquals(2, get("/api/products?category_id=" + topId, null).path("total").asInt());
        JsonNode ceramicDetail = get("/api/products/" + ceramicId, null);
        assertEquals(1, ceramicDetail.path("product").path("image_count").asInt());
        assertTrue(ceramicDetail.path("can_buy").asBoolean());
        assertEquals("/uploads/", imageUrl.substring(0, 9));
        // 库存为 0 直接发布 -> 售罄下架，不进入公开列表
        assertEquals("SOLD_OUT", publish(seller, "无货商品", 10, 0, leafId, new ArrayList<String>())
                .path("product").path("status").asText());
        assertEquals(2, get("/api/products", null).path("total").asInt());

        // 5. 游客不能下单；登录客户按先到先得排队；同商品仅一个有效意向
        assertEquals(401, status(HttpMethod.POST, "/api/customer/intents",
                map("product_id", ceramicId), null));
        JsonNode first = submitIntent(ceramicId, alice);
        String aliceIntent = first.path("intent").path("id").asText();
        assertEquals(1, first.path("queue_position").asInt());
        assertEquals("排队中", first.path("intent").path("status_label").asText());
        assertEquals(2, submitIntent(ceramicId, bob).path("queue_position").asInt());
        assertEquals(409, status(HttpMethod.POST, "/api/customer/intents",
                map("product_id", ceramicId), alice));
        assertEquals(2, get("/api/seller/products/" + ceramicId + "/intents", seller).path("total").asInt());
        // 每商品独立队列
        assertEquals(1, submitIntent(cupId, alice).path("queue_position").asInt());

        // 6. 进入交易 -> 自动冻结，暂停新意向
        JsonNode started = startTransaction(ceramicId, seller);
        assertEquals(aliceIntent, started.path("intent").path("id").asText());
        assertEquals("FROZEN", started.path("product").path("status").asText());
        assertEquals(409, status(HttpMethod.POST, "/api/customer/intents",
                map("product_id", ceramicId), carol));
        assertEquals(409, status(HttpMethod.POST,
                "/api/seller/products/" + ceramicId + "/start-transaction", null, seller));

        // 7. 交易失败 + 同一动作重新排到队尾，商品恢复在售
        JsonNode failed = call(HttpMethod.POST, "/api/seller/intents/" + aliceIntent + "/transaction",
                map("result", "FAILED", "note", "未到场", "failed_action", "REQUEUE"), seller);
        assertEquals("REQUEUED", failed.path("intent").path("status").asText());
        assertEquals(2, failed.path("intent").path("queue_position").asInt());
        assertEquals(1, failed.path("intent").path("requeue_count").asInt());
        assertEquals("ON_SALE", failed.path("product").path("status").asText());

        // 8. 失败必须带处理方式：422 且事务回滚
        startTransaction(ceramicId, seller);
        String bobIntent = queueHead(ceramicId, seller);
        assertEquals(422, status(HttpMethod.POST,
                "/api/seller/intents/" + bobIntent + "/transaction", map("result", "FAILED"), seller));
        assertEquals(1, get("/api/seller/products/" + ceramicId + "/intents?status=IN_TRANSACTION", seller)
                .path("total").asInt());
        // 选择作废失败者
        JsonNode voided = mark(bobIntent, "FAILED", "VOID", seller);
        assertEquals("VOIDED", voided.path("intent").path("status").asText());
        assertEquals("ON_SALE", voided.path("product").path("status").asText());

        // 9. A 成交：库存 2 -> 1，商品恢复在售
        startTransaction(ceramicId, seller);
        String aliceIntentAgain = queueHead(ceramicId, seller);
        JsonNode aliceSuccess = mark(aliceIntentAgain, "SUCCESS", null, seller);
        assertEquals("SUCCESS", aliceSuccess.path("intent").path("status").asText());
        assertEquals(1, aliceSuccess.path("product").path("stock").asInt());
        assertEquals("ON_SALE", aliceSuccess.path("product").path("status").asText());

        // 10. 成交后库存归零 -> 自动下架，剩余排队意向售罄作废
        assertEquals(1, submitIntent(ceramicId, carol).path("queue_position").asInt());
        assertEquals(2, submitIntent(ceramicId, dave).path("queue_position").asInt());
        startTransaction(ceramicId, seller);
        String carolIntent = queueHead(ceramicId, seller);
        JsonNode soldOut = mark(carolIntent, "SUCCESS", null, seller);
        assertEquals(0, soldOut.path("product").path("stock").asInt());
        assertEquals("SOLD_OUT", soldOut.path("product").path("status").asText());
        assertEquals("交易成功，库存已扣减至 0，商品自动下架，剩余排队意向已售罄作废",
                soldOut.path("message").asText());
        assertEquals(1, get("/api/seller/products/" + ceramicId + "/intents?status=SOLD_OUT", seller)
                .path("total").asInt());
        assertEquals(0, get("/api/products?keyword=陶瓷", null).path("total").asInt());
        // 售罄商品不能交易、不能提交
        assertEquals(409, status(HttpMethod.POST,
                "/api/seller/products/" + ceramicId + "/start-transaction", null, seller));

        // 11. 手动下架 / 重新上架 / 库存调整
        JsonNode off = call(HttpMethod.POST, "/api/seller/products/" + cupId + "/off-shelf", null, seller);
        assertEquals("OFF_SHELF", off.path("product").path("status").asText());
        assertEquals(409, status(HttpMethod.POST, "/api/customer/intents", map("product_id", cupId), carol));
        JsonNode on = call(HttpMethod.POST, "/api/seller/products/" + cupId + "/on-shelf", null, seller);
        assertEquals("ON_SALE", on.path("product").path("status").asText());
        JsonNode zero = call(HttpMethod.PATCH,
                "/api/seller/products/" + cupId + "/stock", map("stock", 0), seller);
        assertEquals("SOLD_OUT", zero.path("product").path("status").asText());
        JsonNode restocked = call(HttpMethod.PATCH,
                "/api/seller/products/" + cupId + "/stock", map("stock", 3), seller);
        assertEquals("ON_SALE", restocked.path("product").path("status").asText());
        assertEquals(3, restocked.path("product").path("stock").asInt());
        // 手动冻结 / 解冻（TBD-15 保留）
        JsonNode frozen = call(HttpMethod.POST, "/api/seller/products/" + cupId + "/freeze", null, seller);
        assertEquals("FROZEN", frozen.path("product").path("status").asText());
        assertEquals(409, status(HttpMethod.POST, "/api/customer/intents", map("product_id", cupId), carol));
        JsonNode unfrozen = call(HttpMethod.POST, "/api/seller/products/" + cupId + "/unfreeze", null, seller);
        assertEquals("ON_SALE", unfrozen.path("product").path("status").asText());
        assertEquals(409, status(HttpMethod.POST, "/api/seller/products/" + cupId + "/unfreeze", null, seller));

        // 队列名单导出 CSV
        ResponseEntity<String> csv = rest.exchange(url("/api/seller/products/" + ceramicId + "/intents/export"),
                HttpMethod.GET, new HttpEntity<>(authHeaders(seller)), String.class);
        assertEquals(200, csv.getStatusCodeValue());
        assertTrue(csv.getBody().contains("用户名,电话,默认交易地点"));

        // 12. 客户历史下单记录
        JsonNode orders = get("/api/customer/orders?page_size=100", alice);
        assertEquals(2, orders.path("total").asInt());
        JsonNode ceramicOrder = null;
        for (JsonNode node : orders.path("items")) {
            if (ceramicId.equals(node.path("product_id").asText())) {
                ceramicOrder = node;
            }
        }
        assertNotNull(ceramicOrder);
        assertEquals("SUCCESS", ceramicOrder.path("status").asText());
        assertEquals("交易成功", ceramicOrder.path("transaction").path("result_label").asText());
        // 客户意向详情仅本人可见
        assertEquals(404, status(HttpMethod.GET, "/api/customer/intents/" + aliceIntentAgain, null, bob));

        // 13. 商家客户台账
        JsonNode ledger = get("/api/seller/customers?page_size=100", seller);
        assertEquals(4, ledger.path("total").asInt());
        JsonNode aliceRow = null;
        for (JsonNode node : ledger.path("items")) {
            if ("alice".equals(node.path("username").asText())) {
                aliceRow = node;
            }
        }
        assertNotNull(aliceRow);
        assertEquals("13800000001", aliceRow.path("phone").asText());
        assertEquals("一号门", aliceRow.path("trade_location").asText());
        assertEquals(1, aliceRow.path("purchase_count").asInt());
        JsonNode aliceDetail = get("/api/seller/customers/" + aliceRow.path("id").asText(), seller);
        assertEquals(2, aliceDetail.path("orders").size());

        // 14. 历史记录：售罄商品 + 多轮交易
        JsonNode history = get("/api/seller/history?page_size=100", seller);
        JsonNode historyItem = null;
        for (JsonNode node : history.path("items")) {
            if (ceramicId.equals(node.path("id").asText())) {
                historyItem = node;
            }
        }
        assertNotNull(historyItem);
        assertEquals(2, historyItem.path("transaction_summary").path("failed_rounds").asInt());
        assertEquals(4, historyItem.path("transaction_summary").path("buyer_count").asInt());
        JsonNode detail = get("/api/seller/history/" + ceramicId, seller);
        JsonNode transactions = detail.path("transactions");
        assertEquals(4, transactions.size());
        assertEquals("FAILED", transactions.get(0).path("result").asText());
        assertEquals("SUCCESS", transactions.get(3).path("result").asText());

        // 15. 类别删除约束与重命名
        assertEquals(409, status(HttpMethod.DELETE, "/api/seller/categories/" + leafId, null, seller));
        JsonNode renamed = call(HttpMethod.PATCH, "/api/seller/categories/" + leafId,
                map("name", "水杯"), seller);
        assertEquals("水杯", renamed.path("category").path("name").asText());
        String emptyCategory = call(HttpMethod.POST, "/api/seller/categories",
                map("name", "临时类别"), seller).path("category").path("id").asText();
        assertEquals(200, status(HttpMethod.DELETE, "/api/seller/categories/" + emptyCategory, null, seller));

        // 16. 卖家改密（至少 8 位）与操作日志
        assertEquals(422, status(HttpMethod.POST, "/api/seller/password",
                map("old_password", "seller123", "new_password", "1234567"), seller));
        assertEquals(401, status(HttpMethod.POST, "/api/seller/password",
                map("old_password", "wrong", "new_password", "12345678"), seller));
        JsonNode logs = get("/api/seller/logs?page_size=100", seller);
        StringBuilder actions = new StringBuilder();
        for (JsonNode node : logs.path("items")) {
            actions.append(node.path("action").asText()).append(',');
        }
        assertTrue(actions.toString().contains("PUBLISH_PRODUCT"), actions.toString());
        assertTrue(actions.toString().contains("TRANSACTION_SUCCESS"), actions.toString());
        assertTrue(actions.toString().contains("ADJUST_STOCK"), actions.toString());
    }

    private String uploadPng() {
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
        ResponseEntity<String> response = rest.postForEntity(url("/api/files/images"),
                new HttpEntity<>(parts, headers), String.class);
        try {
            assertEquals(200, response.getStatusCodeValue());
            return json.readTree(response.getBody()).path("url").asText();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** 读取当前进入交易的意向 ID（队列首位）。 */
    private String queueHead(String productId, String sellerToken) {
        JsonNode queue = get("/api/seller/products/" + productId + "/intents?status=IN_TRANSACTION", sellerToken);
        return queue.path("items").get(0).path("id").asText();
    }
}
