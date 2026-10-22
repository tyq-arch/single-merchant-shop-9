import http from './http'

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

export function freezeProduct(productId) {
  return http.post(`/seller/products/${encodeURIComponent(productId)}/freeze`)
}

export function unfreezeProduct(productId) {
  return http.post(`/seller/products/${encodeURIComponent(productId)}/unfreeze`)
}

/** 与队列第一位进入交易，商品自动冻结 */
export function startTransaction(productId) {
  return http.post(`/seller/products/${encodeURIComponent(productId)}/start-transaction`)
}

/* ---------------- 交易与队列 ---------------- */

/**
 * 标记交易结果：
 * SUCCESS: { result: 'SUCCESS', note? }
 * FAILED : { result: 'FAILED', failed_action: 'VOID' | 'REQUEUE', note? }
 */
export function markTransaction(intentId, payload) {
  return http.post(`/seller/intents/${encodeURIComponent(intentId)}/transaction`, payload)
}

export function listIntents(productId, params) {
  return http.get(`/seller/products/${encodeURIComponent(productId)}/intents`, { params })
}

/** 导出队列名单 CSV（Blob） */
export function exportIntents(productId) {
  return http.get(`/seller/products/${encodeURIComponent(productId)}/intents/export`, {
    responseType: 'blob',
    timeout: 60000,
  })
}

/* ---------------- 历史 ---------------- */

export function listHistory(params) {
  return http.get('/seller/history', { params })
}

export function getHistoryDetail(productId) {
  return http.get(`/seller/history/${encodeURIComponent(productId)}`)
}

/* ---------------- 客户台账 ---------------- */

export function listCustomers(params) {
  return http.get('/seller/customers', { params })
}

export function getCustomerDetail(customerId) {
  return http.get(`/seller/customers/${encodeURIComponent(customerId)}`)
}

/* ---------------- 操作日志 ---------------- */

export function listLogs(params) {
  return http.get('/seller/logs', { params })
}
