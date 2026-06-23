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

      <!-- 爐子卡片網格 -->
      <div class="grid">
        <div
          v-for="f in store.furnaces"
          :key="f.furnaceId"
          class="fcard"
          :class="{ 'fcard--off': !isLive(f.furnaceId), 'fcard--alarm': hasAlarm(f.furnaceId) }"
          @click="goTwin(f.furnaceId)"
        >
          <!-- header -->
          <div class="fcard-head">
            <span class="fid mono">{{ f.furnaceId }}</span>
            <span class="mode mono" v-if="live(f.furnaceId)?.operationMode">
              {{ live(f.furnaceId).operationMode }}
            </span>
            <span class="dot" :class="isLive(f.furnaceId) ? 'dot--live' : 'dot--off'" />
          </div>

          <!-- KPI 4 格 -->
          <div class="kpis">
            <div class="kpi" v-for="k in KPI_DEFS" :key="k.key">
              <span class="kpi-lbl mono">{{ k.label }}</span>
              <span class="kpi-val mono" :style="{ color: k.color }">
                {{ fmt(live(f.furnaceId)?.[k.key], k.dp) }}<i v-if="k.unit">{{ k.unit }}</i>
              </span>
            </div>
          </div>

          <!-- sparkline -->
          <div class="spark-wrap">
            <span class="spark-lbl mono">{{ SPARK_LABEL }}</span>
            <svg class="spark" viewBox="0 0 200 40" preserveAspectRatio="none">
              <polyline
                v-if="sparkPoints(f.furnaceId)"
                :points="sparkPoints(f.furnaceId)"
                fill="none" stroke="var(--teal)" stroke-width="1.5"
                vector-effect="non-scaling-stroke"
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
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useFurnaceStore } from '@/stores/furnaceStore.js'

const store  = useFurnaceStore()
const router = useRouter()

// ── 設定 ──────────────────────────────────────────────────
const SPARK_METRIC = 'diameter'        // ← 想換 sparkline 指標改這一行
const SPARK_LABEL  = 'DIAMETER'
const STALE_MS     = 8000              // 超過 8 秒沒更新視為離線
const MAX_PTS      = 40

const KPI_DEFS = [
  { key: 'heaterTemp', label: 'TEMP', unit: '°C',  dp: 1, color: '#f87171' },
  { key: 'diameter',   label: 'Ø',    unit: 'mm',  dp: 2, color: '#38bdf8' },
  { key: 'grMean',     label: 'GR',   unit: '',    dp: 3, color: '#34d399' },
  { key: 'bodyLength', label: 'BODY', unit: 'mm',  dp: 1, color: '#f59e0b' },
]

// ── 非 reactive buffer（避免 Chart/Proxy 遞迴），tick 觸發重繪 ──
const buffers = {}            // { [id]: number[] }   plain JS object
const tick    = ref(0)

watch(() => store.liveData, (map) => {
  for (const id in map) {
    const v = map[id]?.[SPARK_METRIC]
    if (typeof v === 'number' && isFinite(v)) {
      ;(buffers[id] ??= []).push(v)
      if (buffers[id].length > MAX_PTS) buffers[id].shift()
    }
  }
  tick.value++
})

// ── 即時數據存取（讀 tick 以保持反應性）─────────────────────
const live   = (id) => (tick.value, store.liveData[id] ?? null)
const isLive = (id) => {
  const d = (tick.value, store.liveData[id])
  return !!d && (Date.now() - (d._updatedAt ?? 0) < STALE_MS)
}

const liveCount = computed(() =>
  (tick.value, store.furnaces.filter(f => isLive(f.furnaceId)).length)
)

// 最近 30 秒內有告警的爐子 → 紅框
const alarmIds = computed(() => {
  const cut = Date.now() - 30_000
  return new Set(store.alarms.filter(a => (a._clientTs ?? 0) > cut).map(a => a.furnaceId))
})
const hasAlarm = (id) => alarmIds.value.has(id)

// ── sparkline 點位（自動縮放）──────────────────────────────
function sparkPoints(id) {
  void tick.value
  const arr = buffers[id]
  if (!arr || arr.length < 2) return ''
  const min = Math.min(...arr), max = Math.max(...arr)
  const span = max - min || 1
  const n = arr.length
  return arr.map((v, i) => {
    const x = (i / (n - 1)) * 200
    const y = 38 - ((v - min) / span) * 36   // 留 2px 上下邊
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
.dash-view {
  display: flex; flex-direction: column;
  height: 100%; overflow: hidden; background: var(--bg-0);
}

/* ── 摘要列 ─────────────────────────────────────────────── */
.summary-bar {
  display: flex; align-items: center; gap: 28px;
  padding: 12px 24px; flex-shrink: 0;
  background: var(--bg-1); border-bottom: 1px solid var(--border);
}
.sum-item { display: flex; flex-direction: column; gap: 2px; }
.sum-val { font-size: 22px; font-weight: 700; color: var(--text-0); line-height: 1; }
.sum-live  { color: var(--green); }
.sum-off   { color: var(--text-2); }
.sum-alarm { color: var(--red); }
.sum-lbl { font-size: 9px; letter-spacing: 0.14em; color: var(--text-2); text-transform: uppercase; }
.sum-spacer { flex: 1; }
.sum-clock { font-size: 13px; color: var(--text-1); }

/* ── 主體 ───────────────────────────────────────────────── */
.dash-body { flex: 1; display: flex; overflow: hidden; min-height: 0; }

.grid {
  flex: 1; overflow-y: auto; padding: 18px;
  display: grid; gap: 14px; align-content: start;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
}
.empty { grid-column: 1 / -1; text-align: center; color: var(--text-2); padding: 40px 0; }

/* ── 爐子卡片 ───────────────────────────────────────────── */
.fcard {
  background: var(--bg-1); border: 1px solid var(--border);
  border-radius: var(--radius-sm); padding: 14px;
  cursor: pointer; transition: border-color 0.15s, transform 0.1s;
  display: flex; flex-direction: column; gap: 12px;
}
.fcard:hover { border-color: var(--border-hi); transform: translateY(-1px); }
.fcard--off { opacity: 0.55; }
.fcard--alarm { border-color: rgba(248,113,113,0.5); box-shadow: 0 0 0 1px rgba(248,113,113,0.2); }

.fcard-head { display: flex; align-items: center; gap: 10px; }
.fid { font-size: 16px; font-weight: 700; color: var(--teal); }
.mode {
  font-size: 10px; padding: 2px 7px; border-radius: var(--radius-sm);
  background: var(--bg-3); color: var(--text-1); letter-spacing: 0.06em;
}
.dot { width: 8px; height: 8px; border-radius: 50%; margin-left: auto; }
.dot--live { background: var(--green); box-shadow: 0 0 6px var(--green); }
.dot--off  { background: var(--text-2); }

.kpis { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; }
.kpi {
  display: flex; flex-direction: column; gap: 2px;
  background: var(--bg-2); border: 1px solid var(--border);
  border-radius: var(--radius-sm); padding: 7px 9px;
}
.kpi-lbl { font-size: 9px; letter-spacing: 0.1em; color: var(--text-2); }
.kpi-val { font-size: 16px; font-weight: 600; line-height: 1; }
.kpi-val i { font-size: 9px; font-style: normal; color: var(--text-2); margin-left: 2px; }

.spark-wrap { display: flex; flex-direction: column; gap: 3px; }
.spark-lbl { font-size: 8px; letter-spacing: 0.12em; color: var(--text-2); }
.spark { width: 100%; height: 36px; display: block; }

.fcard-foot {
  display: flex; justify-content: space-between;
  font-size: 10px; color: var(--text-2);
}
.age { margin-left: auto; }

/* ── 告警側欄 ───────────────────────────────────────────── */
.alarm-panel {
  width: 260px; flex-shrink: 0;
  background: var(--bg-1); border-left: 1px solid var(--border);
  display: flex; flex-direction: column;
}
.ap-title {
  font-size: 10px; font-weight: 600; letter-spacing: 0.14em;
  color: var(--text-2); padding: 14px 16px 10px;
  border-bottom: 1px solid var(--border);
}
.ap-list { flex: 1; overflow-y: auto; padding: 8px; display: flex; flex-direction: column; gap: 6px; }
.ap-item {
  display: grid; grid-template-columns: auto 1fr auto; gap: 8px; align-items: baseline;
  background: var(--bg-2); border: 1px solid var(--border);
  border-left: 2px solid var(--red);
  border-radius: var(--radius-sm); padding: 7px 9px;
}
.ap-fid { font-size: 11px; font-weight: 700; color: var(--teal); }
.ap-msg { font-size: 11px; color: var(--text-1); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.ap-time { font-size: 9px; color: var(--text-2); }
.ap-empty { font-size: 11px; color: var(--text-2); text-align: center; padding: 20px 0; }
</style>
