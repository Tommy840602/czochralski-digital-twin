import { createRouter, createWebHistory } from 'vue-router'
import TwinView from '@/views/TwinView.vue'
import DashboardView from '@/views/DashboardView.vue'
import ReportView from '@/views/ReportView.vue'
import { useAuthStore } from '@/stores/auth'

const routes = [
  // 受保護頁
  { path: '/',          name: 'twin',      component: TwinView,      meta: { title: '數位孿生' } },
  { path: '/dashboard', name: 'dashboard', component: DashboardView, meta: { title: '儀表板' } },
  { path: '/reports',   name: 'reports',   component: ReportView,    meta: { title: '分析報告' } },

  // 公開（認證）頁
  { path: '/login',          name: 'login',          component: () => import('@/views/LoginView.vue'),          meta: { title: '登入',     public: true } },
  { path: '/register',       name: 'register',       component: () => import('@/views/RegisterView.vue'),       meta: { title: '註冊',     public: true } },
  { path: '/forgot',         name: 'forgot',         component: () => import('@/views/ForgotPasswordView.vue'), meta: { title: '忘記密碼', public: true } },
  { path: '/reset-password', name: 'reset-password', component: () => import('@/views/ResetPasswordView.vue'),  meta: { title: '重設密碼', public: true } },
  { path: '/login/callback', name: 'oauth-callback', component: () => import('@/views/OAuthCallbackView.vue'),  meta: { title: '登入中',   public: true } },

  { path: '/:pathMatch(.*)*', redirect: '/' }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

const PUBLIC_NAMES = ['login', 'register', 'forgot', 'reset-password', 'oauth-callback']

router.beforeEach((to) => {
  document.title = `${to.meta.title ?? ''} — 長晶爐數位孿生系統`

  const auth = useAuthStore()

  if (PUBLIC_NAMES.includes(to.name)) {
    // 已登入就別再進登入頁
    if (to.name === 'login' && auth.isAuthenticated) return { path: '/' }
    return true
  }

  if (!auth.isAuthenticated) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }

  // 頁面級 RBAC：route.meta.roles 存在時檢查
  if (to.meta?.roles && !auth.hasAnyRole(to.meta.roles)) {
    return { path: '/' }
  }

  return true
})

export default router
