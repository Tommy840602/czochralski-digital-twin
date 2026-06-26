<template>
  <div class="app-shell">
    <!-- ── 頂部導覽列 ─────────────────────────────────────── -->
    <header class="topnav">
      <div class="topnav-left">
        <div class="logo-mark">DEMO</div>
        <div class="brand">
          <span class="brand-title mono">CZOCHRALSKI TWIN</span>
          <span class="brand-sub">長晶爐數位孿生系統</span>
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
</template>

<script setup>
import { onMounted } from 'vue'
import { RouterLink, RouterView } from 'vue-router'
import { useFurnaceStore } from '@/stores/furnaceStore.js'
import { useFurnaceWebSocket } from '@/composables/useFurnaceWebSocket.js'

const store = useFurnaceStore()
const { resubscribe } = useFurnaceWebSocket()

onMounted(async () => {
  await store.loadFurnaces()
  resubscribe()   // 爐子載入後補訂各個 topic
})
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
