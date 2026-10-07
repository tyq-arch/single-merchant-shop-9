import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

import { clearSellerAuth, getSellerAuth, setSellerAuth } from '@/utils/storage'

/**
 * 卖家登录态。
 *
 * 需求 FR-034 明确后台无退出登录功能，因此不提供 logout 动作供界面调用；
 * clear() 仅用于令牌失效（401）或本地过期时清场。
 */
export const useSellerStore = defineStore('seller', () => {
  const auth = ref(getSellerAuth())

  const token = computed(() => auth.value?.token || '')

  const username = computed(() => auth.value?.username || '')

  /** expires_at 为后端下发的秒级时间戳；早于当前时间即视为过期 */
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
      username: payload.seller?.username || '',
    }
    setSellerAuth(auth.value)
  }

  function clear() {
    auth.value = null
    clearSellerAuth()
  }

  return { auth, token, username, isExpired, isLoggedIn, save, clear }
})
