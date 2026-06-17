<template>
  <div class="history-view">

    <!-- ── 工具列 ──────────────────────────────────────── -->
    <div class="toolbar">
      <!-- 爐子選擇 -->
      <div class="toolbar-group">
        <label class="toolbar-label mono">PULLER</label>
        <div class="furnace-chips">
          <button
            v-for="f in store.furnaces"
            :key="f.furnaceId"
            class="chip"
            :class="{ 'chip--active': selectedIds.has(f.furnaceId) }"
            @click="toggleFurnace(f.furnaceId)"
          >
            {{ f.furnaceId }}
          </button>
        </div>
      </div>

      <!-- 時間範圍 -->
      <div class="toolbar-group">
        <label class="toolbar-label mono">FROM</label>
        <input type="datetime-local" v-model="fromDt" class="dt-input mono" />
      </div>
      <div class="toolbar-group">
        <label class="toolbar-label mono">TO</label>
        <input type="datetime-local" v-model="toDt" class="dt-input mono" />
      </div>

      <!-- 快速範圍 -->
      <div class="toolbar-group">
        <label class="toolbar-label mono">PRESET</label>
        <div class="preset-btns">
          <button v-for="p in presets" :key="p.label" class="preset-btn mono" @click="applyPreset(p)">
            {{ p.label }}
          </button>
        </div>
      </div>

      <!-- 指標 -->
      <div class="toolbar-group">
        <label class="toolbar-label mono">METRIC</label>
        <select v-model="metric" class="metric-sel mono">
          <option v-for="m in metrics" :key="m.key" :value="m.key">{{ m.label }}</option>
        </select>
      </div>

      <button class="query-btn mono" :disabled="loading" @click="query">
        {{ loading ? '查詢中…' : '▶ 查詢' }}
      </button>
    </div>

    <!-- ── 主體 ──────────────────────────────────────────── -->
    <div class="history-body">

      <!-- 左：圖表 -->
      <div class="chart-area">
        <div v-if="error" class="state-msg">{{ error }}</div>
        <div v-else-if="loading" class="state-msg mono">載入中…</div>
        <div v-else-if="chartData.datasets.length === 0" class="state-msg">
          選擇爐子、設定時間範圍，點擊查詢
        </div>
        <Line v-else :data="chartData" :options="chartOptions" class="chart" />

        <!-- 解析度資訊 -->
        <div v-if="resolution" class="resolution-badge mono">
          解析度：{{ resolution }}
        </div>
      </div>

      <!-- 右：統計摘要 -->
      <div class="stat-panel">
        <div class="stat-title mono">STATISTICS</div>

        <template v-if="Object.keys(stats).length > 0">
          <div v-for="(s, id) in stats" :key="id" class="stat-card">
            <div class="stat-id mono">{{ id }}</div>
            <div class="stat-row">
              <span class="sk mono">筆數</span>
              <span class="sv mono">{{ s.count.toLocaleString() }}</span>
            </div>
            <div class="stat-row">
              <span class="sk mono">最小</span>
              <span class="sv mono">{{ fmtStat(s.min) }}</span>
            </div>
            <div class="stat-row">
              <span class="sk mono">最大</span>
              <span class="sv mono">{{ fmtStat(s.max) }}</span>
            </div>
            <div class="stat-row">
              <span class="sk mono">平均</span>
              <span class="sv mono">{{ fmtStat(s.avg) }}</span>
            </div>
          </div>
        </template>

        <div v-else class="stat-empty mono">查詢後顯示統計</div>
      </div>
    </div>

  </div>
</template>

<script setup>
import { ref, computed, reactive } from 'vue'
import { Line } from 'vue-chartjs'
import {
  Chart as ChartJS, CategoryScale, LinearScale,
  PointElement, LineElement, Title, Tooltip, Legend, Filler
} from 'chart.js'
import api from '@/services/api.js'
import { useFurnaceStore } from '@/stores/furnaceStore.js'

ChartJS.register(CategoryScale, LinearScale, PointElement, LineElement, Title, Tooltip, Legend, Filler)

const store = useFurnaceStore()

// ── 工具列狀態 ─────────────────────────────────────────────
const selectedIds = ref(new Set())
const now = new Date()
const pad = n => String(n).padStart(2,'0')
const dtLocal = d => `${d.getFullYear()}-${pad(d.getMonth()+1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`

const fromDt = ref(dtLocal(new Date(now - 24 * 3600 * 1000)))
const toDt   = ref(dtLocal(now))

const metric  = ref('diameter')
const metrics = [
  { key: 'diameter',      label: 'Diameter (mm)' },
  { key: 'heaterTemp',    label: 'Heater Temp (°C)' },
  { key: 'grMean',        label: 'GR Mean (mm/m)' },
  { key: 'bodyLength',    label: 'Body Length (mm)' },
  { key: 'heaterPowerSv', label: 'Heater Power (kW)' },
  { key: 'seedLift',      label: 'Seed Lift' },
  { key: 'residualWeight',label: 'Residual Weight (kg)' },
]

const presets = [
  { label: '1h',   hours: 1 },
  { label: '6h',   hours: 6 },
  { label: '24h',  hours: 24 },
  { label: '3d',   hours: 72 },
  { label: '7d',   hours: 168 },
]

function applyPreset(p) {
  const now = new Date()
  toDt.value   = dtLocal(now)
  fromDt.value = dtLocal(new Date(now - p.hours * 3600 * 1000))
}

function toggleFurnace(id) {
  const s = new Set(selectedIds.value)
  if (s.has(id)) s.delete(id)
  else s.add(id)
  selectedIds.value = s
}

// ── 查詢 ───────────────────────────────────────────────────
const loading    = ref(false)
const error      = ref('')
const resolution = ref('')
const chartData  = ref({ labels: [], datasets: [] })
const stats      = reactive({})

const COLORS = ['#38bdf8','#34d399','#f59e0b','#f87171','#a78bfa','#fb923c','#e879f9','#4ade80']

async function query() {
  if (selectedIds.value.size === 0) { error.value = '請選擇至少一台爐子'; return }
  error.value = ''; loading.value = true

  try {
    const from = new Date(fromDt.value).toISOString()
    const to   = new Date(toDt.value).toISOString()
    const ids  = [...selectedIds.value]

    const results = await Promise.all(
      ids.map(id => api.get(`/furnaces/${id}/history`, { params: { from, to, resolution: 'auto' } }))
    )

    // 對齊時間軸（用第一台爐子的時間標籤）
    const labels = results[0]?.data?.data?.map(d => d.time.slice(11, 16)) ?? []
    resolution.value = results[0]?.data?.resolution ?? ''

    const datasets = results.map((res, i) => {
      const id   = ids[i]
      const pts  = res.data?.data ?? []
      const key  = metric.value

      // 計算統計
      const vals = pts.map(d => d[key]).filter(v => v != null)
      stats[id] = {
        count: vals.length,
        min: Math.min(...vals),
        max: Math.max(...vals),
        avg: vals.length ? vals.reduce((a,b) => a+b, 0) / vals.length : 0
      }

      return {
        label: id,
        data: pts.map(d => d[key] ?? null),
        borderColor: COLORS[i % COLORS.length],
        backgroundColor: COLORS[i % COLORS.length] + '15',
        fill: true,
        borderWidth: 1.5,
        pointRadius: 0,
        tension: 0.3,
        spanGaps: true
      }
    })

    chartData.value = { labels, datasets }
  } catch (e) {
    error.value = '查詢失敗：' + (e.response?.data?.message ?? e.message)
  } finally {
    loading.value = false
  }
}

const metricLabel = computed(() => metrics.find(m => m.key === metric.value)?.label ?? '')

const chartOptions = computed(() => ({
  responsive: true,
  maintainAspectRatio: false,
  animation: false,
  interaction: { mode: 'index', intersect: false },
  plugins: {
    legend: {
      labels: { color: '#8aaec8', font: { family: 'JetBrains Mono', size: 11 } }
    },
    title: {
      display: true,
      text: metricLabel.value,
      color: '#8aaec8',
      font: { family: 'JetBrains Mono', size: 12 }
    },
    tooltip: {
      backgroundColor: '#0d1520',
      borderColor: 'rgba(56,189,248,0.2)',
      borderWidth: 1,
      titleColor: '#38bdf8',
      bodyColor: '#c8d8e8',
      titleFont: { family: 'JetBrains Mono' },
      bodyFont:  { family: 'JetBrains Mono' },
    }
  },
  scales: {
    x: {
      grid: { color: 'rgba(255,255,255,0.04)' },
      ticks: { color: '#4a6a88', font: { family: 'JetBrains Mono', size: 10 }, maxTicksLimit: 12 }
    },
    y: {
      grid: { color: 'rgba(255,255,255,0.04)' },
      ticks: { color: '#4a6a88', font: { family: 'JetBrains Mono', size: 10 }, maxTicksLimit: 8 }
    }
  }
}))

function fmtStat(v) {
  if (v == null || !isFinite(v)) return '—'
  return Number(v).toFixed(2)
}
</script>

<style scoped>
.history-view {
  display: flex; flex-direction: column;
  height: 100%; overflow: hidden;
  background: var(--bg-0);
}

/* ── 工具列 ─────────────────────────────────────────────── */
.toolbar {
  display: flex; flex-wrap: wrap; align-items: flex-end;
  gap: 16px; padding: 12px 20px;
  background: var(--bg-1); border-bottom: 1px solid var(--border);
  flex-shrink: 0;
}

.toolbar-group { display: flex; flex-direction: column; gap: 5px; }
.toolbar-label { font-size: 9px; letter-spacing: 0.14em; color: var(--text-2); }

.furnace-chips { display: flex; gap: 4px; flex-wrap: wrap; }
.chip {
  padding: 4px 12px; border-radius: var(--radius-sm);
  border: 1px solid var(--border); background: var(--bg-2);
  font-family: var(--font-mono); font-size: 12px; font-weight: 600;
  color: var(--text-1); cursor: pointer; transition: all 0.15s;
}
.chip:hover { border-color: var(--border-hi); color: var(--text-0); }
.chip--active { background: var(--teal-dim); border-color: rgba(56,189,248,0.3); color: var(--teal); }

.dt-input, .metric-sel {
  background: var(--bg-2); border: 1px solid var(--border);
  border-radius: var(--radius-sm); color: var(--text-0);
  padding: 5px 8px; font-size: 12px; outline: none;
}
.dt-input:focus, .metric-sel:focus { border-color: var(--border-hi); }

.preset-btns { display: flex; gap: 4px; }
.preset-btn {
  padding: 4px 10px; border-radius: var(--radius-sm);
  border: 1px solid var(--border); background: var(--bg-2);
  font-size: 11px; color: var(--text-1); cursor: pointer; transition: all 0.15s;
}
.preset-btn:hover { background: var(--bg-3); color: var(--text-0); }

.query-btn {
  margin-left: auto; align-self: flex-end;
  padding: 7px 20px; border-radius: var(--radius-sm);
  background: var(--teal-dim); border: 1px solid rgba(56,189,248,0.3);
  color: var(--teal); font-size: 12px; font-weight: 600;
  cursor: pointer; transition: all 0.15s;
}
.query-btn:hover:not(:disabled) { background: rgba(56,189,248,0.2); }
.query-btn:disabled { opacity: 0.5; cursor: not-allowed; }

/* ── 主體 ────────────────────────────────────────────────── */
.history-body {
  flex: 1; display: flex; overflow: hidden;
}

.chart-area {
  flex: 1; position: relative; padding: 20px;
  display: flex; align-items: center; justify-content: center;
}
.chart { width: 100% !important; height: 100% !important; }
.state-msg { color: var(--text-2); font-size: 13px; text-align: center; }
.resolution-badge {
  position: absolute; bottom: 10px; right: 20px;
  font-size: 10px; color: var(--text-2);
  background: var(--bg-1); padding: 3px 8px;
  border-radius: var(--radius-sm); border: 1px solid var(--border);
}

/* ── 統計面板 ────────────────────────────────────────────── */
.stat-panel {
  width: 220px; flex-shrink: 0;
  background: var(--bg-1); border-left: 1px solid var(--border);
  padding: 16px; overflow-y: auto; display: flex; flex-direction: column; gap: 12px;
}
.stat-title {
  font-size: 10px; font-weight: 600; letter-spacing: 0.14em;
  color: var(--text-2);
}
.stat-card {
  background: var(--bg-2); border: 1px solid var(--border);
  border-radius: var(--radius-sm); padding: 10px 12px;
  display: flex; flex-direction: column; gap: 5px;
}
.stat-id { font-size: 13px; font-weight: 700; color: var(--teal); margin-bottom: 3px; }
.stat-row { display: flex; justify-content: space-between; }
.sk { font-size: 10px; color: var(--text-2); }
.sv { font-size: 11px; color: var(--text-0); }
.stat-empty { font-size: 11px; color: var(--text-2); text-align: center; padding: 16px 0; }
</style>
