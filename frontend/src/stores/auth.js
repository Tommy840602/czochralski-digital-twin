import { defineStore } from 'pinia'
import api from '@/services/api'

function parseJwt(token) {
  try {
    const base64Url = token.split('.')[1]
    if (!base64Url) return {}

    const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/')
    const json = decodeURIComponent(
      atob(base64)
        .split('')
        .map(c => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2))
        .join('')
    )

    return JSON.parse(json)
  } catch {
    return {}
  }
}

function readJsonArray(key) {
  try {
    const value = localStorage.getItem(key)
    const parsed = value ? JSON.parse(value) : []
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
}

export const useAuthStore = defineStore('auth', {
  state: () => ({
    accessToken: localStorage.getItem('accessToken') || '',
    refreshToken: localStorage.getItem('refreshToken') || '',
    username: localStorage.getItem('username') || '',
    roles: readJsonArray('roles'),
    perms: readJsonArray('perms')
  }),

  getters: {
    isAuthenticated: (s) => !!s.accessToken,

    hasRole: (s) => (role) => {
      return s.roles.includes(role) || s.roles.includes(`ROLE_${role}`)
    },

    isAdmin: (s) => {
      return s.roles.includes('ADMIN') || s.roles.includes('ROLE_ADMIN')
    },

    isEngineer: (s) => {
      return (
        s.roles.includes('ENGINEER') ||
        s.roles.includes('ROLE_ENGINEER') ||
        s.roles.includes('ADMIN') ||
        s.roles.includes('ROLE_ADMIN')
      )
    },

    hasPermission: (s) => (perm) => {
      if (!perm) return true

      const isAdmin =
        s.roles.includes('ADMIN') ||
        s.roles.includes('ROLE_ADMIN')

      if (isAdmin) return true

      return Array.isArray(s.perms) && s.perms.includes(perm)
    },

    hasAllPermissions: (s) => (requiredPerms) => {
      if (!Array.isArray(requiredPerms) || requiredPerms.length === 0) {
        return true
      }

      const isAdmin =
        s.roles.includes('ADMIN') ||
        s.roles.includes('ROLE_ADMIN')

      if (isAdmin) return true

      return requiredPerms.every(perm => s.perms.includes(perm))
    },

    hasAnyPermission: (s) => (requiredPerms) => {
      if (!Array.isArray(requiredPerms) || requiredPerms.length === 0) {
        return true
      }

      const isAdmin =
        s.roles.includes('ADMIN') ||
        s.roles.includes('ROLE_ADMIN')

      if (isAdmin) return true

      return requiredPerms.some(perm => s.perms.includes(perm))
    }
  },

  actions: {
    setSession(accessToken, refreshToken, username, roles = null, perms = null) {
      this.accessToken = accessToken || ''
      this.refreshToken = refreshToken || ''
      this.username = username || ''

      const claims = accessToken ? parseJwt(accessToken) : {}

      this.roles = Array.isArray(roles)
        ? roles
        : Array.isArray(claims.roles)
          ? claims.roles
          : []

      this.perms = Array.isArray(perms)
        ? perms
        : Array.isArray(claims.perms)
          ? claims.perms
          : []

      localStorage.setItem('accessToken', this.accessToken)
      localStorage.setItem('refreshToken', this.refreshToken)
      localStorage.setItem('username', this.username)
      localStorage.setItem('roles', JSON.stringify(this.roles))
      localStorage.setItem('perms', JSON.stringify(this.perms))
    },

    async login(usernameOrEmail, password) {
      const { data } = await api.post('/auth/login', {
        usernameOrEmail,
        password
      })

      this.setSession(
        data.accessToken,
        data.refreshToken,
        data.username,
        data.roles,
        data.perms
      )

      return data
    },

    /**
     * 註冊。
     *
     * ⚠ 這個 action 原本「不存在」。RegisterView.vue 直接呼叫 auth.register({...})，
     *   得到 undefined is not a function，被 catch 起來後顯示成「註冊失敗」——
     *   所以透過 UI 註冊從來沒有成功過，而且錯誤訊息完全誤導。
     *
     *   後端 /auth/register 本身是好的（用 curl 打會成功），問題純粹在前端少了這一段。
     */
    async register({ username, email, password, phone, smsCode }) {
      const { data } = await api.post('/auth/register', {
        username,
        email,
        password,
        phone,
        smsCode
      })

      this.setSession(
        data.accessToken,
        data.refreshToken,
        data.username,
        data.roles,
        data.perms
      )

      return data
    },

    async refresh() {
      const { data } = await api.post('/auth/refresh', {
        refreshToken: this.refreshToken
      })

      this.setSession(
        data.accessToken,
        data.refreshToken,
        data.username,
        data.roles,
        data.perms
      )

      return data
    },

    logout() {
      this.accessToken = ''
      this.refreshToken = ''
      this.username = ''
      this.roles = []
      this.perms = []

      localStorage.removeItem('accessToken')
      localStorage.removeItem('refreshToken')
      localStorage.removeItem('username')
      localStorage.removeItem('roles')
      localStorage.removeItem('perms')
    }
  }
})
