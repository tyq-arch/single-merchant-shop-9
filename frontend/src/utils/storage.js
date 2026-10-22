/**
 * 本地存储访问。
 *
 * - 卖家令牌存 localStorage：后台无退出登录，刷新不丢登录态，令牌自然过期。
 * - 客户令牌存 localStorage：客户可主动登出，12 小时内免登录。
 *
 * 本模块不依赖任何其他模块，供 http.js 与 stores 共用，避免循环引用。
 */

const SELLER_KEY = 'shop_seller_auth'
const CUSTOMER_KEY = 'shop_customer_auth'

function safeGet(storage, key) {
  try {
    return storage.getItem(key)
  } catch {
    return null
  }
}

function safeSet(storage, key, value) {
  try {
    storage.setItem(key, value)
  } catch {
    /* 隐私模式下可能不可用，静默降级为内存态 */
  }
}

function safeRemove(storage, key) {
  try {
    storage.removeItem(key)
  } catch {
    /* 同上 */
  }
}

function readAuth(storage, key) {
  const raw = safeGet(storage, key)
  if (!raw) return null
  try {
    const parsed = JSON.parse(raw)
    return parsed && parsed.token ? parsed : null
  } catch {
    return null
  }
}

/* ---------- 卖家 ---------- */

export function getSellerAuth() {
  return readAuth(localStorage, SELLER_KEY)
}

export function setSellerAuth(auth) {
  safeSet(localStorage, SELLER_KEY, JSON.stringify(auth))
}

export function clearSellerAuth() {
  safeRemove(localStorage, SELLER_KEY)
}

/* ---------- 客户 ---------- */

export function getCustomerAuth() {
  return readAuth(localStorage, CUSTOMER_KEY)
}

export function setCustomerAuth(auth) {
  safeSet(localStorage, CUSTOMER_KEY, JSON.stringify(auth))
}

export function clearCustomerAuth() {
  safeRemove(localStorage, CUSTOMER_KEY)
}
