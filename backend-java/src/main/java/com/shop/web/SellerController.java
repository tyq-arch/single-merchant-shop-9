package com.shop.web;

import com.shop.config.AppConfig;
import com.shop.db.Db;
import com.shop.dto.CategoryRequest;
import com.shop.dto.ChangePasswordRequest;
import com.shop.dto.ProductCreateRequest;
import com.shop.dto.SellerLoginRequest;
import com.shop.dto.StockUpdateRequest;
import com.shop.dto.TransactionMarkRequest;
import com.shop.db.Jdbc;
import com.shop.serializers.Serializers;
import com.shop.services.AuthService;
import com.shop.services.CategoryService;
import com.shop.services.CustomerService;
import com.shop.services.IntentService;
import com.shop.services.LogService;
import com.shop.services.ProductService;
import com.shop.services.TransactionService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/** 卖家接口：登录、类别、多商品与库存、队列交易、历史、客户台账、日志。 */
@RestController
@RequestMapping("/api/seller")
public class SellerController {

    private final Db db;
    private final AppConfig config;
    private final AuthService auth;
    private final CategoryService categories;
    private final ProductService products;
    private final IntentService intents;
    private final TransactionService transactions;
    private final CustomerService customers;
    private final LogService logs;

    public SellerController(Db db, AppConfig config, AuthService auth, CategoryService categories,
                            ProductService products, IntentService intents, TransactionService transactions,
                            CustomerService customers, LogService logs) {
        this.db = db;
        this.config = config;
        this.auth = auth;
        this.categories = categories;
        this.products = products;
        this.intents = intents;
        this.transactions = transactions;
        this.customers = customers;
        this.logs = logs;
    }

    // --------------------------- 账号 ---------------------------

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody SellerLoginRequest body) {
        String username = Validation.text(body.username, "body", "username", 1, 64, "用户名");
        String password = Validation.text(body.password, "body", "password", 1, 128, "密码");
        return auth.loginSeller(username, password);
    }

    /** 卖家新密码至少 8 位（FR-037）。 */
    @PostMapping("/password")
    public Map<String, Object> changePassword(@RequestBody ChangePasswordRequest body,
                                              @RequestHeader(value = "Authorization", required = false) String authorization) {
        Map<String, Object> seller = auth.requireSeller(authorization);
        String oldPassword = Validation.text(body.oldPassword, "body", "old_password", 1, 128, "旧密码");
        String newPassword = Validation.text(body.newPassword, "body", "new_password", 8, 128, "新密码");
        auth.changeSellerPassword(Jdbc.strOf(seller, "id"), oldPassword, newPassword);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", "密码修改成功");
        return result;
    }

    // --------------------------- 类别 ---------------------------

    @GetMapping("/categories")
    public Map<String, Object> listCategories(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        auth.requireSeller(authorization);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", db.read(categories::tree));
        return result;
    }

    @PostMapping("/categories")
    public Map<String, Object> createCategory(@RequestBody CategoryRequest body,
                                              @RequestHeader(value = "Authorization", required = false) String authorization) {
        Map<String, Object> seller = auth.requireSeller(authorization);
        String name = Validation.text(body.name, "body", "name", 1, 60, "类别名称");
        Map<String, Object> category = db.transaction(conn ->
                categories.create(conn, Jdbc.strOf(seller, "id"), name, body.parentId));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("category", Serializers.category(category));
        result.put("message", "类别创建成功");
        return result;
    }

    @PatchMapping("/categories/{categoryId}")
    public Map<String, Object> renameCategory(@PathVariable String categoryId,
                                              @RequestBody CategoryRequest body,
                                              @RequestHeader(value = "Authorization", required = false) String authorization) {
        Map<String, Object> seller = auth.requireSeller(authorization);
        String name = Validation.text(body.name, "body", "name", 1, 60, "类别名称");
        Map<String, Object> category = db.transaction(conn ->
                categories.rename(conn, Jdbc.strOf(seller, "id"), categoryId, name));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("category", Serializers.category(category));
        result.put("message", "类别重命名成功");
        return result;
    }

    @DeleteMapping("/categories/{categoryId}")
    public Map<String, Object> deleteCategory(@PathVariable String categoryId,
                                              @RequestHeader(value = "Authorization", required = false) String authorization) {
        Map<String, Object> seller = auth.requireSeller(authorization);
        db.transaction(conn -> {
            categories.delete(conn, Jdbc.strOf(seller, "id"), categoryId);
            return null;
        });
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", "类别已删除");
        return result;
    }

    // --------------------------- 商品 ---------------------------

    @PostMapping("/products")
    public Map<String, Object> publishProduct(@RequestBody ProductCreateRequest body,
                                              @RequestHeader(value = "Authorization", required = false) String authorization) {
        Map<String, Object> seller = auth.requireSeller(authorization);
        String name = Validation.text(body.name, "body", "name", 1, 120, "商品名称");
        int cents = Validation.priceCents(body.price, "body", "price");
        int stock = Validation.stock(body.stock, "body", "stock");
        Validation.optionalLength(body.description, "body", "description", 20000, "商品描述");
        if (body.categoryId == null || body.categoryId.trim().isEmpty()) {
            throw Validation.error("body", "category_id", "商品必须选择二级类别");
        }
        Map<String, Object> product = db.transaction(conn ->
                products.publish(conn, Jdbc.strOf(seller, "id"), name, body.description,
                        cents, stock, body.categoryId.trim(), body.imageUrls));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("product", product);
        result.put("message", "商品发布成功");
        return result;
    }

    @GetMapping("/products")
    public Map<String, Object> listProducts(@RequestParam(value = "page", required = false) String page,
                                            @RequestParam(value = "page_size", required = false) String pageSize,
                                            @RequestParam(value = "keyword", required = false) String keyword,
                                            @RequestParam(value = "status", required = false) String status,
                                            @RequestParam(value = "category_id", required = false) String categoryId,
                                            @RequestHeader(value = "Authorization", required = false) String authorization) {
        auth.requireSeller(authorization);
        int current = Validation.page(page);
        int size = Validation.pageSize(pageSize, config.getDefaultPageSize(), config.getMaxPageSize());
        return db.read(conn -> products.listSeller(conn, current, size, keyword, status, categoryId));
    }

    @GetMapping("/products/{productId}")
    public Map<String, Object> productDetail(@PathVariable String productId,
                                             @RequestHeader(value = "Authorization", required = false) String authorization) {
        auth.requireSeller(authorization);
        return db.read(conn -> {
            Map<String, Object> product = products.fullById(conn, productId);
            if (product == null) {
                throw new com.shop.errors.NotFoundException("商品不存在");
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("product", product);
            result.put("queue_count", queueCount(conn, productId));
            return result;
        });
    }

    private int queueCount(java.sql.Connection conn, String productId) throws Exception {
        Long count = Jdbc.scalarLong(conn,
                "SELECT COUNT(*) AS c FROM queue_item WHERE product_id = ?", productId);
        return count == null ? 0 : count.intValue();
    }

    @PatchMapping("/products/{productId}/stock")
    public Map<String, Object> adjustStock(@PathVariable String productId,
                                           @RequestBody StockUpdateRequest body,
                                           @RequestHeader(value = "Authorization", required = false) String authorization) {
        Map<String, Object> seller = auth.requireSeller(authorization);
        int stock = Validation.stock(body.stock, "body", "stock");
        return db.transaction(conn ->
                products.adjustStock(conn, Jdbc.strOf(seller, "id"), productId, stock));
    }

    @PostMapping("/products/{productId}/off-shelf")
    public Map<String, Object> offShelf(@PathVariable String productId,
                                        @RequestHeader(value = "Authorization", required = false) String authorization) {
        Map<String, Object> seller = auth.requireSeller(authorization);
        return db.transaction(conn ->
                products.offShelf(conn, Jdbc.strOf(seller, "id"), productId));
    }

    @PostMapping("/products/{productId}/on-shelf")
    public Map<String, Object> onShelf(@PathVariable String productId,
                                       @RequestHeader(value = "Authorization", required = false) String authorization) {
        Map<String, Object> seller = auth.requireSeller(authorization);
        return db.transaction(conn ->
                products.onShelf(conn, Jdbc.strOf(seller, "id"), productId));
    }

    @PostMapping("/products/{productId}/freeze")
    public Map<String, Object> freeze(@PathVariable String productId,
                                      @RequestHeader(value = "Authorization", required = false) String authorization) {
        Map<String, Object> seller = auth.requireSeller(authorization);
        return db.transaction(conn ->
                products.freeze(conn, Jdbc.strOf(seller, "id"), productId));
    }

    @PostMapping("/products/{productId}/unfreeze")
    public Map<String, Object> unfreeze(@PathVariable String productId,
                                        @RequestHeader(value = "Authorization", required = false) String authorization) {
        Map<String, Object> seller = auth.requireSeller(authorization);
        return db.transaction(conn ->
                products.unfreeze(conn, Jdbc.strOf(seller, "id"), productId));
    }

    // --------------------------- 队列与交易 ---------------------------

    @PostMapping("/products/{productId}/start-transaction")
    public Map<String, Object> startTransaction(@PathVariable String productId,
                                                @RequestHeader(value = "Authorization", required = false) String authorization) {
        Map<String, Object> seller = auth.requireSeller(authorization);
        return db.transaction(conn ->
                transactions.start(conn, Jdbc.strOf(seller, "id"), productId));
    }

    /** 标记交易结果；失败时在同一请求内选择失败者作废或重新排队。 */
    @PostMapping("/intents/{intentId}/transaction")
    public Map<String, Object> markTransaction(@PathVariable String intentId,
                                               @RequestBody TransactionMarkRequest body,
                                               @RequestHeader(value = "Authorization", required = false) String authorization) {
        Map<String, Object> seller = auth.requireSeller(authorization);
        if (body.result == null
                || !("SUCCESS".equals(body.result) || "FAILED".equals(body.result))) {
            throw Validation.error("body", "result", "交易结果必须为 SUCCESS 或 FAILED");
        }
        Validation.optionalLength(body.note, "body", "note", 500, "备注");
        if ("FAILED".equals(body.result) && (body.failedAction == null || body.failedAction.trim().isEmpty())) {
            throw Validation.error("body", "failed_action", "交易失败时必须选择 VOID 或 REQUEUE");
        }
        return db.transaction(conn -> transactions.mark(
                conn, Jdbc.strOf(seller, "id"), intentId, body.result, body.note, body.failedAction));
    }

    @GetMapping("/products/{productId}/intents")
    public Map<String, Object> listIntents(@PathVariable String productId,
                                           @RequestParam(value = "page", required = false) String page,
                                           @RequestParam(value = "page_size", required = false) String pageSize,
                                           @RequestParam(value = "status", required = false) String status,
                                           @RequestHeader(value = "Authorization", required = false) String authorization) {
        auth.requireSeller(authorization);
        int current = Validation.page(page);
        int size = Validation.pageSize(pageSize, config.getDefaultPageSize(), config.getMaxPageSize());
        return db.read(conn -> intents.listProductQueue(conn, productId, current, size, status));
    }

    @GetMapping("/products/{productId}/intents/export")
    public ResponseEntity<String> exportIntents(@PathVariable String productId,
                                                @RequestHeader(value = "Authorization", required = false) String authorization) {
        auth.requireSeller(authorization);
        String csv = db.read(conn -> intents.exportCsv(conn, productId));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"intents_" + productId + ".csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv);
    }

    // --------------------------- 历史与台账 ---------------------------

    @GetMapping("/history")
    public Map<String, Object> history(@RequestParam(value = "page", required = false) String page,
                                       @RequestParam(value = "page_size", required = false) String pageSize,
                                       @RequestParam(value = "keyword", required = false) String keyword,
                                       @RequestHeader(value = "Authorization", required = false) String authorization) {
        auth.requireSeller(authorization);
        int current = Validation.page(page);
        int size = Validation.pageSize(pageSize, config.getDefaultPageSize(), config.getMaxPageSize());
        return db.read(conn -> products.listHistory(conn, current, size, keyword));
    }

    @GetMapping("/history/{productId}")
    public Map<String, Object> historyDetail(@PathVariable String productId,
                                             @RequestHeader(value = "Authorization", required = false) String authorization) {
        auth.requireSeller(authorization);
        return db.read(conn -> products.historyDetail(conn, productId));
    }

    @GetMapping("/customers")
    public Map<String, Object> listCustomers(@RequestParam(value = "page", required = false) String page,
                                             @RequestParam(value = "page_size", required = false) String pageSize,
                                             @RequestParam(value = "keyword", required = false) String keyword,
                                             @RequestHeader(value = "Authorization", required = false) String authorization) {
        auth.requireSeller(authorization);
        int current = Validation.page(page);
        int size = Validation.pageSize(pageSize, config.getDefaultPageSize(), config.getMaxPageSize());
        return db.read(conn -> customers.list(conn, current, size, keyword));
    }

    @GetMapping("/customers/{customerId}")
    public Map<String, Object> customerDetail(@PathVariable String customerId,
                                              @RequestHeader(value = "Authorization", required = false) String authorization) {
        auth.requireSeller(authorization);
        return db.read(conn -> customers.detail(conn, customerId));
    }

    @GetMapping("/logs")
    public Map<String, Object> listLogs(@RequestParam(value = "page", required = false) String page,
                                        @RequestParam(value = "page_size", required = false) String pageSize,
                                        @RequestHeader(value = "Authorization", required = false) String authorization) {
        auth.requireSeller(authorization);
        int current = Validation.page(page);
        int size = Validation.pageSize(pageSize, config.getDefaultPageSize(), config.getMaxPageSize());
        Map<String, Object> data = db.read(conn -> logs.listLogs(conn, current, size));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", data.get("items"));
        result.put("total", data.get("total"));
        result.put("page", current);
        result.put("page_size", size);
        return result;
    }
}
