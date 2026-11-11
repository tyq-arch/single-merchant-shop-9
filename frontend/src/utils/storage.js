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
const GUEST_KEY = 'shop_guest_id'

/** 与后端 Validation.guestId 保持一致：[A-Za-z0-9_-]{8,64} */
const GUEST_ID_RE = /^[A-Za-z0-9_-]{8,64}$/

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

/* ---------- 游客匿名会话（X-Guest-Id） ---------- */

function createGuestId() {
  const webCrypto = globalThis.crypto
  if (webCrypto?.randomUUID) return webCrypto.randomUUID()
  return `g${Date.now().toString(36)}${Math.random().toString(36).slice(2, 12)}`
}

/**
 * 游客会话标识：未登录时收藏/加购都挂在这个 id 下，
 * 登录时随请求带给后端，由后端把临时数据并入账号（TBD-12）。
 */
export function getGuestId() {
  const existing = safeGet(localStorage, GUEST_KEY)
  if (existing && GUEST_ID_RE.test(existing)) return existing
  const created = createGuestId()
  safeSet(localStorage, GUEST_KEY, created)
  return created
}

export function clearGuestId() {
  safeRemove(localStorage, GUEST_KEY)
}
