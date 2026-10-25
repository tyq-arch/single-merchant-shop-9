package com.shop.web;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.shop.config.AppConfig;
import com.shop.db.Db;
import com.shop.db.Jdbc;
import com.shop.dto.ChangePasswordRequest;
import com.shop.dto.CustomerLoginRequest;
import com.shop.dto.CustomerRegisterRequest;
import com.shop.dto.IntentCreateRequest;
import com.shop.serializers.Serializers;
import com.shop.services.AuthService;
import com.shop.services.IntentService;

/** 客户接口：注册、登录、资料、购买意向与历史下单。 */
@RestController
@RequestMapping("/api/customer")
public class CustomerController {

    private final Db db;
    private final AppConfig config;
    private final AuthService auth;
    private final IntentService intents;

    public CustomerController(Db db, AppConfig config, AuthService auth, IntentService intents) {
        this.db = db;
        this.config = config;
        this.auth = auth;
        this.intents = intents;
    }

    @PostMapping("/register")
    public Map<String, Object> register(@RequestBody CustomerRegisterRequest body) {
        String username = Validation.text(body.username, "body", "username", 1, 64, "用户名");
        String password = Validation.password(body.password, "body", "password", "密码");
        Validation.phone(body.phone, "body", "phone");
        String location = Validation.text(body.tradeLocation, "body", "trade_location", 1, 200, "默认交易地点");
        return auth.registerCustomer(username, password, body.phone.trim(), location);
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody CustomerLoginRequest body) {
        String username = Validation.text(body.username, "body", "username", 1, 64, "用户名");
        String password = Validation.text(body.password, "body", "password", 1, 128, "密码");
        return auth.loginCustomer(username, password);
    }

    /** 无状态令牌登出：服务端确认身份，客户端丢弃令牌即可。 */
    @PostMapping("/logout")
    public Map<String, Object> logout(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        auth.requireCustomer(authorization);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", "已退出登录");
        return result;
    }

    @GetMapping("/me")
    public Map<String, Object> me(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        Map<String, Object> customer = auth.requireCustomer(authorization);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("customer", Serializers.customer(customer, false));
        result.put("purchase_count", db.read(conn -> {
            Long count = Jdbc.scalarLong(conn,
                    "SELECT COUNT(*) AS c FROM buy_intent WHERE customer_id = ? AND status = 'SUCCESS'",
                    Jdbc.strOf(customer, "id"));
            return count == null ? 0 : count;
        }));
        return result;
    }

    @PostMapping("/password")
    public Map<String, Object> changePassword(@RequestBody ChangePasswordRequest body,
                                              @RequestHeader(value = "Authorization", required = false) String authorization) {
        Map<String, Object> customer = auth.requireCustomer(authorization);
        String oldPassword = Validation.text(body.oldPassword, "body", "old_password", 1, 128, "旧密码");
        String newPassword = Validation.password(body.newPassword, "body", "new_password", "新密码");
        auth.changeCustomerPassword(Jdbc.strOf(customer, "id"), oldPassword, newPassword);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", "密码修改成功");
        return result;
    }

    @PostMapping("/intents")
    public Map<String, Object> submitIntent(@RequestBody IntentCreateRequest body,
                                            @RequestHeader(value = "Authorization", required = false) String authorization) {
        Map<String, Object> customer = auth.requireCustomer(authorization);
        if (body.productId == null || body.productId.trim().isEmpty()) {
            throw Validation.error("body", "product_id", "商品 ID 不能为空");
        }
        return db.transaction(conn -> intents.submit(
                conn, Jdbc.strOf(customer, "id"), body.productId.trim()));
    }

    @GetMapping("/intents")
    public Map<String, Object> myIntents(@RequestParam(value = "page", required = false) String page,
                                         @RequestParam(value = "page_size", required = false) String pageSize,
                                         @RequestParam(value = "status", required = false) String status,
                                         @RequestHeader(value = "Authorization", required = false) String authorization) {
        Map<String, Object> customer = auth.requireCustomer(authorization);
        int current = Validation.page(page);
        int size = Validation.pageSize(pageSize, config.getDefaultPageSize(), config.getMaxPageSize());
        return db.read(conn -> intents.listMine(conn, Jdbc.strOf(customer, "id"), current, size, status));
    }

    @GetMapping("/intents/{intentId}")
    public Map<String, Object> myIntent(@PathVariable String intentId,
                                        @RequestHeader(value = "Authorization", required = false) String authorization) {
        Map<String, Object> customer = auth.requireCustomer(authorization);
        return db.read(conn -> intents.getMine(conn, Jdbc.strOf(customer, "id"), intentId));
    }

    /** 历史下单记录（FR-031）。 */
    @GetMapping("/orders")
    public Map<String, Object> myOrders(@RequestParam(value = "page", required = false) String page,
                                        @RequestParam(value = "page_size", required = false) String pageSize,
                                        @RequestHeader(value = "Authorization", required = false) String authorization) {
        Map<String, Object> customer = auth.requireCustomer(authorization);
        int current = Validation.page(page);
        int size = Validation.pageSize(pageSize, config.getDefaultPageSize(), config.getMaxPageSize());
        return db.read(conn -> intents.orders(conn, Jdbc.strOf(customer, "id"), current, size));
    }
}
