<template>
  <div class="auth-wrap">
    <div class="auth-card">
      <h1 class="auth-title">數位孿生系統DEMO</h1>
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
      <br>
      <h6>#聲明</h6>
      <h6>
        1.本專案為個人面試展示用途，所有爐子模具圖、設備示意圖與熱場模擬圖皆由 AI 生成或基於模擬情境自行設計，並非取自任何公司、客戶、供應商或第三方之內部圖面、實機照片、工程文件或專有設計資料。
      </h6>
      <h6>
        2.展示中之分析報告與技術說明為 ChatGPT 輔助產生之模擬內容，未引用、改寫、揭露或還原任何內部技術文件、SOP、製程規範、設備手冊或商業資料。所有製程參數、感測數值、爐台狀態、警報事件、品質數據與報表內容皆為亂數生成或模擬資料，僅供系統架構與技術能力展示，不代表任何實際產線、設備、製程、配方或生產條件。
      </h6>
      <h6>
        3.本專案所展示之軟體架構、資料流程、系統模組、通訊方式、服務切分、資料庫設計與部署方式皆為模擬設計，僅用於展示個人開發與系統設計能力，與任何實際公司、產線、設備或既有系統之真實架構無關。
      </h6>
      <h6>
        4.本專案之命名、畫面配置、功能模組、資料表欄位、API 設計、系統架構圖與展示流程，皆為個人基於公開技術知識與模擬需求自行設計，未參考、複製、改寫、還原或對應任何特定公司、客戶、供應商或既有系統之內部架構、程式碼、資料模型、網路拓樸、權限設計、製程邏輯或維運流程。
      </h6>
      <h6>
        5.本專案不具備實際生產控制用途，亦不應視為可直接導入現場之正式工控系統、MES、SCADA、EAP 或品質管理系統。
      </h6>
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
  background: var(--bg-2);
  padding: 24px;
}
.auth-card {
  width: 100%;
  max-width: 380px;
  background: var(--bg-1);
  border: 1px solid var(--border);
  border-radius: 12px;
  padding: 32px 28px;
}
.auth-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-1);
  margin: 0 0 4px;
}
.auth-sub {
  font-size: 12px;
  color: var(--text-2);
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
  color: var(--text-2);
}
.fld input {
  background: var(--bg-2);
  border: 1px solid var(--border);
  border-radius: var(--radius-sm, 6px);
  padding: 9px 11px;
  font-size: 13px;
  color: var(--text-1);
  outline: none;
}
.fld input:focus {
  border-color: var(--teal);
}
.auth-err {
  font-size: 12px;
  color: var(--red);
  margin: 0;
}
.btn-primary {
  margin-top: 4px;
  background: var(--teal);
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
  color: var(--teal);
  text-decoration: none;
}
.divider {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 22px 0 14px;
  color: var(--text-2);
  font-size: 11px;
}
.divider::before,
.divider::after {
  content: '';
  flex: 1;
  height: 1px;
  background: var(--border);
}
.oauth-row {
  display: flex;
  gap: 8px;
}
.btn-oauth {
  flex: 1;
  background: var(--bg-2);
  border: 1px solid var(--border);
  border-radius: var(--radius-sm, 6px);
  padding: 8px;
  font-size: 12px;
  color: var(--text-1);
  cursor: pointer;
}
.btn-oauth:hover {
  border-color: var(--teal);
}
</style>
