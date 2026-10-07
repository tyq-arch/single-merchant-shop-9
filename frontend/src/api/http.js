import axios from 'axios'

import { clearSellerAuth, getSellerAuth } from '@/utils/storage'

const LOGIN_PATH = '/seller/login'
const CHANGE_PASSWORD_PATH = '/seller/password'

const http = axios.create({
  baseURL: '/api',
  timeout: 10000,
})

/**
 * 判断 401 是否应作为业务反馈放行，而不是清场跳登录页。
 *
 * 后端把「登录密码错误」「旧密码错误」「令牌失效」统一实现为
 * Unauthorized → 401 + code `UNAUTHORIZED`（见 backend/app/errors.py），
 * 前端无法凭状态码或 code 区分，因此按路径 + 本地令牌状态判断：
 *
 *   - 登录接口：401 就是密码错误，属于正常业务反馈；
 *   - 改密接口：仅当本地令牌仍有效时才视为「旧密码不正确」，
 *     否则（本地令牌缺失或已过期）仍按登录失效处理，清场跳转；
 *   - 其余接口：一律按登录失效处理。
 *
 * 注意：后端若为「旧密码不正确」单独定义错误码，这里可以简化。
 */
function shouldPassThroughAuthError(url) {
  if (url === LOGIN_PATH) return true

  if (url === CHANGE_PASSWORD_PATH) {
    const auth = getSellerAuth()
    const locallyValid =
      Boolean(auth?.token) && (!auth.expiresAt || auth.expiresAt * 1000 > Date.now())
    return locallyValid
  }

  return false
}

function makeError(message, status, code) {
  const error = new Error(message)
  error.status = status
  error.code = code
  return error
}

http.interceptors.request.use((config) => {
  const auth = getSellerAuth()
  if (auth?.token) {
    config.headers.Authorization = `Bearer ${auth.token}`
  }
  return config
})

http.interceptors.response.use(
  // 成功响应直接解包，业务层不再写 .data
  (response) => response.data,
  (error) => {
    // 网络不可达 / 超时
    if (!error.response) {
      return Promise.reject(
        makeError('无法连接服务器，请确认后端已启动（127.0.0.1:8000）', 0, 'NETWORK_ERROR'),
      )
    }

    const { status, data } = error.response
    const code = data?.error?.code
    // 后端统一错误结构 {"error": {"code", "message"}}，见接口设计文档第 1 节
    const message = data?.error?.message || `请求失败（HTTP ${status}）`

    if (status === 401 && !shouldPassThroughAuthError(error.config?.url)) {
      clearSellerAuth()
      if (window.location.pathname !== LOGIN_PATH) {
        window.location.replace(LOGIN_PATH)
      }
      return Promise.reject(makeError('登录已过期，请重新登录', status, code))
    }

    return Promise.reject(makeError(message, status, code))
  },
)

export default http
