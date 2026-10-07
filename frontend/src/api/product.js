import http from './http'

/** 当前商品：state 为 NONE / ON_SALE / FROZEN，message 为后端下发的中文提示 */
export function getCurrentProduct() {
  return http.get('/products/current')
}

export function getProductDetail(productId) {
  return http.get(`/products/${encodeURIComponent(productId)}`)
}
