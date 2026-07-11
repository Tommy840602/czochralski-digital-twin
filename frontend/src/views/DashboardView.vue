<template>
  <div class="dash-view">
    <!-- ── 頂部摘要列 ──────────────────────────────────── -->
    <div class="summary-bar">
      <div class="sum-item">
        <span class="sum-val mono">{{ store.furnaces.length }}</span>
        <span class="sum-lbl">總爐數</span>
      </div>

      <div class="sum-item">
        <span class="sum-val mono sum-live">{{ liveCount }}</span>
        <span class="sum-lbl">即時連線</span>
      </div>

      <div class="sum-item">
        <span class="sum-val mono sum-off">
          {{ store.furnaces.length - liveCount }}
        </span>
        <span class="sum-lbl">離線</span>
      </div>

      <div class="sum-hint mono" v-if="anyOverlay">
        ⠿ 拖曳卡片把手到另一張卡可疊圖比較
      </div>

      <div class="sum-spacer" />
      <div class="sum-clock mono">{{ clock }}</div>
    </div>

    <!-- ── 主體：爐子網格 ───────────────────────── -->
    <div class="dash-body">
      <div class="grid">
        <div
          v-for="f in visibleFurnaces"
          :key="f.furnaceId"
          class="fcard"
          :class="{
            'fcard--off': !isLive(f.furnaceId),
            'fcard--dropping': dropTarget === f.furnaceId,
            'fcard--dragging': dragSrc === f.furnaceId,
          }"
          @dragover.prevent="onDragOver($event, f.furnaceId)"
          @dragleave="onDragLeave(f.furnaceId)"
          @drop="onDrop($event, f.furnaceId)"
        >
          <!-- header -->
          <div class="fcard-head">
            <span
              class="drag-handle"
              draggable="true"
              title="拖曳到另一張卡疊圖"
              @dragstart="onDragStart($event, f.furnaceId)"
              @dragend="onDragEnd"
              @click.stop
            >
              ⠿
            </span>

            <span class="fid mono" @click="goTwin(f.furnaceId)">
              {{ f.furnaceId }}
            </span>

            <span class="mode mono" v-if="live(f.furnaceId)?.operationMode">
              {{ live(f.furnaceId).operationMode }}
            </span>

            <span
              class="dot"
              :class="isLive(f.furnaceId) ? 'dot--live' : 'dot--off'"
            />
          </div>

          <!-- 固定 8 主力 KPI -->
          <div class="kpis">
            <div class="kpi" v-for="k in PRIMARY" :key="k.key">
              <span class="kpi-lbl mono">{{ k.label }}</span>
              <span class="kpi-val mono" :style="{ color: k.color }">
                {{ fmt(mval(f.furnaceId, k.key), k.dp) }}
                <i v-if="k.unit">{{ k.unit }}</i>
              </span>
            </div>
          </div>

          <!-- 可選即時圖：ECharts + 雙 Y 軸 + 疊加爐獨立指標 -->
          <div class="spark-block">
            <div class="spark-head">
              <select
                class="spark-sel mono"
                v-model="sparkSel[f.furnaceId]"
                @click.stop
              >
                <option
                  v-for="m in METRICS"
                  :key="m.key"
                  :value="m.key"
                >
                  {{ m.label }}
                </option>
              </select>

              <span
                class="spark-now mono"
                :style="{ color: metricColor(curKey(f.furnaceId)) }"
              >
                {{ fmt(mval(f.furnaceId, curKey(f.furnaceId)), metricDp(curKey(f.furnaceId))) }}
                <i>{{ metricUnit(curKey(f.furnaceId)) }}</i>
              </span>
            </div>

            <div class="axis-panel">
              <div class="axis-row">
                <span class="axis-title mono">LEFT Y</span>

                <input
                  class="axis-input mono"
                  v-model="ensureAxisRange(f.furnaceId).leftMin"
                  placeholder="min"
                  inputmode="decimal"
                  @click.stop
                />

                <input
                  class="axis-input mono"
                  v-model="ensureAxisRange(f.furnaceId).leftMax"
                  placeholder="max"
                  inputmode="decimal"
                  @click.stop
                />

                <span class="axis-title mono">RIGHT Y</span>

                <input
                  class="axis-input mono"
                  v-model="ensureAxisRange(f.furnaceId).rightMin"
                  placeholder="min"
                  inputmode="decimal"
                  @click.stop
                />

                <input
                  class="axis-input mono"
                  v-model="ensureAxisRange(f.furnaceId).rightMax"
                  placeholder="max"
                  inputmode="decimal"
                  @click.stop
                />

                <button
                  class="expand-btn mono"
                  type="button"
                  @click.stop="expandedTargetId = f.furnaceId"
                >
                  放大
                </button>
              </div>
            </div>

            <FurnaceOverlayChart
              :target-id="f.furnaceId"
              :overlay-ids="overlays[f.furnaceId] || []"
              :target-metric="curKey(f.furnaceId)"
              :overlay-metric-map="overlaySparkSel[f.furnaceId] || {}"
              :metric-map="METRIC_MAP"
              :buffers="buffers"
              :axis-range="ensureAxisRange(f.furnaceId)"
              :furnace-color="furnaceColor"
              :revision="tick"
            />

            <!-- 疊圖圖例：主爐 + 疊加爐各自可選指標 -->
            <div class="legend" v-if="(overlays[f.furnaceId] || []).length">
              <span
                class="lg"
                :style="{ color: furnaceColor(f.furnaceId) }"
              >
                ● {{ f.furnaceId }} / {{ METRIC_MAP[curKey(f.furnaceId)]?.label }}
              </span>

              <span
                class="lg lg-edit"
                v-for="oid in overlays[f.furnaceId]"
                :key="oid"
                :style="{ color: furnaceColor(oid) }"
              >
                ● {{ oid }}

                <select
                  class="overlay-sel mono"
                  v-model="overlaySparkSel[f.furnaceId][oid]"
                  @click.stop
                >
                  <option
                    v-for="m in METRICS"
                    :key="m.key"
                    :value="m.key"
                  >
                    {{ m.label }}
                  </option>
                </select>

                <button
                  class="lg-x"
                  type="button"
                  @click.stop="removeOverlay(f.furnaceId, oid)"
                >
                  ✕
                </button>
              </span>
            </div>
          </div>

          <div class="fcard-foot mono">
            <span v-if="live(f.furnaceId)?.ingotNo">
              INGOT {{ live(f.furnaceId).ingotNo }}
            </span>
            <span class="age">{{ ageText(f.furnaceId) }}</span>
          </div>
        </div>

        <div v-if="visibleFurnaces.length === 0" class="empty mono">
          尚未載入爐子…
        </div>
      </div>
    </div>

    <FurnaceOverlayChartModal
      v-if="expandedTargetId"
      :target-id="expandedTargetId"
      :overlay-ids="overlays[expandedTargetId] || []"
      :target-metric="curKey(expandedTargetId)"
      :overlay-metric-map="overlaySparkSel[expandedTargetId] || {}"
      :metric-map="METRIC_MAP"
      :buffers="buffers"
      :axis-range="ensureAxisRange(expandedTargetId)"
      :furnace-color="furnaceColor"
      :revision="tick"
      @close="expandedTargetId = null"
    />
  </div>
</template>

<script setup>
import { ref, reactive, computed, watch, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useFurnaceStore } from '@/stores/furnaceStore.js'
import FurnaceOverlayChart from '@/components/FurnaceOverlayChart.vue'
import FurnaceOverlayChartModal from '@/components/FurnaceOverlayChartModal.vue'

const store = useFurnaceStore()
const router = useRouter()

const STALE_MS = 8000
const MAX_PTS = 120

// ── 欄位登錄表：前 8 = 主力 tile；其餘下拉可選 ───────────────
const METRICS = [
  { key: 'heaterTemp', label: 'TEMP', unit: '°C', dp: 1, color: '#f87171' },
  { key: 'diameter', label: 'Ø', unit: 'mm', dp: 2, color: '#38bdf8' },
  { key: 'diameterTarget', label: 'Ø TGT', unit: 'mm', dp: 2, color: '#7dd3fc' },
  { key: 'grMean', label: 'GR', unit: 'mm/m', dp: 3, color: '#34d399' },
  { key: 'bodyLength', label: 'BODY', unit: 'mm', dp: 1, color: '#f59e0b' },
  { key: 'heaterPowerSv', label: 'PWR', unit: 'kW', dp: 1, color: '#fb923c' },
  { key: 'seedLift', label: 'SEED', unit: '', dp: 2, color: '#a78bfa' },
  { key: 'residualWeight', label: 'RES WT', unit: 'kg', dp: 1, color: '#e879f9' },

  { key: 'dMean', label: 'D MEAN', unit: 'mm', dp: 2, color: '#60a5fa' },
  { key: 'heaterTempTarget', label: 'TEMP TGT', unit: '°C', dp: 1, color: '#fca5a5' },
  { key: 'htMean', label: 'HT MEAN', unit: '°C', dp: 1, color: '#fca5a5' },
  { key: 'temp2', label: 'TEMP2', unit: '°C', dp: 1, color: '#fca5a5' },
  { key: 'temp4', label: 'TEMP4', unit: '°C', dp: 1, color: '#fca5a5' },
  { key: 'temp5', label: 'TEMP5', unit: '°C', dp: 1, color: '#fca5a5' },
  { key: 'temp9', label: 'TEMP9', unit: '°C', dp: 1, color: '#fca5a5' },
  { key: 'temp29', label: 'TEMP29', unit: '°C', dp: 1, color: '#fca5a5' },

  { key: 'neckLengthAccum', label: 'NECK', unit: 'mm', dp: 1, color: '#c4b5fd' },
  { key: 'seedLiftSp', label: 'SEED SP', unit: '', dp: 2, color: '#c4b5fd' },
  { key: 'seedLiftTarget', label: 'SEED TGT', unit: '', dp: 2, color: '#c4b5fd' },
  { key: 'seedRotationSp', label: 'SEED ROT', unit: 'rpm', dp: 2, color: '#c4b5fd' },

  { key: 'crucibleRotationSp', label: 'CRU ROT', unit: 'rpm', dp: 2, color: '#4ade80' },
  { key: 'crMean', label: 'CR', unit: 'rpm', dp: 2, color: '#4ade80' },
  { key: 'crucibleLift', label: 'CRU LIFT', unit: 'mm', dp: 2, color: '#4ade80' },
  { key: 'crucibleLiftRatio', label: 'CRU R', unit: '', dp: 3, color: '#4ade80' },
  { key: 'cruciblePosition', label: 'CRU POS', unit: 'mm', dp: 2, color: '#4ade80' },
  { key: 'cruciblePosCalibrated', label: 'CRU CAL', unit: 'mm', dp: 2, color: '#4ade80' },

  { key: 'ctpflPul', label: 'CTPFL', unit: '', dp: 2, color: '#94a3b8' },
  { key: 'magnetPv', label: 'MAGNET', unit: '', dp: 2, color: '#22d3ee' },
  { key: 'argonFlowRate', label: 'ARGON', unit: 'L/m', dp: 1, color: '#60a5fa' },
  { key: 'lowerChamberPress', label: 'L.PRESS', unit: '', dp: 2, color: '#94a3b8' },
  { key: 'lowerChamberPressSp', label: 'L.P SP', unit: '', dp: 2, color: '#94a3b8' },
  { key: 'throValveOpen', label: 'THRO V', unit: '%', dp: 1, color: '#94a3b8' },
  { key: 'bpMean', label: 'BP MEAN', unit: '', dp: 2, color: '#94a3b8' },
  { key: 'bpu60mean', label: 'BPU60', unit: '', dp: 2, color: '#94a3b8' },
  { key: 'btplBpul1', label: 'BTPL UL', unit: '', dp: 2, color: '#94a3b8' },
  { key: 'btplBpll1', label: 'BTPL LL', unit: '', dp: 2, color: '#94a3b8' },
  { key: 'pidslDdmean', label: 'PID DD', unit: '', dp: 3, color: '#94a3b8' },
  { key: 'pidslTemp1', label: 'PID T1', unit: '°C', dp: 1, color: '#94a3b8' },
]

const PRIMARY = METRICS.slice(0, 8)
const METRIC_KEYS = METRICS.map(m => m.key)
const METRIC_MAP = Object.fromEntries(METRICS.map(m => [m.key, m]))
const DEFAULT_KEY = 'diameter'

const metricColor = key => METRIC_MAP[key]?.color ?? 'var(--teal)'
const metricUnit = key => METRIC_MAP[key]?.unit ?? ''
const metricDp = key => METRIC_MAP[key]?.dp ?? 2

// 每爐固定顏色：疊圖用
const FCOLORS = [
  '#38bdf8',
  '#34d399',
  '#f59e0b',
  '#f87171',
  '#a78bfa',
  '#22d3ee',
  '#fb923c',
  '#e879f9',
]

function furnaceColor(id) {
  const i = store.furnaces.findIndex(f => f.furnaceId === id)
  return FCOLORS[(i < 0 ? 0 : i) % FCOLORS.length]
}

// ── 每張卡的主爐指標 ─────────────────────────────────────
const sparkSel = reactive({})
const curKey = id => sparkSel[id] ?? DEFAULT_KEY

// ── 疊圖狀態：overlays[目標爐] = [來源爐, ...] ──────────────
const overlays = reactive({})

// 疊加爐獨立指標：overlaySparkSel[targetId][overlayId] = metricKey
const overlaySparkSel = reactive({})

// 雙 Y 軸範圍：axisRange[targetId] = { leftMin, leftMax, rightMin, rightMax }
const axisRange = reactive({})

// 放大檢視
const expandedTargetId = ref(null)

const anyOverlay = computed(() => store.furnaces.length > 1)

function ensureOverlayMetricState(targetId, overlayId) {
  if (!overlaySparkSel[targetId]) {
    overlaySparkSel[targetId] = {}
  }

  if (!overlaySparkSel[targetId][overlayId]) {
    overlaySparkSel[targetId][overlayId] = curKey(targetId)
  }
}

function ensureAxisRange(targetId) {
  if (!axisRange[targetId]) {
    axisRange[targetId] = {
      leftMin: '',
      leftMax: '',
      rightMin: '',
      rightMax: '',
    }
  }

  return axisRange[targetId]
}

// 被疊加合併走的爐子，不再渲染自己的卡
const mergedIds = computed(() => {
  const s = new Set()

  for (const targetId in overlays) {
    for (const id of overlays[targetId]) {
      s.add(id)
    }
  }

  return s
})

const visibleFurnaces = computed(() => {
  return store.furnaces.filter(f => !mergedIds.value.has(f.furnaceId))
})

// ── 拖曳 ────────────────────────────────────────────────
const dragSrc = ref(null)
const dropTarget = ref(null)

function onDragStart(e, id) {
  dragSrc.value = id
  e.dataTransfer.setData('text/plain', id)
  e.dataTransfer.effectAllowed = 'copy'
}

function onDragEnd() {
  dragSrc.value = null
  dropTarget.value = null
}

function onDragOver(e, id) {
  if (dragSrc.value && dragSrc.value !== id) {
    e.dataTransfer.dropEffect = 'copy'
    dropTarget.value = id
  }
}

function onDragLeave(id) {
  if (dropTarget.value === id) {
    dropTarget.value = null
  }
}

function onDrop(e, targetId) {
  const src = e.dataTransfer.getData('text/plain') || dragSrc.value

  dropTarget.value = null
  dragSrc.value = null

  if (!src || src === targetId) return

  const list = overlays[targetId] || (overlays[targetId] = [])

  if (!list.includes(src)) {
    list.push(src)
    ensureOverlayMetricState(targetId, src)
  }

  // 如果 src 本身也是某張疊圖目標，把它的孩子攤平搬到 targetId
  if (overlays[src]) {
    for (const child of overlays[src]) {
      if (child !== targetId && !list.includes(child)) {
        list.push(child)
        ensureOverlayMetricState(targetId, child)
      }
    }

    delete overlays[src]
    delete overlaySparkSel[src]
  }

  ensureAxisRange(targetId)
}

function removeOverlay(targetId, oid) {
  const list = overlays[targetId]
  if (!list) return

  const i = list.indexOf(oid)
  if (i >= 0) {
    list.splice(i, 1)
  }

  if (overlaySparkSel[targetId]) {
    delete overlaySparkSel[targetId][oid]
  }

  if (list.length === 0) {
    delete overlays[targetId]
    delete overlaySparkSel[targetId]
  }
  tick.value++
}

// ── 非 reactive buffer：buffers["<id>::<metric>"] = number[] ──
// 注意：這裡故意維持普通物件，避免大量即時資料造成深層 reactive 開銷。
const buffers = {}
const tick = ref(0)

function parseMetricValue(raw) {
  if (typeof raw === 'number') {
    return Number.isFinite(raw) ? raw : NaN
  }

  if (raw === null || raw === undefined || raw === '') {
    return NaN
  }

  const v = Number(raw)
  return Number.isFinite(v) ? v : NaN
}

function mval(id, key) {
  const d = (tick.value, store.liveData[id])
  if (!d) return null

  const raw = d.metrics?.[key] ?? d[key]
  const v = parseMetricValue(raw)

  return Number.isFinite(v) ? v : null
}

watch(
  () => store.liveData,
  map => {
    for (const id in map) {
      if (!(id in sparkSel)) {
        sparkSel[id] = DEFAULT_KEY
      }

      ensureAxisRange(id)

      const d = map[id]
      if (!d) continue

      for (const key of METRIC_KEYS) {
        const raw = d.metrics?.[key] ?? d[key]
        const v = parseMetricValue(raw)

        if (Number.isFinite(v)) {
          const bk = `${id}::${key}`

          if (!buffers[bk]) {
            buffers[bk] = []
          }

          buffers[bk].push(v)

          if (buffers[bk].length > MAX_PTS) {
            buffers[bk].shift()
          }
        }
      }
    }

    tick.value++
  },
  {
    deep: true,
    flush: 'post',
  },
)

// ── 即時數據存取 ─────────────────────────────────────────
const live = id => (tick.value, store.liveData[id] ?? null)

const isLive = id => {
  const d = (tick.value, store.liveData[id])
  return Boolean(d && Date.now() - (d._updatedAt ?? 0) < STALE_MS)
}

const liveCount = computed(() => {
  return (tick.value, store.furnaces.filter(f => isLive(f.furnaceId)).length)
})

// ── 格式化 ───────────────────────────────────────────────
function fmt(v, dp = 2) {
  if (v === null || v === undefined || !Number.isFinite(Number(v))) {
    return '—'
  }

  return Number(v).toFixed(dp)
}

function ageText(id) {
  const d = (tick.value, store.liveData[id])
  if (!d?._updatedAt) return ''

  const s = Math.floor((Date.now() - d._updatedAt) / 1000)
  return s < 1 ? 'now' : `${s}s ago`
}

function timeText(ts) {
  if (!ts) return ''

  const d = new Date(ts)
  const p = n => String(n).padStart(2, '0')

  return `${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
}

function goTwin(id) {
  store.selectFurnace(id)
  router.push('/')
}

// ── 時鐘 ────────────────────────────────────────────────
const clock = ref('')
let timer = null

onMounted(() => {
  const upd = () => {
    clock.value = timeText(Date.now())
  }

  upd()
  timer = window.setInterval(upd, 1000)
})

onUnmounted(() => {
  if (timer) {
    window.clearInterval(timer)
  }
})
</script>

<style scoped>
.dash-view {
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
  background: var(--bg-0);
}

/* 摘要列 */
.summary-bar {
  display: flex;
  align-items: center;
  gap: 28px;
  padding: 12px 24px;
  flex-shrink: 0;
  background: var(--bg-1);
  border-bottom: 1px solid var(--border);
}

.sum-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.sum-val {
  font-size: 22px;
  font-weight: 700;
  color: var(--text-0);
  line-height: 1;
}

.sum-live {
  color: var(--green);
}

.sum-off {
  color: var(--text-2);
}

.sum-lbl {
  font-size: 9px;
  letter-spacing: 0.14em;
  color: var(--text-2);
  text-transform: uppercase;
}

.sum-hint {
  font-size: 11px;
  color: var(--text-2);
}

.sum-spacer {
  flex: 1;
}

.sum-clock {
  font-size: 13px;
  color: var(--text-1);
}

/* 主體 */
.dash-body {
  flex: 1;
  display: flex;
  overflow: hidden;
  min-height: 0;
}

.grid {
  flex: 1;
  overflow-y: auto;
  padding: 18px;
  display: grid;
  gap: 14px;
  align-content: start;
  grid-template-columns: repeat(auto-fill, minmax(330px, 1fr));
}

.empty {
  grid-column: 1 / -1;
  text-align: center;
  color: var(--text-2);
  padding: 40px 0;
}

/* 爐子卡片 */
.fcard {
  background: var(--bg-1);
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  padding: 14px;
  transition:
    border-color 0.15s,
    box-shadow 0.15s,
    opacity 0.15s;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.fcard:hover {
  border-color: var(--border-hi);
}

.fcard--off {
  opacity: 0.55;
}

.fcard--dragging {
  opacity: 0.45;
}

.fcard--dropping {
  border-color: var(--teal);
  box-shadow: 0 0 0 2px var(--teal);
}

.fcard-head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.drag-handle {
  cursor: grab;
  color: var(--text-2);
  font-size: 14px;
  line-height: 1;
  padding: 2px 2px;
  user-select: none;
  letter-spacing: -2px;
}

.drag-handle:hover {
  color: var(--text-0);
}

.drag-handle:active {
  cursor: grabbing;
}

.fid {
  font-size: 16px;
  font-weight: 700;
  color: var(--teal);
  cursor: pointer;
}

.mode {
  font-size: 10px;
  padding: 2px 7px;
  border-radius: var(--radius-sm);
  background: var(--bg-3);
  color: var(--text-1);
  letter-spacing: 0.06em;
}

.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin-left: auto;
}

.dot--live {
  background: var(--green);
  box-shadow: 0 0 6px var(--green);
}

.dot--off {
  background: var(--text-2);
}

/* KPI */
.kpis {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 6px;
}

.kpi {
  display: flex;
  flex-direction: column;
  gap: 2px;
  background: var(--bg-2);
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  padding: 6px 7px;
}

.kpi-lbl {
  font-size: 8px;
  letter-spacing: 0.08em;
  color: var(--text-2);
}

.kpi-val {
  font-size: 14px;
  font-weight: 600;
  line-height: 1;
}

.kpi-val i {
  font-size: 8px;
  font-style: normal;
  color: var(--text-2);
  margin-left: 2px;
}

/* ECharts 即時圖 */
.spark-block {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.spark-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.spark-sel {
  background: var(--bg-2);
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  color: var(--text-0);
  padding: 3px 6px;
  font-size: 11px;
  outline: none;
  cursor: pointer;
  max-width: 130px;
}

.spark-sel:focus {
  border-color: var(--border-hi);
}

.spark-now {
  font-size: 14px;
  font-weight: 600;
}

.spark-now i {
  font-size: 8px;
  font-style: normal;
  color: var(--text-2);
  margin-left: 2px;
}

/* Y 軸設定 */
.axis-panel {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.axis-row {
  display: flex;
  align-items: center;
  gap: 5px;
  flex-wrap: wrap;
}

.axis-title {
  font-size: 9px;
  color: var(--text-2);
  letter-spacing: 0.08em;
}

.axis-input {
  width: 48px;
  height: 22px;
  background: var(--bg-2);
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  color: var(--text-0);
  font-size: 10px;
  padding: 2px 5px;
  outline: none;
}

.axis-input:focus {
  border-color: var(--border-hi);
}

.expand-btn {
  margin-left: auto;
  height: 22px;
  padding: 0 8px;
  background: var(--bg-3);
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  color: var(--text-1);
  font-size: 10px;
  cursor: pointer;
}

.expand-btn:hover {
  color: var(--teal);
  border-color: var(--teal);
}

/* 疊圖圖例 */
.legend {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding-top: 2px;
}

.lg {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  font-size: 10px;
  font-weight: 600;
}

.lg-edit {
  gap: 5px;
}

.overlay-sel {
  max-width: 92px;
  height: 22px;
  background: var(--bg-2);
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  color: var(--text-0);
  font-size: 10px;
  padding: 1px 4px;
  outline: none;
}

.overlay-sel:focus {
  border-color: var(--border-hi);
}

.lg-x {
  background: none;
  border: none;
  color: var(--text-2);
  cursor: pointer;
  font-size: 9px;
  padding: 0 2px;
  line-height: 1;
  margin-left: 1px;
}

.lg-x:hover {
  color: var(--red);
}

.fcard-foot {
  display: flex;
  justify-content: space-between;
  font-size: 10px;
  color: var(--text-2);
}

.age {
  margin-left: auto;
}

</style>
