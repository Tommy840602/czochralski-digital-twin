<template>
  <div class="spc-page">
    <!-- 頁面標題 -->
    <div class="page-header">
      <h1>SPC 統計製程管制</h1>
      <p class="page-sub">
        Self-baselining SPC · Western Electric Rules (8 Rules)
      </p>
      <button class="btn-rebuild" @click="rebuildBaseline" :disabled="rebuilding">
        {{ rebuilding ? '重算中…' : '↻ 重算 Baseline' }}
      </button>
    </div>

    <!-- 爐子選擇 tab -->
    <div class="furnace-tabs">
      <button
        v-for="f in furnaces"
        :key="f"
        :class="['tab', { active: selectedFurnace === f }]"
        @click="selectedFurnace = f"
      >{{ f }}</button>
    </div>

    <!-- 統計摘要 (8 條 Rules) -->
    <div class="stats-grid">
      <div v-for="(rule, id) in rules" :key="id" class="stat-card">
        <div class="stat-header">
          <span class="rule-id">Rule {{ id }}</span>
          <span :class="['severity', rule.severity.toLowerCase()]">{{ rule.severity }}</span>
        </div>
        <div class="stat-value">{{ furnaceStatistics[id] || 0 }}</div>
        <div class="stat-total">Total: {{ statistics[id] || 0 }}</div>
        <div class="stat-desc">{{ rule.name }}</div>
      </div>
    </div>

    <!-- 主要圖表：Shewhart 管制圖 -->
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

    <!-- 違規事件表格 -->
    <div class="violation-panel">
      <div class="violation-header">
        <h2>違規事件 (最近 60 分鐘)</h2>
        <span class="count-badge">{{ violations.length }}</span>
      </div>
      <div class="violation-table">
        <table>
          <thead>
          <tr>
            <th>時間</th>
            <th>爐</th>
            <th>參數</th>
            <th>Rule</th>
            <th>值</th>
            <th>Mean</th>
            <th>±3σ 範圍</th>
            <th>Severity</th>
          </tr>
          </thead>
          <tbody>
          <tr v-for="v in violations" :key="`${v.id}-${v.ts}`"
              :class="v.severity.toLowerCase()">
            <td>{{ formatTime(v.ts) }}</td>
            <td><strong>{{ v.furnaceId }}</strong></td>
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
          <tr v-if="!violations.length">
            <td colspan="8" class="empty">尚無違規事件</td>
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

const furnaces = FURNACES
const params = SPC_PARAMS
const rules = SPC_RULES

const selectedFurnace = ref('D1')
const selectedParam = ref('heaterTemp')
const baselines = ref([])
const violations = ref([])
const statistics = ref({})        // 全域總計（Total，不受爐子切換影響）
const furnaceStatistics = ref({}) // 目前選中爐子的統計

const timeseries = ref([])
const rebuilding = ref(false)

const chartRef = ref(null)

let chart = null
let refreshTimer = null
let disposed = false
let fetching = false

const currentBaseline = computed(() => {
  return baselines.value.find(b => b.paramName === selectedParam.value)
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

  chart = echarts.init(chartRef.value, 'dark')
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
      nextStatistics,
      nextFurnaceStatistics,
      nextTimeseries
    ] = await Promise.all([
      spcService.getBaselines(selectedFurnace.value),
      spcService.getRecentViolations(60),
      spcService.getStatistics(1440),
      spcService.getStatistics(1440, selectedFurnace.value),
      spcService.getTimeseries(selectedFurnace.value, selectedParam.value, 60)
    ])

    if (disposed) return

    baselines.value = Array.isArray(nextBaselines) ? nextBaselines : []
    violations.value = Array.isArray(nextViolations) ? nextViolations : []
    statistics.value = nextStatistics || {}
    furnaceStatistics.value = nextFurnaceStatistics || {}
    timeseries.value = Array.isArray(nextTimeseries) ? nextTimeseries : []

    updateChart()
  } catch (e) {
    if (!disposed) {
      console.error('[SPC] fetch failed', e)
    }
  } finally {
    fetching = false
  }
}

function updateChart() {
  if (!chart || !currentBaseline.value) return
  const b = currentBaseline.value

  // 當前 furnace + param 的 violations（用來標記紅點）
  const paramViolations = violations.value.filter(
    v => v.furnaceId === selectedFurnace.value && v.paramName === selectedParam.value
  )
  const violationTs = new Set(paramViolations.map(v => new Date(v.ts).getTime()))

  // 用 timeseries API 拿到的所有點畫線
  const times = timeseries.value.map(p => formatTime(p.ts))
  const points = timeseries.value.map(p => {
    const ts = new Date(p.ts).getTime()
    const isViolation = [...violationTs].some(vt => Math.abs(vt - ts) < 60000)
    const outside3sigma = p.value > b.ucl3sigma || p.value < b.lcl3sigma
    const outside2sigma = p.value > b.ucl2sigma || p.value < b.lcl2sigma

    let color = '#1890ff'  // 藍 = 正常
    let symbolSize = 6

    if (outside3sigma || isViolation) {
      color = '#ff4d4f'   // 紅 = 超規
      symbolSize = 10
    } else if (outside2sigma) {
      color = '#faad14'   // 黃 = 警戒
      symbolSize = 8
    }

    return {
      value: p.value,
      itemStyle: { color },
      symbolSize
    }
  })

  const option = {
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
      axisLabel: { rotate: 45, color: '#8b949e', fontSize: 10 },
      axisLine: { lineStyle: { color: '#2a3038' } }
    },
    yAxis: {
      type: 'value',
      scale: true,
      axisLabel: { color: '#8b949e' },
      splitLine: { lineStyle: { color: '#2a3038' } }
    },
    series: [
      {
        name: 'Value',
        type: 'line',
        data: points,
        symbol: 'circle',
        smooth: false,
        lineStyle: { color: '#1890ff', width: 1.5 },
        // Zone A (2σ-3σ 紅色警戒帶)、Zone B (1σ-2σ 黃色)、Zone C (中心 ±1σ 綠色)
        markArea: {
          silent: true,
          data: [
            // Zone A 上（+2σ ~ +3σ）
            [{ yAxis: b.ucl2sigma, itemStyle: { color: 'rgba(255, 77, 79, 0.08)' } },
              { yAxis: b.ucl3sigma }],
            // Zone A 下（-3σ ~ -2σ）
            [{ yAxis: b.lcl3sigma, itemStyle: { color: 'rgba(255, 77, 79, 0.08)' } },
              { yAxis: b.lcl2sigma }],
            // Zone B 上（+1σ ~ +2σ）
            [{ yAxis: b.ucl1sigma, itemStyle: { color: 'rgba(250, 173, 20, 0.06)' } },
              { yAxis: b.ucl2sigma }],
            // Zone B 下（-2σ ~ -1σ）
            [{ yAxis: b.lcl2sigma, itemStyle: { color: 'rgba(250, 173, 20, 0.06)' } },
              { yAxis: b.lcl1sigma }],
            // Zone C 中心（-1σ ~ +1σ）
            [{ yAxis: b.lcl1sigma, itemStyle: { color: 'rgba(82, 196, 26, 0.05)' } },
              { yAxis: b.ucl1sigma }]
          ]
        },
        // 管制線
        markLine: {
          silent: true,
          symbol: 'none',
          label: { position: 'end', color: '#e6edf3', fontSize: 10 },
          data: [
            { yAxis: b.mean, name: 'Avg',
              lineStyle: { color: '#52c41a', type: 'solid', width: 2 } },
            { yAxis: b.ucl3sigma, name: 'UCL 3σ',
              lineStyle: { color: '#ff4d4f', type: 'dashed', width: 1.5 } },
            { yAxis: b.lcl3sigma, name: 'LCL 3σ',
              lineStyle: { color: '#ff4d4f', type: 'dashed', width: 1.5 } },
            { yAxis: b.ucl2sigma, name: '+2σ',
              lineStyle: { color: '#faad14', type: 'dashed', opacity: 0.7 } },
            { yAxis: b.lcl2sigma, name: '-2σ',
              lineStyle: { color: '#faad14', type: 'dashed', opacity: 0.7 } },
            { yAxis: b.ucl1sigma, name: '+1σ',
              lineStyle: { color: '#8b949e', type: 'dotted', opacity: 0.5 } },
            { yAxis: b.lcl1sigma, name: '-1σ',
              lineStyle: { color: '#8b949e', type: 'dotted', opacity: 0.5 } }
          ]
        }
      }
    ]
  }
  chart.setOption(option, true)
}

async function rebuildBaseline() {
  if (!confirm('重算所有爐子的 baseline？這會使用過去 7 天的資料重新計算。')) {
    return
  }

  rebuilding.value = true

  try {
    await spcService.rebuildBaseline()

    if (disposed) return

    await fetchData()
    alert('Baseline 重算完成')
  } catch (e) {
    if (!disposed) {
      alert('重算失敗：' + (e.response?.data?.message || e.message))
    }
  } finally {
    rebuilding.value = false
  }
}

watch(
  [selectedFurnace, selectedParam],
  async () => {
    if (disposed) return
    await fetchData()
  }
)

onMounted(async () => {
  disposed = false

  await nextTick()

  initChart()
  window.addEventListener('resize', safeResizeChart)

  await fetchData()

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
  min-height: 100vh;
  background: var(--bg-2, #0e1116);
  color: var(--text-1, #e6edf3);
  padding: 20px 32px;
}

.page-header {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 24px;
  flex-wrap: wrap;
}
.page-header h1 { font-size: 22px; margin: 0; }
.page-sub { color: var(--text-2, #8b949e); font-size: 12px; margin: 0; }

.btn-rebuild {
  margin-left: auto;
  background: var(--teal, #1d9e75);
  color: #fff;
  border: none;
  padding: 8px 16px;
  border-radius: 6px;
  cursor: pointer;
  font-size: 12px;
}
.btn-rebuild:disabled { opacity: 0.5; cursor: not-allowed; }

.furnace-tabs {
  display: flex;
  gap: 8px;
  margin-bottom: 24px;
}
.tab {
  padding: 8px 20px;
  background: var(--bg-1, #161b22);
  border: 1px solid var(--border, #2a3038);
  border-radius: 6px;
  color: var(--text-2, #8b949e);
  cursor: pointer;
  font-family: 'JetBrains Mono', monospace;
}
.tab.active {
  border-color: var(--teal, #1d9e75);
  color: var(--teal, #1d9e75);
  background: rgba(29, 158, 117, 0.1);
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  gap: 12px;
  margin-bottom: 24px;
}
.stat-card {
  background: var(--bg-1, #161b22);
  border: 1px solid var(--border, #2a3038);
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
  color: var(--text-2, #8b949e);
}
.severity {
  font-size: 10px;
  padding: 2px 6px;
  border-radius: 3px;
  font-weight: 600;
}
.severity.critical { background: #ff4d4f; color: #fff; }
.severity.warn { background: rgba(250, 173, 20, 0.2); color: #faad14; }
.stat-value {
  font-size: 26px;
  font-weight: 600;
  color: var(--text-1, #e6edf3);
}
.stat-total {
  font-size: 10px;
  color: var(--text-2, #8b949e);
  font-family: 'JetBrains Mono', monospace;
  margin-top: 2px;
}
.stat-desc {
  font-size: 11px;
  color: var(--text-2, #8b949e);
  margin-top: 4px;
}

.chart-panel {
  background: var(--bg-1, #161b22);
  border: 1px solid var(--border, #2a3038);
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
  background: var(--bg-2, #0e1116);
  border: 1px solid var(--border, #2a3038);
  color: var(--text-1, #e6edf3);
  padding: 6px 12px;
  border-radius: 4px;
  font-size: 12px;
}
.chart-container {
  height: 340px;
}
.chart-legend {
  display: flex;
  justify-content: center;
  gap: 20px;
  margin-top: 8px;
  font-size: 11px;
  color: var(--text-2, #8b949e);
}
.legend-item { display: flex; align-items: center; gap: 6px; }
.legend-color {
  display: inline-block;
  width: 10px;
  height: 10px;
  border-radius: 2px;
}

.violation-panel {
  background: var(--bg-1, #161b22);
  border: 1px solid var(--border, #2a3038);
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
  background: var(--teal, #1d9e75);
  color: #fff;
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 10px;
}

.violation-table {
  max-height: 400px;
  overflow-y: auto;
}
.violation-table table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
}
.violation-table th,
.violation-table td {
  text-align: left;
  padding: 8px 10px;
  border-bottom: 1px solid var(--border, #2a3038);
}
.violation-table th {
  color: var(--text-2, #8b949e);
  font-weight: 500;
  font-size: 11px;
  letter-spacing: 0.05em;
  text-transform: uppercase;
  background: var(--bg-2, #0e1116);
  position: sticky;
  top: 0;
}
.violation-table tr.critical { background: rgba(255, 77, 79, 0.06); }
.violation-table tr.warn { background: rgba(250, 173, 20, 0.03); }
.mono { font-family: 'JetBrains Mono', monospace; }
.empty {
  text-align: center !important;
  color: var(--text-2, #8b949e);
  padding: 40px !important;
}

.rule-tag {
  display: inline-block;
  padding: 1px 6px;
  border-radius: 3px;
  font-size: 10px;
  color: #fff;
  font-family: 'JetBrains Mono', monospace;
  margin-right: 6px;
}
.sev-badge {
  padding: 2px 8px;
  border-radius: 3px;
  font-size: 10px;
  font-weight: 600;
}
.sev-badge.critical { background: #ff4d4f; color: #fff; }
.sev-badge.warn { background: rgba(250, 173, 20, 0.2); color: #faad14; }
</style>
