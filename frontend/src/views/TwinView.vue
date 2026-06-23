<template>
  <div class="twin-view">
    <FurnaceScene
      :furnace-data="store.liveData"
      :furnace-ids="store.furnaceIds"
      @open-section="onOpenSection"
    />

    <SectionViewModal
      :furnace-id="sectionFurnaceId"
      :live-data="store.liveData"
      @close="onCloseSection"
    />

    <!-- KPI overlay -->
    <Transition name="slide">
      <div v-if="store.selectedLive" class="kpi-overlay" :style="kpiStyle" ref="kpiEl">
        <div class="kpi-header" @mousedown="startDrag">
          <span class="kpi-title mono">{{ store.selected }}</span>
          <span class="kpi-mode mono">{{ store.selectedLive.operationMode }}</span>
          <button class="kpi-close" @click="store.selectFurnace(null)">✕</button>
        </div>
        <div class="kpi-grid">
          <div class="kpi-item">
            <div class="kpi-label mono">HEATER TEMP</div>
            <div class="kpi-value mono">{{ fmt(store.selectedLive.heaterTemp, 1) }}<span class="kpi-unit">°C</span></div>
          </div>
          <div class="kpi-item">
            <div class="kpi-label mono">DIAMETER</div>
            <div class="kpi-value mono">{{ fmt(store.selectedLive.diameter, 2) }}<span class="kpi-unit">mm</span></div>
          </div>
          <div class="kpi-item">
            <div class="kpi-label mono">GR MEAN</div>
            <div class="kpi-value mono">{{ fmt(store.selectedLive.grMean, 3) }}<span class="kpi-unit">mm/m</span></div>
          </div>
          <div class="kpi-item">
            <div class="kpi-label mono">BODY LEN</div>
            <div class="kpi-value mono">{{ fmt(store.selectedLive.bodyLength, 1) }}<span class="kpi-unit">mm</span></div>
          </div>
          <div class="kpi-item">
            <div class="kpi-label mono">HTP POWER</div>
            <div class="kpi-value mono">{{ fmt(store.selectedLive.heaterPowerSv, 1) }}<span class="kpi-unit">kW</span></div>
          </div>
          <div class="kpi-item">
            <div class="kpi-label mono">SEED LIFT</div>
            <div class="kpi-value mono">{{ fmt(store.selectedLive.seedLift, 3) }}</div>
          </div>
        </div>
        <div class="kpi-ingot mono">INGOT {{ store.selectedLive.ingotNo }}</div>
      </div>
    </Transition>

    <!-- 即時趨勢面板 -->
    <button class="trends-toggle mono" @click="showTrends = !showTrends">
      {{ showTrends ? '◧ 隱藏' : '◨ 即時' }}
    </button>
    <Transition name="slide-left">
      <RealtimeTrends
        v-if="showTrends && store.selected"
        :furnace-id="store.selected"
        class="trends-panel"
      />
    </Transition>

    <!-- 爐子選擇按鈕 -->
    <div class="furnace-pills">
      <button
        v-for="f in store.furnaces"
        :key="f.furnaceId"
        class="pill mono"
        :class="{ active: store.selected === f.furnaceId }"
        @click="store.selectFurnace(f.furnaceId)"
      >
        {{ f.furnaceId }}
      </button>
    </div>
  </div>
</template>

<script setup>
import { computed, ref, reactive, onUnmounted } from "vue"
import { useFurnaceStore } from "@/stores/furnaceStore.js"
import FurnaceScene from "@/components/FurnaceScene.vue"
import RealtimeTrends from "@/components/RealtimeTrends.vue"
import SectionViewModal from '@/components/SectionViewModal.vue'

const store = useFurnaceStore()
const showTrends = ref(true)
const sectionFurnaceId = ref(null)

function onOpenSection(id) { sectionFurnaceId.value = id }
function onCloseSection()   { sectionFurnaceId.value = null }

// ── KPI 面板拖移 ──────────────────────────────────────
const kpiEl = ref(null)
const pos = reactive({ x: null, y: null }) // null = 用預設 CSS（top/right）
let dragOffset = { x: 0, y: 0 }
let dragging = false

const kpiStyle = computed(() => {
  if (pos.x === null) return {}
  return { left: pos.x + "px", top: pos.y + "px", right: "auto" }
})

function startDrag(e) {
  if (!kpiEl.value) return
  dragging = true
  const rect = kpiEl.value.getBoundingClientRect()
  dragOffset.x = e.clientX - rect.left
  dragOffset.y = e.clientY - rect.top
  window.addEventListener("mousemove", onDrag)
  window.addEventListener("mouseup", stopDrag)
}

function onDrag(e) {
  if (!dragging) return
  pos.x = e.clientX - dragOffset.x
  pos.y = e.clientY - dragOffset.y
}

function stopDrag() {
  dragging = false
  window.removeEventListener("mousemove", onDrag)
  window.removeEventListener("mouseup", stopDrag)
}

onUnmounted(() => {
  window.removeEventListener("mousemove", onDrag)
  window.removeEventListener("mouseup", stopDrag)
})

const sceneData = computed(() => {
  if (Object.keys(store.liveData).length > 0) return store.liveData
  const dummy = {}
  store.furnaceIds.forEach(id => { dummy[id] = { furnaceId: id } })
  return dummy
})

function fmt(val, dec) {
  const n = parseFloat(val)
  return isNaN(n) ? "—" : n.toFixed(dec)
}
</script>

<style scoped>
.twin-view {
  position: relative;
  width: 100%;
  height: 100%;
  overflow: hidden;
}

.kpi-overlay {
  position: absolute;
  top: 16px;
  right: 16px;
  width: 240px;
  background: rgba(8, 14, 22, 0.88);
  border: 1px solid rgba(56, 189, 248, 0.25);
  border-radius: 10px;
  padding: 14px;
  backdrop-filter: blur(12px);
  z-index: 10;
}

.kpi-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  padding-bottom: 10px;
  border-bottom: 1px solid rgba(56, 189, 248, 0.15);
  cursor: move;
  user-select: none;
}

.kpi-title {
  font-size: 18px;
  font-weight: 700;
  color: #38bdf8;
  letter-spacing: 0.08em;
}

.kpi-mode {
  font-size: 10px;
  color: #64748b;
  background: rgba(56, 189, 248, 0.1);
  border: 1px solid rgba(56, 189, 248, 0.2);
  border-radius: 4px;
  padding: 2px 6px;
  letter-spacing: 0.06em;
}

.kpi-close {
  margin-left: auto;
  background: transparent;
  border: none;
  color: #64748b;
  cursor: pointer;
  font-size: 14px;
  padding: 2px 4px;
}
.kpi-close:hover { color: #f87171; }

.kpi-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
  margin-bottom: 12px;
}

.kpi-item {
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 6px;
  padding: 8px 10px;
}

.kpi-label {
  font-size: 9px;
  color: #64748b;
  letter-spacing: 0.06em;
  margin-bottom: 4px;
}

.kpi-value {
  font-size: 18px;
  font-weight: 600;
  color: #e2e8f0;
  line-height: 1;
}

.kpi-unit {
  font-size: 10px;
  color: #64748b;
  margin-left: 2px;
}

.kpi-ingot {
  font-size: 10px;
  color: #475569;
  letter-spacing: 0.06em;
  text-align: center;
  padding-top: 8px;
  border-top: 1px solid rgba(255, 255, 255, 0.06);
}

.furnace-pills {
  position: absolute;
  bottom: 20px;
  left: 50%;
  transform: translateX(-50%);
  display: flex;
  gap: 8px;
  z-index: 10;
}

.pill {
  background: rgba(8, 14, 22, 0.8);
  border: 1px solid rgba(56, 189, 248, 0.2);
  border-radius: 20px;
  color: #64748b;
  cursor: pointer;
  font-size: 11px;
  letter-spacing: 0.06em;
  padding: 6px 14px;
  backdrop-filter: blur(8px);
  transition: all 0.2s;
}
.pill:hover {
  border-color: rgba(56, 189, 248, 0.5);
  color: #e2e8f0;
}
.pill.active {
  background: rgba(56, 189, 248, 0.15);
  border-color: #38bdf8;
  color: #38bdf8;
}

.trends-toggle {
  position: absolute; top: 16px; left: 16px; z-index: 11;
  background: rgba(8, 14, 22, 0.8);
  border: 1px solid rgba(56, 189, 248, 0.25);
  color: #38bdf8; border-radius: 6px;
  padding: 6px 12px; font-size: 11px; cursor: pointer;
  backdrop-filter: blur(8px); transition: border-color 0.2s;
}
.trends-toggle:hover { border-color: #38bdf8; }

.trends-panel {
  position: absolute; top: 56px; left: 16px; bottom: 16px;
  width: 320px; z-index: 10; overflow-y: auto;
}

.slide-left-enter-active, .slide-left-leave-active { transition: all 0.25s ease; }
.slide-left-enter-from, .slide-left-leave-to { opacity: 0; transform: translateX(-20px); }

.slide-enter-active, .slide-leave-active { transition: all 0.25s ease; }
.slide-enter-from, .slide-leave-to { opacity: 0; transform: translateX(20px); }
</style>
