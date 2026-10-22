package com.shop.db;

import com.shop.config.AppConfig;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.locks.ReentrantLock;

/**
 * SQLite 数据库访问层。
 *
 * <p>并发策略（NFR-013 / NFR-014）：
 * <ul>
 *   <li>读操作使用独立连接，开启 WAL 模式，读写互不阻塞；</li>
 *   <li>写操作统一走 {@link #transaction(Fn)}，内部用全局可重入锁 + {@code BEGIN IMMEDIATE}，
 *       保证排队、冻结、递补在并发下严格串行，先到先得不被破坏。</li>
 * </ul>
 */
@Component
public class Db {

    /** 带受检异常的函数式接口。 */
    public interface Fn<T> {
        T apply(Connection conn) throws Exception;
    }

    private final AppConfig config;
    private final ReentrantLock writeLock = new ReentrantLock(true);

    public Db(AppConfig config) {
        this.config = config;
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("缺少 SQLite JDBC 驱动", e);
        }
    }

    public Connection connect() throws SQLException {
        Connection conn = DriverManager.getConnection("jdbc:sqlite:" + config.getDbPath());
        try (Statement st = conn.createStatement()) {
            st.execute("PRAGMA foreign_keys = ON");
            st.execute("PRAGMA busy_timeout = 30000");
            st.execute("PRAGMA journal_mode = WAL");
        }
        return conn;
    }

    /** 只读 / 自动提交连接，用完即关。 */
    public <T> T read(Fn<T> fn) {
        try (Connection conn = connect()) {
            return fn.apply(conn);
        } catch (Exception e) {
            throw wrap(e);
        }
    }

    /**
     * 串行化写事务，保证业务状态机变更的原子性与顺序一致性。
     */
    public <T> T transaction(Fn<T> fn) {
        writeLock.lock();
        try {
            try (Connection conn = connect(); Statement st = conn.createStatement()) {
                st.execute("BEGIN IMMEDIATE");
                try {
                    T result = fn.apply(conn);
                    st.execute("COMMIT");
                    return result;
                } catch (Exception e) {
                    try {
                        st.execute("ROLLBACK");
                    } catch (SQLException ignore) {
                        // 回滚失败时保留原始异常
                    }
                    throw wrap(e);
                }
            }
        } catch (Exception e) {
            throw wrap(e);
        } finally {
            writeLock.unlock();
        }
    }

    /** 执行建表脚本（按分号拆分，忽略注释行）。 */
    public void executeScript(String script) {
        StringBuilder cleaned = new StringBuilder();
        for (String line : script.split("\\r?\\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("--")) {
                continue;
            }
            int commentAt = trimmed.indexOf("--");
            if (commentAt >= 0) {
                trimmed = trimmed.substring(0, commentAt).trim();
            }
            if (!trimmed.isEmpty()) {
                cleaned.append(trimmed).append('\n');
            }
        }
        read(conn -> {
            try (Statement st = conn.createStatement()) {
                for (String statement : cleaned.toString().split(";")) {
                    if (!statement.trim().isEmpty()) {
                        st.execute(statement);
                    }
                }
            }
            return null;
        });
    }

    private RuntimeException wrap(Exception e) {
        if (e instanceof RuntimeException) {
            return (RuntimeException) e;
        }
        return new DbException("数据库访问失败：" + e.getMessage(), e);
    }

    /** 数据库访问异常（非业务异常）。 */
    public static class DbException extends RuntimeException {
        public DbException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
