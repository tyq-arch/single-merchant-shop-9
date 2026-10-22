import axios from 'axios'

import {
  clearCustomerAuth,
  clearSellerAuth,
  getCustomerAuth,
  getSellerAuth,
} from '@/utils/storage'

const CUSTOMER_LOGIN_PATH = '/customer/login'
const CUSTOMER_REGISTER_PATH = '/customer/register'
const CUSTOMER_PASSWORD_PATH = '/customer/password'
const SELLER_LOGIN_PATH = '/seller/login'
const SELLER_PASSWORD_PATH = '/seller/password'

const http = axios.create({
  baseURL: '/api',
  timeout: 30000,
})

/**
 * 判断某个请求应该携带哪一类令牌。
 *
 * 约定：
 *   - /customer/**  → 客户令牌
 *   - /seller/**    → 卖家令牌
 *   - /files/**     → 卖家令牌（上传目前仅卖家使用）
 *   - 其余（公开商品、类别）→ 不携带
 *
 * 也可在单个请求里用 `authRole: 'customer' | 'seller' | 'none'` 显式覆盖。
 * 注意不要命名为 `auth`：axios 原生 `auth` 是 HTTP Basic 认证配置。
 */
function resolveAuthRole(config) {
  if (config.authRole) return config.authRole
  const url = config.url || ''
  if (url.startsWith('/customer')) return 'customer'
  if (url.startsWith('/seller')) return 'seller'
  if (url.startsWith('/files')) return 'seller'
  return 'none'
}

function locallyValid(auth) {
  return Boolean(auth?.token) && (!auth.expiresAt || auth.expiresAt * 1000 > Date.now())
}

/**
 * 401 是否属于正常业务反馈（而不是登录失效）。
 *
 * 后端把「用户名或密码错误」「旧密码错误」「令牌失效」都实现为
 * 401 + code UNAUTHORIZED，前端按接口路径 + 本地令牌状态区分。
 */
function shouldPassThroughAuthError(role, url) {
  if (role === 'customer') {
    if (url === CUSTOMER_LOGIN_PATH || url === CUSTOMER_REGISTER_PATH) return true
    if (url === CUSTOMER_PASSWORD_PATH) return locallyValid(getCustomerAuth())
    return false
  }

  if (role === 'seller') {
    if (url === SELLER_LOGIN_PATH) return true
    if (url === SELLER_PASSWORD_PATH) return locallyValid(getSellerAuth())
    return false
  }

  return true
}

function redirectToLogin(role) {
  const loginPath = role === 'customer' ? '/login' : '/seller/login'
  if (window.location.pathname === loginPath) return

  const current = `${window.location.pathname}${window.location.search}`
  const target =
    role === 'customer'
      ? `${loginPath}?redirect=${encodeURIComponent(current)}`
      : loginPath
  window.location.replace(target)
}

function makeError(message, status, code) {
  const error = new Error(message)
  error.status = status
  error.code = code
  return error
}

http.interceptors.request.use((config) => {
  const role = resolveAuthRole(config)
  config.__authRole = role

  if (role === 'customer') {
    const auth = getCustomerAuth()
    if (auth?.token) config.headers.Authorization = `Bearer ${auth.token}`
  } else if (role === 'seller') {
    const auth = getSellerAuth()
    if (auth?.token) config.headers.Authorization = `Bearer ${auth.token}`
  }

  return config
})

http.interceptors.response.use(
  // 成功响应直接解包，业务层不再写 .data
  (response) => response.data,
  (error) => {
    if (!error.response) {
      return Promise.reject(
        makeError('无法连接服务器，请确认后端已启动（127.0.0.1:8000）', 0, 'NETWORK_ERROR'),
      )
    }

    const { status, data } = error.response
    const code = data?.error?.code
    const message = data?.error?.message || `请求失败（HTTP ${status}）`
    const role = error.config?.__authRole || 'none'
    const url = error.config?.url || ''

    if (status === 401 && role !== 'none' && !shouldPassThroughAuthError(role, url)) {
      if (role === 'customer') {
        clearCustomerAuth()
        redirectToLogin('customer')
        return Promise.reject(makeError('登录已过期，请重新登录', status, code))
      }

      clearSellerAuth()
      redirectToLogin('seller')
      return Promise.reject(makeError('登录已过期，请重新登录', status, code))
    }

    return Promise.reject(makeError(message, status, code))
  },
)

export default http
