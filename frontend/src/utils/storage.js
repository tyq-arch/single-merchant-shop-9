/**
 * 本地存储访问。
 *
 * - 卖家令牌存 localStorage：刷新后台不丢登录态（无退出登录功能，令牌自然过期）
 * - 买家口令码存 sessionStorage：等价于买家唯一凭证，关闭标签页即清除，
 *   且不写入 URL，避免残留在浏览器历史记录中（见技术栈报告 3.4 节）
 *
 * 本模块不依赖任何其他模块，供 http.js 与 stores 共用，避免循环引用。
 */

const SELLER_KEY = 'shop_seller_auth'
const BUYER_CODE_KEY = 'shop_buyer_command_code'

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

/* ---------- 卖家 ---------- */

export function getSellerAuth() {
  const raw = safeGet(localStorage, SELLER_KEY)
  if (!raw) return null
  try {
    const parsed = JSON.parse(raw)
    return parsed && parsed.token ? parsed : null
  } catch {
    return null
  }
}

export function setSellerAuth(auth) {
  safeSet(localStorage, SELLER_KEY, JSON.stringify(auth))
}

export function clearSellerAuth() {
  safeRemove(localStorage, SELLER_KEY)
}

/* ---------- 买家 ---------- */

export function getBuyerCode() {
  return safeGet(sessionStorage, BUYER_CODE_KEY) || ''
}

export function setBuyerCode(code) {
  safeSet(sessionStorage, BUYER_CODE_KEY, code)
}

export function clearBuyerCode() {
  safeRemove(sessionStorage, BUYER_CODE_KEY)
}
