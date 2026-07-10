<template>
  <Teleport to="body">
    <Transition name="modal-fade">
      <div v-if="furnaceId" class="sec-mask" @click.self="close">
        <div class="sec-dialog">
          <!-- 頂部工具列 -->
          <div class="sec-topbar">
            <div class="sec-title mono">
              <span class="sec-title-tag">SECTION VIEW</span>
              <span class="sec-title-id">{{ furnaceId }}</span>
              <span class="sec-title-mode" v-if="mode">{{ mode }}</span>
            </div>
            <button class="sec-close mono" @click="close" title="關閉 (Esc)">×</button>
          </div>

          <!-- 主體：左側剖面 3D + 右側 KPI -->
          <div class="sec-body">
            <div class="sec-stage">
              <SectionScene :furnace-id="furnaceId" :live="live" />
              <div class="sec-stage-hint mono">滑鼠拖曳旋轉 · 滾輪縮放</div>
            </div>

            <aside class="sec-kpi">
              <div class="sec-kpi-title mono">即時感測</div>
              <div
                v-for="m in metrics"
                :key="m.key"
                class="sec-kpi-row"
              >
                <span class="sec-kpi-label mono">{{ m.label }}</span>
                <span class="sec-kpi-value mono" :style="{ color: m.color }">
                  {{ fmt(latest(m.key), m.dec) }}<span class="sec-kpi-unit">{{ m.unit }}</span>
                </span>
              </div>

              <div class="sec-info mono">
                <div v-if="ingotNo" class="sec-info-row">
                  <span>INGOT</span><span class="mono">{{ ingotNo }}</span>
                </div>
                <div v-if="updatedAt" class="sec-info-row">
                  <span>UPDATED</span><span class="mono">{{ updatedAt }}</span>
                </div>
              </div>
            </aside>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup>
import { computed, watch, onBeforeUnmount } from 'vue'
import SectionScene from './section/SectionScene.vue'

const props = defineProps({
  furnaceId: { type: String, default: null },
  liveData: { type: Object, default: () => ({}) },
})
const emit = defineEmits(['close'])

const metrics = [
  { key: 'heaterTemp',    label: 'HEATER TEMP',  unit: '°C',   dec: 1, color: '#f87171' },
  { key: 'diameter',      label: 'DIAMETER',     unit: 'mm',   dec: 2, color: '#38bdf8' },
  { key: 'grMean',        label: 'GR MEAN',      unit: 'mm/m', dec: 3, color: '#34d399' },
  { key: 'bodyLength',    label: 'BODY LEN',     unit: 'mm',   dec: 1, color: '#a78bfa' },
  { key: 'heaterPowerSv', label: 'HTR POWER',    unit: 'kW',   dec: 1, color: '#f59e0b' },
  { key: 'seedLift',      label: 'SEED LIFT',    unit: 'mm',   dec: 3, color: '#fb923c' },
]

const live = computed(() => {
  return props.furnaceId ? (props.liveData?.[props.furnaceId] ?? null) : null
})

const mode    = computed(() => live.value?.operationMode ?? null)
const ingotNo = computed(() => live.value?.ingotNo ?? null)
const updatedAt = computed(() => {
  const t = live.value?.logTime
  if (!t) return null
  const m = String(t).match(/\d{2}:\d{2}:\d{2}/)
  return m ? m[0] : String(t)
})

function latest(key) {
  const v = live.value?.[key]
  return (v == null || !isFinite(v)) ? null : Number(v)
}
function fmt(v, d) {
  return (v == null || !isFinite(v)) ? '—' : Number(v).toFixed(d)
}

function close() { emit('close') }

function onKey(ev) {
  if (ev.key === 'Escape' && props.furnaceId) close()
}
watch(() => props.furnaceId, (v) => {
  if (v) window.addEventListener('keydown', onKey)
  else window.removeEventListener('keydown', onKey)
})
onBeforeUnmount(() => window.removeEventListener('keydown', onKey))
</script>

<style scoped>
.sec-mask {
  position: fixed; inset: 0; z-index: 9000;
  background: rgba(3, 6, 12, 0.78);
  backdrop-filter: blur(6px);
  display: flex; align-items: center; justify-content: center;
  padding: 24px;
}

.sec-dialog {
  width: min(1280px, 95vw);
  height: min(820px, 92vh);
  background: rgba(10, 16, 24, 0.96);
  border: 1px solid rgba(56, 189, 248, 0.25);
  border-radius: 12px;
  box-shadow: 0 30px 80px rgba(0, 0, 0, 0.6),
  0 0 0 1px rgba(56, 189, 248, 0.05) inset;
  display: flex; flex-direction: column;
  overflow: hidden;
}

.sec-topbar {
  height: 48px;
  flex: 0 0 48px;
  display: flex; align-items: center; justify-content: space-between;
  padding: 0 14px 0 18px;
  border-bottom: 1px solid rgba(56, 189, 248, 0.15);
  background: linear-gradient(180deg, rgba(56,189,248,0.06), transparent);
}
.sec-title { display: flex; align-items: baseline; gap: 12px; }
.sec-title-tag {
  font-size: 10px; letter-spacing: 0.18em;
  color: var(--teal); opacity: 0.7;
}
.sec-title-id {
  font-size: 16px; font-weight: 700; color: #e2e8f0;
}
.sec-title-mode {
  font-size: 10px; letter-spacing: 0.14em;
  padding: 2px 8px;
  border: 1px solid rgba(64, 200, 140, 0.5);
  color: #40c88c; border-radius: 4px;
}
.sec-close {
  width: 30px; height: 30px;
  background: transparent;
  border: 1px solid rgba(255,255,255,0.12);
  border-radius: 6px;
  color: var(--text-1);
  font-size: 18px; line-height: 1;
  cursor: pointer;
  display: flex; align-items: center; justify-content: center;
  transition: all 0.15s ease;
}
.sec-close:hover { color: var(--red); border-color: rgba(248,113,113,0.6); }

.sec-body {
  flex: 1 1 auto;
  display: flex;
  min-height: 0;
}

.sec-stage {
  flex: 1 1 auto;
  position: relative;
  background: #050810;
  overflow: hidden;
}
.sec-stage-hint {
  position: absolute;
  left: 12px; bottom: 10px;
  font-size: 9px; letter-spacing: 0.1em;
  color: rgba(148, 163, 184, 0.4);
  pointer-events: none;
}

.sec-kpi {
  flex: 0 0 240px;
  background: rgba(8, 14, 22, 0.6);
  border-left: 1px solid rgba(56, 189, 248, 0.15);
  padding: 16px 14px;
  display: flex; flex-direction: column; gap: 8px;
  overflow-y: auto;
}
.sec-kpi-title {
  font-size: 10px; letter-spacing: 0.18em;
  color: var(--teal); opacity: 0.6;
  padding-bottom: 6px;
  margin-bottom: 4px;
  border-bottom: 1px solid rgba(56, 189, 248, 0.1);
}
.sec-kpi-row {
  display: flex; flex-direction: column;
  padding: 8px 10px;
  background: rgba(255, 255, 255, 0.025);
  border: 1px solid rgba(255, 255, 255, 0.04);
  border-radius: 6px;
}
.sec-kpi-label {
  font-size: 9px; letter-spacing: 0.12em; color: var(--text-2);
  margin-bottom: 4px;
}
.sec-kpi-value { font-size: 18px; font-weight: 700; }
.sec-kpi-unit { font-size: 9px; color: var(--text-2); margin-left: 3px; font-weight: 500; }

.sec-info {
  margin-top: auto;
  padding-top: 12px;
  border-top: 1px solid rgba(255,255,255,0.06);
  display: flex; flex-direction: column; gap: 6px;
}
.sec-info-row {
  display: flex; justify-content: space-between;
  font-size: 9px; color: var(--text-2); letter-spacing: 0.08em;
}
.sec-info-row .mono { color: var(--text-1); }

.modal-fade-enter-active, .modal-fade-leave-active { transition: opacity 0.18s ease; }
.modal-fade-enter-active .sec-dialog,
.modal-fade-leave-active .sec-dialog {
  transition: transform 0.22s cubic-bezier(0.16, 1, 0.3, 1), opacity 0.18s ease;
}
.modal-fade-enter-from, .modal-fade-leave-to { opacity: 0; }
.modal-fade-enter-from .sec-dialog,
.modal-fade-leave-to .sec-dialog {
  opacity: 0; transform: translateY(8px) scale(0.985);
}
</style>
