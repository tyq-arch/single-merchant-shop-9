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
