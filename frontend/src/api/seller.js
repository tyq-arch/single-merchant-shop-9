import http from './http'

/* ---------- 登录与账号 ---------- */

export function login(payload) {
  return http.post('/seller/login', payload)
}

export function changePassword(payload) {
  return http.post('/seller/password', payload)
}

/* ---------- 当前商品 ---------- */

export function getCurrentProduct() {
  return http.get('/seller/product/current')
}

export function publishProduct(payload) {
  return http.post('/seller/products', payload)
}

export function offShelf(productId) {
  return http.post(`/seller/products/${encodeURIComponent(productId)}/off-shelf`)
}

export function freezeProduct(productId) {
  return http.post(`/seller/products/${encodeURIComponent(productId)}/freeze`)
}

export function unfreezeProduct(productId) {
  return http.post(`/seller/products/${encodeURIComponent(productId)}/unfreeze`)
}

export function startTransaction(productId) {
  return http.post(`/seller/products/${encodeURIComponent(productId)}/start-transaction`)
}

/* ---------- 意向购买人 ---------- */

export function listIntents(productId, params = {}) {
  return http.get(`/seller/products/${encodeURIComponent(productId)}/intents`, { params })
}

/** 导出需带令牌，故走 axios 而非直接链接（接口文档 5.10） */
export function exportIntents(productId) {
  return http.get(`/seller/products/${encodeURIComponent(productId)}/intents/export`, {
    responseType: 'blob',
  })
}

/* ---------- 历史商品 ---------- */

export function listHistory(params = {}) {
  return http.get('/seller/history', { params })
}

export function getHistoryDetail(productId) {
  return http.get(`/seller/history/${encodeURIComponent(productId)}`)
}

/* ---------- 交易结果与失败者处理 ---------- */

export function markTransaction(orderId, payload) {
  return http.post(`/seller/orders/${encodeURIComponent(orderId)}/transaction`, payload)
}

export function voidOrder(orderId) {
  return http.post(`/seller/orders/${encodeURIComponent(orderId)}/void`)
}

export function requeueOrder(orderId) {
  return http.post(`/seller/orders/${encodeURIComponent(orderId)}/requeue`)
}

/* ---------- 操作日志 ---------- */

export function listLogs(params = {}) {
  return http.get('/seller/logs', { params })
}
