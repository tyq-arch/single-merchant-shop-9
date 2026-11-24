import http from './http'

/**
 * 卖家接口 —— 挑战 C。
 *
 * 订单接口约定（与后端对接时以本节为准）：
 *   GET   /seller/orders?page=&page_size=&status=&keyword= → { items, total }
 *   GET   /seller/orders/{order_id}                         → { order }
 *   POST  /seller/orders/{order_id}/advance                 → { message, order }
 *   POST  /seller/orders/{order_id}/ship { carrier, tracking_no } → { message, order }
 *   PATCH /seller/orders/{order_id}/shipment { status }     → { message, shipment }
 *   POST  /seller/orders/{order_id}/cancel                  → { message, order }
 *
 * 订单状态：PAYMENT_PENDING → PAID → SELLER_CONFIRMED → STOCK_READY → SHIPPING
 * → COMPLETED，CANCELLED 为终态。推进只有「推进一步」一个接口，下一状态由
 * 后端状态机决定；「备货完成 → 开始发货」必须走 /ship 并填写物流信息（FR-049）。
 *
 * 物流状态由商家手动更新（TBD-09）：
 *   SHIPPED → IN_TRANSIT → DELIVERING → DELIVERED
 *
 * 售后接口（FR-053）：
 *   GET  /seller/aftersales?page=&page_size=&status=&keyword= → { items, total }
 *   GET  /seller/aftersales/{aftersale_id}                    → { aftersale }
 *   POST /seller/aftersales/{aftersale_id}/accept             → { message, aftersale }
 *   POST /seller/aftersales/{aftersale_id}/reject { reason }  → { message, aftersale }
 *   POST /seller/aftersales/{aftersale_id}/resolve
 *        { result, note, refund_amount? }                     → { message, aftersale }
 */

/* ---------------- 账号 ---------------- */

export function login(payload) {
  return http.post('/seller/login', payload, { authRole: 'none' })
}

/** 卖家新密码至少 8 位（FR-036） */
export function changePassword(payload) {
  return http.post('/seller/password', payload)
}

/* ---------------- 类别 ---------------- */

/** 二级类别树，一级类别带 children 与商品数 */
export function listCategories() {
  return http.get('/seller/categories')
}

/** 新增类别：不传 parent_id 为一级，传一级 ID 为二级 */
export function createCategory(payload) {
  return http.post('/seller/categories', payload)
}

export function renameCategory(categoryId, name) {
  return http.patch(`/seller/categories/${encodeURIComponent(categoryId)}`, { name })
}

export function deleteCategory(categoryId) {
  return http.delete(`/seller/categories/${encodeURIComponent(categoryId)}`)
}

/* ---------------- 商品 ---------------- */

/**
 * 发布商品：
 * { name, price, description, stock, category_id, image_urls[] }
 */
export function createProduct(payload) {
  return http.post('/seller/products', payload)
}

export function listProducts(params) {
  return http.get('/seller/products', { params })
}

export function getProduct(productId) {
  return http.get(`/seller/products/${encodeURIComponent(productId)}`)
}

/** 调整库存（非负整数；调 0 即售罄下架，调 >0 恢复在售） */
export function updateStock(productId, stock) {
  return http.patch(`/seller/products/${encodeURIComponent(productId)}/stock`, { stock })
}

export function offShelf(productId) {
  return http.post(`/seller/products/${encodeURIComponent(productId)}/off-shelf`)
}

export function onShelf(productId) {
  return http.post(`/seller/products/${encodeURIComponent(productId)}/on-shelf`)
}

/* ---------------- 订单 ---------------- */

export function listOrders(params) {
  return http.get('/seller/orders', { params })
}

export function getOrder(orderId) {
  return http.get(`/seller/orders/${encodeURIComponent(orderId)}`)
}

/**
 * 按订单状态机推进一步（FR-026）。
 *
 * 商家可推进的只有 PAID 之后的状态：确认 → 备货完成 → 交易完成。
 * 「开始发货」不走本接口，必须调用 shipOrder 填写物流信息。
 */
export function advanceOrder(orderId) {
  return http.post(`/seller/orders/${encodeURIComponent(orderId)}/advance`)
}

/** 开始发货：填写物流服务商与运单号（FR-049），订单进入「开始发货」 */
export function shipOrder(orderId, { carrier, trackingNo }) {
  return http.post(`/seller/orders/${encodeURIComponent(orderId)}/ship`, {
    carrier,
    tracking_no: trackingNo,
  })
}

/** 商家手动更新物流状态：SHIPPED / IN_TRANSIT / DELIVERING / DELIVERED */
export function updateShipmentStatus(orderId, status) {
  return http.patch(
    `/seller/orders/${encodeURIComponent(orderId)}/shipment`,
    { status },
  )
}

export function cancelOrder(orderId) {
  return http.post(`/seller/orders/${encodeURIComponent(orderId)}/cancel`)
}

/* ---------------- 售后 ---------------- */

export function listAfterSales(params) {
  return http.get('/seller/aftersales', { params })
}

export function getAfterSale(afterSaleId) {
  return http.get(`/seller/aftersales/${encodeURIComponent(afterSaleId)}`)
}

/** 受理售后：待处理 → 协商中 */
export function acceptAfterSale(afterSaleId) {
  return http.post(`/seller/aftersales/${encodeURIComponent(afterSaleId)}/accept`)
}

/** 拒绝售后：待处理 / 协商中 → 已拒绝 */
export function rejectAfterSale(afterSaleId, reason) {
  return http.post(
    `/seller/aftersales/${encodeURIComponent(afterSaleId)}/reject`,
    { reason },
  )
}

/**
 * 记录售后结果：协商中 → 已解决。
 * result: RETURN 退货 / REFUND 退款 / EXCHANGE 换货 / OTHER 其他；
 * 退款时 refund_amount 必填（TBD-14：与取消退款同一原路退回通道）。
 */
export function resolveAfterSale(afterSaleId, payload) {
  return http.post(
    `/seller/aftersales/${encodeURIComponent(afterSaleId)}/resolve`,
    payload,
  )
}

/* ---------------- 客户台账 ---------------- */

export function listCustomers(params) {
  return http.get('/seller/customers', { params })
}

/** 客户详情，含该客户的订单历史 orders[] */
export function getCustomerDetail(customerId) {
  return http.get(`/seller/customers/${encodeURIComponent(customerId)}`)
}

/* ---------------- 操作日志 ---------------- */

export function listLogs(params) {
  return http.get('/seller/logs', { params })
}
