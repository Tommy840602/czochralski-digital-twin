<template>
  <div class="oee-page">
    <div class="page-header">
      <h1>OEE 分析</h1>
      <p class="page-sub">Overall Equipment Effectiveness · 可用率 × 表現率 × 良率</p>
    </div>

    <div class="furnace-tabs">
      <button
        v-for="f in furnaces"
        :key="f"
        :class="['tab', { active: selectedFurnace === f }]"
        @click="selectFurnace(f)"
      >{{ f }}</button>
    </div>

    <div class="period-control">
      <label>統計區間：</label>
      <select v-model.number="minutes" @change="fetchData">
        <option :value="60">最近 1 小時</option>
        <option :value="480">最近 8 小時</option>
        <option :value="1440">最近 24 小時</option>
        <option :value="10080">最近 7 天</option>
      </select>
    </div>

    <div v-if="loading" class="loading">載入中…</div>

    <template v-else-if="data">
      <!-- OEE 總覽卡片 -->
      <div class="oee-hero">
        <div class="oee-hero-value">
          {{ data.oee != null ? (data.oee * 100).toFixed(1) + '%' : '—' }}
        </div>
        <div class="oee-hero-label">{{ selectedFurnace }} OEE 綜合效率</div>
        <div v-if="data.oee == null" class="oee-hero-note">
          尚無已完工晶棒資料，無法計算完整 OEE
        </div>
      </div>

      <!-- 三大指標卡片 -->
      <div class="metrics-grid">
        <div class="metric-card">
          <div class="metric-label">可用率 Availability</div>
          <div class="metric-value">{{ (data.availability * 100).toFixed(1) }}%</div>
          <div class="metric-bar">
            <div class="metric-bar-fill availability" :style="{ width: (data.availability * 100) + '%' }"></div>
          </div>
          <div class="metric-desc">爐子實際運轉時間佔比（heater_temp &gt; 20）</div>
        </div>

        <div class="metric-card">
          <div class="metric-label">表現率 Performance</div>
          <div class="metric-value">
            {{ data.performance != null ? (data.performance * 100).toFixed(1) + '%' : '—' }}
          </div>
          <div class="metric-bar">
            <div class="metric-bar-fill performance"
                 :style="{ width: (data.performance != null ? data.performance * 100 : 0) + '%' }"></div>
          </div>
          <div class="metric-desc">
            BODY 階段實際拉速 / 目標拉速 ({{ data.targetGrMean }} mm/min)
          </div>
        </div>

        <div class="metric-card">
          <div class="metric-label">良率 Quality</div>
          <div class="metric-value">
            {{ data.quality != null ? (data.quality * 100).toFixed(1) + '%' : '—' }}
          </div>
          <div class="metric-bar">
            <div class="metric-bar-fill quality"
                 :style="{ width: (data.quality != null ? data.quality * 100 : 0) + '%' }"></div>
          </div>
          <div class="metric-desc">
            {{ data.goodIngots }} / {{ data.totalIngots }} 根晶棒達標
            (目標長度 {{ data.targetLengthMm }} mm × 80%)
          </div>
        </div>
      </div>

      <!-- 目標基準值參考 -->
      <div class="target-panel">
        <h3>{{ selectedFurnace }} 目標基準值</h3>
        <div class="target-grid">
          <div class="target-item">
            <span class="target-label">目標長度</span>
            <span class="target-value">{{ data.targetLengthMm }} mm</span>
          </div>
          <div class="target-item">
            <span class="target-label">目標週期</span>
            <span class="target-value">{{ data.targetCycleHours }} hr</span>
          </div>
          <div class="target-item">
            <span class="target-label">目標拉速</span>
            <span class="target-value">{{ data.targetGrMean }} mm/min</span>
          </div>
        </div>
      </div>
    </template>

    <div v-else-if="error" class="error-panel">
      載入失敗：{{ error }}
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { oeeService, OEE_FURNACES } from '@/services/oee'
import { useAuthStore } from '@/stores/auth'
import { useRouter } from 'vue-router'

const authStore = useAuthStore()
const router = useRouter()

const furnaces = OEE_FURNACES
const STORAGE_KEY = 'oee_last_furnace'

const selectedFurnace = ref(
  localStorage.getItem(STORAGE_KEY) && furnaces.includes(localStorage.getItem(STORAGE_KEY))
    ? localStorage.getItem(STORAGE_KEY)
    : 'D1'
)
const minutes = ref(1440)
const data = ref(null)
const loading = ref(false)
const error = ref(null)

let refreshTimer = null
let disposed = false

let fetching = false

async function fetchData() {
  if (disposed || fetching) return
  fetching = true
  loading.value = true
  error.value = null
  try {
    data.value = await oeeService.getOee(selectedFurnace.value, minutes.value)
  } catch (e) {
    if (disposed) return
    if (e.response?.status === 401) {
      handleSessionExpired()
      return
    }
    error.value = e.response?.data?.message || e.message
  } finally {
    loading.value = false
    fetching = false
  }
}

let sessionExpiredHandled = false
function handleSessionExpired() {
  if (sessionExpiredHandled) return
  sessionExpiredHandled = true
  if (refreshTimer) {
    window.clearInterval(refreshTimer)
    refreshTimer = null
  }
  authStore.logout()
  alert('登入已過期，請重新登入')
  router.push({ name: 'login', query: { redirect: '/oee' } })
}

function selectFurnace(f) {
  if (f === selectedFurnace.value) return
  selectedFurnace.value = f
  localStorage.setItem(STORAGE_KEY, f)
  fetchData()
}

onMounted(() => {
  disposed = false
  fetchData()
  refreshTimer = window.setInterval(fetchData, 120000)
})

onBeforeUnmount(() => {
  disposed = true
  if (refreshTimer) {
    window.clearInterval(refreshTimer)
    refreshTimer = null
  }
})
</script>

<style scoped>
.oee-page {
  height: 100%;
  overflow-y: auto;
  background: var(--bg-2);
  color: var(--text-1);
  padding: 20px 32px;
}

.page-header { margin-bottom: 16px; }
.page-header h1 { font-size: 22px; margin: 0; }
.page-sub { color: var(--text-2); font-size: 12px; margin: 0; }

.furnace-tabs {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
}
.tab {
  padding: 8px 20px;
  background: var(--bg-1);
  border: 1px solid var(--border);
  border-radius: 6px;
  color: var(--text-2);
  cursor: pointer;
  font-family: 'JetBrains Mono', monospace;
}
.tab.active {
  border-color: var(--teal);
  color: var(--teal);
  background: rgba(29, 158, 117, 0.1);
}

.period-control {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 24px;
  font-size: 12px;
  color: var(--text-2);
}
.period-control select {
  background: var(--bg-1);
  border: 1px solid var(--border);
  color: var(--text-1);
  padding: 6px 12px;
  border-radius: 4px;
  font-size: 12px;
}

.loading { text-align: center; padding: 60px; color: var(--text-2); }
.error-panel { text-align: center; padding: 60px; color: var(--red); }

.oee-hero {
  background: var(--bg-1);
  border: 1px solid var(--border);
  border-radius: 12px;
  padding: 32px;
  text-align: center;
  margin-bottom: 24px;
}
.oee-hero-value {
  font-size: 56px;
  font-weight: 700;
  color: var(--teal);
  font-family: 'JetBrains Mono', monospace;
}
.oee-hero-label {
  font-size: 14px;
  color: var(--text-2);
  margin-top: 8px;
}
.oee-hero-note {
  font-size: 12px;
  color: var(--amber);
  margin-top: 12px;
}

.metrics-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
  margin-bottom: 24px;
}
.metric-card {
  background: var(--bg-1);
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 20px;
}
.metric-label {
  font-size: 12px;
  color: var(--text-2);
  margin-bottom: 8px;
}
.metric-value {
  font-size: 32px;
  font-weight: 600;
  font-family: 'JetBrains Mono', monospace;
  margin-bottom: 12px;
}
.metric-bar {
  height: 6px;
  background: var(--bg-2);
  border-radius: 3px;
  overflow: hidden;
  margin-bottom: 10px;
}
.metric-bar-fill {
  height: 100%;
  border-radius: 3px;
  transition: width 0.3s ease;
}
.metric-bar-fill.availability { background: #1890ff; }
.metric-bar-fill.performance { background: var(--amber); }
.metric-bar-fill.quality { background: #52c41a; }
.metric-desc {
  font-size: 11px;
  color: var(--text-2);
}

.target-panel {
  background: var(--bg-1);
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 20px;
}
.target-panel h3 {
  font-size: 14px;
  margin: 0 0 16px 0;
}
.target-grid {
  display: flex;
  gap: 32px;
}
.target-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.target-label {
  font-size: 11px;
  color: var(--text-2);
}
.target-value {
  font-size: 18px;
  font-weight: 600;
  font-family: 'JetBrains Mono', monospace;
}
</style>
