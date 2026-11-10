package com.shop;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 挑战B 店家端集成测试：订单列表/详情、单向流转（客户下单→确认→备货→发货→交易完成）、
 * 交易完成实际扣减库存、商家取消回补占用库存、发货后客户不可取消、角色隔离。
 * 独立临时数据库，避免与买家端测试共享全局计数断言。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SellerFlowIntegrationTest {

    private static Path dataDir;

    @DynamicPropertySource
    static void dataDirProperties(DynamicPropertyRegistry registry) throws IOException {
        dataDir = Files.createTempDirectory("shop-java-seller-test-");
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
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (token != null) {
                headers.setBearerAuth(token);
            }
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

    private String uploadPng(String token) {
        byte[] png = new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
        org.springframework.util.MultiValueMap<String, Object> parts
                = new org.springframework.util.LinkedMultiValueMap<>();
        parts.add("file", new org.springframework.core.io.ByteArrayResource(png) {
            @Override
            public String getFilename() {
                return "product.png";
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

    @Test
    void sellerFlowChallengeB() throws Exception {
        String seller = call(HttpMethod.POST, "/api/seller/login",
                map("username", "seller", "password", "seller123"), null).path("token").asText();
        String topId = call(HttpMethod.POST, "/api/seller/categories",
                map("name", "数码"), seller).path("category").path("id").asText();
        String leafId = call(HttpMethod.POST, "/api/seller/categories",
                map("name", "耳机", "parent_id", topId), seller).path("category").path("id").asText();
        String earphone = publish(seller, "无线耳机", 199.0, 3, leafId, new ArrayList<String>())
                .path("product").path("id").asText();

        register("carol", "13800000011", "三号门");
        String carol = customerToken("carol");
        addCart(earphone, 3, carol);
        JsonNode order = checkout(new ArrayList<>(Arrays.asList(earphone)), carol).path("order");
        String orderId = order.path("id").asText();
        assertEquals("CUSTOMER_PLACED", order.path("status").asText());
        assertEquals("客户下单", order.path("status_label").asText());
        assertEquals(0, get("/api/products/" + earphone, null)
                .path("product").path("available_stock").asInt());

        // 商家订单列表与详情：状态筛选、客户信息、订单项
        JsonNode list = get("/api/seller/orders?status=CUSTOMER_PLACED", seller);
        assertEquals(1, list.path("total").asInt());
        assertEquals(orderId, list.path("items").get(0).path("id").asText());
        assertEquals("carol", list.path("items").get(0).path("customer").path("username").asText());
        assertEquals(1, list.path("items").get(0).path("item_count").asInt());
        assertEquals(0, get("/api/seller/orders?status=COMPLETED", seller).path("total").asInt());
        JsonNode detail = get("/api/seller/orders/" + orderId, seller).path("order");
        assertEquals(3, detail.path("items").get(0).path("quantity").asInt());
        assertEquals("carol", detail.path("customer").path("username").asText());

        // 单向推进：确认→备货→发货
        assertEquals("SELLER_CONFIRMED", call(HttpMethod.POST,
                "/api/seller/orders/" + orderId + "/advance", null, seller).path("order").path("status").asText());
        assertEquals("STOCK_READY", call(HttpMethod.POST,
                "/api/seller/orders/" + orderId + "/advance", null, seller).path("order").path("status").asText());
        assertEquals("SHIPPING", call(HttpMethod.POST,
                "/api/seller/orders/" + orderId + "/advance", null, seller).path("order").path("status").asText());
        // 发货后客户不可取消
        assertEquals(409, status(HttpMethod.POST,
                "/api/customer/orders/" + orderId + "/cancel", null, carol));

        // 交易完成：库存实际扣减、不可再推进/取消
        JsonNode completed = call(HttpMethod.POST,
                "/api/seller/orders/" + orderId + "/advance", null, seller).path("order");
        assertEquals("COMPLETED", completed.path("status").asText());
        assertEquals("交易完成", completed.path("status_label").asText());
        assertEquals(0, get("/api/products/" + earphone, null)
                .path("product").path("available_stock").asInt());
        assertEquals(409, status(HttpMethod.POST,
                "/api/seller/orders/" + orderId + "/advance", null, seller));
        assertEquals(409, status(HttpMethod.POST,
                "/api/seller/orders/" + orderId + "/cancel", null, seller));

        // 商家取消（发货前）：占用回补
        String keyboard = publish(seller, "机械键盘", 299.0, 2, leafId, new ArrayList<String>())
                .path("product").path("id").asText();
        register("dave", "13800000012", "四号门");
        String dave = customerToken("dave");
        addCart(keyboard, 2, dave);
        String order2Id = checkout(new ArrayList<>(Arrays.asList(keyboard)), dave).path("order").path("id").asText();
        assertEquals(0, get("/api/products/" + keyboard, null)
                .path("product").path("available_stock").asInt());
        JsonNode cancelledSeller = call(HttpMethod.POST,
                "/api/seller/orders/" + order2Id + "/cancel", null, seller).path("order");
        assertEquals("CANCELLED", cancelledSeller.path("status").asText());
        assertEquals(2, get("/api/products/" + keyboard, null)
                .path("product").path("available_stock").asInt());
        assertEquals(409, status(HttpMethod.POST,
                "/api/seller/orders/" + order2Id + "/cancel", null, seller));

        // 角色隔离：客户不能访问商家订单接口
        assertEquals(401, status(HttpMethod.GET, "/api/seller/orders", null, carol));
        assertEquals(401, status(HttpMethod.POST,
                "/api/seller/orders/" + orderId + "/advance", null, carol));
    }
}