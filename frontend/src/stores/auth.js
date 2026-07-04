import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import api from '@/services/api' // 既有 axios instance（baseURL = VITE_API_URL = gateway 8085）

const LS_KEY = 'twin.auth'

function load() {
  try {
    return JSON.parse(localStorage.getItem(LS_KEY)) || {}
  } catch {
    return {}
  }
}

export const useAuthStore = defineStore('auth', () => {
  const saved = load()
  const accessToken = ref(saved.accessToken || '')
  const refreshToken = ref(saved.refreshToken || '')
  const username = ref(saved.username || '')
  const roles = ref(saved.roles || [])

  const isAuthenticated = computed(() => !!accessToken.value)
  const hasRole = (r) => roles.value.includes(r)
  const hasAnyRole = (list) => list.some((r) => roles.value.includes(r))

  function persist() {
    localStorage.setItem(
      LS_KEY,
      JSON.stringify({
        accessToken: accessToken.value,
        refreshToken: refreshToken.value,
        username: username.value,
        roles: roles.value,
      })
    )
  }

  function setSession(data) {
    accessToken.value = data.accessToken
    refreshToken.value = data.refreshToken
    username.value = data.username
    roles.value = data.roles || []
    persist()
  }

  function clear() {
    accessToken.value = ''
    refreshToken.value = ''
    username.value = ''
    roles.value = []
    localStorage.removeItem(LS_KEY)
  }

  async function login(usernameOrEmail, password) {
    const { data } = await api.post('/auth/login', { usernameOrEmail, password })
    setSession(data)
    return data
  }

  async function register(payload) {
    // payload: { username, email, password, phone }
    const { data } = await api.post('/auth/register', payload)
    setSession(data)
    return data
  }

  async function refresh() {
    if (!refreshToken.value) throw new Error('no refresh token')
    const { data } = await api.post('/auth/refresh', { refreshToken: refreshToken.value })
    setSession(data)
    return data
  }

  function logout() {
    clear()
  }

  return {
    accessToken,
    refreshToken,
    username,
    roles,
    isAuthenticated,
    hasRole,
    hasAnyRole,
    login,
    register,
    refresh,
    logout,
    setSession,
  }
})
