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

/** 密码规则（NFR-003 / C-29）：≥8 位，且大写/小写/数字/特殊字符至少 3 类 */
export const PASSWORD_MIN = 8
export const PASSWORD_MAX = 128
export const PASSWORD_RULE_TEXT = '至少 8 位，且包含大写字母、小写字母、数字、特殊字符中的至少 3 类'

/** 统计命中的字符类别数：大写 / 小写 / 数字 / 其他符号，与后端 Validation.strongPassword 一致 */
export function passwordClassCount(value) {
  let upper = false
  let lower = false
  let digit = false
  let symbol = false
  for (const char of String(value ?? '')) {
    if (char >= 'A' && char <= 'Z') upper = true
    else if (char >= 'a' && char <= 'z') lower = true
    else if (char >= '0' && char <= '9') digit = true
    else symbol = true
  }
  return [upper, lower, digit, symbol].filter(Boolean).length
}

/** 客户密码：8–128 位且至少 3 类字符 */
export function validateCustomerPassword(value) {
  const password = String(value ?? '')
  if (!password) return '请输入密码'
  if (password.length < PASSWORD_MIN) {
    return `密码长度不能少于 ${PASSWORD_MIN} 位（当前 ${password.length} 位）`
  }
  if (password.length > PASSWORD_MAX) return `密码长度不能超过 ${PASSWORD_MAX} 位`
  const classes = passwordClassCount(password)
  if (classes < 3) {
    return `密码强度不足：需包含大写字母、小写字母、数字、特殊字符中的至少 3 类（当前 ${classes} 类）`
  }
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
