import http from './http'

/**
 * 游客临时预选接口（TBD-12 / FR-022）。
 *
 * 未登录也能收藏、加购、多选转收藏，数据存在后端的匿名会话里，
 * 以 X-Guest-Id 标识（http 拦截器自动注入，见 api/http.js）。
 * 登录时把同一个 X-Guest-Id 交给 /customer/login，由后端合并进账号，
 * 前端不再逐条补录。
 *
 * 下单必须登录：POST /api/guest/orders 固定返回 401（TBD-14）。
 *
 *   GET    /guest/favorites                          → { items }
 *   POST   /guest/favorites { product_id }           → { message }
 *   DELETE /guest/favorites/{product_id}             → { message }
 *   GET    /guest/cart                               → { items, item_count, total_quantity }
 *   POST   /guest/cart { product_id, quantity }      → { message, items... }
 *   PATCH  /guest/cart/{product_id} { quantity }     → { message, items... }
 *   DELETE /guest/cart/{product_id}                  → { message, items... }
 *   POST   /guest/cart/favorites { product_ids[] }   → { message, converted_count }
 */

/* ---------------- 收藏 ---------------- */

export function listFavorites(params) {
  return http.get('/guest/favorites', { params })
}

export function addFavorite(productId) {
  return http.post('/guest/favorites', { product_id: productId })
}

export function removeFavorite(productId) {
  return http.delete(`/guest/favorites/${encodeURIComponent(productId)}`)
}

/* ---------------- 购物车 ---------------- */

export function listCart() {
  return http.get('/guest/cart')
}

export function addCartItem(productId, quantity = 1) {
  return http.post('/guest/cart', { product_id: productId, quantity })
}

export function updateCartItemQuantity(productId, quantity) {
  return http.patch(`/guest/cart/${encodeURIComponent(productId)}`, { quantity })
}

export function removeCartItem(productId) {
  return http.delete(`/guest/cart/${encodeURIComponent(productId)}`)
}

/** 游客多选转收藏，后端负责把原购物车项移出 */
export function convertCartToFavorites(productIds) {
  return http.post('/guest/cart/favorites', { product_ids: productIds })
}
