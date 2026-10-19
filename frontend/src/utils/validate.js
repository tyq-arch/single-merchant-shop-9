/**
 * 表单校验规则。
 *
 * 提交意向（IntentFormView）和修改联系方式（IntentDetailView）都要校验手机号，
 * 规则只写一份，避免两处提示文案或正则不一致。
 */

/** 中国大陆手机号：11 位数字，1 开头，第二位 3-9 */
export const PHONE_RE = /^1[3-9]\d{9}$/

/** 手机号固定 11 位 */
export const PHONE_LENGTH = 11

/**
 * 校验手机号。
 *
 * @param {string} value 输入框原值
 * @returns {string} 通过返回空字符串，否则返回可直接展示的中文提示
 */
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
