package com.shop.security;

import com.shop.config.AppConfig;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 安全模块：口令哈希与双角色无状态会话令牌。
 *
 * <p>令牌载荷为 {@code role.subjectId.expiresAt.signature}，卖家与客户互不越权；
 * 口令哈希仍为 PBKDF2-HMAC-SHA256（200000 轮）。
 */
@Component
public class SecurityService {

    public static final String ROLE_SELLER = "seller";
    public static final String ROLE_CUSTOMER = "customer";

    private static final int PBKDF2_ITERATIONS = 200_000;
    private static final int PBKDF2_KEY_LENGTH_BITS = 256;
    private static final int SALT_BYTES = 16;
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final AppConfig config;
    private final SecureRandom random = new SecureRandom();

    public SecurityService(AppConfig config) {
        this.config = config;
    }

    // --------------------------- 口令哈希 ---------------------------

    public String hashPassword(String password) {
        byte[] salt = new byte[SALT_BYTES];
        random.nextBytes(salt);
        byte[] digest = pbkdf2(password, salt, PBKDF2_ITERATIONS);
        return "pbkdf2_sha256$" + PBKDF2_ITERATIONS + "$" + hex(salt) + "$" + hex(digest);
    }

    public boolean verifyPassword(String password, String stored) {
        if (password == null || stored == null) {
            return false;
        }
        try {
            String[] parts = stored.split("\\$", -1);
            if (parts.length != 4 || !"pbkdf2_sha256".equals(parts[0])) {
                return false;
            }
            int iterations = Integer.parseInt(parts[1]);
            byte[] salt = unhex(parts[2]);
            byte[] expected = unhex(parts[3]);
            byte[] actual = pbkdf2(password, salt, iterations);
            return MessageDigest.isEqual(expected, actual);
        } catch (RuntimeException e) {
            return false;
        }
    }

    private byte[] pbkdf2(String password, byte[] salt, int iterations) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, PBKDF2_KEY_LENGTH_BITS);
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            return factory.generateSecret(spec).getEncoded();
        } catch (Exception e) {
            throw new IllegalStateException("口令哈希失败", e);
        }
    }

    // --------------------------- 会话令牌 ---------------------------

    public Token createToken(String role, String subjectId) {
        long expiresAt = System.currentTimeMillis() / 1000 + config.getTokenTtlSeconds();
        String payload = role + "." + subjectId + "." + expiresAt;
        String signature = sign(payload);
        String token = Base64.getUrlEncoder()
                .encodeToString((payload + "." + signature).getBytes(StandardCharsets.UTF_8));
        return new Token(token, expiresAt);
    }

    /** 校验令牌，成功返回角色与主体 ID，失败返回 null。 */
    public Principal verifyToken(String token) {
        if (token == null || token.isEmpty()) {
            return null;
        }
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
            String[] parts = decoded.split("\\.", -1);
            if (parts.length != 4) {
                return null;
            }
            String role = parts[0];
            String subjectId = parts[1];
            if (!ROLE_SELLER.equals(role) && !ROLE_CUSTOMER.equals(role)) {
                return null;
            }
            String payload = role + "." + subjectId + "." + parts[2];
            if (!MessageDigest.isEqual(sign(payload).getBytes(StandardCharsets.UTF_8),
                    parts[3].getBytes(StandardCharsets.UTF_8))) {
                return null;
            }
            if (Long.parseLong(parts[2]) < System.currentTimeMillis() / 1000) {
                return null;
            }
            return new Principal(role, subjectId);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(config.getSecretKey().getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return hex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("令牌签名失败", e);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            builder.append(String.format("%02x", b));
        }
        return builder.toString();
    }

    private static byte[] unhex(String hex) {
        int length = hex.length();
        byte[] result = new byte[length / 2];
        for (int i = 0; i < length; i += 2) {
            result[i / 2] = (byte) Integer.parseInt(hex.substring(i, i + 2), 16);
        }
        return result;
    }

    /** 令牌与过期时间戳。 */
    public static final class Token {
        private final String token;
        private final long expiresAt;

        public Token(String token, long expiresAt) {
            this.token = token;
            this.expiresAt = expiresAt;
        }

        public String getToken() {
            return token;
        }

        public long getExpiresAt() {
            return expiresAt;
        }
    }

    /** 令牌解析结果。 */
    public static final class Principal {
        private final String role;
        private final String subjectId;

        public Principal(String role, String subjectId) {
            this.role = role;
            this.subjectId = subjectId;
        }

        public String getRole() {
            return role;
        }

        public String getSubjectId() {
            return subjectId;
        }
    }
}
