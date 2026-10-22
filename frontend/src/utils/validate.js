/**
 * 手机号校验 —— 中国大陆 11 位手机号。
 *
 * 注册页、个人资料等所有需要手机号的位置统一使用本规则，
 * 保证前端只有一份实现（对应需求 FR-017）。
 */
export const PHONE_RE = /^1[3-9]\d{9}$/
export const PHONE_LENGTH = 11

export function validatePhone(value) {
  const phone = String(value ?? '').trim()
  if (!phone) return '请输入联系电话'
  if (!/^\d+$/.test(phone)) return '联系电话只能输入数字'
  if (phone.length !== PHONE_LENGTH) {
    return `请输入 ${PHONE_LENGTH} 位手机号（当前 ${phone.length} 位）`
  }
  if (!PHONE_RE.test(phone)) return '手机号格式不正确，请检查后重试'
  return ''
}

/** 客户密码：后端要求 6–128 位 */
export function validateCustomerPassword(value) {
  const password = String(value ?? '')
  if (!password) return '请输入密码'
  if (password.length < 6) return '密码长度不能少于 6 位'
  if (password.length > 128) return '密码长度不能超过 128 位'
  return ''
}

/** 卖家密码：FR-037 要求至少 8 位 */
export function validateSellerPassword(value) {
  const password = String(value ?? '')
  if (!password) return '请输入新密码'
  if (password.length < 8) return '（卖家）新密码长度不能少于 8 位'
  if (password.length > 128) return '密码长度不能超过 128 位'
  return ''
}
