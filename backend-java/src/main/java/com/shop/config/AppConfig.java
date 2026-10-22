package com.shop.config;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 后端配置：路径、卖家初始账号、安全参数。
 *
 * <p>与 Python 版一致，全部支持环境变量覆盖（SHOP_*），便于测试与部署。
 */
@Component
public class AppConfig {

    private static final Set<String> ALLOWED_IMAGE_EXT = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(".jpg", ".jpeg", ".png")));
    private static final Set<String> ALLOWED_MEDIA_EXT = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(".jpg", ".jpeg", ".png", ".gif", ".webp", ".mp4", ".webm")));

    private final Path dataDir;
    private final Path dbPath;
    private final Path uploadDir;
    private final String sellerUsername;
    private final String sellerPassword;
    private final String secretKey;
    private final int tokenTtlSeconds;
    private final int maxImageBytes;
    private final int maxMediaBytes;
    private final int defaultPageSize;
    private final int maxPageSize;

    public AppConfig(Environment env) {
        this.dataDir = Paths.get(value(env, "SHOP_DATA_DIR", "shop.data-dir", "data"))
                .toAbsolutePath().normalize();
        this.dbPath = Paths.get(value(env, "SHOP_DB_PATH", "shop.db-path",
                this.dataDir.resolve("shop-v2.db").toString())).toAbsolutePath().normalize();
        this.uploadDir = Paths.get(value(env, "SHOP_UPLOAD_DIR", "shop.upload-dir",
                this.dataDir.resolve("uploads").toString())).toAbsolutePath().normalize();

        this.sellerUsername = value(env, "SHOP_SELLER_USERNAME", "shop.seller-username", "seller");
        this.sellerPassword = value(env, "SHOP_SELLER_PASSWORD", "shop.seller-password", "seller123");
        this.secretKey = value(env, "SHOP_SECRET_KEY", "shop.secret-key", "dev-secret-key-change-me");
        this.tokenTtlSeconds = intValue(env, "SHOP_TOKEN_TTL_SECONDS", "shop.token-ttl-seconds", 12 * 3600);
        this.maxImageBytes = intValue(env, "SHOP_MAX_IMAGE_BYTES", "shop.max-image-bytes", 5 * 1024 * 1024);
        this.maxMediaBytes = intValue(env, "SHOP_MAX_MEDIA_BYTES", "shop.max-media-bytes", 50 * 1024 * 1024);
        this.defaultPageSize = intValue(env, "SHOP_DEFAULT_PAGE_SIZE", "shop.default-page-size", 10);
        this.maxPageSize = intValue(env, "SHOP_MAX_PAGE_SIZE", "shop.max-page-size", 100);
    }

    private static String value(Environment env, String envName, String propName, String fallback) {
        String raw = env.getProperty(envName);
        if (raw == null || raw.trim().isEmpty()) {
            raw = env.getProperty(propName);
        }
        return (raw == null || raw.trim().isEmpty()) ? fallback : raw.trim();
    }

    private static int intValue(Environment env, String envName, String propName, int fallback) {
        String raw = value(env, envName, propName, null);
        return raw == null ? fallback : Integer.parseInt(raw);
    }

    @PostConstruct
    public void ensureDirs() {
        try {
            Files.createDirectories(dataDir);
            Files.createDirectories(uploadDir);
            Path parent = dbPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
        } catch (Exception e) {
            throw new IllegalStateException("无法创建数据目录：" + e.getMessage(), e);
        }
    }

    public Path getDataDir() {
        return dataDir;
    }

    public Path getDbPath() {
        return dbPath;
    }

    public Path getUploadDir() {
        return uploadDir;
    }

    public String getSellerUsername() {
        return sellerUsername;
    }

    public String getSellerPassword() {
        return sellerPassword;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public int getTokenTtlSeconds() {
        return tokenTtlSeconds;
    }

    public int getMaxImageBytes() {
        return maxImageBytes;
    }

    public int getMaxMediaBytes() {
        return maxMediaBytes;
    }

    public int getDefaultPageSize() {
        return defaultPageSize;
    }

    public int getMaxPageSize() {
        return maxPageSize;
    }

    public Set<String> getAllowedImageExt() {
        return ALLOWED_IMAGE_EXT;
    }

    public Set<String> getAllowedMediaExt() {
        return ALLOWED_MEDIA_EXT;
    }
}
