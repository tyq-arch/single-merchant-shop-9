/** 金额：后端返回数值型元，统一补 ¥ 与 2 位小数 */
export function formatPrice(value) {
  const num = Number(value)
  if (!Number.isFinite(num)) return '—'
  return `¥ ${num.toFixed(2)}`
}

/** 口令码：去空格并转大写（后端生成为去混淆大写字符集） */
export function normalizeCode(value) {
  return String(value || '')
    .replace(/\s+/g, '')
    .toUpperCase()
}

/** 触发浏览器下载，用于意向购买人 CSV 导出 */
export function downloadBlob(blob, filename) {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
}
