import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const routes = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/LoginView.vue'),
    meta: { public: true, hideNav: true }
  },
  {
    path: '/register',
    name: 'register',
    component: () => import('@/views/RegisterView.vue'),
    meta: { public: true, hideNav: true }
  },
  {
    path: '/forgot',
    name: 'forgot',
    component: () => import('@/views/ForgotPasswordView.vue'),
    meta: { public: true, hideNav: true }
  },
  {
    path: '/reset-password',
    name: 'reset-password',
    component: () => import('@/views/ResetPasswordView.vue'),
    meta: { public: true, hideNav: true }
  },
  {
    path: '/login/callback',
    name: 'oauth-callback',
    component: () => import('@/views/OAuthCallbackView.vue'),
    meta: { public: true, hideNav: true }
  },
  {
    path: '/',
    name: 'twin',
    component: () => import('@/views/TwinView.vue')
  },
  {
    path: '/dashboard',
    name: 'dashboard',
    component: () => import('@/views/DashboardView.vue')
  },
  {
    path: '/reports',
    name: 'reports',
    component: () => import('@/views/ReportView.vue'),
    // 漏了這行：/spc 和 /oee 都有 requiresPerm，只有 /reports 沒有——
    // 導覽列藏起來也沒用，直接打網址一樣進得去（後端會擋，但畫面很難看）。
    meta: { requiresPerm: 'REPORT_GEN' }
  },
  {
    path: '/spc',
    name: 'spc',
    component: () => import('@/views/SpcMonitorView.vue'),
    meta: { requiresPerm: 'SPC_VIEW' }
  },
  {
    path: '/oee',
    name: 'oee',
    component: () => import('@/views/OeeAnalysisView.vue'),
    meta: { requiresPerm: 'OEE_VIEW' }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  const auth = useAuthStore()

  // Public routes 不用登入
  if (to.meta.public) return next()

  // 需要登入
  if (!auth.accessToken) {
    return next({ name: 'login', query: { redirect: to.fullPath } })
  }

  // 檢查權限
  if (to.meta.requiresPerm && !auth.hasPermission(to.meta.requiresPerm)) {
    // 沒權限、導回首頁
    console.warn(`[router] blocked ${to.path}, missing ${to.meta.requiresPerm}`)
    return next({ name: 'twin' })
  }

  next()
})

export default router
