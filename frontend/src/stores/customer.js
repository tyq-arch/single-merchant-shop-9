import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

import { clearCustomerAuth, getCustomerAuth, setCustomerAuth } from '@/utils/storage'

/**
 * 客户登录态。
 *
 * 令牌自带 12 小时有效期（后端 expires_at 为秒级时间戳），
 * 过期后由 http 拦截器清场并引导重新登录。
 */
export const useCustomerStore = defineStore('customer', () => {
  const auth = ref(getCustomerAuth())

  const token = computed(() => auth.value?.token || '')

  const customer = computed(() => auth.value?.customer || null)

  const username = computed(() => customer.value?.username || '')

  const isExpired = computed(() => {
    const expiresAt = auth.value?.expiresAt
    if (!expiresAt) return false
    return expiresAt * 1000 <= Date.now()
  })

  const isLoggedIn = computed(() => Boolean(token.value) && !isExpired.value)

  function save(payload) {
    auth.value = {
      token: payload.token,
      expiresAt: payload.expires_at,
      customer: payload.customer || null,
    }
    setCustomerAuth(auth.value)
  }

  function updateCustomer(next) {
    if (!auth.value) return
    auth.value = { ...auth.value, customer: next }
    setCustomerAuth(auth.value)
  }

  function clear() {
    auth.value = null
    clearCustomerAuth()
  }

  return {
    auth,
    token,
    customer,
    username,
    isExpired,
    isLoggedIn,
    save,
    updateCustomer,
    clear,
  }
})
