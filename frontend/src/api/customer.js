import http from './http'

/**
 * 客户接口 —— 挑战 C。
 *
 * 浏览、搜索、收藏、加购无需登录；下单必须登录。
 * 注册、登录本身不携带令牌，其余接口由 http 拦截器自动注入客户令牌。
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
 * 收货地址（FR-046 ~ FR-048）
 *   GET    /customer/addresses                    → { items }
 *   POST   /customer/addresses                    → { message, address }
 *   PATCH  /customer/addresses/{address_id}       → { message, address }
 *   DELETE /customer/addresses/{address_id}       → { message }
 *   POST   /customer/addresses/{address_id}/default → { message }
 *
 * 订单（下单必经购物车；下单时必须选择已有收货地址，TBD-07）
 *   POST   /customer/orders { product_ids[], address_id } → { message, order }
 *   GET    /customer/orders?page=&page_size=&status=      → { items, total }
 *   GET    /customer/orders/{order_id}                    → { order }
 *   POST   /customer/orders/{order_id}/cancel             → { message, order }
 *
 * 支付（FR-039 ~ FR-042，本期为模拟/预留接口，TBD-03）
 *   POST   /customer/orders/{order_id}/pay { method } → { message, order }
 *   method: BANK_CARD | CREDIT_CARD | ALIPAY | WECHAT
 *
 * 售后（FR-051 ~ FR-055，按订单项发起，申请单无「类型」字段）
 *   GET    /customer/aftersales?page=&page_size=&status= → { items, total }
 *   GET    /customer/aftersales/{aftersale_id}           → { aftersale }
 *   POST   /customer/orders/{order_id}/items/{item_id}/aftersales
 *          { title, description, images[] }              → { message, aftersale }
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

/** 从购物车多选下单，生成含多个订单项的订单（必须带收货地址） */
export function checkoutCart(productIds, addressId) {
  return http.post('/customer/orders', {
    product_ids: productIds,
    address_id: addressId,
  })
}

/** 购物车多选转收藏，后端负责移除原购物车项 */
export function convertCartToFavorites(productIds) {
  return http.post('/customer/cart/favorites', { product_ids: productIds })
}

/* ---------------- 收货地址 ---------------- */

export function listAddresses() {
  return http.get('/customer/addresses')
}

export function createAddress(payload) {
  return http.post('/customer/addresses', payload)
}

export function updateAddress(addressId, payload) {
  return http.patch(
    `/customer/addresses/${encodeURIComponent(addressId)}`,
    payload,
  )
}

export function removeAddress(addressId) {
  return http.delete(`/customer/addresses/${encodeURIComponent(addressId)}`)
}

export function setDefaultAddress(addressId) {
  return http.post(
    `/customer/addresses/${encodeURIComponent(addressId)}/default`,
  )
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

/** 线上支付（模拟）：method 为 BANK_CARD / CREDIT_CARD / ALIPAY / WECHAT */
export function payOrder(orderId, method) {
  return http.post(`/customer/orders/${encodeURIComponent(orderId)}/pay`, {
    method,
  })
}

/* ---------------- 售后 ---------------- */

export function listAfterSales(params) {
  return http.get('/customer/aftersales', { params })
}

export function getAfterSale(afterSaleId) {
  return http.get(`/customer/aftersales/${encodeURIComponent(afterSaleId)}`)
}

/** 对订单内某件商品发起售后：itemId 为订单项 ID */
export function createAfterSale(orderId, itemId, payload) {
  return http.post(
    `/customer/orders/${encodeURIComponent(orderId)}/items/${encodeURIComponent(itemId)}/aftersales`,
    payload,
  )
}
