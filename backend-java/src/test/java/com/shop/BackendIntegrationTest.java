/**
 * 说明：本文件是"挑战A升级版"后端的端到端（End-to-End，E2E）集成测试。
 *
 * 什么叫"集成测试"？
 *   - 它不像单元测试那样只测某个函数，而是会真正把整个 Spring Boot 应用启动起来，
 *     通过 HTTP 请求去调用真实的接口，再检查接口返回的数据是否符合预期。
 *   - 它覆盖一条从"卖家发布商品"到"客户买走商品"的完整业务链路。
 *
 * 本测试用注释逐段解释了系统新增的核心能力：
 *   多商品在售、二级类别、客户账号、库存驱动上下架、每商品独立排队、
 *   交易失败重排队/作废、售罄自动下架、客户历史下单、商家台账等。
 */
package com.shop;

import com.fasterxml.jackson.databind.JsonNode;      // 代表一个 JSON 节点，方便我们读取接口返回的嵌套数据
import com.fasterxml.jackson.databind.ObjectMapper;  // 用来把字符串转成 JSON 对象 / 把 Java 对象序列化为 JSON
import org.junit.jupiter.api.Test;                   // JUnit5 的测试注解，被标注的方法即被视为一个测试用例
import org.springframework.beans.factory.annotation.Autowired; // 自动注入 Spring 容器中的对象
import org.springframework.boot.test.context.SpringBootTest;        // 启动整个 Spring 应用上下文来测试
import org.springframework.boot.test.web.client.TestRestTemplate;  // 专用于测试的 HTTP 客户端，可向应用发请求
import org.springframework.boot.web.server.LocalServerPort;        // 注入应用实际占用的随机端口号
import org.springframework.core.io.ByteArrayResource;              // 把内存中的字节数组包装成可上传的文件资源
import org.springframework.http.HttpEntity;                        // HTTP 请求实体（携带请求体 + 请求头）
import org.springframework.http.HttpHeaders;                       // 保存 HTTP 请求头（如 Content-Type、Token）
import org.springframework.http.HttpMethod;                        // HTTP 请求方法枚举（GET / POST / DELETE 等）
import org.springframework.http.MediaType;                         // 媒体类型常量（如 application/json）
import org.springframework.http.ResponseEntity;                    // HTTP 响应对象（含状态码 + 响应体）
import org.springframework.test.context.DynamicPropertyRegistry;   // 运行时动态注册配置属性的工具
import org.springframework.test.context.DynamicPropertySource;      // 标注动态配置来源，覆盖默认配置
import org.springframework.util.LinkedMultiValueMap;                // 多值 Map（用于构造 multipart 表单数据）
import org.springframework.util.MultiValueMap;                      // 多值 Map 接口

import java.io.IOException;          // IO 异常
import java.nio.file.Files;         // 文件操作工具（这里用于创建临时目录）
import java.nio.file.Path;          // 代表文件或目录的路径
import java.util.ArrayList;         // 动态数组（列表）
import java.util.Arrays;            // 数组工具（把普通数组转成 List）
import java.util.HashMap;           // 键值对集合（Map 的实现类）
import java.util.List;              // 列表接口
import java.util.Map;               // 键值对接口

// 静态导入断言方法，方便直接写 assertEquals(...) 而不用写 Assertions.assertEquals(...)
import static org.junit.jupiter.api.Assertions.assertEquals;   // 断言"相等"，不等则测试失败
import static org.junit.jupiter.api.Assertions.assertNotNull;  // 断言"不为空"，为空则测试失败
import static org.junit.jupiter.api.Assertions.assertTrue;     // 断言"为真"，不真则测试失败

/**
 * 挑战A升级版端到端集成测试：多商品、二级类别、客户账号、库存驱动上下架、
 * 每商品独立队列、单动作失败处理、售罄作废、客户历史与商家台账。
 */
// @SpringBootTest：让测试真正启动整个 Spring Boot 应用。
// webEnvironment.RANDOM_PORT：使用一个随机端口启动，避免和真实运行的端口冲突，
// 测试结束后自动关闭。
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BackendIntegrationTest {

    // 静态字段：保存测试用的临时数据目录（static 是因为 @DynamicPropertySource 方法是静态的）
    private static Path dataDir;

    // @DynamicPropertySource：在应用启动前动态覆盖配置属性。
    // 这里把数据目录/数据库/上传目录全部改到测试专用的临时目录中，
    // 这样测试不会污染真实的数据库，且每次测试都是全新环境。
    @DynamicPropertySource
    static void dataDirProperties(DynamicPropertyRegistry registry) throws IOException {
        // 创建一个以 "shop-java-test-" 开头的临时目录（系统会在合适时机自动清理）
        dataDir = Files.createTempDirectory("shop-java-test-");
        // 覆盖数据目录配置
        registry.add("SHOP_DATA_DIR", () -> dataDir.toString());
        // 把数据库文件放在临时目录下，路径为 临时目录/shop.db
        registry.add("SHOP_DB_PATH", () -> dataDir.resolve("shop.db").toString());
        // 把商品图片上传目录放到临时目录下
        registry.add("SHOP_UPLOAD_DIR", () -> dataDir.resolve("uploads").toString());
    }

    // @LocalServerPort：自动注入上面随机分配的端口号，用于拼出完整的请求地址
    @LocalServerPort
    private int port;

    // @Autowired：自动注入 Spring 提供的 HTTP 测试客户端，用来发真实 HTTP 请求
    @Autowired
    private TestRestTemplate rest;

    // @Autowired：自动注入 Jackson 的 JSON 转换器，用于解析接口返回的 JSON
    @Autowired
    private ObjectMapper json;

    // --------------------------- 请求工具 ---------------------------
    // 下面这些是封装好的"小工具方法"，把重复的 HTTP 请求代码抽出来复用，
    // 让后面的测试代码更简洁、可读。

    /** 拼接出完整的接口地址，例如 url("/api/customer/login") -> http://127.0.0.1:端口/api/customer/login */
    private String url(String path) {
        return "http://127.0.0.1:" + port + path;
    }

    /**
     * 发送一个 HTTP 请求并返回解析后的 JSON 结果（核心工具方法）。
     *
     * @param method HTTP 方法，如 GET / POST
     * @param path   接口路径，如 /api/customer/login
     * @param body   请求体（Java 对象，下面会被转成 JSON），可为 null
     * @param token  卖家或客户的登录 Token，登录验证时需要，可为 null
     * @return 接口返回内容解析成的 JSON 节点
     */
    private JsonNode call(HttpMethod method, String path, Object body, String token) {
        try {
            HttpHeaders headers = new HttpHeaders();
            // 告诉服务器请求体是 JSON 格式
            headers.setContentType(MediaType.APPLICATION_JSON);
            // 如果传了 token，就放进请求头的 Bearer 认证字段里（Authorization: Bearer xxx）
            if (token != null) {
                headers.setBearerAuth(token);
            }
            // body == null ? null : ... 表示"没有请求体时传 null，有则先序列化为 JSON 字符串"
            ResponseEntity<String> response = rest.exchange(url(path), method,
                    new HttpEntity<>(body == null ? null : json.writeValueAsString(body), headers), String.class);
            // 把返回的字符串解析成 JSON 节点；若返回体为空则用 "{}" 代替
            return json.readTree(response.getBody() == null ? "{}" : response.getBody());
        } catch (Exception e) {
            // 任何异常都包装成运行时异常抛出，让测试失败并显示原因
            throw new IllegalStateException(e);
        }
    }

    /**
     * 发送请求并返回 HTTP 状态码（比如 200 成功、401 未授权、422 参数不合法、409 冲突）。
     * 用于测试"某些非法操作应当返回什么错误码"。
     */
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

    /** 发送 GET 请求，返回 JSON 结果（Get 请求通常没有请求体） */
    private JsonNode get(String path, String token) {
        return call(HttpMethod.GET, path, null, token);
    }

    /** 构造只带 token 的请求头（CSV 导出等需要返回原始响应的场景使用） */
    private HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    /**
     * 把"键值对"数组转成 Map 对象，用于构造请求体。
     * 例如 map("name", "杯子", "price", 99.9) 会变成 {name=杯子, price=99.9}。
     */
    private Map<String, Object> map(Object... pairs) {
        Map<String, Object> result = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) { // 每两个参数为一对（键、值）
            result.put((String) pairs[i], pairs[i + 1]);
        }
        return result;
    }

    /** 调用注册接口，注册一个新客户账号，返回注册结果 JSON */
    private JsonNode register(String username, String phone, String location) {
        return call(HttpMethod.POST, "/api/customer/register",
                map("username", username, "password", "Pass1234!", "phone", phone,
                        "trade_location", location), null);
    }

    /** 登录客户账号，返回登录成功后接口给我们的 token 字符串 */
    private String customerToken(String username) {
        return call(HttpMethod.POST, "/api/customer/login",
                map("username", username, "password", "Pass1234!"), null).path("token").asText();
    }

    /** 卖家发布一件新商品，返回发布结果 JSON（包含商品的 id、status 等） */
    private JsonNode publish(String token, String name, double price, int stock,
                             String categoryId, List<String> images) {
        Map<String, Object> body = map("name", name, "price", price, "stock", stock,
                "category_id", categoryId, "description", "<p>" + name + " 富媒体描述</p>");
        body.put("image_urls", images); // 商品图片地址列表
        return call(HttpMethod.POST, "/api/seller/products", body, token);
    }

    /** 客户对某件商品提交购买意向（进入排队），返回意向结果 JSON */
    private JsonNode submitIntent(String productId, String token) {
        return call(HttpMethod.POST, "/api/customer/intents", map("product_id", productId), token);
    }

    /** 卖家对某件商品开启交易（自动冻结库存、暂停新意向） */
    private JsonNode startTransaction(String productId, String seller) {
        return call(HttpMethod.POST, "/api/seller/products/" + productId + "/start-transaction", null, seller);
    }

    /**
     * 卖家对某个意向标记交易结果（成功 / 失败）。
     *
     * @param intentId    意向 id
     * @param result      交易结果：SUCCESS（成功）或 FAILED（失败）
     * @param failedAction 失败时的处理方式：REQUEUE（重排队尾）或 VOID（作废该意向），成功时传 null
     * @param seller      卖家 token
     */
    private JsonNode mark(String intentId, String result, String failedAction, String seller) {
        Map<String, Object> body = map("result", result);
        if (failedAction != null) { // 只有失败处理才需要带上 failed_action 字段
            body.put("failed_action", failedAction);
        }
        return call(HttpMethod.POST, "/api/seller/intents/" + intentId + "/transaction", body, seller);
    }

    /** 读取当前进入交易的意向 id（即队列首位） */
    private String queueHead(String productId, String sellerToken) {
        JsonNode queue = get("/api/seller/products/" + productId + "/intents?status=IN_TRANSACTION", sellerToken);
        return queue.path("items").get(0).path("id").asText();
    }

    // --------------------------- 测试主体 ---------------------------

    /**
     * 主测试用例：按业务顺序一步步验证完整流程。
     * 方法名 upgradedBusinessFlow 表示"升级后的核心业务流程"。
     */
    @Test
    void upgradedBusinessFlow() throws Exception {
        // 0. 服务信息与卖家登录
        // 访问根路径 /，应能拿到接口文档地址 /docs，证明服务已正常起来
        assertEquals("/docs", get("/", null).path("docs").asText());
        // 用内置卖家账号登录，换取 token（后续卖家操作都要带它）
        String seller = call(HttpMethod.POST, "/api/seller/login",
                map("username", "seller", "password", "seller123"), null).path("token").asText();
        assertTrue(seller.length() > 0); // token 不能为空
        // 用错误密码登录，应返回 401（未授权/认证失败）
        assertEquals(401, status(HttpMethod.POST, "/api/seller/login",
                map("username", "seller", "password", "bad"), null));

        // 1. 二级类别：仅两级、同级不可重名
        // 新建顶级类别"厨房用品"，topId 记录其 id
        String topId = call(HttpMethod.POST, "/api/seller/categories",
                map("name", "厨房用品"), seller).path("category").path("id").asText();
        // 在顶级类别下新建子类别"杯子"（带 parent_id），得到 leafId
        JsonNode leaf = call(HttpMethod.POST, "/api/seller/categories",
                map("name", "杯子", "parent_id", topId), seller);
        String leafId = leaf.path("category").path("id").asText();
        assertEquals(2, leaf.path("category").path("level").asInt()); // 子类别层级=2（只有两级）
        // 在子类别"杯子"下再建"陶瓷杯" -> 422，因为只允许两级分类
        assertEquals(422, status(HttpMethod.POST, "/api/seller/categories",
                map("name", "陶瓷杯", "parent_id", leafId), seller));
        // 同级重复名称"杯子" -> 409（冲突），同级不允许重名
        assertEquals(409, status(HttpMethod.POST, "/api/seller/categories",
                map("name", "杯子", "parent_id", topId), seller));
        // 公开类别列表：顶层应有 1 项，且它下面有 1 个子类别
        assertEquals(1, get("/api/categories", null).path("items").size());
        assertEquals(1, get("/api/categories", null).path("items").get(0).path("children").size());

        // 2. 客户注册 / 登录 / 重名
        // 注册 alice，返回的提示语应为"注册成功，请登录"
        assertEquals("注册成功，请登录", register("alice", "13800000001", "一号门").path("message").asText());
        // 再注册同名 alice -> 应提示已被注册
        assertTrue(register("alice", "13800000002", "二号门").path("error").path("message").asText()
                .contains("已被注册"));
        // 再注册 b/carol/dave 三个客户，用于后续排队测试
        register("bob", "13800000002", "二号门");
        register("carol", "13800000003", "三号门");
        register("dave", "13800000004", "四号门");
        // 获取四个客户的登录 token
        String alice = customerToken("alice");
        String bob = customerToken("bob");
        String carol = customerToken("carol");
        String dave = customerToken("dave");
        // 客户用错误的密码登录 -> 401
        assertEquals(401, status(HttpMethod.POST, "/api/customer/login",
                map("username", "alice", "password", "wrong"), null));

        // 3. 买卖双方角色隔离
        // 客户 token 访问卖家接口 -> 401；卖家 token 访问客户接口 -> 401
        assertEquals(401, status(HttpMethod.GET, "/api/seller/products", null, alice));
        assertEquals(401, status(HttpMethod.GET, "/api/customer/me", null, seller));

        // 4. 多图上传 + 多商品同时在售
        String imageUrl = uploadPng(); // 先上传一张测试图片，拿到图片地址
        // 发布"手工陶瓷杯"，库存 2，归属子类别 leafId，带一张图
        JsonNode ceramic = publish(seller, "手工陶瓷杯", 99.9, 2, leafId,
                new ArrayList<>(Arrays.asList(imageUrl)));
        String ceramicId = ceramic.path("product").path("id").asText(); // 记录陶瓷杯 id
        assertEquals("ON_SALE", ceramic.path("product").path("status").asText()); // 发布后应是在售
        // 再发布"玻璃杯"，库存 5，无图片
        String cupId = publish(seller, "玻璃杯", 29.9, 5, leafId, new ArrayList<String>())
                .path("product").path("id").asText();
        // 公开商品列表应有 2 件
        assertEquals(2, get("/api/products", null).path("total").asInt());
        // 关键词"陶瓷"搜索到 1 件
        assertEquals(1, get("/api/products?keyword=陶瓷", null).path("total").asInt());
        // 按顶级类别筛选到 2 件（两个子商品都属于"厨房用品"）
        assertEquals(2, get("/api/products?category_id=" + topId, null).path("total").asInt());
        JsonNode ceramicDetail = get("/api/products/" + ceramicId, null);
        assertEquals(1, ceramicDetail.path("product").path("image_count").asInt()); // 1 张图
        assertTrue(ceramicDetail.path("can_buy").asBoolean()); // 在售可购买
        assertEquals("/uploads/", imageUrl.substring(0, 9)); // 上传图片地址以 /uploads/ 开头
        // 库存为 0 直接发布 -> 状态为售罄下架，不进入公开列表
        assertEquals("SOLD_OUT", publish(seller, "无货商品", 10, 0, leafId, new ArrayList<String>())
                .path("product").path("status").asText());
        assertEquals(2, get("/api/products", null).path("total").asInt()); // 公开列表仍是 2 件

        // 5. 游客不能下单；登录客户按先到先得排队；同商品仅一个有效意向
        // 游客（无 token）提交意向 -> 401
        assertEquals(401, status(HttpMethod.POST, "/api/customer/intents",
                map("product_id", ceramicId), null));
        // alice 第一个下单 -> 排在队列第 1，状态"排队中"
        JsonNode first = submitIntent(ceramicId, alice);
        String aliceIntent = first.path("intent").path("id").asText();
        assertEquals(1, first.path("queue_position").asInt());
        assertEquals("排队中", first.path("intent").path("status_label").asText());
        // bob 第二个下单 -> 排在第 2（先到先得）
        assertEquals(2, submitIntent(ceramicId, bob).path("queue_position").asInt());
        // alice 对同一商品再次提交 -> 409，同一商品每人只能有一个有效意向
        assertEquals(409, status(HttpMethod.POST, "/api/customer/intents",
                map("product_id", ceramicId), alice));
        // 卖家侧确认该商品队列有 2 条意向
        assertEquals(2, get("/api/seller/products/" + ceramicId + "/intents", seller).path("total").asInt());
        // 每商品独立队列：alice 对"玻璃杯"提交，排第 1（和陶瓷杯的队列互不影响）
        assertEquals(1, submitIntent(cupId, alice).path("queue_position").asInt());

        // 6. 进入交易 -> 自动冻结，暂停新意向
        // 卖家对陶瓷杯开启交易，系统应自动选中队列首位的 alice 意向
        JsonNode started = startTransaction(ceramicId, seller);
        assertEquals(aliceIntent, started.path("intent").path("id").asText());
        assertEquals("FROZEN", started.path("product").path("status").asText()); // 商品被冻结
        // 交易进行中：新客户 carol 提交意向 -> 409（暂停接单）
        assertEquals(409, status(HttpMethod.POST, "/api/customer/intents",
                map("product_id", ceramicId), carol));
        // 已有一笔交易进行中，再次开启交易 -> 409
        assertEquals(409, status(HttpMethod.POST,
                "/api/seller/products/" + ceramicId + "/start-transaction", null, seller));

        // 7. 交易失败 + 同一动作重新排到队尾，商品恢复在售
        // 标记 alice 交易失败，处理方式 REQUEUE（重新排到队尾），备注"未到场"
        JsonNode failed = call(HttpMethod.POST, "/api/seller/intents/" + aliceIntent + "/transaction",
                map("result", "FAILED", "note", "未到场", "failed_action", "REQUEUE"), seller);
        assertEquals("REQUEUED", failed.path("intent").path("status").asText()); // 状态变为已重排队
        assertEquals(2, failed.path("intent").path("queue_position").asInt());   // 排到队尾（第 2）
        assertEquals(1, failed.path("intent").path("requeue_count").asInt());    // 重排次数=1
        assertEquals("ON_SALE", failed.path("product").path("status").asText()); // 商品恢复在售

        // 8. 失败必须带处理方式：422 且事务回滚
        startTransaction(ceramicId, seller); // 重新开启交易，此时队首是 bob
        String bobIntent = queueHead(ceramicId, seller); // 取得 bob 的意向 id
        // 只传 result=FAILED 不给 failed_action -> 422（参数不合法），且交易状态保持 IN_TRANSACTION
        assertEquals(422, status(HttpMethod.POST,
                "/api/seller/intents/" + bobIntent + "/transaction", map("result", "FAILED"), seller));
        assertEquals(1, get("/api/seller/products/" + ceramicId + "/intents?status=IN_TRANSACTION", seller)
                .path("total").asInt()); // 事务仍然停留在"交易中"，说明 422 时已回滚
        // 选择作废失败者：这次补上 VOID 处理方式，bob 意向作废
        JsonNode voided = mark(bobIntent, "FAILED", "VOID", seller);
        assertEquals("VOIDED", voided.path("intent").path("status").asText()); // 已作废
        assertEquals("ON_SALE", voided.path("product").path("status").asText()); // 商品恢复在售

        // 9. A 成交：库存 2 -> 1，商品恢复在售
        startTransaction(ceramicId, seller); // 开启交易
        String aliceIntentAgain = queueHead(ceramicId, seller); // 队首是重新排队的 alice
        JsonNode aliceSuccess = mark(aliceIntentAgain, "SUCCESS", null, seller); // 标记交易成功
        assertEquals("SUCCESS", aliceSuccess.path("intent").path("status").asText());
        assertEquals(1, aliceSuccess.path("product").path("stock").asInt()); // 库存 2 -> 1
        assertEquals("ON_SALE", aliceSuccess.path("product").path("status").asText()); // 卖出后仍在售

        // 10. 成交后库存归零 -> 自动下架，剩余排队意向售罄作废
        assertEquals(1, submitIntent(ceramicId, carol).path("queue_position").asInt()); // carol 排第 1
        assertEquals(2, submitIntent(ceramicId, dave).path("queue_position").asInt());  // dave 排第 2
        startTransaction(ceramicId, seller); // 开启交易
        String carolIntent = queueHead(ceramicId, seller); // 队首是 carol
        JsonNode soldOut = mark(carolIntent, "SUCCESS", null, seller); // carol 成交，最后一台库存
        assertEquals(0, soldOut.path("product").path("stock").asInt()); // 库存归零
        assertEquals("SOLD_OUT", soldOut.path("product").path("status").asText()); // 自动下架
        // 返回的提示语应说明"售罄下架、剩余排队意向作废"
        assertEquals("交易成功，库存已扣减至 0，商品自动下架，剩余排队意向已售罄作废",
                soldOut.path("message").asText());
        // dave 的意向应被标记为 SOLD_OUT（售罄作废）
        assertEquals(1, get("/api/seller/products/" + ceramicId + "/intents?status=SOLD_OUT", seller)
                .path("total").asInt());
        assertEquals(0, get("/api/products?keyword=陶瓷", null).path("total").asInt()); // 公开列表不再显示
        // 售罄商品不能交易、不能提交
        assertEquals(409, status(HttpMethod.POST,
                "/api/seller/products/" + ceramicId + "/start-transaction", null, seller));

        // 11. 手动下架 / 重新上架 / 库存调整
        JsonNode off = call(HttpMethod.POST, "/api/seller/products/" + cupId + "/off-shelf", null, seller);
        assertEquals("OFF_SHELF", off.path("product").path("status").asText()); // 手动下架
        assertEquals(409, status(HttpMethod.POST, "/api/customer/intents", map("product_id", cupId), carol));
        JsonNode on = call(HttpMethod.POST, "/api/seller/products/" + cupId + "/on-shelf", null, seller);
        assertEquals("ON_SALE", on.path("product").path("status").asText()); // 重新上架
        // 把库存调为 0 -> 状态变为售罄下架
        JsonNode zero = call(HttpMethod.PATCH,
                "/api/seller/products/" + cupId + "/stock", map("stock", 0), seller);
        assertEquals("SOLD_OUT", zero.path("product").path("status").asText());
        // 把库存补到 3 -> 恢复在售，库存=3
        JsonNode restocked = call(HttpMethod.PATCH,
                "/api/seller/products/" + cupId + "/stock", map("stock", 3), seller);
        assertEquals("ON_SALE", restocked.path("product").path("status").asText());
        assertEquals(3, restocked.path("product").path("stock").asInt());
        // 手动冻结 / 解冻（TBD-15 保留）：冻结后不能下单，解冻后恢复
        JsonNode frozen = call(HttpMethod.POST, "/api/seller/products/" + cupId + "/freeze", null, seller);
        assertEquals("FROZEN", frozen.path("product").path("status").asText());
        assertEquals(409, status(HttpMethod.POST, "/api/customer/intents", map("product_id", cupId), carol));
        JsonNode unfrozen = call(HttpMethod.POST, "/api/seller/products/" + cupId + "/unfreeze", null, seller);
        assertEquals("ON_SALE", unfrozen.path("product").path("status").asText());
        // 对未冻结商品再次解冻 -> 409
        assertEquals(409, status(HttpMethod.POST, "/api/seller/products/" + cupId + "/unfreeze", null, seller));

        // 队列名单导出 CSV：应返回 200，且内容含表头"用户名,电话,默认交易地点"
        ResponseEntity<String> csv = rest.exchange(url("/api/seller/products/" + ceramicId + "/intents/export"),
                HttpMethod.GET, new HttpEntity<>(authHeaders(seller)), String.class);
        assertEquals(200, csv.getStatusCodeValue());
        assertTrue(csv.getBody().contains("用户名,电话,默认交易地点"));

        // 12. 客户历史下单记录
        JsonNode orders = get("/api/customer/orders?page_size=100", alice);
        assertEquals(2, orders.path("total").asInt()); // alice 应有 2 条订单
        JsonNode ceramicOrder = null;
        // 在订单列表中找到陶瓷杯那一条
        for (JsonNode node : orders.path("items")) {
            if (ceramicId.equals(node.path("product_id").asText())) {
                ceramicOrder = node;
            }
        }
        assertNotNull(ceramicOrder); // 必须能找到
        assertEquals("SUCCESS", ceramicOrder.path("status").asText()); // 该条成交成功
        assertEquals("交易成功", ceramicOrder.path("transaction").path("result_label").asText());
        // 客户意向详情仅本人可见：bob 看 alice 的意向 -> 404
        assertEquals(404, status(HttpMethod.GET, "/api/customer/intents/" + aliceIntentAgain, null, bob));

        // 13. 商家客户台账
        JsonNode ledger = get("/api/seller/customers?page_size=100", seller);
        assertEquals(4, ledger.path("total").asInt()); // 共注册 4 个客户
        JsonNode aliceRow = null;
        // 找到台账中 alice 那行
        for (JsonNode node : ledger.path("items")) {
            if ("alice".equals(node.path("username").asText())) {
                aliceRow = node;
            }
        }
        assertNotNull(aliceRow);
        assertEquals("13800000001", aliceRow.path("phone").asText());         // 电话正确
        assertEquals("一号门", aliceRow.path("trade_location").asText());      // 交易地点正确
        assertEquals(1, aliceRow.path("purchase_count").asInt());             // 购买 1 次
        JsonNode aliceDetail = get("/api/seller/customers/" + aliceRow.path("id").asText(), seller);
        assertEquals(2, aliceDetail.path("orders").size()); // alice 累计订单 2 条

        // 14. 历史记录：售罄商品 + 多轮交易
        JsonNode history = get("/api/seller/history?page_size=100", seller);
        JsonNode historyItem = null;
        // 在历史里找到陶瓷杯那条
        for (JsonNode node : history.path("items")) {
            if (ceramicId.equals(node.path("id").asText())) {
                historyItem = node;
            }
        }
        assertNotNull(historyItem);
        assertEquals(2, historyItem.path("transaction_summary").path("failed_rounds").asInt()); // 失败 2 轮
        assertEquals(4, historyItem.path("transaction_summary").path("buyer_count").asInt());    // 4 个买家
        JsonNode detail = get("/api/seller/history/" + ceramicId, seller);
        JsonNode transactions = detail.path("transactions");
        assertEquals(4, transactions.size()); // 共 4 笔交易
        assertEquals("FAILED", transactions.get(0).path("result").asText());  // 第 1 笔失败
        assertEquals("SUCCESS", transactions.get(3).path("result").asText()); // 最后 1 笔成功

        // 15. 类别删除约束与重命名
        // 删除仍有商品的类别 -> 409（被占用，不允许删）
        assertEquals(409, status(HttpMethod.DELETE, "/api/seller/categories/" + leafId, null, seller));
        // 把"杯子"改名为"水杯"
        JsonNode renamed = call(HttpMethod.PATCH, "/api/seller/categories/" + leafId,
                map("name", "水杯"), seller);
        assertEquals("水杯", renamed.path("category").path("name").asText());
        // 新建空类别并删除 -> 允许（200）
        String emptyCategory = call(HttpMethod.POST, "/api/seller/categories",
                map("name", "临时类别"), seller).path("category").path("id").asText();
        assertEquals(200, status(HttpMethod.DELETE, "/api/seller/categories/" + emptyCategory, null, seller));

        // 16. 卖家改密（至少 8 位）与操作日志
        // 新密码只有 7 位 -> 422；旧密码错误 -> 401
        assertEquals(422, status(HttpMethod.POST, "/api/seller/password",
                map("old_password", "seller123", "new_password", "1234567"), seller));
        assertEquals(401, status(HttpMethod.POST, "/api/seller/password",
                map("old_password", "wrong", "new_password", "12345678"), seller));
        // 读取卖家操作日志，把所有 action 拼成一个字符串
        JsonNode logs = get("/api/seller/logs?page_size=100", seller);
        StringBuilder actions = new StringBuilder();
        for (JsonNode node : logs.path("items")) {
            actions.append(node.path("action").asText()).append(',');
        }
        assertTrue(actions.toString().contains("PUBLISH_PRODUCT"), actions.toString());
        assertTrue(actions.toString().contains("TRANSACTION_SUCCESS"), actions.toString());
        assertTrue(actions.toString().contains("ADJUST_STOCK"), actions.toString());
    }

    /**
     * 上传一张"假"的 PNG 图片（只有文件头的几个固定字节），
     * 用来验证图片上传接口。返回服务器生成的图片访问地址。
     */
    private String uploadPng() {
        // PNG 文件的魔数（固定文件头）：89 50 4E 47 0D 0A 1A 0A，足够让服务判断为 PNG
        byte[] png = new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
        // 构造 multipart 表单数据，键名是 "file"
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("file", new ByteArrayResource(png) {
            @Override
            public String getFilename() { // 指定上传的文件名为 cup.png
                return "cup.png";
            }
        });
        // 设置请求头为 multipart/form-data（文件上传专用格式）
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        // 发送 POST 到图片上传接口
        ResponseEntity<String> response = rest.postForEntity(url("/api/files/images"),
                new HttpEntity<>(parts, headers), String.class);
        try {
            assertEquals(200, response.getStatusCodeValue()); // 上传应成功
            // 从返回的 JSON 里取出图片地址 url
            return json.readTree(response.getBody()).path("url").asText();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * 读取某商品当前"正在交易中"队列的首位意向 id。
     * 相当于"现在轮到谁了"。
     */
    private String queueHead(String productId, String sellerToken) {
        // 按 status=IN_TRANSACTION 过滤出正在交易的意向，取第 0 条（队首）的 id
        JsonNode queue = get("/api/seller/products/" + productId + "/intents?status=IN_TRANSACTION", sellerToken);
        return queue.path("items").get(0).path("id").asText();
    }
}
