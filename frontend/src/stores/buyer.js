import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

import { clearBuyerCode, getBuyerCode, setBuyerCode } from '@/utils/storage'

/**
 * 买家口令码与最近一次提交结果。
 *
 * 口令码不写入 URL，仅存 sessionStorage，避免残留在浏览器历史记录中被他人取走
 * （见 docs/04-前端技术栈调研报告.md 第 3.4 节）。
 */
export const useBuyerStore = defineStore('buyer', () => {
  const code = ref(getBuyerCode())

  /** 提交成功页所需的即时数据；刷新后丢失时可凭口令码重新查询补齐 */
  const lastSubmit = ref(null)

  const hasCode = computed(() => Boolean(code.value))

  function saveCode(value) {
    code.value = value || ''
    if (code.value) {
      setBuyerCode(code.value)
    } else {
      clearBuyerCode()
    }
  }

  function saveSubmit(payload) {
    lastSubmit.value = payload
    saveCode(payload?.command_code || '')
  }

  /** 主动清除：口令码页的「退出查询」入口调用 */
  function reset() {
    code.value = ''
    lastSubmit.value = null
    clearBuyerCode()
  }

  return { code, lastSubmit, hasCode, saveCode, saveSubmit, reset }
})
