<template>
  <div class="auth-wrap">
    <div class="auth-card">
      <h1 class="auth-title">註冊帳號</h1>
      <p class="auth-sub">建立後預設為 VIEWER 角色</p>

      <form class="auth-form" @submit.prevent="onSubmit">
        <label class="fld">
          <span class="fld-lbl">使用者名稱</span>
          <input v-model.trim="form.username" type="text" autocomplete="username" required minlength="3" />
        </label>

        <label class="fld">
          <span class="fld-lbl">Email</span>
          <input v-model.trim="form.email" type="email" autocomplete="email" required />
        </label>

        <label class="fld">
          <span class="fld-lbl">手機號碼（簡訊驗證用，例 +886912345678）</span>
          <input v-model.trim="form.phone" type="tel" autocomplete="tel" required />
        </label>

        <label class="fld">
          <span class="fld-lbl">密碼（至少 8 碼）</span>
          <input v-model="form.password" type="password" autocomplete="new-password" required minlength="8" />
        </label>

        <!-- reCAPTCHA v2 -->
        <div ref="recaptchaEl" class="recaptcha"></div>
        <p v-if="!siteKey" class="auth-note">（未設定 VITE_RECAPTCHA_SITE_KEY，dev 模式略過 reCAPTCHA）</p>

        <!-- 簡訊驗證碼 -->
        <div class="sms-row">
          <input v-model.trim="form.smsCode" class="sms-input" type="text" inputmode="numeric"
                 maxlength="6" placeholder="6 位數驗證碼" />
          <button type="button" class="btn-sms" :disabled="cooldown > 0 || sending" @click="sendCode">
            {{ cooldown > 0 ? `${cooldown}s` : (sending ? '發送中…' : '發送驗證碼') }}
          </button>
        </div>

        <p v-if="info" class="auth-note">{{ info }}</p>
        <p v-if="error" class="auth-err">{{ error }}</p>

        <button class="btn-primary" type="submit" :disabled="loading">
          {{ loading ? '註冊中…' : '註冊' }}
        </button>
      </form>

      <div class="auth-links">
        <span></span>
        <RouterLink to="/login">已有帳號？登入</RouterLink>
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import api from '@/services/api'

const auth = useAuthStore()
const router = useRouter()

const siteKey = import.meta.env.VITE_RECAPTCHA_SITE_KEY || ''

const form = reactive({ username: '', email: '', phone: '', password: '', smsCode: '' })
const loading = ref(false)
const sending = ref(false)
const error = ref('')
const info = ref('')
const cooldown = ref(0)

const recaptchaEl = ref(null)
let widgetId = null
let timer = null

// 動態載入 reCAPTCHA script 並 explicit render，避免改 index.html
onMounted(() => {
  if (!siteKey) return
  const cbName = '__recaptchaOnload_' + Date.now()
  window[cbName] = () => {
    widgetId = window.grecaptcha.render(recaptchaEl.value, { sitekey: siteKey })
  }
  const s = document.createElement('script')
  s.src = `https://www.google.com/recaptcha/api.js?onload=${cbName}&render=explicit`
  s.async = true
  s.defer = true
  document.head.appendChild(s)
})

onBeforeUnmount(() => {
  if (timer) clearInterval(timer)
})

function startCooldown(sec = 60) {
  cooldown.value = sec
  timer = setInterval(() => {
    cooldown.value -= 1
    if (cooldown.value <= 0) clearInterval(timer)
  }, 1000)
}

function recaptchaToken() {
  if (!siteKey || !window.grecaptcha || widgetId === null) return ''
  return window.grecaptcha.getResponse(widgetId)
}

async function sendCode() {
  error.value = ''
  info.value = ''
  if (!form.phone) {
    error.value = '請先填手機號碼'
    return
  }
  const token = recaptchaToken()
  if (siteKey && !token) {
    error.value = '請先完成 reCAPTCHA'
    return
  }
  sending.value = true
  try {
    await api.post('/auth/sms/send', { phone: form.phone, recaptchaToken: token })
    info.value = '驗證碼已發送（log 模式請看後端 console）'
    startCooldown(60)
    if (siteKey && window.grecaptcha) window.grecaptcha.reset(widgetId)
  } catch (e) {
    error.value = e.response?.data?.message || '發送失敗'
  } finally {
    sending.value = false
  }
}

async function onSubmit() {
  error.value = ''
  loading.value = true
  try {
    await auth.register({
      username: form.username,
      email: form.email,
      password: form.password,
      phone: form.phone,
      smsCode: form.smsCode,
    })
    router.push('/')
  } catch (e) {
    error.value = e.response?.data?.message || '註冊失敗'
  } finally {
    loading.value = false
  }
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
.fld input,
.sms-input {
  background: var(--bg-2, #0e1116);
  border: 1px solid var(--border, #2a3038);
  border-radius: var(--radius-sm, 6px);
  padding: 9px 11px;
  font-size: 13px;
  color: var(--text-1, #e6edf3);
  outline: none;
}
.fld input:focus,
.sms-input:focus {
  border-color: var(--teal, #1d9e75);
}
.recaptcha {
  min-height: 0;
}
.sms-row {
  display: flex;
  gap: 8px;
}
.sms-input {
  flex: 1;
}
.btn-sms {
  white-space: nowrap;
  background: var(--bg-2, #0e1116);
  border: 1px solid var(--border, #2a3038);
  border-radius: var(--radius-sm, 6px);
  padding: 0 12px;
  font-size: 12px;
  color: var(--text-1, #e6edf3);
  cursor: pointer;
  min-width: 96px;
}
.btn-sms:disabled {
  opacity: 0.6;
  cursor: default;
}
.auth-err {
  font-size: 12px;
  color: var(--red, #e24b4a);
  margin: 0;
}
.auth-note {
  font-size: 12px;
  color: var(--text-2, #8b949e);
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
</style>
