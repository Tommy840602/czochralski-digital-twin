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

  auth.setSession({
    accessToken: String(accessToken),
    refreshToken: String(refreshToken || ''),
    username: String(username || ''),
    roles: roles ? String(roles).split(',').filter(Boolean) : [],
  })

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
