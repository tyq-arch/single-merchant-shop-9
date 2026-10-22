/** 金额：后端返回数值型元，统一补 ¥ 与 2 位小数 */
export function formatPrice(value) {
  const num = Number(value)
  if (!Number.isFinite(num)) return '—'
  return `¥ ${num.toFixed(2)}`
}

/** 触发浏览器下载，用于队列名单 CSV 导出 */
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

/** 库存展示：0 显示「售罄」 */
export function formatStock(stock) {
  const num = Number(stock)
  if (!Number.isFinite(num)) return '—'
  return num > 0 ? `${num} 件` : '售罄'
}
