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
        <label>
          σ 寬鬆度（{{ paramLabel(selectedParam) }}
          <span v-if="currentMode" class="cur-mode">· {{ currentMode }}</span>）
        </label>
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

    <!-- 載入失敗：明確告知，不要顯示一堆 0（0 在 SPC 代表製程穩定，會誤導） -->
    <div v-if="loadError" class="load-error">
      <div class="le-title">⚠ 無法取得 SPC 資料</div>
      <div class="le-msg">{{ loadError }}</div>
      <button class="le-retry" @click="fetchData">重試</button>
    </div>

    <!-- 統計摘要 (8 條 Rules) — 只顯示目前選中爐子 -->
    <div v-if="!loadError" class="stats-grid">
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

    <div v-if="!loadError" class="chart-panel">
      <div class="chart-header">
        <h2>{{ selectedFurnace }} · {{ paramLabel(selectedParam) }}
          <span v-if="currentMode" class="chart-mode">目前階段：{{ currentMode }}</span>
        </h2>
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

    <div v-if="!loadError" class="violation-panel">
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
// 製程階段：baseline 以 (爐, 參數, 階段) 為單位，各階段分佈不同，管制界不能共用
const baselines = ref([])
const violations = ref([])
const furnaceStatistics = ref({})       // 目前參數的違規數
const furnaceTotalStatistics = ref({})  // 整爐（全部參數）的違規數
const timeseries = ref([])
const busy = ref(false)
const sigmaMultiplier = ref(1.0)
/** 載入失敗訊息。有值時整頁不顯示統計，避免把「抓不到」誤畫成「數值是 0」 */
const loadError = ref(null)

const chartRef = ref(null)

let chart = null
let refreshTimer = null
let disposed = false
let fetching = false

/**
 * 爐子「當下」的製程階段，由後端取最新一筆原始讀值（與數位孿生同源）。
 * 不能用圖上最後一個子群推斷——1 分鐘子群有延遲，跨階段那分鐘還會取樣本較多的舊 mode。
 */
const currentMode = ref('')

/** 當下階段的 baseline（非穩態階段沒有 → undefined） */
const currentBaseline = computed(() => {
  return baselines.value.find(
    b => b.paramName === selectedParam.value && b.operationMode === currentMode.value
  )
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
      nextTimeseries,
      nextCurrentMode
    ] = await Promise.all([
      spcService.getBaselines(selectedFurnace.value),
      spcService.getRecentViolations(60),
      spcService.getStatistics(1440, selectedFurnace.value, selectedParam.value),
      spcService.getStatistics(1440, selectedFurnace.value),
      spcService.getTimeseries(selectedFurnace.value, selectedParam.value, 120),
      spcService.getCurrentMode(selectedFurnace.value)
    ])

    if (disposed) return

    baselines.value = Array.isArray(nextBaselines) ? nextBaselines : []
    violations.value = Array.isArray(nextViolations) ? nextViolations : []
    furnaceStatistics.value = nextFurnaceStatistics || {}
    furnaceTotalStatistics.value = nextFurnaceTotalStatistics || {}
    timeseries.value = Array.isArray(nextTimeseries) ? nextTimeseries : []
    currentMode.value = nextCurrentMode || ''

    // σ 寬鬆度顯示「當下階段」那一組的值
    if (currentBaseline.value) {
      sigmaMultiplier.value = currentBaseline.value.sigmaMultiplier ?? 1.0
    }

    updateChart()
    loadError.value = null
  } catch (e) {
    if (disposed) return

    if (e.response?.status === 401) {
      handleSessionExpired()
      return
    }

    // 不要把「抓不到資料」畫成「數值是 0」——0 在 SPC 代表製程穩定，
    // 跟「服務還沒起來」是完全不同的意思，混在一起會嚴重誤導判讀。
    loadError.value = (e.response?.status === 404 || !e.response)
      ? '無法連線到 SPC 服務（alarm-service 可能還在啟動中，約需 1～2 分鐘）'
      : `載入失敗：${e.response?.status ?? ''} ${e.message}`
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
  if (!chart) return

  const ts = timeseries.value
  if (!ts.length) {
    chart.clear()
    return
  }

  const times = ts.map(p => formatTime(p.ts))

  // 每個點用「它自己所處階段」的管制界判定顏色
  const points = ts.map(p => {
    let color = '#1890ff'
    let symbolSize = 6
    if (p.ucl3sigma != null) {
      if (p.value > p.ucl3sigma || p.value < p.lcl3sigma) {
        color = '#ff4d4f'; symbolSize = 10
      } else if (p.value > p.ucl2sigma || p.value < p.lcl2sigma) {
        color = '#faad14'; symbolSize = 8
      }
    }
    return { value: p.value, itemStyle: { color }, symbolSize }
  })

  // 管制界畫成階梯線：階段一換就跳到該階段的值；非穩態階段沒有 baseline → 該段斷開
  const limitSeries = (key, name, color, type, width, opacity) => ({
    name,
    type: 'line',
    step: 'middle',
    data: ts.map(p => p[key] ?? null),
    symbol: 'none',
    connectNulls: false,
    silent: true,
    lineStyle: { color, type, width, opacity: opacity ?? 1 },
    z: 1
  })

  const cAxisLabel = cssVar('--text-1', '#8b949e')
  const cGridLine = cssVar('--bg-3', '#2a3038')

  const all = []
  for (const p of ts) {
    if (p.value != null) all.push(p.value)
    if (p.ucl3sigma != null) all.push(p.ucl3sigma, p.lcl3sigma)
  }
  const lo = Math.min(...all), hi = Math.max(...all)
  const pad = (hi - lo) * 0.08 || 1

  const option = {
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'axis',
      formatter: (params) => {
        const p = ts[params[0].dataIndex]
        const lim = p.ucl3sigma != null
          ? `<br/>μ: ${p.mean.toFixed(2)}　±3σ: ${p.lcl3sigma.toFixed(2)} ~ ${p.ucl3sigma.toFixed(2)}`
          : '<br/><span style="opacity:.6">此階段為非穩態，無管制界</span>'
        return `${params[0].axisValue}`
          + `<br/><b>${p.mode ?? '—'}</b>`
          + `<br/>value: <b>${p.value.toFixed(3)}</b>${lim}`
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
      min: +(lo - pad).toFixed(3),
      max: +(hi + pad).toFixed(3),
      axisLabel: { color: cAxisLabel },
      splitLine: { lineStyle: { color: cGridLine } }
    },
    series: [
      limitSeries('ucl3sigma', 'UCL 3σ', '#ff4d4f', 'dashed', 1.5),
      limitSeries('lcl3sigma', 'LCL 3σ', '#ff4d4f', 'dashed', 1.5),
      limitSeries('ucl2sigma', '+2σ', '#faad14', 'dashed', 1, 0.7),
      limitSeries('lcl2sigma', '-2σ', '#faad14', 'dashed', 1, 0.7),
      limitSeries('mean', 'Mean', '#52c41a', 'solid', 2),
      {
        name: 'Value',
        type: 'line',
        data: points,
        symbol: 'circle',
        smooth: false,
        lineStyle: { color: '#1890ff', width: 1.5 },
        z: 3
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

/** σ 寬鬆度調整「當下階段」那一組 baseline */
async function applySigmaMultiplier() {
  if (!currentMode.value) return
  await runHeavyJob(
    () => spcService.adjustSigmaMultiplier(
      selectedFurnace.value, selectedParam.value, currentMode.value, sigmaMultiplier.value),
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


.load-error {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 48px 24px;
  background: var(--bg-1);
  border: 1px solid var(--border);
  border-left: 3px solid var(--amber);
  border-radius: 8px;
  margin-bottom: 24px;
}
.le-title { font-size: 15px; font-weight: 600; color: var(--amber); }
.le-msg   { font-size: 12px; color: var(--text-2); text-align: center; }
.le-retry {
  margin-top: 6px;
  padding: 6px 18px;
  background: var(--bg-2);
  border: 1px solid var(--border);
  border-radius: 4px;
  color: var(--text-1);
  font-size: 12px;
  cursor: pointer;
}
.le-retry:hover { border-color: var(--teal); color: var(--teal); }


.chart-mode {
  color: var(--teal);
  font-family: 'JetBrains Mono', monospace;
  font-size: 12px;
  margin-left: 6px;
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
