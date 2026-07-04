<template>
  <div class="auth-wrap">
    <div class="auth-card">
      <h1 class="auth-title">數位孿生監控平台</h1>
      <p class="auth-sub">登入以繼續</p>

      <form class="auth-form" @submit.prevent="onSubmit">
        <label class="fld">
          <span class="fld-lbl">帳號 / Email</span>
          <input v-model.trim="usernameOrEmail" type="text" autocomplete="username" required />
        </label>

        <label class="fld">
          <span class="fld-lbl">密碼</span>
          <input v-model="password" type="password" autocomplete="current-password" required />
        </label>

        <p v-if="error" class="auth-err">{{ error }}</p>

        <button class="btn-primary" type="submit" :disabled="loading">
          {{ loading ? '登入中…' : '登入' }}
        </button>
      </form>

      <div class="auth-links">
        <RouterLink to="/forgot">忘記密碼？</RouterLink>
        <RouterLink to="/register">註冊新帳號</RouterLink>
      </div>

      <div class="divider"><span>或使用第三方登入</span></div>

      <div class="oauth-row">
        <button class="btn-oauth" @click="oauth('github')">GitHub</button>
        <button class="btn-oauth" @click="oauth('google')">Google</button>
        <button class="btn-oauth" @click="oauth('azure')">Outlook</button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()

const usernameOrEmail = ref('')
const password = ref('')
const loading = ref(false)
const error = ref('')

async function onSubmit() {
  error.value = ''
  loading.value = true
  try {
    await auth.login(usernameOrEmail.value, password.value)
    const redirect = route.query.redirect
    router.push(typeof redirect === 'string' ? redirect : '/')
  } catch (e) {
    error.value = e.response?.data?.message || '登入失敗，請確認帳號密碼'
  } finally {
    loading.value = false
  }
}

// 第三方登入：第 ⑤ 階段接後端 OAuth2。屆時導向 gateway 的 /oauth2/authorization/{provider}
function oauth(provider) {
  // 走 vite proxy /api → gateway → auth-service
  window.location.href = `/api/oauth2/authorization/${provider}`
}
</script>

<style scoped>
.auth-wrap {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--bg-2, #0e1116);
  padding: 24px;
}
.auth-card {
  width: 100%;
  max-width: 380px;
  background: var(--bg-1, #161b22);
  border: 1px solid var(--border, #2a3038);
  border-radius: 12px;
  padding: 32px 28px;
}
.auth-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-1, #e6edf3);
  margin: 0 0 4px;
}
.auth-sub {
  font-size: 12px;
  color: var(--text-2, #8b949e);
  margin: 0 0 24px;
}
.auth-form {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.fld {
  display: flex;
  flex-direction: column;
  gap: 5px;
}
.fld-lbl {
  font-size: 11px;
  letter-spacing: 0.08em;
  color: var(--text-2, #8b949e);
}
.fld input {
  background: var(--bg-2, #0e1116);
  border: 1px solid var(--border, #2a3038);
  border-radius: var(--radius-sm, 6px);
  padding: 9px 11px;
  font-size: 13px;
  color: var(--text-1, #e6edf3);
  outline: none;
}
.fld input:focus {
  border-color: var(--teal, #1d9e75);
}
.auth-err {
  font-size: 12px;
  color: var(--red, #e24b4a);
  margin: 0;
}
.btn-primary {
  margin-top: 4px;
  background: var(--teal, #1d9e75);
  color: #fff;
  border: none;
  border-radius: var(--radius-sm, 6px);
  padding: 10px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
}
.btn-primary:disabled {
  opacity: 0.6;
  cursor: default;
}
.auth-links {
  display: flex;
  justify-content: space-between;
  margin-top: 16px;
  font-size: 12px;
}
.auth-links a {
  color: var(--teal, #1d9e75);
  text-decoration: none;
}
.divider {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 22px 0 14px;
  color: var(--text-2, #8b949e);
  font-size: 11px;
}
.divider::before,
.divider::after {
  content: '';
  flex: 1;
  height: 1px;
  background: var(--border, #2a3038);
}
.oauth-row {
  display: flex;
  gap: 8px;
}
.btn-oauth {
  flex: 1;
  background: var(--bg-2, #0e1116);
  border: 1px solid var(--border, #2a3038);
  border-radius: var(--radius-sm, 6px);
  padding: 8px;
  font-size: 12px;
  color: var(--text-1, #e6edf3);
  cursor: pointer;
}
.btn-oauth:hover {
  border-color: var(--teal, #1d9e75);
}
</style>
