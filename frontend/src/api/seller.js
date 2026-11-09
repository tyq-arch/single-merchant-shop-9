import http from './http'

/**
 * 卖家接口 —— 挑战 B。
 *
 * 订单接口约定（与后端对接时以本节为准）：
 *   GET  /seller/orders?page=&page_size=&status=&keyword= → { items, total }
 *   GET  /seller/orders/{order_id}                         → { order }
 *   POST /seller/orders/{order_id}/confirm                 → { message, order }
 *   POST /seller/orders/{order_id}/prepare                 → { message, order }
 *   POST /seller/orders/{order_id}/ship                    → { message, order }
 *   POST /seller/orders/{order_id}/complete                → { message, order }
 *   POST /seller/orders/{order_id}/cancel                  → { message, order }
 *
 * 订单状态：CREATED → CONFIRMED → PREPARED → SHIPPED → COMPLETED，
 * CANCELLED 为终态。状态只能按序推进，前端不自行计算业务规则。
 */

/* ---------------- 账号 ---------------- */

export function login(payload) {
  return http.post('/seller/login', payload, { authRole: 'none' })
}

/** 卖家新密码至少 8 位（FR-037） */
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

const ADVANCE_PATHS = {
  CONFIRM: 'confirm',
  PREPARE: 'prepare',
  SHIP: 'ship',
  COMPLETE: 'complete',
}

/** 按订单状态机推进一步：CONFIRM / PREPARE / SHIP / COMPLETE */
export function advanceOrder(orderId, action) {
  const path = ADVANCE_PATHS[action]
  if (!path) {
    return Promise.reject(new Error(`未知的订单推进动作：${action}`))
  }
  return http.post(`/seller/orders/${encodeURIComponent(orderId)}/${path}`)
}

export function cancelOrder(orderId) {
  return http.post(`/seller/orders/${encodeURIComponent(orderId)}/cancel`)
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
