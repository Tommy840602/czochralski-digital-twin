<template>
  <div class="auth-wrap">
    <div class="auth-card">
      <h1 class="auth-title">忘記密碼</h1>
      <p class="auth-sub">兩種重設方式擇一</p>

      <div class="tab-row">
        <button
          class="tab"
          :class="{ active: mode === 'email' }"
          @click="mode = 'email'"
        >Email 連結</button>
        <button
          class="tab"
          :class="{ active: mode === 'sms' }"
          @click="mode = 'sms'"
        >簡訊重設</button>
      </div>

      <!-- ── Email 重設 ────────────────────────── -->
      <div v-if="mode === 'email'" class="auth-form">
        <div class="fld">
          <label class="fld-lbl">EMAIL</label>
          <input v-model="email" type="email" placeholder="you@example.com" />
        </div>

        <div ref="captchaEmailBox" class="captcha"></div>
        <p v-if="!siteKey" class="hint-mini">
          （未設定 VITE_RECAPTCHA_SITE_KEY，dev 模式略過 reCAPTCHA）
        </p>

        <p v-if="msg" class="auth-ok">{{ msg }}</p>
        <p v-if="err" class="auth-err">{{ err }}</p>

        <button class="btn-primary" @click="sendEmail" :disabled="loading">
          {{ loading ? '傳送中…' : '寄送重設連結' }}
        </button>
      </div>

      <!-- ── 簡訊重設 ────────────────────────── -->
      <div v-else class="auth-form">
        <div class="fld">
          <label class="fld-lbl">手機號碼</label>
          <input v-model="phone" placeholder="+886912345678" />
        </div>

        <div ref="captchaSmsBox" class="captcha"></div>
        <p v-if="!siteKey" class="hint-mini">
          （未設定 VITE_RECAPTCHA_SITE_KEY，dev 模式略過 reCAPTCHA）
        </p>

        <div class="code-row">
          <input v-model="smsCode" class="code-input" placeholder="6 位數驗證碼" />
          <button class="btn-secondary" @click="sendCode" :disabled="loading">發送驗證碼</button>
        </div>

        <div class="fld">
          <label class="fld-lbl">新密碼（至少 8 碼）</label>
          <input v-model="newPassword" type="password" />
        </div>

        <p v-if="msg" class="auth-ok">{{ msg }}</p>
        <p v-if="err" class="auth-err">{{ err }}</p>

        <button class="btn-primary" @click="resetBySms" :disabled="loading">
          {{ loading ? '重設中…' : '重設密碼' }}
        </button>
      </div>

      <div class="auth-links">
        <RouterLink to="/login">返回登入</RouterLink>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, watch, nextTick, onMounted } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import api from '@/services/api'

const router = useRouter()

const mode = ref('email')
const email = ref('')
const phone = ref('')
const smsCode = ref('')
const newPassword = ref('')

const msg = ref('')
const err = ref('')
const loading = ref(false)

const captchaEmailBox = ref(null)
const captchaSmsBox = ref(null)
const captchaEmailId = ref(null)
const captchaSmsId = ref(null)

const siteKey = import.meta.env.VITE_RECAPTCHA_SITE_KEY

function loadRecaptchaScript() {
  return new Promise((resolve) => {
    if (window.grecaptcha) return resolve()
    if (document.getElementById('recaptcha-script')) {
      const iv = setInterval(() => {
        if (window.grecaptcha) { clearInterval(iv); resolve() }
      }, 100)
      return
    }
    const s = document.createElement('script')
    s.id = 'recaptcha-script'
    s.src = 'https://www.google.com/recaptcha/api.js?render=explicit'
    s.async = true
    s.defer = true
    s.onload = () => {
      const iv = setInterval(() => {
        if (window.grecaptcha && window.grecaptcha.render) {
          clearInterval(iv); resolve()
        }
      }, 100)
    }
    document.head.appendChild(s)
  })
}

async function renderCaptcha() {
  if (!siteKey) return
  await loadRecaptchaScript()
  await nextTick()
  if (mode.value === 'email' && captchaEmailBox.value && captchaEmailId.value === null) {
    captchaEmailId.value = window.grecaptcha.render(captchaEmailBox.value, { sitekey: siteKey })
  }
  if (mode.value === 'sms' && captchaSmsBox.value && captchaSmsId.value === null) {
    captchaSmsId.value = window.grecaptcha.render(captchaSmsBox.value, { sitekey: siteKey })
  }
}

function getCaptchaToken(which) {
  if (!siteKey) return 'dev-skip'
  const id = which === 'email' ? captchaEmailId.value : captchaSmsId.value
  if (id === null) return ''
  return window.grecaptcha.getResponse(id) || ''
}

function resetCaptcha(which) {
  if (!siteKey) return
  const id = which === 'email' ? captchaEmailId.value : captchaSmsId.value
  if (id !== null) window.grecaptcha.reset(id)
}

onMounted(renderCaptcha)
watch(mode, () => renderCaptcha())

async function sendEmail() {
  msg.value = ''; err.value = ''
  const recaptchaToken = getCaptchaToken('email')
  if (siteKey && !recaptchaToken) { err.value = '請先完成 reCAPTCHA'; return }
  if (!email.value) { err.value = '請輸入 Email'; return }
  loading.value = true
  try {
    await api.post('/auth/password/forgot', { email: email.value, recaptchaToken })
    msg.value = '若信箱存在，重設連結已寄出，請至信箱查收（5 分鐘內有效）'
  } catch (e) {
    err.value = e.response?.data?.message || '寄送失敗'
    resetCaptcha('email')
  } finally { loading.value = false }
}

async function sendCode() {
  msg.value = ''; err.value = ''
  const recaptchaToken = getCaptchaToken('sms')
  if (siteKey && !recaptchaToken) { err.value = '請先完成 reCAPTCHA'; return }
  if (!phone.value) { err.value = '請輸入手機號碼'; return }
  loading.value = true
  try {
    await api.post('/auth/password/forgot-sms', { phone: phone.value, recaptchaToken })
    msg.value = '簡訊已發送，請於 5 分鐘內填入驗證碼'
  } catch (e) {
    err.value = e.response?.data?.message || '發送失敗'
    resetCaptcha('sms')
  } finally { loading.value = false }
}

async function resetBySms() {
  msg.value = ''; err.value = ''
  if (!phone.value || !smsCode.value || !newPassword.value) {
    err.value = '請填齊所有欄位'; return
  }
  if (newPassword.value.length < 8) { err.value = '密碼至少 8 碼'; return }
  loading.value = true
  try {
    await api.post('/auth/password/reset-sms', {
      phone: phone.value, smsCode: smsCode.value, newPassword: newPassword.value,
    })
    msg.value = '密碼已重設，3 秒後導回登入'
    setTimeout(() => router.push({ name: 'login' }), 3000)
  } catch (e) {
    err.value = e.response?.data?.message || '重設失敗'
  } finally { loading.value = false }
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
  max-width: 420px;
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
  margin: 0 0 20px;
}

.tab-row {
  display: flex;
  gap: 8px;
  margin-bottom: 20px;
}
.tab {
  flex: 1;
  background: var(--bg-2, #0e1116);
  color: var(--text-2, #8b949e);
  border: 1px solid var(--border, #2a3038);
  border-radius: var(--radius-sm, 6px);
  padding: 8px;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.15s;
}
.tab.active {
  background: var(--bg-1, #161b22);
  border-color: var(--teal, #1d9e75);
  color: var(--teal, #1d9e75);
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

.captcha {
  display: flex;
  justify-content: center;
  margin: 4px 0;
}
.hint-mini {
  color: var(--text-2, #8b949e);
  font-size: 11px;
  margin: -4px 0 0;
  text-align: center;
}

.code-row {
  display: flex;
  gap: 8px;
}
.code-input {
  flex: 1;
  background: var(--bg-2, #0e1116);
  border: 1px solid var(--border, #2a3038);
  border-radius: var(--radius-sm, 6px);
  padding: 9px 11px;
  font-size: 13px;
  color: var(--text-1, #e6edf3);
  outline: none;
}
.code-input:focus {
  border-color: var(--teal, #1d9e75);
}

.auth-ok {
  font-size: 12px;
  color: var(--teal, #1d9e75);
  margin: 0;
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

.btn-secondary {
  background: var(--bg-2, #0e1116);
  border: 1px solid var(--border, #2a3038);
  border-radius: var(--radius-sm, 6px);
  padding: 8px 14px;
  font-size: 12px;
  color: var(--text-1, #e6edf3);
  cursor: pointer;
  white-space: nowrap;
}
.btn-secondary:hover {
  border-color: var(--teal, #1d9e75);
}
.btn-secondary:disabled {
  opacity: 0.6;
  cursor: default;
}

.auth-links {
  display: flex;
  justify-content: center;
  margin-top: 16px;
  font-size: 12px;
}
.auth-links a {
  color: var(--teal, #1d9e75);
  text-decoration: none;
}
</style>
