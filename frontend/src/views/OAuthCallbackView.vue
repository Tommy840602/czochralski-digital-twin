<template>
  <div class="cb-wrap">
    <p class="cb-text">{{ message }}</p>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const message = ref('登入中…')

onMounted(() => {
  const { accessToken, refreshToken, username, roles, error } = route.query

  if (error || !accessToken) {
    message.value = '第三方登入失敗，將返回登入頁…'
    setTimeout(() => router.replace({ name: 'login', query: { error: 'oauth' } }), 1200)
    return
  }

  // ⚠ setSession 收的是「位置參數」不是物件：
  //       setSession(accessToken, refreshToken, username, roles, perms)
  //   原本這裡傳了一個物件進去，整包被當成第一個參數 accessToken，
  //   結果 Authorization header 變成 "Bearer [object Object]"，
  //   每一個 API 呼叫都 401。
  //   帳密登入沒事（auth.js 是用位置參數呼叫的），只有 OAuth 這條路徑會踩到。
  //   perms 不用傳，setSession 會自己從 JWT 的 claims 解出來。
  auth.setSession(
    String(accessToken),
    String(refreshToken || ''),
    String(username || ''),
    roles ? String(roles).split(',').filter(Boolean) : [],
  )

  // 清掉網址列的 token，避免被書籤/歷史記錄留存
  router.replace('/')
})
</script>

<style scoped>
.cb-wrap {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--bg-2);
}
.cb-text {
  color: var(--text-2);
  font-size: 13px;
}
</style>
