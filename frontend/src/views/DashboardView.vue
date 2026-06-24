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
        <span class="sum-val mono sum-off">{{ store.furnaces.length - liveCount }}</span>
        <span class="sum-lbl">離線</span>
      </div>
      <div class="sum-item">
        <span class="sum-val mono sum-alarm">{{ store.alarms.length }}</span>
        <span class="sum-lbl">告警</span>
      </div>
      <div class="sum-spacer" />
      <div class="sum-clock mono">{{ clock }}</div>
    </div>

    <!-- ── 主體：爐子網格 + 告警 ───────────────────────── -->
    <div class="dash-body">

      <div class="grid">
        <div
          v-for="f in store.furnaces"
          :key="f.furnaceId"
          class="fcard"
          :class="{ 'fcard--off': !isLive(f.furnaceId), 'fcard--alarm': hasAlarm(f.furnaceId) }"
        >
          <!-- header（點 header / fid 進詳情）-->
          <div class="fcard-head" @click="goTwin(f.furnaceId)">
            <span class="fid mono">{{ f.furnaceId }}</span>
            <span class="mode mono" v-if="live(f.furnaceId)?.operationMode">
              {{ live(f.furnaceId).operationMode }}
            </span>
            <span class="dot" :class="isLive(f.furnaceId) ? 'dot--live' : 'dot--off'" />
          </div>

          <!-- 固定 8 主力 KPI -->
          <div class="kpis">
            <div class="kpi" v-for="k in PRIMARY" :key="k.key">
              <span class="kpi-lbl mono">{{ k.label }}</span>
              <span class="kpi-val mono" :style="{ color: k.color }">
                {{ fmt(live(f.furnaceId)?.[k.key], k.dp) }}<i v-if="k.unit">{{ k.unit }}</i>
              </span>
            </div>
          </div>

          <!-- 可選即時圖 -->
          <div class="spark-block">
            <div class="spark-head">
              <select class="spark-sel mono" v-model="sparkSel[f.furnaceId]" @click.stop>
                <option v-for="m in METRICS" :key="m.key" :value="m.key">{{ m.label }}</option>
              </select>
              <span class="spark-now mono" :style="{ color: metricColor(curKey(f.furnaceId)) }">
                {{ fmt(live(f.furnaceId)?.[curKey(f.furnaceId)], metricDp(curKey(f.furnaceId))) }}
                <i>{{ metricUnit(curKey(f.furnaceId)) }}</i>
              </span>
            </div>
            <svg class="spark" viewBox="0 0 200 48" preserveAspectRatio="none">
              <polyline
                v-if="sparkPoints(f.furnaceId, curKey(f.furnaceId))"
                :points="sparkPoints(f.furnaceId, curKey(f.furnaceId))"
                fill="none" :stroke="metricColor(curKey(f.furnaceId))"
                stroke-width="1.5" vector-effect="non-scaling-stroke"
              />
            </svg>
          </div>

          <div class="fcard-foot mono">
            <span v-if="live(f.furnaceId)?.ingotNo">INGOT {{ live(f.furnaceId).ingotNo }}</span>
            <span class="age">{{ ageText(f.furnaceId) }}</span>
          </div>
        </div>

        <div v-if="store.furnaces.length === 0" class="empty mono">尚未載入爐子…</div>
      </div>

      <!-- 告警側欄 -->
      <aside class="alarm-panel">
        <div class="ap-title mono">告警 ALARMS</div>
        <div class="ap-list">
          <div v-for="(a, i) in store.alarms.slice(0, 20)" :key="i" class="ap-item">
            <span class="ap-fid mono">{{ a.furnaceId }}</span>
            <span class="ap-msg">{{ a.message ?? a.event ?? '—' }}</span>
            <span class="ap-time mono">{{ timeText(a._clientTs) }}</span>
          </div>
          <div v-if="store.alarms.length === 0" class="ap-empty mono">無告警</div>
        </div>
      </aside>

    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, watch, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useFurnaceStore } from '@/stores/furnaceStore.js'

const store  = useFurnaceStore()
const router = useRouter()

const STALE_MS = 8000
const MAX_PTS  = 40

// ── 欄位登錄表（= liveData 可用欄位；前 8 個為主力 tile）──────
const METRICS = [
  { key: 'heaterTemp',        label: 'TEMP',     unit: '°C',   dp: 1, color: '#f87171' },
  { key: 'diameter',          label: 'Ø',        unit: 'mm',   dp: 2, color: '#38bdf8' },
  { key: 'diameterTarget',    label: 'Ø TGT',    unit: 'mm',   dp: 2, color: '#7dd3fc' },
  { key: 'grMean',            label: 'GR',       unit: 'mm/m', dp: 3, color: '#34d399' },
  { key: 'bodyLength',        label: 'BODY',     unit: 'mm',   dp: 1, color: '#f59e0b' },
  { key: 'heaterPowerSv',     label: 'PWR',      unit: 'kW',   dp: 1, color: '#fb923c' },
  { key: 'seedLift',          label: 'SEED',     unit: '',     dp: 2, color: '#a78bfa' },
  { key: 'residualWeight',    label: 'RES WT',   unit: 'kg',   dp: 1, color: '#e879f9' },
  // ── 以下為下拉可選的「其他」欄位（需 DTO 已開放）──
  { key: 'crMean',            label: 'CR',       unit: 'rpm',  dp: 2, color: '#4ade80' },
  { key: 'magnetPv',          label: 'MAGNET',   unit: '',     dp: 2, color: '#22d3ee' },
  { key: 'argonFlowRate',     label: 'ARGON',    unit: 'L/m',  dp: 1, color: '#60a5fa' },
  { key: 'lowerChamberPress', label: 'L.PRESS',  unit: '',     dp: 2, color: '#94a3b8' },
  { key: 'temp2',             label: 'TEMP2',    unit: '°C',   dp: 1, color: '#fca5a5' },
  { key: 'temp4',             label: 'TEMP4',    unit: '°C',   dp: 1, color: '#fca5a5' },
  { key: 'temp5',             label: 'TEMP5',    unit: '°C',   dp: 1, color: '#fca5a5' },
]
const PRIMARY     = METRICS.slice(0, 8)
const METRIC_KEYS = METRICS.map(m => m.key)
const METRIC_MAP  = Object.fromEntries(METRICS.map(m => [m.key, m]))
const DEFAULT_KEY = 'diameter'

const metricColor = k => METRIC_MAP[k]?.color ?? 'var(--teal)'
const metricUnit  = k => METRIC_MAP[k]?.unit  ?? ''
const metricDp    = k => METRIC_MAP[k]?.dp    ?? 2

// ── 每張卡的下拉選擇（key 字串放 reactive 安全）──────────────
const sparkSel = reactive({})
const curKey   = id => sparkSel[id] ?? DEFAULT_KEY

// ── 非 reactive buffer：buffers["<id>::<metric>"] = number[] ──
const buffers = {}
const tick    = ref(0)

watch(() => store.liveData, (map) => {
  for (const id in map) {
    if (!(id in sparkSel)) sparkSel[id] = DEFAULT_KEY        // 補預設
    const d = map[id]
    if (!d) continue
    for (const key of METRIC_KEYS) {
      const v = d[key]
      if (typeof v === 'number' && isFinite(v)) {
        const bk = id + '::' + key
        ;(buffers[bk] ??= []).push(v)
        if (buffers[bk].length > MAX_PTS) buffers[bk].shift()
      }
    }
  }
  tick.value++
})

// ── 即時數據存取（讀 tick 維持反應性）───────────────────────
const live   = id => (tick.value, store.liveData[id] ?? null)
const isLive = id => {
  const d = (tick.value, store.liveData[id])
  return !!d && (Date.now() - (d._updatedAt ?? 0) < STALE_MS)
}
const liveCount = computed(() =>
  (tick.value, store.furnaces.filter(f => isLive(f.furnaceId)).length)
)

const alarmIds = computed(() => {
  const cut = Date.now() - 30_000
  return new Set(store.alarms.filter(a => (a._clientTs ?? 0) > cut).map(a => a.furnaceId))
})
const hasAlarm = id => alarmIds.value.has(id)

// ── sparkline 點位（自動縮放）──────────────────────────────
function sparkPoints(id, key) {
  void tick.value
  const arr = buffers[id + '::' + key]
  if (!arr || arr.length < 2) return ''
  const min = Math.min(...arr), max = Math.max(...arr)
  const span = max - min || 1
  const n = arr.length
  return arr.map((v, i) => {
    const x = (i / (n - 1)) * 200
    const y = 46 - ((v - min) / span) * 44
    return `${x.toFixed(1)},${y.toFixed(1)}`
  }).join(' ')
}

// ── 格式化 ────────────────────────────────────────────────
function fmt(v, dp = 2) {
  if (v == null || !isFinite(v)) return '—'
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

// ── 時鐘 ──────────────────────────────────────────────────
const clock = ref('')
let timer = null
onMounted(() => {
  const upd = () => { clock.value = timeText(Date.now()) }
  upd(); timer = setInterval(upd, 1000)
})
onUnmounted(() => clearInterval(timer))
</script>

<style scoped>
.dash-view { display: flex; flex-direction: column; height: 100%; overflow: hidden; background: var(--bg-0); }

/* 摘要列 */
.summary-bar {
  display: flex; align-items: center; gap: 28px;
  padding: 12px 24px; flex-shrink: 0;
  background: var(--bg-1); border-bottom: 1px solid var(--border);
}
.sum-item { display: flex; flex-direction: column; gap: 2px; }
.sum-val { font-size: 22px; font-weight: 700; color: var(--text-0); line-height: 1; }
.sum-live { color: var(--green); }
.sum-off { color: var(--text-2); }
.sum-alarm { color: var(--red); }
.sum-lbl { font-size: 9px; letter-spacing: 0.14em; color: var(--text-2); text-transform: uppercase; }
.sum-spacer { flex: 1; }
.sum-clock { font-size: 13px; color: var(--text-1); }

/* 主體 */
.dash-body { flex: 1; display: flex; overflow: hidden; min-height: 0; }
.grid {
  flex: 1; overflow-y: auto; padding: 18px;
  display: grid; gap: 14px; align-content: start;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
}
.empty { grid-column: 1 / -1; text-align: center; color: var(--text-2); padding: 40px 0; }

/* 爐子卡片 */
.fcard {
  background: var(--bg-1); border: 1px solid var(--border);
  border-radius: var(--radius-sm); padding: 14px;
  transition: border-color 0.15s; display: flex; flex-direction: column; gap: 12px;
}
.fcard:hover { border-color: var(--border-hi); }
.fcard--off { opacity: 0.55; }
.fcard--alarm { border-color: rgba(248,113,113,0.5); box-shadow: 0 0 0 1px rgba(248,113,113,0.2); }

.fcard-head { display: flex; align-items: center; gap: 10px; cursor: pointer; }
.fid { font-size: 16px; font-weight: 700; color: var(--teal); }
.mode { font-size: 10px; padding: 2px 7px; border-radius: var(--radius-sm); background: var(--bg-3); color: var(--text-1); letter-spacing: 0.06em; }
.dot { width: 8px; height: 8px; border-radius: 50%; margin-left: auto; }
.dot--live { background: var(--green); box-shadow: 0 0 6px var(--green); }
.dot--off { background: var(--text-2); }

.kpis { display: grid; grid-template-columns: repeat(4, 1fr); gap: 6px; }
.kpi { display: flex; flex-direction: column; gap: 2px; background: var(--bg-2); border: 1px solid var(--border); border-radius: var(--radius-sm); padding: 6px 7px; }
.kpi-lbl { font-size: 8px; letter-spacing: 0.08em; color: var(--text-2); }
.kpi-val { font-size: 14px; font-weight: 600; line-height: 1; }
.kpi-val i { font-size: 8px; font-style: normal; color: var(--text-2); margin-left: 2px; }

/* 可選即時圖 */
.spark-block { display: flex; flex-direction: column; gap: 5px; }
.spark-head { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
.spark-sel {
  background: var(--bg-2); border: 1px solid var(--border); border-radius: var(--radius-sm);
  color: var(--text-0); padding: 3px 6px; font-size: 11px; outline: none; cursor: pointer;
}
.spark-sel:focus { border-color: var(--border-hi); }
.spark-now { font-size: 14px; font-weight: 600; }
.spark-now i { font-size: 8px; font-style: normal; color: var(--text-2); margin-left: 2px; }
.spark { width: 100%; height: 44px; display: block; }

.fcard-foot { display: flex; justify-content: space-between; font-size: 10px; color: var(--text-2); }
.age { margin-left: auto; }

/* 告警側欄 */
.alarm-panel { width: 260px; flex-shrink: 0; background: var(--bg-1); border-left: 1px solid var(--border); display: flex; flex-direction: column; }
.ap-title { font-size: 10px; font-weight: 600; letter-spacing: 0.14em; color: var(--text-2); padding: 14px 16px 10px; border-bottom: 1px solid var(--border); }
.ap-list { flex: 1; overflow-y: auto; padding: 8px; display: flex; flex-direction: column; gap: 6px; }
.ap-item { display: grid; grid-template-columns: auto 1fr auto; gap: 8px; align-items: baseline; background: var(--bg-2); border: 1px solid var(--border); border-left: 2px solid var(--red); border-radius: var(--radius-sm); padding: 7px 9px; }
.ap-fid { font-size: 11px; font-weight: 700; color: var(--teal); }
.ap-msg { font-size: 11px; color: var(--text-1); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.ap-time { font-size: 9px; color: var(--text-2); }
.ap-empty { font-size: 11px; color: var(--text-2); text-align: center; padding: 20px 0; }
</style>
