<template>
  <div class="rv">

    <!-- 頂部列 -->
    <div class="rv-bar">
      <div class="rv-titles">
        <span class="rv-title mono">AI 分析報告</span>
        <span class="rv-sub mono">依《NG分析注意事項》就即時快照生成</span>
      </div>
      <div class="rv-bar-right">
        <span class="rv-progress mono">{{ doneCount }}/{{ ids.length }} 完成</span>
        <span v-if="cooldownLabel" class="cooldown">{{ cooldownLabel }}</span>
        <button class="rv-btn" :disabled="doneCount === 0 || downloadingAll" @click="downloadAll">
          {{ downloadingAll ? '打包中…' : '下載全部 .docx' }}
        </button>
        <button class="rv-btn ghost" :disabled="anyLoading" @click="generateAll">全部重新生成</button>
      </div>
    </div>

    <!-- 爐子 tab -->
    <div class="rv-tabs">
      <button
        v-for="id in ids" :key="id"
        class="rv-tab mono"
        :class="{ active: selected === id }"
        @click="selected = id"
      >
        {{ id }}
        <span class="tab-dot"
              :class="{
            'd-load': reports[id]?.status === 'loading',
            'd-ok':   reports[id]?.status === 'done',
            'd-err':  reports[id]?.status === 'error',
          }" />
      </button>
    </div>

    <!-- 報告主體 -->
    <div class="rv-body">
      <div v-if="!cur" class="rv-state mono">尚未載入爐子…</div>

      <div v-else-if="cur.status === 'loading'" class="rv-state mono">
        <span class="spinner" /> {{ selected }} AI 分析中…
      </div>

      <div v-else-if="cur.status === 'error'" class="rv-state rv-err mono">
        ⚠ {{ cur.error }}
        <button class="rv-btn ghost" @click="generateOne(selected)">重試</button>
      </div>

      <div v-else-if="cur.status === 'done'" class="rv-report">
        <div class="rep-head">
          <div class="rep-meta mono">
            <span>爐 {{ cur.data.furnaceId }}</span>
            <span>INGOT {{ cur.data.ingotNo }}</span>
            <span>階段 {{ cur.data.stage }}</span>
            <span class="verdict" :class="verdictClass(cur.data.verdict)">{{ cur.data.verdict }}</span>
          </div>
          <div class="rep-actions">
            <button class="rv-btn" :disabled="downloading === selected" @click="downloadDocx(selected)">
              {{ downloading === selected ? '產生中…' : '下載 .docx' }}
            </button>
            <button class="rv-btn ghost" @click="generateOne(selected)">重新生成</button>
          </div>
        </div>

        <p class="rep-summary">{{ cur.data.summary }}</p>

        <section v-for="(s, i) in cur.data.sections" :key="i" class="rep-sec">
          <h3 class="rep-h">{{ s.heading }}</h3>
          <p class="rep-p">{{ s.content }}</p>
        </section>

        <div v-if="cur.data.keyPoints?.length" class="rep-sec">
          <h3 class="rep-h">關鍵數值</h3>
          <table class="rep-table mono">
            <thead><tr><th>參數</th><th>數值</th><th>判讀</th></tr></thead>
            <tbody>
            <tr v-for="(k, i) in cur.data.keyPoints" :key="i">
              <td>{{ k.param }}</td><td>{{ k.value }}</td><td>{{ k.note }}</td>
            </tr>
            </tbody>
          </table>
        </div>

        <div v-if="cur.data.recommendations?.length" class="rep-sec">
          <h3 class="rep-h">建議事項</h3>
          <ul class="rep-rec">
            <li v-for="(r, i) in cur.data.recommendations" :key="i">{{ r }}</li>
          </ul>
        </div>

        <p class="rep-foot mono">本報告由 AI 依《NG分析注意事項》就即時快照生成，僅供參考。</p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, watch, onMounted } from 'vue'
import api from '@/services/api'
import { useFurnaceStore } from '@/stores/furnaceStore.js'

const store = useFurnaceStore()

const ids       = computed(() => store.furnaces.map(f => f.furnaceId))
const reports   = reactive({})        // { id: { status, data, error } }
const selected  = ref(null)
const downloading    = ref(null)
const downloadingAll = ref(false)
const generatingAll=ref(false)
const cooldownLabel=ref('')
let kicked = false                    // 避免重複觸發全生成

const cur        = computed(() => selected.value ? reports[selected.value] : null)
const doneCount  = computed(() => ids.value.filter(id => reports[id]?.status === 'done').length)
const anyLoading = computed(() => ids.value.some(id => reports[id]?.status === 'loading'))

const sleep = (ms) => new Promise(r => setTimeout(r, ms))

// 生成單一爐子的報告，含 429 (TPM 超限) 自動退避重試
async function generateOne(id, attempt = 1) {
  // 標記 loading（同時確保第一個被選取的 tab 有內容可顯示）
  reports[id] = { status: 'loading', data: null, error: null }
  if (!selected.value) selected.value = id

  try {
    const res = await api.post(`/furnaces/${id}/report`)
    reports[id] = { status: 'done', data: res.data, error: null }
    return res.data
  } catch (e) {
    const status = e.response?.status
    const msg = e.response?.data?.error ?? e.message ?? ''
    const isRateLimit = status === 429 ||
      (status === 502 && /rate limit|TPM/i.test(msg))

    // OpenAI 限流：讀 "try again in 8.422s" 等過視窗再重試
    if (isRateLimit && attempt <= 4) {
      const m = msg.match(/try again in ([\d.]+)s/i)
      const waitMs = m ? Math.ceil(parseFloat(m[1]) * 1000) + 1000 : 20000
      reports[id] = { status: 'loading', data: null,
        error: `限流中，${Math.round(waitMs / 1000)} 秒後重試（第 ${attempt} 次）…` }
      await sleep(waitMs)
      return generateOne(id, attempt + 1)
    }

    reports[id] = { status: 'error', data: null,
      error: msg || `生成失敗（${status ?? '網路錯誤'}）` }
    throw e
  }
}

//generateAll 加「每爐間隔」避免撞 TPM 限流
//  單發 ~16626 token，TPM 上限 30000 → 一分鐘只能跑 1.x 發
//  → 每爐之間等 GAP_MS 讓 TPM 回血，五發序列雖慢但都會成功
const GAP_MS = 40000   // 每爐之間間隔（毫秒）。TPM 30000、單發16626 → 40s 保險

async function generateAll() {
  generatingAll.value = true
  try {
    const list = ids.value
    for (let i = 0; i < list.length; i++) {
      const id = list[i]

      // 生成這一爐（generateOne 內已含 429 退避重試）
      await generateOne(id)

      // 最後一爐不用等
      if (i < list.length - 1) {
        // 顯示倒數，讓使用者知道不是卡住，是在等 TPM 回血
        let remain = Math.ceil(GAP_MS / 1000)
        // 把下一爐標記成「排隊中」狀態（可選，看你的狀態結構）
        const nextId = list[i + 1]
        if (reports[nextId]) reports[nextId].status = 'queued'

        while (remain > 0) {
          cooldownLabel.value = `避免限流，${remain}s 後生成 ${nextId}…`
          await sleep(1000)
          remain--
        }
        cooldownLabel.value = ''
      }
    }
  } finally {
    generatingAll.value = false
    cooldownLabel.value = ''
  }
}

async function downloadDocx(id) {
  const rep = reports[id]
  if (rep?.status !== 'done') return
  downloading.value = id
  try {
    const res = await api.post('/furnaces/reports/docx', rep.data, { responseType: 'blob' })
    triggerDownload(res.data, `report_${id}_${Date.now()}.docx`)
  } catch (e) {
    rep.error = '下載失敗：' + (e.message || '')
  } finally {
    downloading.value = null
  }
}

async function downloadAll() {
  // 收集所有已成功生成的 report
  const doneReports = ids.value
    .filter(id => reports[id]?.status === 'done')
    .map(id => reports[id].data)

  if (doneReports.length === 0) {
    alert('目前沒有已完成的報告可下載')
    return
  }

  downloadingAll.value = true
  try {
    // 一次把所有成功的 report 送後端，回傳 ZIP（responseType: blob）
    const res = await api.post('/furnaces/reports/zip', doneReports, {
      responseType: 'blob',
      timeout: 60000,   // 打包通常快，但給足緩衝
    })
    triggerDownload(res.data, `furnace_reports_${Date.now()}.zip`)
  } catch (e) {
    alert('下載失敗：' + (e.message || ''))
  } finally {
    downloadingAll.value = false
  }
}

function triggerDownload(blob, name) {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url; a.download = name
  document.body.appendChild(a); a.click(); a.remove()
  URL.revokeObjectURL(url)
}

function verdictClass(v) {
  if (v === 'NG') return 'v-ng'
  if (v === '疑似NG') return 'v-warn'
  if (v === 'OK') return 'v-ok'
  return 'v-na'
}

// 爐子清單就緒就自動全生成（含 ReportView 先於 loadFurnaces 掛載的情況）
watch(ids, list => {
  if (!kicked && list.length) { kicked = true; generateAll() }
}, { immediate: true })

onMounted(() => { if (!kicked && ids.value.length) { kicked = true; generateAll() } })
</script>

<style scoped>
.rv { display: flex; flex-direction: column; height: 100%; overflow: hidden; background: var(--bg-0); }

.rv-bar { display: flex; align-items: center; justify-content: space-between; padding: 14px 24px; border-bottom: 1px solid var(--border); background: var(--bg-1); flex-shrink: 0; }
.rv-titles { display: flex; flex-direction: column; gap: 2px; }
.rv-title { font-size: 15px; font-weight: 700; color: var(--text-0); letter-spacing: 0.06em; }
.rv-sub { font-size: 10px; color: var(--text-2); }
.rv-bar-right { display: flex; align-items: center; gap: 10px; }
.rv-progress { font-size: 11px; color: var(--text-2); }
.rv-btn { background: var(--teal); border: none; color: #06121a; font-weight: 700; font-size: 11px; padding: 6px 13px; border-radius: var(--radius-sm); cursor: pointer; }
.rv-btn.ghost { background: var(--bg-3); color: var(--text-1); }
.rv-btn:disabled { opacity: 0.45; cursor: default; }

.rv-tabs { display: flex; gap: 6px; padding: 10px 24px; border-bottom: 1px solid var(--border); background: var(--bg-1); flex-shrink: 0; flex-wrap: wrap; }
.rv-tab { display: flex; align-items: center; gap: 6px; background: var(--bg-2); border: 1px solid var(--border); color: var(--text-1); font-size: 13px; font-weight: 700; padding: 5px 14px; border-radius: var(--radius-sm); cursor: pointer; }
.rv-tab.active { border-color: var(--teal); color: var(--teal); }
.tab-dot { width: 7px; height: 7px; border-radius: 50%; background: var(--text-2); }
.tab-dot.d-load { background: var(--teal); animation: pulse 1s infinite; }
.tab-dot.d-ok { background: var(--green); }
.tab-dot.d-err { background: var(--red); }
@keyframes pulse { 0%,100%{opacity:1} 50%{opacity:0.3} }

.rv-body { flex: 1; overflow-y: auto; padding: 22px 28px; }
.rv-state { display: flex; align-items: center; gap: 12px; justify-content: center; padding: 80px 0; color: var(--text-2); font-size: 13px; }
.rv-err { color: var(--red); flex-direction: column; }
.spinner { width: 14px; height: 14px; border: 2px solid var(--border); border-top-color: var(--teal); border-radius: 50%; animation: spin 0.8s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }

.rv-report { max-width: 860px; margin: 0 auto; }
.rep-head { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 16px; flex-wrap: wrap; }
.rep-meta { display: flex; gap: 14px; align-items: center; font-size: 11px; color: var(--text-2); }
.rep-actions { display: flex; gap: 8px; }
.verdict { padding: 3px 12px; border-radius: 999px; font-weight: 700; font-size: 12px; }
.v-ng { background: rgba(220,38,38,0.15); color: #f87171; }
.v-warn { background: rgba(217,119,6,0.15); color: #fbbf24; }
.v-ok { background: rgba(5,150,105,0.15); color: #34d399; }
.v-na { background: var(--bg-3); color: var(--text-2); }

.rep-summary { font-size: 14px; line-height: 1.6; color: var(--text-0); margin: 0 0 18px; }
.rep-sec { margin-bottom: 18px; }
.rep-h { font-size: 13px; font-weight: 700; color: var(--teal); border-bottom: 1px solid var(--border); padding-bottom: 5px; margin: 0 0 8px; }
.rep-p { font-size: 13px; line-height: 1.65; color: var(--text-1); white-space: pre-wrap; margin: 0; }
.rep-table { width: 100%; border-collapse: collapse; font-size: 12px; }
.rep-table th, .rep-table td { border: 1px solid var(--border); padding: 6px 9px; text-align: left; color: var(--text-1); }
.rep-table th { background: var(--bg-2); color: var(--text-2); font-weight: 600; }
.rep-rec { margin: 0; padding-left: 20px; }
.rep-rec li { font-size: 13px; line-height: 1.6; color: var(--text-1); margin-bottom: 4px; }
.rep-foot { font-size: 10px; color: var(--text-2); margin-top: 22px; padding-top: 10px; border-top: 1px solid var(--border); }
.cooldown {
  font-size: 12px;
  color: var(--text-2);
  margin-left: 12px;
}
</style>

