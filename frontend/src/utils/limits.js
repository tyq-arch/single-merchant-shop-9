/**
 * 预选数量上限（TBD-11）—— 与后端 PreselectService 保持一致：
 *   - 购物车最多 50 种商品
 *   - 收藏最多 200 条
 *   - 单个商品数量 1–99
 *
 * 前端只做提前拦截与输入截断，最终以上限校验以后端为准。
 */
export const CART_MAX_ITEMS = 50
export const FAVORITES_MAX_ITEMS = 200
export const ITEM_MIN_QUANTITY = 1
export const ITEM_MAX_QUANTITY = 99

/**
 * 分页大小上限 —— 必须 ≤ 后端 AppConfig.maxPageSize（默认 100），
 * 否则分页接口会返回 422「page_size 必须在 1 到 100 之间」。
 * 收藏最多 200 条，取全部时按此页长循环翻页。
 */
export const FAVORITES_PAGE_SIZE = 100

/** 把任意输入截断成 1–99 的整数 */
export function clampQuantity(value) {
  const num = Math.trunc(Number(value))
  if (!Number.isFinite(num)) return ITEM_MIN_QUANTITY
  return Math.min(ITEM_MAX_QUANTITY, Math.max(ITEM_MIN_QUANTITY, num))
}
