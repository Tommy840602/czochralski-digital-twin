<template>
  <div class="auth-wrap">
    <div class="auth-card">
      <h1 class="auth-title">設定新密碼</h1>

      <div v-if="!token" class="auth-err">缺少重設 token，連結可能不完整。</div>

      <form v-else class="auth-form" @submit.prevent="onSubmit">
        <label class="fld">
          <span class="fld-lbl">新密碼（至少 8 碼）</span>
          <input v-model="pw1" type="password" required minlength="8" autocomplete="new-password" />
        </label>
        <label class="fld">
          <span class="fld-lbl">再次輸入</span>
          <input v-model="pw2" type="password" required minlength="8" autocomplete="new-password" />
        </label>

        <p v-if="error" class="auth-err">{{ error }}</p>

        <button class="btn-primary" type="submit" :disabled="loading">確認重設</button>
      </form>

      <div class="auth-links">
        <span></span>
        <RouterLink to="/login">返回登入</RouterLink>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import api from '@/services/api'

const route = useRoute()
const router = useRouter()

const token = String(route.query.token || '')
const pw1 = ref('')
const pw2 = ref('')
const loading = ref(false)
const error = ref('')

async function onSubmit() {
  error.value = ''
  if (pw1.value !== pw2.value) {
    error.value = '兩次輸入的密碼不一致'
    return
  }
  loading.value = true
  try {
    await api.post('/auth/password/reset', { token, newPassword: pw1.value })
    router.push({ name: 'login', query: { reset: '1' } })
  } catch (e) {
    error.value = e.response?.data?.message || '重設失敗，連結可能已失效'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.auth-wrap { min-height: 100vh; display: flex; align-items: center; justify-content: center; background: var(--bg-2); padding: 24px; }
.auth-card { width: 100%; max-width: 380px; background: var(--bg-1); border: 1px solid var(--border); border-radius: 12px; padding: 32px 28px; }
.auth-title { font-size: 18px; font-weight: 600; color: var(--text-1); margin: 0 0 20px; }
.auth-form { display: flex; flex-direction: column; gap: 14px; }
.fld { display: flex; flex-direction: column; gap: 5px; }
.fld-lbl { font-size: 11px; letter-spacing: 0.08em; color: var(--text-2); }
.fld input { background: var(--bg-2); border: 1px solid var(--border); border-radius: var(--radius-sm, 6px); padding: 9px 11px; font-size: 13px; color: var(--text-1); outline: none; }
.fld input:focus { border-color: var(--teal); }
.auth-err { font-size: 12px; color: var(--red); margin: 0; }
.btn-primary { margin-top: 4px; background: var(--teal); color: #fff; border: none; border-radius: var(--radius-sm, 6px); padding: 10px; font-size: 13px; font-weight: 600; cursor: pointer; }
.btn-primary:disabled { opacity: 0.6; cursor: default; }
.auth-links { display: flex; justify-content: space-between; margin-top: 16px; font-size: 12px; }
.auth-links a { color: var(--teal); text-decoration: none; }
</style>
