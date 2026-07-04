import axios from 'axios'
import { useAuthStore } from '@/stores/auth'
import router from '@/router'

const api = axios.create({
  baseURL: import.meta.env.VITE_API_URL ?? '/api',
  timeout: 120000,
  headers: { 'Content-Type': 'application/json' }
})

// ── 請求：自動掛 Bearer ──────────────────────────────────────
api.interceptors.request.use((config) => {
  const auth = useAuthStore()
  if (auth.accessToken) {
    config.headers = config.headers || {}
    config.headers.Authorization = `Bearer ${auth.accessToken}`
  }
  return config
})

// ── 回應：保留原本 log，加上 401 自動 refresh 重試 ───────────
let refreshing = null
api.interceptors.response.use(
  res => res,
  async err => {
    const original = err.config
    const status = err.response?.status
    console.error('[api]', original?.url, status, err.message)

    const auth = useAuthStore()
    const isAuthCall = original?.url?.includes('/auth/') // 避免 refresh 自己 401 無限迴圈

    if (status === 401 && !original?._retry && auth.refreshToken && !isAuthCall) {
      original._retry = true
      try {
        refreshing = refreshing || auth.refresh()
        await refreshing
        refreshing = null
        original.headers = original.headers || {}
        original.headers.Authorization = `Bearer ${auth.accessToken}`
        return api(original)
      } catch (e) {
        refreshing = null
        auth.logout()
        router.push({ name: 'login', query: { redirect: router.currentRoute.value.fullPath } })
        return Promise.reject(e)
      }
    }
    return Promise.reject(err)
  }
)

export default api
