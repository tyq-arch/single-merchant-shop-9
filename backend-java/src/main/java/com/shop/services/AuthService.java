package com.shop.services;

import com.shop.db.Db;
import com.shop.db.Jdbc;
import com.shop.enums.Enums;
import com.shop.errors.ConflictException;
import com.shop.errors.UnauthorizedException;
import com.shop.security.SecurityService;
import com.shop.util.Utils;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 鉴权与账号：卖家固定账号登录/改密，客户注册/登录/改密。
 */
@Service
public class AuthService {

    private final Db db;
    private final SecurityService security;
    private final LogService logs;

    public AuthService(Db db, SecurityService security, LogService logs) {
        this.db = db;
        this.security = security;
        this.logs = logs;
    }

    // --------------------------- 卖家 ---------------------------

    public Map<String, Object> loginSeller(String username, String password) {
        Map<String, Object> seller = db.read(conn ->
                Jdbc.queryOne(conn, "SELECT * FROM seller WHERE username = ?", username));
        if (seller == null || !security.verifyPassword(password, Jdbc.strOf(seller, "password_hash"))) {
            throw new UnauthorizedException("用户名或密码错误");
        }
        SecurityService.Token token = security.createToken(
                SecurityService.ROLE_SELLER, Jdbc.strOf(seller, "id"));
        db.transaction(conn -> {
            logs.logAction(conn, Jdbc.strOf(seller, "id"), Enums.LogAction.LOGIN,
                    "卖家 " + username + " 登录后台");
            return null;
        });

        Map<String, Object> sellerInfo = new LinkedHashMap<>();
        sellerInfo.put("id", Jdbc.strOf(seller, "id"));
        sellerInfo.put("username", Jdbc.strOf(seller, "username"));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("token", token.getToken());
        result.put("token_type", "Bearer");
        result.put("expires_at", token.getExpiresAt());
        result.put("seller", sellerInfo);
        return result;
    }

    public void changeSellerPassword(String sellerId, String oldPassword, String newPassword) {
        Map<String, Object> seller = db.read(conn ->
                Jdbc.queryOne(conn, "SELECT * FROM seller WHERE id = ?", sellerId));
        if (seller == null) {
            throw new UnauthorizedException("卖家账号不存在");
        }
        if (!security.verifyPassword(oldPassword, Jdbc.strOf(seller, "password_hash"))) {
            throw new UnauthorizedException("旧密码不正确");
        }
        db.transaction(conn -> {
            Jdbc.update(conn, "UPDATE seller SET password_hash = ?, updated_at = ? WHERE id = ?",
                    security.hashPassword(newPassword), Utils.nowIso(), sellerId);
            logs.logAction(conn, sellerId, Enums.LogAction.CHANGE_PASSWORD, "修改登录密码");
            return null;
        });
    }

    public Map<String, Object> requireSeller(String authorization) {
        SecurityService.Principal principal = principal(authorization, SecurityService.ROLE_SELLER);
        Map<String, Object> seller = db.read(conn ->
                Jdbc.queryOne(conn, "SELECT * FROM seller WHERE id = ?", principal.getSubjectId()));
        if (seller == null) {
            throw new UnauthorizedException("卖家账号不存在");
        }
        return seller;
    }

    // --------------------------- 客户 ---------------------------

    public Map<String, Object> registerCustomer(String username, String password, String phone,
                                                String tradeLocation) {
        Map<String, Object> existing = db.read(conn ->
                Jdbc.queryOne(conn, "SELECT id FROM customer WHERE username = ?", username));
        if (existing != null) {
            throw new ConflictException("用户名已被注册");
        }
        String id = Utils.newId();
        String now = Utils.nowIso();
        db.transaction(conn -> {
            Jdbc.update(conn,
                    "INSERT INTO customer (id, username, password_hash, phone, trade_location, created_at, updated_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                    id, username, security.hashPassword(password), phone, tradeLocation, now, now);
            return null;
        });
        Map<String, Object> customer = db.read(conn ->
                Jdbc.queryOne(conn, "SELECT * FROM customer WHERE id = ?", id));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("customer", com.shop.serializers.Serializers.customer(customer, false));
        result.put("message", "注册成功，请登录");
        return result;
    }

    public Map<String, Object> loginCustomer(String username, String password) {
        Map<String, Object> customer = db.read(conn ->
                Jdbc.queryOne(conn, "SELECT * FROM customer WHERE username = ?", username));
        if (customer == null || !security.verifyPassword(password, Jdbc.strOf(customer, "password_hash"))) {
            throw new UnauthorizedException("用户名或密码错误");
        }
        SecurityService.Token token = security.createToken(
                SecurityService.ROLE_CUSTOMER, Jdbc.strOf(customer, "id"));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("token", token.getToken());
        result.put("token_type", "Bearer");
        result.put("expires_at", token.getExpiresAt());
        result.put("customer", com.shop.serializers.Serializers.customer(customer, false));
        return result;
    }

    public void changeCustomerPassword(String customerId, String oldPassword, String newPassword) {
        Map<String, Object> customer = db.read(conn ->
                Jdbc.queryOne(conn, "SELECT * FROM customer WHERE id = ?", customerId));
        if (customer == null) {
            throw new UnauthorizedException("客户账号不存在");
        }
        if (!security.verifyPassword(oldPassword, Jdbc.strOf(customer, "password_hash"))) {
            throw new UnauthorizedException("旧密码不正确");
        }
        db.transaction(conn -> {
            Jdbc.update(conn, "UPDATE customer SET password_hash = ?, updated_at = ? WHERE id = ?",
                    security.hashPassword(newPassword), Utils.nowIso(), customerId);
            return null;
        });
    }

    public Map<String, Object> requireCustomer(String authorization) {
        SecurityService.Principal principal = principal(authorization, SecurityService.ROLE_CUSTOMER);
        Map<String, Object> customer = db.read(conn ->
                Jdbc.queryOne(conn, "SELECT * FROM customer WHERE id = ?", principal.getSubjectId()));
        if (customer == null) {
            throw new UnauthorizedException("客户账号不存在");
        }
        return customer;
    }

    private SecurityService.Principal principal(String authorization, String expectedRole) {
        if (authorization == null || !authorization.toLowerCase().startsWith("bearer ")) {
            throw new UnauthorizedException("未登录或凭证缺失");
        }
        SecurityService.Principal principal = security.verifyToken(authorization.substring(7).trim());
        if (principal == null) {
            throw new UnauthorizedException("登录已失效，请重新登录");
        }
        if (!expectedRole.equals(principal.getRole())) {
            throw new UnauthorizedException("当前登录身份无权访问该接口");
        }
        return principal;
    }
}
