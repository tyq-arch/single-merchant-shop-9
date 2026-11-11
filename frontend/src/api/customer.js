import http from './http'

/**
 * 客户接口 —— 挑战 B。
 *
 * 浏览、搜索、收藏、加购无需登录；下单必须登录。
 * 注册、登录本身不携带令牌，其余接口由 http 拦截器自动注入客户令牌。
 *
 * 收藏 / 购物车 / 订单接口约定（与后端对接时以本节为准）：
 *
 * 收藏
 *   GET    /customer/favorites?page=&page_size=   → { items, total }
 *   GET    /customer/favorites/{product_id}       → { favorited }
 *   POST   /customer/favorites { product_id }     → { message }
 *   DELETE /customer/favorites/{product_id}       → { message }
 *
 * 购物车（同一商品仅一条）
 *   GET    /customer/cart                         → { items, item_count, total_quantity, total_amount }
 *   POST   /customer/cart { product_id, quantity }→ { message, item }
 *   PATCH  /customer/cart/{product_id} { quantity }→ { message, item }
 *   DELETE /customer/cart/{product_id}            → { message }
 *   POST   /customer/cart/favorites { product_ids[] } → { message, converted_count }
 *
 * 订单（下单必经购物车，TBD-14：没有单独的 checkout 路径，直接建单）
 *   POST   /customer/orders { product_ids[] }        → { message, order }
 *   GET    /customer/orders?page=&page_size=&status= → { items, total }
 *   GET    /customer/orders/{order_id}               → { order }
 *   POST   /customer/orders/{order_id}/cancel        → { message, order }
 *
 * 下单库存不足时后端返回 409，error.details 形如：
 *   [{ product_id, name, available }] 或 { shortages: [...] }
 */

/* ---------------- 账号 ---------------- */

/** 注册：username / password / phone / trade_location */
export function register(payload) {
  return http.post('/customer/register', payload, { authRole: 'none' })
}

/** 登录，返回 { token, expires_at, customer, preselect_merged }；带 X-Guest-Id 时合并游客临时预选 */
export function login(payload) {
  return http.post('/customer/login', payload, { authRole: 'none', withGuestId: true })
}

/** 登出（无状态令牌，服务端确认后客户端丢弃即可） */
export function logout() {
  return http.post('/customer/logout')
}

/** 个人资料与成交次数 */
export function getMe() {
  return http.get('/customer/me')
}

/** 修改密码 */
export function changePassword(payload) {
  return http.post('/customer/password', payload)
}

/* ---------------- 收藏 ---------------- */

export function listFavorites(params) {
  return http.get('/customer/favorites', { params })
}

export function addFavorite(productId) {
  return http.post('/customer/favorites', { product_id: productId })
}

export function removeFavorite(productId) {
  return http.delete(`/customer/favorites/${encodeURIComponent(productId)}`)
}

/* ---------------- 购物车 ---------------- */

export function listCart() {
  return http.get('/customer/cart')
}

export function addCartItem(productId, quantity = 1) {
  return http.post('/customer/cart', { product_id: productId, quantity })
}

export function updateCartItemQuantity(productId, quantity) {
  return http.patch(`/customer/cart/${encodeURIComponent(productId)}`, { quantity })
}

export function removeCartItem(productId) {
  return http.delete(`/customer/cart/${encodeURIComponent(productId)}`)
}

/** 从购物车多选下单，生成含多个订单项的订单（TBD-14：下单必经购物车） */
export function checkoutCart(productIds) {
  return http.post('/customer/orders', { product_ids: productIds })
}

/** 购物车多选转收藏，后端负责移除原购物车项 */
export function convertCartToFavorites(productIds) {
  return http.post('/customer/cart/favorites', { product_ids: productIds })
}

/* ---------------- 订单 ---------------- */

export function listOrders(params) {
  return http.get('/customer/orders', { params })
}

export function getOrder(orderId) {
  return http.get(`/customer/orders/${encodeURIComponent(orderId)}`)
}

export function cancelOrder(orderId) {
  return http.post(`/customer/orders/${encodeURIComponent(orderId)}/cancel`)
}
