package com.shop.services;

import com.shop.config.AppConfig;
import com.shop.db.Db;
import com.shop.db.Jdbc;
import com.shop.security.SecurityService;
import com.shop.util.Utils;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 启动初始化：建表（幂等）+ 写入唯一固定卖家账号。
 */
@Component
public class InitService {

    private final AppConfig config;
    private final Db db;
    private final SecurityService security;

    public InitService(AppConfig config, Db db, SecurityService security) {
        this.config = config;
        this.db = db;
        this.security = security;
    }

    @PostConstruct
    public void init() {
        db.executeScript(readSchema());
        db.transaction(conn -> {
            migrateOrderColumns(conn);
            Map<String, Object> existing = Jdbc.queryOne(conn,
                    "SELECT id FROM seller WHERE username = ?", config.getSellerUsername());
            if (existing == null) {
                String now = Utils.nowIso();
                Jdbc.update(conn,
                        "INSERT INTO seller (id, username, password_hash, created_at, updated_at) "
                                + "VALUES (?, ?, ?, ?, ?)",
                        Utils.newId(), config.getSellerUsername(),
                        security.hashPassword(config.getSellerPassword()), now, now);
            }
            return null;
        });
    }

    /** 挑战C：为已存在的订单表补充收货地址快照与支付/取消时间字段，并迁移旧状态。 */
    private void migrateOrderColumns(java.sql.Connection conn) throws Exception {
        ensureColumn(conn, "shop_order", "receiver_name", "TEXT");
        ensureColumn(conn, "shop_order", "receiver_phone", "TEXT");
        ensureColumn(conn, "shop_order", "receiver_province", "TEXT");
        ensureColumn(conn, "shop_order", "receiver_city", "TEXT");
        ensureColumn(conn, "shop_order", "receiver_district", "TEXT");
        ensureColumn(conn, "shop_order", "receiver_detail", "TEXT");
        ensureColumn(conn, "shop_order", "receiver_postal_code", "TEXT");
        ensureColumn(conn, "shop_order", "paid_at", "TEXT");
        ensureColumn(conn, "shop_order", "cancelled_at", "TEXT");
        ensureColumn(conn, "shop_order", "cancel_by", "TEXT");
        ensureColumn(conn, "shop_order", "cancel_reason", "TEXT");
        Jdbc.update(conn, "UPDATE shop_order SET status = 'PENDING_PAYMENT' WHERE status = 'CUSTOMER_PLACED'");
    }

    private void ensureColumn(java.sql.Connection conn, String table, String column, String definition)
            throws Exception {
        for (Map<String, Object> info : Jdbc.query(conn, "PRAGMA table_info(" + table + ")")) {
            if (column.equals(Jdbc.strOf(info, "name"))) {
                return;
            }
        }
        Jdbc.update(conn, "ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
    }

    private String readSchema() {
        try (InputStream in = new ClassPathResource("schema.sql").getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("无法读取 schema.sql", e);
        }
    }
}
