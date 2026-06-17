import axios from 'axios'

const api = axios.create({
  baseURL: import.meta.env.VITE_API_URL ?? '/api',
  timeout: 10000,
  headers: { 'Content-Type': 'application/json' }
})

api.interceptors.response.use(
  res => res,
  err => {
    console.error('[api]', err.config?.url, err.response?.status, err.message)
    return Promise.reject(err)
  }
)

export default api
