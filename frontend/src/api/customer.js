import http from './http'

/**
 * 客户接口 —— 「看不用登录，买必须登录」。
 * 注册、登录本身不携带令牌，其余接口由 http 拦截器自动注入客户令牌。
 */

/** 注册：username / password / phone / trade_location */
export function register(payload) {
  return http.post('/customer/register', payload, { authRole: 'none' })
}

/** 登录，返回 { token, expires_at, customer } */
export function login(payload) {
  return http.post('/customer/login', payload, { authRole: 'none' })
}

/** 登出（无状态令牌，服务端确认后客户端丢弃即可） */
export function logout() {
  return http.post('/customer/logout')
}

/** 个人资料与成交次数 */
export function getMe() {
  return http.get('/customer/me')
}

/** 修改密码（新密码 ≥6 位） */
export function changePassword(payload) {
  return http.post('/customer/password', payload)
}

/** 提交购买意向，进入该商品独立队列 */
export function createIntent(productId) {
  return http.post('/customer/intents', { product_id: productId })
}

/** 我的意向列表（分页、可按状态筛选） */
export function listIntents(params) {
  return http.get('/customer/intents', { params })
}

/** 我的意向详情（只读，无撤销/修改入口） */
export function getIntent(intentId) {
  return http.get(`/customer/intents/${encodeURIComponent(intentId)}`)
}

/** 历史下单记录（含全部意向结果） */
export function listOrders(params) {
  return http.get('/customer/orders', { params })
}
