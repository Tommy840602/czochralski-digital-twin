<template>
  <div class="spc-page">
    <div class="page-header">
      <h1>SPC 統計製程管制</h1>
      <p class="page-sub">
        Self-baselining SPC · Western Electric Rules (8 Rules)
      </p>
    </div>

    <div class="furnace-tabs">
      <button
        v-for="f in furnaces"
        :key="f"
        :class="['tab', { active: selectedFurnace === f }]"
        @click="selectFurnace(f)"
      >{{ f }}</button>
    </div>

    <!-- 該爐子的重算控制列 -->
    <div class="furnace-control-bar">
      <button class="btn-rebuild" :disabled="busy" @click="rebuildBaseline">
        {{ busy ? '計算中…' : `↻ 重算 ${selectedFurnace} Baseline` }}
      </button>

      <div class="sigma-control">
        <label>σ 寬鬆度（{{ paramLabel(selectedParam) }}）</label>
        <input
          type="number" min="0.5" max="3" step="0.1"
          v-model.number="sigmaMultiplier"
          :disabled="busy"
          class="sigma-input"
        />
        <span class="sigma-unit">x</span>
        <button class="btn-apply-sigma" :disabled="busy" @click="applySigmaMultiplier">
          {{ busy ? '計算中…' : '套用' }}
        </button>
      </div>
    </div>

    <!-- 統計摘要 (8 條 Rules) — 只顯示目前選中爐子 -->
    <div class="stats-grid">
      <div v-for="(rule, id) in rules" :key="id" class="stat-card">
        <div class="stat-header">
          <span class="rule-id">Rule {{ id }}</span>
          <span :class="['severity', rule.severity.toLowerCase()]">{{ rule.severity }}</span>
        </div>
        <div class="stat-value">{{ furnaceStatistics[id] || 0 }}</div>
        <div class="stat-total">Total: {{ furnaceTotalStatistics[id] || 0 }}</div>
        <div class="stat-desc">{{ rule.name }}</div>
      </div>
    </div>

    <div class="chart-panel">
      <div class="chart-header">
        <h2>{{ selectedFurnace }} · {{ paramLabel(selectedParam) }}</h2>
        <select v-model="selectedParam" class="param-select">
          <option v-for="p in params" :key="p.key" :value="p.key">
            {{ p.label }} ({{ p.unit }})
          </option>
        </select>
      </div>
      <div ref="chartRef" class="chart-container"></div>
      <div class="chart-legend">
        <span class="legend-item">
          <span class="legend-color" style="background:#52c41a"></span> Mean
        </span>
        <span class="legend-item">
          <span class="legend-color" style="background:#faad14"></span> ±1σ / ±2σ
        </span>
        <span class="legend-item">
          <span class="legend-color" style="background:#ff4d4f"></span> ±3σ (UCL/LCL)
        </span>
      </div>
    </div>

    <div class="violation-panel">
      <div class="violation-header">
        <h2>{{ selectedFurnace }} 違規事件 (最近 60 分鐘)</h2>
        <span class="count-badge">{{ furnaceViolations.length }}</span>
      </div>
      <div class="violation-table">
        <table>
          <thead>
          <tr>
            <th>時間</th>
            <th>參數</th>
            <th>Rule</th>
            <th>值</th>
            <th>Mean</th>
            <th>±3σ 範圍</th>
            <th>Severity</th>
          </tr>
          </thead>
          <tbody>
          <tr v-for="v in furnaceViolations" :key="`${v.id}-${v.ts}`"
              :class="v.severity.toLowerCase()">
            <td>{{ formatTime(v.ts) }}</td>
            <td>{{ paramLabel(v.paramName) }}</td>
            <td>
                <span class="rule-tag" :style="{ background: ruleColor(v.ruleId) }">
                  R{{ v.ruleId }}
                </span>
              {{ v.ruleName }}
            </td>
            <td class="mono">{{ v.value.toFixed(3) }}</td>
            <td class="mono">{{ v.mean.toFixed(3) }}</td>
            <td class="mono">
              {{ v.lcl3sigma?.toFixed(2) }} ~ {{ v.ucl3sigma?.toFixed(2) }}
            </td>
            <td>
                <span :class="['sev-badge', v.severity.toLowerCase()]">
                  {{ v.severity }}
                </span>
            </td>
          </tr>
          <tr v-if="!furnaceViolations.length">
            <td colspan="7" class="empty">尚無違規事件</td>
          </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, watch, computed, nextTick } from 'vue'
import * as echarts from 'echarts'
import { spcService, SPC_RULES, SPC_PARAMS, FURNACES } from '@/services/spc'
import { useAuthStore } from '@/stores/auth'
import { useRouter } from 'vue-router'
import { useTheme } from '@/composables/useTheme.js'

const authStore = useAuthStore()
const router = useRouter()
const { theme } = useTheme()

/** 讀取當前 CSS 變數值（供 echarts 使用，隨主題翻轉） */
function cssVar(name, fallback) {
  const v = getComputedStyle(document.documentElement).getPropertyValue(name).trim()
  return v || fallback
}

const furnaces = FURNACES
const params = SPC_PARAMS
const rules = SPC_RULES

const STORAGE_KEY = 'spc_last_furnace'

const selectedFurnace = ref(localStorage.getItem(STORAGE_KEY) && furnaces.includes(localStorage.getItem(STORAGE_KEY))
  ? localStorage.getItem(STORAGE_KEY)
  : 'D1')
const selectedParam = ref('heaterTemp')
const baselines = ref([])
const violations = ref([])
const furnaceStatistics = ref({})       // 目前參數的違規數
const furnaceTotalStatistics = ref({})  // 整爐（全部參數）的違規數
const timeseries = ref([])
const busy = ref(false)
const sigmaMultiplier = ref(1.0)

const chartRef = ref(null)

let chart = null
let refreshTimer = null
let disposed = false
let fetching = false

const currentBaseline = computed(() => {
  return baselines.value.find(b => b.paramName === selectedParam.value)
})

const furnaceViolations = computed(() => {
  return violations.value.filter(v => v.furnaceId === selectedFurnace.value)
})

function paramLabel(paramName) {
  const p = params.find(x => x.key === paramName)
  return p ? p.label : paramName
}

function ruleColor(ruleId) {
  return SPC_RULES[ruleId]?.color || '#999'
}

function formatTime(ts) {
  return new Date(ts).toLocaleTimeString('zh-TW', { hour12: false })
}

function isChartAlive() {
  return chart && !disposed && !chart.isDisposed?.()
}

function initChart() {
  if (!chartRef.value || disposed) return
  const existing = echarts.getInstanceByDom(chartRef.value)
  if (existing && !existing.isDisposed?.()) {
    chart = existing
    return
  }
  // 不綁 echarts 內建 'dark' 主題；背景設透明、軸色改讀 CSS 變數，讓圖表隨主題翻轉
  chart = echarts.init(chartRef.value)
}

function safeResizeChart() {
  if (!isChartAlive()) return
  chart.resize()
}

async function fetchData() {
  if (disposed || fetching) return
  fetching = true

  try {
    const [
      nextBaselines,
      nextViolations,
      nextFurnaceStatistics,
      nextFurnaceTotalStatistics,
      nextTimeseries
    ] = await Promise.all([
      spcService.getBaselines(selectedFurnace.value),
      spcService.getRecentViolations(60),
      spcService.getStatistics(1440, selectedFurnace.value, selectedParam.value),
      spcService.getStatistics(1440, selectedFurnace.value),
      spcService.getTimeseries(selectedFurnace.value, selectedParam.value, 60)
    ])

    if (disposed) return

    baselines.value = Array.isArray(nextBaselines) ? nextBaselines : []
    violations.value = Array.isArray(nextViolations) ? nextViolations : []
    furnaceStatistics.value = nextFurnaceStatistics || {}
    furnaceTotalStatistics.value = nextFurnaceTotalStatistics || {}
    timeseries.value = Array.isArray(nextTimeseries) ? nextTimeseries : []

    if (currentBaseline.value) {
      sigmaMultiplier.value = currentBaseline.value.sigmaMultiplier ?? 1.0
    }

    updateChart()
  } catch (e) {
    if (disposed) return

    if (e.response?.status === 401) {
      handleSessionExpired()
      return
    }

    console.error('[SPC] fetch failed', e)
  } finally {
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
  router.push({ name: 'login', query: { redirect: '/spc' } })
}

function updateChart() {
  if (!chart || !currentBaseline.value) return
  const b = currentBaseline.value

  const times = timeseries.value.map(p => formatTime(p.ts))
  const points = timeseries.value.map(p => {
    const outside3sigma = p.value > b.ucl3sigma || p.value < b.lcl3sigma
    const outside2sigma = p.value > b.ucl2sigma || p.value < b.lcl2sigma

    let color = '#1890ff'
    let symbolSize = 6

    if (outside3sigma) {
      color = '#ff4d4f'
      symbolSize = 10
    } else if (outside2sigma) {
      color = '#faad14'
      symbolSize = 8
    }

    return {
      value: p.value,
      itemStyle: { color },
      symbolSize
    }
  })

  // 隨主題翻轉的顏色（讀取當前 CSS 變數）
  const cAxisLabel = cssVar('--text-1', '#8b949e')
  const cGridLine = cssVar('--bg-3', '#2a3038')
  const cMarkLabel = cssVar('--text-0', '#e6edf3')

  const option = {
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'axis',
      formatter: (params) => {
        const p = params[0]
        return `${p.axisValue}<br/>${p.marker} value: ${p.value.toFixed(3)}<br/>μ: ${b.mean.toFixed(2)}, σ: ${b.stdDev.toFixed(3)}`
      }
    },
    grid: { left: 60, right: 40, top: 30, bottom: 60 },
    xAxis: {
      type: 'category',
      data: times,
      axisLabel: { rotate: 45, color: cAxisLabel, fontSize: 10 },
      axisLine: { lineStyle: { color: cGridLine } }
    },
    yAxis: {
      type: 'value',
      min: () => Math.floor(Math.min(...timeseries.value.map(p => p.value), b.lcl3sigma) / 10) * 10,
      max: () => Math.ceil(Math.max(...timeseries.value.map(p => p.value), b.ucl3sigma) / 10) * 10,
      axisLabel: { color: cAxisLabel },
      splitLine: { lineStyle: { color: cGridLine } }
    },
    series: [
      {
        name: 'Value',
        type: 'line',
        data: points,
        symbol: 'circle',
        smooth: false,
        lineStyle: { color: '#1890ff', width: 1.5 },
        markArea: {
          silent: true,
          data: [
            [{ yAxis: b.ucl2sigma, itemStyle: { color: 'rgba(255, 77, 79, 0.08)' } }, { yAxis: b.ucl3sigma }],
            [{ yAxis: b.lcl3sigma, itemStyle: { color: 'rgba(255, 77, 79, 0.08)' } }, { yAxis: b.lcl2sigma }],
            [{ yAxis: b.ucl1sigma, itemStyle: { color: 'rgba(250, 173, 20, 0.06)' } }, { yAxis: b.ucl2sigma }],
            [{ yAxis: b.lcl2sigma, itemStyle: { color: 'rgba(250, 173, 20, 0.06)' } }, { yAxis: b.lcl1sigma }],
            [{ yAxis: b.lcl1sigma, itemStyle: { color: 'rgba(82, 196, 26, 0.05)' } }, { yAxis: b.ucl1sigma }]
          ]
        },
        markLine: {
          silent: true,
          symbol: 'none',
          label: { position: 'end', color: cMarkLabel, fontSize: 10 },
          data: [
            { yAxis: b.mean, name: 'Avg', lineStyle: { color: '#52c41a', type: 'solid', width: 2 } },
            { yAxis: b.ucl3sigma, name: 'UCL 3σ', lineStyle: { color: '#ff4d4f', type: 'dashed', width: 1.5 } },
            { yAxis: b.lcl3sigma, name: 'LCL 3σ', lineStyle: { color: '#ff4d4f', type: 'dashed', width: 1.5 } },
            { yAxis: b.ucl2sigma, name: '+2σ', lineStyle: { color: '#faad14', type: 'dashed', opacity: 0.7 } },
            { yAxis: b.lcl2sigma, name: '-2σ', lineStyle: { color: '#faad14', type: 'dashed', opacity: 0.7 } },
            { yAxis: b.ucl1sigma, name: '+1σ', lineStyle: { color: '#8b949e', type: 'dotted', opacity: 0.5 } },
            { yAxis: b.lcl1sigma, name: '-1σ', lineStyle: { color: '#8b949e', type: 'dotted', opacity: 0.5 } }
          ]
        }
      }
    ]
  }
  chart.setOption(option, true)
}

// ---- 共用的「計算中/完成」邏輯：重算 baseline 與調整 σ 都走這裡 ----

async function pollUntilDone(furnaceId) {
  const maxAttempts = 36
  for (let i = 0; i < maxAttempts; i++) {
    await new Promise(r => setTimeout(r, 5000))
    if (disposed) return
    try {
      const running = await spcService.checkFurnaceRebuildStatus(furnaceId)
      if (!running) break
    } catch (e) {
      break
    }
  }
  if (disposed) return
  if (selectedFurnace.value === furnaceId) {
    await fetchData()
    busy.value = false
    alert(`${furnaceId} 爐計算完成！`)
  }
}

async function runHeavyJob(triggerFn, furnaceId) {
  busy.value = true
  try {
    await triggerFn()
  } catch (e) {
    if (!disposed) {
      if (e.response?.status === 409) {
        alert('此爐子已有計算正在進行中，請稍候再試')
      } else {
        alert('操作失敗：' + (e.response?.data?.message || e.message))
      }
    }
    busy.value = false
    return
  }
  await pollUntilDone(furnaceId)
}

async function rebuildBaseline() {
  if (!confirm(`重算 ${selectedFurnace.value} 爐的 baseline？這會使用過去 7 天的資料重新計算，約需 1-2 分鐘。`)) return
  await runHeavyJob(
    () => spcService.rebuildFurnaceBaseline(selectedFurnace.value),
    selectedFurnace.value
  )
}

async function applySigmaMultiplier() {
  await runHeavyJob(
    () => spcService.adjustSigmaMultiplier(selectedFurnace.value, selectedParam.value, sigmaMultiplier.value),
    selectedFurnace.value
  )
}

// ---- 切換爐子：記憶 + 檢查是否有背景任務還在跑 ----

async function selectFurnace(f) {
  if (f === selectedFurnace.value) return
  selectedFurnace.value = f
  localStorage.setItem(STORAGE_KEY, f)
  await fetchData()
  try {
    const running = await spcService.checkFurnaceRebuildStatus(f)
    busy.value = running
    if (running) pollUntilDone(f)
  } catch (e) {
    busy.value = false
  }
}

watch(selectedParam, async () => {
  if (disposed) return
  await fetchData()
})

// 主題切換時，重繪圖表讓軸色/背景跟著翻轉
watch(theme, () => {
  nextTick(() => { if (isChartAlive()) updateChart() })
})

onMounted(async () => {
  disposed = false

  await nextTick()

  initChart()
  window.addEventListener('resize', safeResizeChart)

  await fetchData()

  try {
    const running = await spcService.checkFurnaceRebuildStatus(selectedFurnace.value)
    if (running) {
      busy.value = true
      pollUntilDone(selectedFurnace.value)
    }
  } catch (e) {
    // 忽略
  }

  refreshTimer = window.setInterval(fetchData, 10000)
})

onBeforeUnmount(() => {
  disposed = true

  if (refreshTimer) {
    window.clearInterval(refreshTimer)
    refreshTimer = null
  }

  window.removeEventListener('resize', safeResizeChart)

  if (chart && !chart.isDisposed?.()) {
    chart.dispose()
  }

  chart = null
})
</script>

<style scoped>
.spc-page {
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

.furnace-control-bar {
  display: flex;
  align-items: center;
  gap: 20px;
  flex-wrap: wrap;
  margin-bottom: 24px;
  padding: 12px 16px;
  background: var(--bg-1);
  border: 1px solid var(--border);
  border-radius: 8px;
}

.btn-rebuild {
  background: var(--teal);
  color: #fff;
  border: none;
  padding: 8px 16px;
  border-radius: 6px;
  cursor: pointer;
  font-size: 12px;
}
.btn-rebuild:disabled { opacity: 0.5; cursor: not-allowed; }

.sigma-control {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 11px;
  color: var(--text-2);
}
.sigma-control label {
  white-space: nowrap;
  font-family: 'JetBrains Mono', monospace;
}
.sigma-input {
  width: 60px;
  background: var(--bg-2);
  border: 1px solid var(--border);
  color: var(--text-1);
  padding: 4px 8px;
  border-radius: 4px;
  font-size: 12px;
  font-family: 'JetBrains Mono', monospace;
}
.sigma-input:focus { outline: none; border-color: var(--teal); }
.sigma-unit { font-family: 'JetBrains Mono', monospace; }
.btn-apply-sigma {
  background: var(--teal);
  color: #fff;
  border: none;
  padding: 4px 12px;
  border-radius: 4px;
  cursor: pointer;
  font-size: 11px;
}
.btn-apply-sigma:disabled { opacity: 0.5; cursor: not-allowed; }

.stats-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  gap: 12px;
  margin-bottom: 24px;
}
.stat-card {
  background: var(--bg-1);
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 14px;
}
.stat-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
}
.rule-id {
  font-family: 'JetBrains Mono', monospace;
  font-size: 11px;
  color: var(--text-2);
}
.severity {
  font-size: 10px;
  padding: 2px 6px;
  border-radius: 3px;
  font-weight: 600;
}
.severity.critical { background: var(--red); color: #fff; }
.severity.warn { background: rgba(250, 173, 20, 0.2); color: var(--amber); }
.stat-value {
  font-size: 26px;
  font-weight: 600;
  color: var(--text-1);
}
.stat-total {
  font-size: 10px;
  color: var(--text-2);
  font-family: 'JetBrains Mono', monospace;
  margin-top: 2px;
}
.stat-desc {
  font-size: 11px;
  color: var(--text-2);
  margin-top: 4px;
}

.chart-panel {
  background: var(--bg-1);
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 16px;
  margin-bottom: 24px;
}
.chart-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.chart-header h2 { font-size: 14px; margin: 0; }
.param-select {
  background: var(--bg-2);
  border: 1px solid var(--border);
  color: var(--text-1);
  padding: 6px 12px;
  border-radius: 4px;
  font-size: 12px;
}
.chart-container { height: 340px; }
.chart-legend {
  display: flex;
  justify-content: center;
  gap: 20px;
  margin-top: 8px;
  font-size: 11px;
  color: var(--text-2);
}
.legend-item { display: flex; align-items: center; gap: 6px; }
.legend-color {
  display: inline-block;
  width: 10px;
  height: 10px;
  border-radius: 2px;
}

.violation-panel {
  background: var(--bg-1);
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 16px;
}
.violation-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}
.violation-header h2 { font-size: 14px; margin: 0; }
.count-badge {
  background: var(--teal);
  color: #fff;
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 10px;
}

.violation-table { max-height: 400px; overflow-y: auto; }
.violation-table table { width: 100%; border-collapse: collapse; font-size: 12px; }
.violation-table th,
.violation-table td {
  text-align: left;
  padding: 8px 10px;
  border-bottom: 1px solid var(--border);
}
.violation-table th {
  color: var(--text-2);
  font-weight: 500;
  font-size: 11px;
  letter-spacing: 0.05em;
  text-transform: uppercase;
  background: var(--bg-2);
  position: sticky;
  top: 0;
}
.violation-table tr.critical { background: rgba(255, 77, 79, 0.06); }
.violation-table tr.warn { background: rgba(250, 173, 20, 0.03); }
.mono { font-family: 'JetBrains Mono', monospace; }
.empty { text-align: center !important; color: var(--text-2); padding: 40px !important; }

.rule-tag {
  display: inline-block;
  padding: 1px 6px;
  border-radius: 3px;
  font-size: 10px;
  color: #fff;
  font-family: 'JetBrains Mono', monospace;
  margin-right: 6px;
}
.sev-badge { padding: 2px 8px; border-radius: 3px; font-size: 10px; font-weight: 600; }
.sev-badge.critical { background: var(--red); color: #fff; }
.sev-badge.warn { background: rgba(250, 173, 20, 0.2); color: var(--amber); }
</style>
