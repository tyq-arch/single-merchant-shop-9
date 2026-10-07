import http from './http'

/**
 * 提交购买意向，返回 { order, command_code, queue_position, notice }。
 * 成功即生成口令码，需在成功页强提示买家保存（FR-014 / FR-048）。
 */
export function submitIntent(payload) {
  return http.post('/buyer/intents', payload)
}

/** 凭口令码查询，返回 { order, position, can_edit, can_cancel, product_status } */
export function queryIntent(code) {
  return http.get(`/buyer/intents/${encodeURIComponent(code)}`)
}

/** 修改姓名/电话，姓名与电话至少提供一项，位次不变（FR-023） */
export function updateIntent(payload) {
  return http.patch('/buyer/intents', payload)
}

/** 撤销排队意向（仅排队中可用，FR-024） */
export function cancelIntent(code) {
  return http.post('/buyer/intents/cancel', { code })
}
