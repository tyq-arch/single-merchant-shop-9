/**
 * 游客临时收藏 / 临时购物车（挑战 B，TBD-12）。
 *
 * 游客未登录也能收藏、加购，数据只落 localStorage；登录后由
 * stores/shop.js 的 mergeGuestData() 合并进账号并清空本地临时数据。
 *
 * 上限与后端保持一致（TBD-11）：
 *   - 购物车最多 50 种商品
 *   - 收藏最多 200 条
 *   - 单个商品数量 1–99
 */
const CART_KEY = 'shop_guest_cart'
const FAVORITES_KEY = 'shop_guest_favorites'

export const CART_MAX_ITEMS = 50
export const FAVORITES_MAX_ITEMS = 200
export const ITEM_MIN_QUANTITY = 1
export const ITEM_MAX_QUANTITY = 99

function safeGet(key) {
  try {
    return localStorage.getItem(key)
  } catch {
    return null
  }
}

function safeSet(key, value) {
  try {
    localStorage.setItem(key, value)
  } catch {
    /* 隐私模式下静默降级 */
  }
}

function readList(key) {
  const raw = safeGet(key)
  if (!raw) return []
  try {
    const parsed = JSON.parse(raw)
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
}

function writeList(key, items) {
  safeSet(key, JSON.stringify(items))
}

function sameId(left, right) {
  return String(left) === String(right)
}

export function clampQuantity(value) {
  const num = Math.trunc(Number(value))
  if (!Number.isFinite(num)) return ITEM_MIN_QUANTITY
  return Math.min(ITEM_MAX_QUANTITY, Math.max(ITEM_MIN_QUANTITY, num))
}

/** 把商品对象压成购物车/收藏需要的最小快照 */
export function toProductSnapshot(product) {
  return {
    product_id: product.id,
    name: product.name,
    price: Number(product.price) || 0,
    primary_image: product.primary_image || '',
    stock: Number(product.stock) || 0,
    status: product.status || '',
    status_label: product.status_label || '',
  }
}

/* ---------------- 临时购物车 ---------------- */

export function readGuestCart() {
  return readList(CART_KEY)
}

export function writeGuestCart(items) {
  writeList(CART_KEY, items)
}

/**
 * 加购。同一商品只保留一条，数量累加并截断到 99。
 * 超过 50 种商品时抛出可展示的错误。
 */
export function addGuestCartItem(product, quantity = 1) {
  const items = readGuestCart()
  const index = items.findIndex((item) => sameId(item.product_id, product.id))
  const addQuantity = clampQuantity(quantity)

  if (index >= 0) {
    const next = {
      ...items[index],
      ...toProductSnapshot(product),
      quantity: clampQuantity(Number(items[index].quantity) + addQuantity),
    }
    items[index] = next
  } else {
    if (items.length >= CART_MAX_ITEMS) {
      throw new Error(`购物车最多放 ${CART_MAX_ITEMS} 种商品，请先清理`)
    }
    items.push({ ...toProductSnapshot(product), quantity: addQuantity })
  }

  writeGuestCart(items)
  return items
}

export function updateGuestCartQuantity(productId, quantity) {
  const items = readGuestCart()
  const index = items.findIndex((item) => sameId(item.product_id, productId))
  if (index < 0) return items
  items[index] = { ...items[index], quantity: clampQuantity(quantity) }
  writeGuestCart(items)
  return items
}

export function removeGuestCartItems(productIds) {
  const idSet = new Set(productIds.map(String))
  const items = readGuestCart().filter((item) => !idSet.has(String(item.product_id)))
  writeGuestCart(items)
  return items
}

export function clearGuestCart() {
  writeGuestCart([])
}

/* ---------------- 临时收藏 ---------------- */

export function readGuestFavorites() {
  return readList(FAVORITES_KEY)
}

export function writeGuestFavorites(items) {
  writeList(FAVORITES_KEY, items)
}

export function isGuestFavorite(productId) {
  return readGuestFavorites().some((item) => sameId(item.product_id, productId))
}

/** 收藏。已收藏则幂等返回；超过 200 条时抛出可展示的错误。 */
export function addGuestFavorite(product) {
  const items = readGuestFavorites()
  if (items.some((item) => sameId(item.product_id, product.id))) return items
  if (items.length >= FAVORITES_MAX_ITEMS) {
    throw new Error(`收藏最多 ${FAVORITES_MAX_ITEMS} 条，请先清理`)
  }
  items.push(toProductSnapshot(product))
  writeGuestFavorites(items)
  return items
}

export function removeGuestFavorite(productId) {
  const items = readGuestFavorites().filter(
    (item) => !sameId(item.product_id, productId),
  )
  writeGuestFavorites(items)
  return items
}

export function clearGuestFavorites() {
  writeGuestFavorites([])
}
