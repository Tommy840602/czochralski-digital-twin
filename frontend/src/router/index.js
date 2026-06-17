import { createRouter, createWebHistory } from 'vue-router'
import TwinView    from '@/views/TwinView.vue'
import HistoryView from '@/views/HistoryView.vue'

const routes = [
  { path: '/',        name: 'twin',    component: TwinView,    meta: { title: '數位孿生' } },
  { path: '/history', name: 'history', component: HistoryView, meta: { title: '歷史分析' } },
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
