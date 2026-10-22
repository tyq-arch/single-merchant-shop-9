import http from './http'

/** 商品列表 / 搜索（仅返回在售与交易中商品，游客可访问） */
export function listProducts(params) {
  return http.get('/products', { params })
}

/** 商品详情，返回 { product, queue_count, can_buy } */
export function getProduct(productId) {
  return http.get(`/products/${encodeURIComponent(productId)}`)
}

/** 二级类别树 */
export function getCategories() {
  return http.get('/categories')
}
