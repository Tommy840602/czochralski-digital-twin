<template>
  <div class="rt-panel">
    <div class="rt-header">
      <span class="rt-title mono">{{ furnaceId }} 即時圖表</span>
      <span class="rt-sub mono">{{ pointCount }} pts · ~2 min</span>
    </div>

    <div v-if="!hasData" class="rt-empty mono">等待即時資料…</div>

    <div v-else class="rt-list">
      <div v-for="m in metrics" :key="m.key" class="rt-card">
        <div class="rt-card-top">
          <span class="rt-card-label mono">{{ m.label }}</span>
          <span class="rt-card-value mono" :style="{ color: m.color }">
            {{ fmt(latest(m.key), m.dec) }}<span class="rt-unit">{{ m.unit }}</span>
          </span>
        </div>
        <div class="rt-chart" :ref="el => setChartRef(m.key, el)"></div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, onBeforeUnmount, nextTick } from 'vue'
import * as echarts from 'echarts/core'
import { LineChart } from 'echarts/charts'
import {
  GridComponent, TooltipComponent
} from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { useFurnaceStore } from '@/stores/furnaceStore.js'

echarts.use([LineChart, GridComponent, TooltipComponent, CanvasRenderer])

const props = defineProps({
  furnaceId: { type: String, default: null }
})

const store = useFurnaceStore()

const MAX = 60
const MIN_INTERVAL = 1500

const metrics = [
  { key: 'heaterTemp',    label: 'Heater Temp (°C)',  unit: '°C',   dec: 1, color: '#f87171' },
  { key: 'diameter',      label: 'Diameter (mm)',     unit: 'mm',   dec: 2, color: '#38bdf8' },
  { key: 'grMean',        label: 'GR Mean (mm/m)',    unit: 'mm/m',     dec: 3, color: '#34d399' },
  { key: 'bodyLength',    label: 'Body Length (mm)',  unit: 'mm',   dec: 1, color: '#a78bfa' },
  { key: 'heaterPowerSv', label: 'Heater Power (kW)', unit: 'kW',   dec: 1, color: '#f59e0b' },
  { key: 'seedLift',      label: 'Seed Lift (mm)',     unit: 'mm',     dec: 3, color: '#fb923c' },
]

// 純物件，跟之前一樣：不放進 reactive，避免任何 proxy 介入
const buffers = {}
const lastTs = {}
const tick = ref(0)

// echarts 實例與 DOM ref（非響應式）
const chartEls = {}
const chartInstances = {}

const pad = n => String(n).padStart(2, '0')

function setChartRef(key, el) {
  if (el) chartEls[key] = el
}

function ensure(id) {
  if (!buffers[id]) {
    const b = { labels: [] }
    for (const m of metrics) b[m.key] = []
    buffers[id] = b
  }
  return buffers[id]
}

function pushPoint(id, live) {
  const b = ensure(id)
  const d = new Date()
  const label = `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
  b.labels.push(label)
  for (const m of metrics) {
    const v = parseFloat(live[m.key])
    b[m.key].push(Number.isFinite(v) ? v : null)
  }
  if (b.labels.length > MAX) {
    b.labels.shift()
    for (const m of metrics) b[m.key].shift()
  }
  tick.value++
}

watch(
  () => store.liveData,
  (map) => {
    const now = Date.now()
    for (const id in map) {
      const live = map[id]
      if (!live) continue
      if (now - (lastTs[id] ?? 0) >= MIN_INTERVAL) {
        lastTs[id] = now
        pushPoint(id, live)
      }
    }
  },
  { deep: true, immediate: true }
)

const pointCount = computed(() => {
  tick.value
  return buffers[props.furnaceId]?.labels.length ?? 0
})
const hasData = computed(() => pointCount.value > 0)

function baseOption(m) {
  return {
    grid: { left: 28, right: 8, top: 6, bottom: 6, containLabel: false },
    tooltip: {
      trigger: 'axis',
      axisPointer: {
        type: 'line',
        lineStyle: { color: m.color, width: 1, type: 'dashed' }
      },
      backgroundColor: 'rgba(8, 14, 22, 0.95)',
      borderColor: m.color,
      borderWidth: 1,
      padding: [6, 10],
      textStyle: {
        color: m.color,
        fontFamily: 'JetBrains Mono',
        fontSize: 11,
        fontWeight: 700,
      },
      formatter: (params) => {
        if (!params || !params.length) return ''
        const p = params[0]
        const v = p.data
        const val = (v == null || !isFinite(v)) ? '—'
          : `${Number(v).toFixed(m.dec)}${m.unit ? ' ' + m.unit : ''}`
        return `<span style="color:#94a3b8;font-weight:600;">${p.axisValue}</span><br/>${val}`
      },
    },
    xAxis: {
      type: 'category',
      show: false,
      data: [],
      boundaryGap: false,
    },
    yAxis: {
      type: 'value',
      scale: true,
      splitNumber: 3,
      axisLine: { show: false },
      axisTick: { show: false },
      splitLine: { lineStyle: { color: 'rgba(255,255,255,0.04)' } },
      axisLabel: {
        color: '#4a6a88',
        fontFamily: 'JetBrains Mono',
        fontSize: 9,
      },
    },
    series: [{
      type: 'line',
      smooth: 0.35,
      symbol: 'circle',
      symbolSize: 5,
      showSymbol: true,
      sampling: 'lttb',
      itemStyle: { color: m.color, borderColor: m.color },
      lineStyle: { color: m.color, width: 1.5 },
      emphasis: {
        scale: 1.6,
        itemStyle: {
          color: m.color,
          borderColor: '#fff',
          borderWidth: 1.5,
        },
      },
      areaStyle: {
        color: {
          type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
          colorStops: [
            { offset: 0, color: m.color + '55' },
            { offset: 1, color: m.color + '00' },
          ],
        },
      },
      data: [],
    }],
  }
}

function applyData() {
  const b = buffers[props.furnaceId]
  if (!b) return
  for (const m of metrics) {
    const inst = chartInstances[m.key]
    if (!inst) continue
    inst.setOption({
      xAxis: { data: [...b.labels] },
      series: [{ data: [...b[m.key]] }],
    })
  }
}

// 初始化 / 銷毀 / 視窗縮放 / props 切換
function initCharts() {
  for (const m of metrics) {
    const el = chartEls[m.key]
    if (!el || chartInstances[m.key]) continue
    const inst = echarts.init(el, null, { renderer: 'canvas' })
    inst.setOption(baseOption(m))
    chartInstances[m.key] = inst
  }
  applyData()
}

function disposeCharts() {
  for (const k in chartInstances) {
    chartInstances[k].dispose()
    delete chartInstances[k]
  }
}

function onResize() {
  for (const k in chartInstances) chartInstances[k].resize()
}

// 資料變動 → 直接 setOption，不重建實例
watch(tick, () => applyData())

// 切換爐子 → 換資料即可（圖表實例共用）
watch(() => props.furnaceId, async () => {
  await nextTick()
  applyData()
})

// hasData 從 false → true 時，DOM 才剛掛上，需要等下一個 tick 才能 init
watch(hasData, async (v) => {
  if (v) {
    await nextTick()
    initCharts()
  } else {
    disposeCharts()
  }
}, { immediate: true })

onMounted(() => {
  window.addEventListener('resize', onResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  disposeCharts()
})

function latest(key) {
  tick.value
  const b = buffers[props.furnaceId]
  if (!b || !b[key].length) return null
  for (let i = b[key].length - 1; i >= 0; i--) {
    if (b[key][i] != null) return b[key][i]
  }
  return null
}

function fmt(v, d) {
  return (v == null || !isFinite(v)) ? '—' : Number(v).toFixed(d)
}
</script>

<style scoped>
.rt-panel {
  background: rgba(8, 14, 22, 0.88);
  border: 1px solid rgba(56, 189, 248, 0.25);
  border-radius: 10px;
  padding: 12px;
  backdrop-filter: blur(12px);
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.rt-header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  padding-bottom: 8px;
  border-bottom: 1px solid rgba(56, 189, 248, 0.15);
}
.rt-title { font-size: 14px; font-weight: 700; color: #38bdf8; letter-spacing: 0.06em; }
.rt-sub   { font-size: 10px; color: #4a6a88; }

.rt-empty { font-size: 11px; color: #4a6a88; text-align: center; padding: 24px 0; }

.rt-list { display: flex; flex-direction: column; gap: 10px; }

.rt-card {
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 6px;
  padding: 8px 10px;
}
.rt-card-top {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 4px;
}
.rt-card-label { font-size: 9px; letter-spacing: 0.06em; color: #64748b; }
.rt-card-value { font-size: 14px; font-weight: 600; }
.rt-unit { font-size: 9px; color: #64748b; margin-left: 2px; }

.rt-chart { height: 64px; width: 100%; }
</style>
