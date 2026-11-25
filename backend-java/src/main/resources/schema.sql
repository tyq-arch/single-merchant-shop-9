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

-- ============================================================
-- 挑战B（买家端）：收藏 / 购物车 / 订单 / 订单项 / 库存占用 / 游客临时预选
-- 库存语义：product.stock 为原始库存；可售 = 原始库存 - 活跃占用量
-- 生命周期：下单占用（stock_reservation ACTIVE）→ 取消回补（RELEASED）→ 交易完成扣减（CONSUMED）
-- ============================================================

CREATE TABLE IF NOT EXISTS favorite (
    id          TEXT PRIMARY KEY,
    customer_id TEXT NOT NULL REFERENCES customer(id),
    product_id  TEXT NOT NULL REFERENCES product(id),
    created_at  TEXT NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_favorite_customer_product ON favorite(customer_id, product_id);

CREATE TABLE IF NOT EXISTS cart_item (
    id          TEXT PRIMARY KEY,
    customer_id TEXT NOT NULL REFERENCES customer(id),
    product_id  TEXT NOT NULL REFERENCES product(id),
    quantity    INTEGER NOT NULL CHECK (quantity > 0),
    created_at  TEXT NOT NULL,
    updated_at  TEXT NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_cart_customer_product ON cart_item(customer_id, product_id);

CREATE TABLE IF NOT EXISTS shop_order (
    id          TEXT PRIMARY KEY,
    order_no    TEXT NOT NULL UNIQUE,
    customer_id TEXT NOT NULL REFERENCES customer(id),
    status      TEXT NOT NULL,
    total_cents INTEGER NOT NULL CHECK (total_cents >= 0),
    created_at  TEXT NOT NULL,
    updated_at  TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS ix_order_customer ON shop_order(customer_id, created_at DESC);

CREATE TABLE IF NOT EXISTS order_item (
    id             TEXT PRIMARY KEY,
    order_id       TEXT NOT NULL REFERENCES shop_order(id) ON DELETE CASCADE,
    product_id     TEXT NOT NULL REFERENCES product(id),
    product_name   TEXT NOT NULL,
    price_cents    INTEGER NOT NULL,
    quantity       INTEGER NOT NULL,
    subtotal_cents INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS ix_order_item_order ON order_item(order_id);

CREATE TABLE IF NOT EXISTS stock_reservation (
    id         TEXT PRIMARY KEY,
    order_id   TEXT NOT NULL REFERENCES shop_order(id) ON DELETE CASCADE,
    product_id TEXT NOT NULL REFERENCES product(id),
    quantity   INTEGER NOT NULL,
    status     TEXT NOT NULL,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS ix_reservation_product ON stock_reservation(product_id, status);
CREATE INDEX IF NOT EXISTS ix_reservation_order ON stock_reservation(order_id, status);

-- 游客临时预选：以客户端匿名会话 ID 归属，登录后并入账号
CREATE TABLE IF NOT EXISTS guest_favorite (
    id         TEXT PRIMARY KEY,
    guest_id   TEXT NOT NULL,
    product_id TEXT NOT NULL REFERENCES product(id),
    created_at TEXT NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_guest_favorite ON guest_favorite(guest_id, product_id);

CREATE TABLE IF NOT EXISTS guest_cart_item (
    id         TEXT PRIMARY KEY,
    guest_id   TEXT NOT NULL,
    product_id TEXT NOT NULL REFERENCES product(id),
    quantity   INTEGER NOT NULL CHECK (quantity > 0),
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_guest_cart ON guest_cart_item(guest_id, product_id);

-- ============================================================
-- 挑战C（买家端）：收货地址 / 线上支付 / 退款 / 配送物流 / 售后服务
-- 订单状态：待支付 → 已支付 → 商家确认 → 备货完成 → 开始发货 → 交易完成（+已取消）
-- 下单即占用库存；已支付订单取消自动退款；待支付超 24 小时自动取消并回补
-- ============================================================

CREATE TABLE IF NOT EXISTS customer_address (
    id             TEXT PRIMARY KEY,
    customer_id    TEXT NOT NULL REFERENCES customer(id),
    receiver_name  TEXT NOT NULL,
    receiver_phone TEXT NOT NULL,
    province       TEXT NOT NULL,
    city           TEXT NOT NULL,
    district       TEXT NOT NULL,
    detail         TEXT NOT NULL,
    postal_code    TEXT,
    is_default     INTEGER NOT NULL DEFAULT 0,
    created_at     TEXT NOT NULL,
    updated_at     TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS ix_address_customer ON customer_address(customer_id, is_default DESC);

CREATE TABLE IF NOT EXISTS payment (
    id             TEXT PRIMARY KEY,
    order_id       TEXT NOT NULL REFERENCES shop_order(id),
    payment_no     TEXT NOT NULL UNIQUE,
    method         TEXT NOT NULL,
    amount_cents   INTEGER NOT NULL CHECK (amount_cents >= 0),
    status         TEXT NOT NULL,
    transaction_no TEXT,
    failure_reason TEXT,
    paid_at        TEXT,
    created_at     TEXT NOT NULL,
    updated_at     TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS ix_payment_order ON payment(order_id, created_at DESC);

CREATE TABLE IF NOT EXISTS refund (
    id           TEXT PRIMARY KEY,
    order_id     TEXT NOT NULL REFERENCES shop_order(id),
    payment_id   TEXT NOT NULL REFERENCES payment(id),
    refund_no    TEXT NOT NULL UNIQUE,
    amount_cents INTEGER NOT NULL,
    channel      TEXT NOT NULL,
    status       TEXT NOT NULL,
    reason       TEXT,
    refunded_at  TEXT NOT NULL,
    created_at   TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS ix_refund_order ON refund(order_id);

CREATE TABLE IF NOT EXISTS shipment (
    id          TEXT PRIMARY KEY,
    order_id    TEXT NOT NULL UNIQUE REFERENCES shop_order(id),
    carrier     TEXT NOT NULL,
    tracking_no TEXT NOT NULL,
    status      TEXT NOT NULL,
    shipped_at  TEXT NOT NULL,
    updated_at  TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS shipment_track (
    id          TEXT PRIMARY KEY,
    shipment_id TEXT NOT NULL REFERENCES shipment(id) ON DELETE CASCADE,
    status      TEXT NOT NULL,
    description TEXT,
    occurred_at TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS ix_track_shipment ON shipment_track(shipment_id, occurred_at);

CREATE TABLE IF NOT EXISTS after_sale (
    id            TEXT PRIMARY KEY,
    after_sale_no TEXT NOT NULL UNIQUE,
    order_id      TEXT NOT NULL REFERENCES shop_order(id),
    order_item_id TEXT NOT NULL REFERENCES order_item(id),
    customer_id   TEXT NOT NULL REFERENCES customer(id),
    title         TEXT NOT NULL,
    description   TEXT NOT NULL,
    status        TEXT NOT NULL,
    result        TEXT,
    result_note   TEXT,
    created_at    TEXT NOT NULL,
    updated_at    TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS ix_after_sale_customer ON after_sale(customer_id, created_at DESC);
CREATE INDEX IF NOT EXISTS ix_after_sale_item ON after_sale(order_item_id);

CREATE TABLE IF NOT EXISTS after_sale_image (
    id            TEXT PRIMARY KEY,
    after_sale_id TEXT NOT NULL REFERENCES after_sale(id) ON DELETE CASCADE,
    url           TEXT NOT NULL,
    sort_order    INTEGER NOT NULL,
    created_at    TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS ix_after_sale_image ON after_sale_image(after_sale_id, sort_order);
