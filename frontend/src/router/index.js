import { createRouter, createWebHistory } from 'vue-router'
import TwinView    from '@/views/TwinView.vue'
import DashboardView from '@/views/DashboardView.vue'

const routes = [
  { path: '/',        name: 'twin',    component: TwinView,    meta: { title: '數位孿生' } },
  { path: '/dashboard', name: 'dashboard', component: DashboardView, meta: { title: '總覽' } },
  { path: '/:pathMatch(.*)*', redirect: '/' }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach(to => {
  document.title = `${to.meta.title ?? ''} — 長晶爐數位孿生`
})

export default router
