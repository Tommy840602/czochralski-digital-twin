<template>
  <!-- 認證頁（登入/註冊/忘記/重設/OAuth callback）：不套戰情室大門，只渲染表單 -->
  <RouterView v-if="isAuthPage" />

  <!-- 其餘：原本的戰情室大門 + app 框架 -->
  <ControlRoomDoor
    v-else
    :phase="phase"
    :progress="progress"
    :stage="stage"
    :status-label="statusLabel"
    :error="error"
    @enter="enter"
    @retry="retry(tasks)"
  >
    <div class="app-shell">
      <!-- ── 頂部導覽列 ─────────────────────────────────────── -->
      <header class="topnav">
        <div class="topnav-left">
          <div class="logo-mark">DEMO</div>
          <div class="brand">
            <span class="brand-title mono">CZ Digital Twin Sys</span>
            <span class="brand-sub">CZ數位孿生系統</span>
          </div>
        </div>

        <nav class="topnav-links">
          <RouterLink to="/" class="nav-link" active-class="nav-link--active">
            <span class="nav-icon">⬡</span> 數位孿生
          </RouterLink>
          <RouterLink to="/dashboard" class="nav-link" active-class="nav-link--active">
            <span class="nav-icon">◈</span> 儀表板
          </RouterLink>
          <RouterLink to="/reports" class="nav-link" active-class="nav-link--active">
            <span class="nav-icon">▤</span> 報告生成
          </RouterLink>
          <RouterLink
            v-if="auth.hasPermission('SPC_VIEW')"
            to="/spc"
            class="nav-link"
          >
            <span class="nav-icon">▧</span>SPC 監測
          </RouterLink>
          <RouterLink
            v-if="auth.hasPermission('OEE_VIEW')"
            to="/oee"
            class="nav-link"
          >
            <span class="nav-icon">▣</span>OEE 分析
          </RouterLink>
        </nav>

        <div class="topnav-right">
          <!-- 爐子計數 -->
          <div class="stat-pill">
            <span class="stat-val mono">{{ store.furnaces.length }}</span>
            <span class="stat-lbl">爐</span>
          </div>
          <!-- WS 狀態 -->
          <div class="ws-indicator" :class="store.wsConnected ? 'ws--live' : 'ws--off'">
            <span class="ws-dot"></span>
            <span class="mono">{{ store.wsConnected ? 'LIVE' : 'OFFLINE' }}</span>
          </div>
          <!-- 主題切換 -->
          <button
            class="theme-btn"
            :title="theme === 'light' ? '切換為深色模式' : '切換為日光模式'"
            @click="toggleTheme"
          >{{ theme === 'light' ? '☾' : '☀' }}</button>
          <!-- 使用者 + 登出 -->
          <div class="user-box">
            <span class="user-name mono">{{ auth.username }}</span>
            <button class="logout-btn" @click="logout">登出</button>
          </div>
        </div>
      </header>

      <!-- ── 主體 ───────────────────────────────────────────── -->
      <main class="app-body">
        <RouterView v-slot="{ Component }">
          <Transition name="fade" mode="out-in">
            <component :is="Component" />
          </Transition>
        </RouterView>
      </main>
    </div>
  </ControlRoomDoor>
</template>

<script setup>
import { onMounted, watch, computed, ref } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import { useFurnaceStore } from '@/stores/furnaceStore.js'
import { useFurnaceWebSocket } from '@/composables/useFurnaceWebSocket.js'
import ControlRoomDoor from '@/components/ControlRoomDoor.vue'
import { useDigitalTwinBoot } from '@/composables/useDigitalTwinBoot.js'
import { useAuthStore } from '@/stores/auth'
import { useTheme } from '@/composables/useTheme.js'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const { theme, toggle: toggleTheme } = useTheme()

const isAuthPage = computed(() =>
  ['login', 'register', 'forgot', 'oauth-callback', 'reset-password'].includes(route.name))

const store = useFurnaceStore()
const { resubscribe } = useFurnaceWebSocket()
const { phase, progress, stage, statusLabel, error, boot, enter, retry } =
  useDigitalTwinBoot()

// ── 把 boot 的三個里程碑對接到真實 store 訊號 ──
const tasks = {
  initScene: async () => {
    await store.loadFurnaces()
    resubscribe()
  },
  connectWs: () => waitFor(() => store.wsConnected, 15000),
  firstData: () => waitFor(() => Object.keys(store.liveData).length > 0, 15000),
}

function waitFor(cond, timeoutMs = 15000) {
  return new Promise((resolve) => {
    if (cond()) return resolve()
    const stop = watch(cond, (v) => {
      if (v) { stop(); resolve() }
    })
    setTimeout(() => { stop(); resolve() }, timeoutMs)
  })
}

// ── boot 只在「已登入且不在認證頁」時跑（避免未登入就打 /furnaces 被 401 卡住門）──
const booted = ref(false)
function maybeBoot() {
  if (booted.value || isAuthPage.value || !auth.isAuthenticated) return
  booted.value = true
  boot(tasks)
}

function logout() {
  auth.logout()
  booted.value = false        // 允許下次登入重新 boot
  router.push({ name: 'login' })
}

onMounted(maybeBoot)
watch(() => auth.isAuthenticated, maybeBoot)
watch(isAuthPage, maybeBoot)
</script>

<style scoped>
.app-shell {
  display: flex;
  flex-direction: column;
  width: 100%;
  height: 100vh;
  overflow: hidden;
  background: var(--bg-0);
}

/* ── 頂部導覽 ────────────────────────────────────────────── */
.topnav {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 24px;
  padding: 0 20px;
  height: 52px;
  background: var(--bg-1);
  border-bottom: 1px solid var(--border);
}

.topnav-left { display: flex; align-items: center; gap: 12px; }

.logo-mark {
  width: 32px; height: 32px;
  border-radius: var(--radius-sm);
  background: linear-gradient(135deg, var(--teal) 0%, #0ea5e9 100%);
  display: flex; align-items: center; justify-content: center;
  font-family: var(--font-mono); font-size: 12px; font-weight: 700;
  color: var(--bg-0); letter-spacing: 0.05em;
  flex-shrink: 0;
}

.brand-title {
  display: block; font-size: 13px; font-weight: 600;
  letter-spacing: 0.1em; color: var(--text-0);
}
.brand-sub {
  display: block; font-size: 10px; color: var(--text-2);
  margin-top: 1px;
}

/* ── Nav links ───────────────────────────────────────────── */
.topnav-links {
  display: flex; gap: 4px; margin-left: 8px;
}

.nav-link {
  display: flex; align-items: center; gap: 6px;
  padding: 6px 14px; border-radius: var(--radius-sm);
  font-size: 12px; font-weight: 500; color: var(--text-1);
  text-decoration: none; letter-spacing: 0.04em;
  transition: background 0.15s, color 0.15s;
}
.nav-link:hover { background: var(--bg-3); color: var(--text-0); }
.nav-link--active {
  background: var(--teal-dim); color: var(--teal);
}
.nav-icon { font-size: 11px; opacity: 0.7; }

/* ── Right ───────────────────────────────────────────────── */
.topnav-right {
  margin-left: auto;
  display: flex; align-items: center; gap: 12px;
}

.stat-pill {
  display: flex; align-items: baseline; gap: 5px;
  padding: 4px 10px;
  background: var(--bg-2); border-radius: var(--radius-sm);
  border: 1px solid var(--border);
}
.stat-val { font-size: 15px; font-weight: 700; color: var(--teal); }
.stat-lbl { font-size: 10px; color: var(--text-2); }

.ws-indicator {
  display: flex; align-items: center; gap: 6px;
  padding: 4px 10px; border-radius: var(--radius-sm);
  font-size: 11px; font-weight: 600;
  border: 1px solid transparent;
  transition: all 0.3s;
}
.ws--live {
  background: var(--green-dim); color: var(--green);
  border-color: rgba(52, 211, 153, 0.2);
}
.ws--off {
  background: var(--red-dim); color: var(--red);
  border-color: rgba(248, 113, 113, 0.2);
}
.ws-dot {
  width: 6px; height: 6px; border-radius: 50%;
  background: currentColor;
}
.ws--live .ws-dot {
  animation: pulse 2s ease-in-out infinite;
}

.spc-page {
  height: 100vh;        /* 從 min-height 改成 height */
  overflow-y: auto;      /* 新增這行 */
  background: var(--bg-2, #0e1116);
  color: var(--text-1, #e6edf3);
  padding: 20px 32px;
}

/* ── 使用者 + 登出 ───────────────────────────────────────── */
.user-box {
  display: flex; align-items: center; gap: 8px;
  padding-left: 12px;
  border-left: 1px solid var(--border);
}
.user-name { font-size: 12px; color: var(--text-1); }
.logout-btn {
  padding: 4px 10px;
  background: var(--bg-2); border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  font-size: 11px; color: var(--text-1); cursor: pointer;
  transition: border-color 0.15s, color 0.15s;
}
.logout-btn:hover { border-color: var(--red); color: var(--red); }

.theme-btn {
  width: 30px; height: 30px;
  display: flex; align-items: center; justify-content: center;
  background: var(--bg-2); border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  color: var(--text-1); cursor: pointer; font-size: 15px; line-height: 1;
  transition: border-color 0.15s, color 0.15s, background 0.15s;
}
.theme-btn:hover { border-color: var(--teal); color: var(--teal); }

/* ── Main ────────────────────────────────────────────────── */
.app-body {
  flex: 1;
  width: 100%;
  overflow: hidden;
  min-height: 0;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50%       { opacity: 0.4; }
}
</style>
