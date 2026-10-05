-- 在线购物系统（单卖家版）数据库结构
-- 对应需求文档 6.1 实体关系图，并在其上补充业务必需字段与索引。

PRAGMA foreign_keys = ON;

-- 卖家：唯一固定账号，系统初始化写入，无注册、无退出登录
CREATE TABLE IF NOT EXISTS seller (
    id            TEXT PRIMARY KEY,
    username      TEXT NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    created_at    TEXT NOT NULL
);

-- 商品：同一时间只能有一条处于 ON_SALE / FROZEN，发布后不可编辑
CREATE TABLE IF NOT EXISTS product (
    id           TEXT PRIMARY KEY,
    name         TEXT NOT NULL,
    description  TEXT,
    image_url    TEXT,
    price_cents  INTEGER NOT NULL CHECK (price_cents > 0),
    status       TEXT NOT NULL CHECK (status IN ('ON_SALE', 'FROZEN', 'OFF_SHELF')),
    published_at TEXT NOT NULL,
    updated_at   TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_product_status ON product (status, published_at);

-- 订单 / 购买意向：含商品快照（FR-007），口令码是买家认领意向的唯一凭证
CREATE TABLE IF NOT EXISTS order_intent (
    id                   TEXT PRIMARY KEY,
    product_id           TEXT NOT NULL REFERENCES product (id),
    -- 商品快照：下单瞬间固化，保证历史可追溯
    snapshot_name        TEXT NOT NULL,
    snapshot_description TEXT,
    snapshot_image_url   TEXT,
    snapshot_price_cents INTEGER NOT NULL,
    buyer_name           TEXT NOT NULL,
    buyer_phone          TEXT NOT NULL,
    status               TEXT NOT NULL CHECK (status IN (
        'QUEUED', 'CANCELLED', 'IN_TRANSACTION', 'SUCCESS', 'FAILED', 'VOIDED', 'REQUEUED'
    )),
    queue_position       INTEGER,
    requeue_count        INTEGER NOT NULL DEFAULT 0,
    command_code         TEXT,
    created_at           TEXT NOT NULL,
    updated_at           TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_order_product_status ON order_intent (product_id, status);
CREATE INDEX IF NOT EXISTS idx_order_code ON order_intent (command_code);

-- 口令码：全局唯一，与订单一一对应
CREATE TABLE IF NOT EXISTS command_code (
    code       TEXT PRIMARY KEY,
    order_id   TEXT NOT NULL REFERENCES order_intent (id),
    product_id TEXT NOT NULL REFERENCES product (id),
    status     TEXT NOT NULL CHECK (status IN ('ACTIVE', 'INVALID')),
    created_at TEXT NOT NULL,
    expires_at TEXT
);
CREATE INDEX IF NOT EXISTS idx_code_order ON command_code (order_id);
CREATE INDEX IF NOT EXISTS idx_code_product ON command_code (product_id);

-- 排队队列：严格按 submitted_at 先到先得
CREATE TABLE IF NOT EXISTS queue_item (
    id           TEXT PRIMARY KEY,
    product_id   TEXT NOT NULL REFERENCES product (id),
    order_id     TEXT NOT NULL REFERENCES order_intent (id),
    position     INTEGER NOT NULL,
    submitted_at TEXT NOT NULL,
    UNIQUE (product_id, order_id)
);
CREATE INDEX IF NOT EXISTS idx_queue_product_pos ON queue_item (product_id, position);

-- 交易记录：同一商品多轮交易（多次失败）全部保留，不可覆盖
CREATE TABLE IF NOT EXISTS transaction_record (
    id         TEXT PRIMARY KEY,
    order_id   TEXT NOT NULL REFERENCES order_intent (id),
    product_id TEXT NOT NULL REFERENCES product (id),
    result     TEXT NOT NULL CHECK (result IN ('SUCCESS', 'FAILED')),
    note       TEXT,
    created_at TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_tx_product ON transaction_record (product_id, created_at);
CREATE INDEX IF NOT EXISTS idx_tx_order ON transaction_record (order_id);

-- 操作日志：卖家关键操作可追溯（FR-042 / NFR-010）
CREATE TABLE IF NOT EXISTS operation_log (
    id         TEXT PRIMARY KEY,
    seller_id  TEXT REFERENCES seller (id),
    action     TEXT NOT NULL,
    detail     TEXT,
    created_at TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_log_created ON operation_log (created_at);
