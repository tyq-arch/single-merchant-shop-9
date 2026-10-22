-- 在线购物系统（挑战A升级版）数据库结构
-- 单卖家 + 注册客户 + 多商品 + 二级类别 + 库存 + 每商品独立队列

CREATE TABLE IF NOT EXISTS seller (
    id            TEXT PRIMARY KEY,
    username      TEXT NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    created_at    TEXT NOT NULL,
    updated_at    TEXT
);

CREATE TABLE IF NOT EXISTS customer (
    id             TEXT PRIMARY KEY,
    username       TEXT NOT NULL UNIQUE,
    password_hash  TEXT NOT NULL,
    phone          TEXT NOT NULL,
    trade_location TEXT NOT NULL,
    created_at     TEXT NOT NULL,
    updated_at     TEXT
);

CREATE TABLE IF NOT EXISTS category (
    id         TEXT PRIMARY KEY,
    parent_id  TEXT REFERENCES category(id),
    name       TEXT NOT NULL,
    level      INTEGER NOT NULL CHECK (level IN (1, 2)),
    created_at TEXT NOT NULL,
    updated_at TEXT
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_category_sibling
    ON category(COALESCE(parent_id, ''), name);

CREATE TABLE IF NOT EXISTS product (
    id           TEXT PRIMARY KEY,
    seller_id    TEXT NOT NULL REFERENCES seller(id),
    category_id  TEXT NOT NULL REFERENCES category(id),
    name         TEXT NOT NULL,
    description  TEXT,
    price_cents  INTEGER NOT NULL CHECK (price_cents > 0),
    stock        INTEGER NOT NULL DEFAULT 0 CHECK (stock >= 0),
    status       TEXT NOT NULL,
    published_at TEXT NOT NULL,
    created_at   TEXT NOT NULL,
    updated_at   TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS ix_product_status ON product(status);
CREATE INDEX IF NOT EXISTS ix_product_category ON product(category_id);

CREATE TABLE IF NOT EXISTS product_image (
    id         TEXT PRIMARY KEY,
    product_id TEXT NOT NULL REFERENCES product(id) ON DELETE CASCADE,
    url        TEXT NOT NULL,
    sort_order INTEGER NOT NULL,
    created_at TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS ix_product_image ON product_image(product_id, sort_order);

CREATE TABLE IF NOT EXISTS buy_intent (
    id             TEXT PRIMARY KEY,
    product_id     TEXT NOT NULL REFERENCES product(id),
    customer_id    TEXT NOT NULL REFERENCES customer(id),
    status         TEXT NOT NULL,
    queue_position INTEGER NOT NULL DEFAULT 0,
    requeue_count  INTEGER NOT NULL DEFAULT 0,
    created_at     TEXT NOT NULL,
    updated_at     TEXT NOT NULL
);

-- 同一客户对同一商品同时仅允许一个有效意向（FR-021）
CREATE UNIQUE INDEX IF NOT EXISTS ux_intent_active
    ON buy_intent(product_id, customer_id)
    WHERE status IN ('QUEUED', 'REQUEUED', 'IN_TRANSACTION');

CREATE INDEX IF NOT EXISTS ix_intent_product ON buy_intent(product_id, status);
CREATE INDEX IF NOT EXISTS ix_intent_customer ON buy_intent(customer_id);

CREATE TABLE IF NOT EXISTS queue_item (
    intent_id  TEXT PRIMARY KEY REFERENCES buy_intent(id) ON DELETE CASCADE,
    product_id TEXT NOT NULL REFERENCES product(id),
    position   INTEGER NOT NULL,
    created_at TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS ix_queue_product ON queue_item(product_id, position);

CREATE TABLE IF NOT EXISTS trade_record (
    id          TEXT PRIMARY KEY,
    intent_id   TEXT NOT NULL REFERENCES buy_intent(id),
    product_id  TEXT NOT NULL REFERENCES product(id),
    customer_id TEXT NOT NULL REFERENCES customer(id),
    result      TEXT NOT NULL,
    note        TEXT,
    created_at  TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS ix_trade_product ON trade_record(product_id, created_at);
CREATE INDEX IF NOT EXISTS ix_trade_customer ON trade_record(customer_id, created_at);

CREATE TABLE IF NOT EXISTS operation_log (
    id         TEXT PRIMARY KEY,
    seller_id  TEXT NOT NULL,
    action     TEXT NOT NULL,
    detail     TEXT,
    created_at TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS ix_log_created ON operation_log(created_at DESC);
