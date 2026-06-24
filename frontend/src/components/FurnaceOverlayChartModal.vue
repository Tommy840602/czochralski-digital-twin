<template>
  <Teleport to="body">
    <div class="modal-mask" @click.self="$emit('close')">
      <div class="modal-card">
        <div class="modal-head">
          <div>
            <div class="modal-title mono">{{ targetId }} 疊圖放大檢視</div>
            <div class="modal-sub mono">
              即時更新 · Left Y: {{ targetMetric }} · Right Y: overlay metrics
            </div>
          </div>

          <button class="modal-close" type="button" @click="$emit('close')">
            ✕
          </button>
        </div>

        <FurnaceOverlayChart
          class="modal-chart"
          :target-id="targetId"
          :overlay-ids="overlayIds"
          :target-metric="targetMetric"
          :overlay-metric-map="overlayMetricMap"
          :metric-map="metricMap"
          :buffers="buffers"
          :axis-range="axisRange"
          :furnace-color="furnaceColor"
          :revision="revision"
        />
      </div>
    </div>
  </Teleport>
</template>

<script setup>
import FurnaceOverlayChart from './FurnaceOverlayChart.vue'

defineProps({
  targetId: {
    type: String,
    required: true,
  },
  overlayIds: {
    type: Array,
    default: () => [],
  },
  targetMetric: {
    type: String,
    required: true,
  },
  overlayMetricMap: {
    type: Object,
    default: () => ({}),
  },
  metricMap: {
    type: Object,
    required: true,
  },
  buffers: {
    type: Object,
    required: true,
  },
  axisRange: {
    type: Object,
    required: true,
  },
  furnaceColor: {
    type: Function,
    required: true,
  },
  revision: {
    type: Number,
    default: 0,
  },
})

defineEmits(['close'])
</script>

<style scoped>
.modal-mask {
  position: fixed;
  inset: 0;
  z-index: 9999;
  background: rgba(2, 6, 23, 0.72);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32px;
}

.modal-card {
  width: min(1280px, 96vw);
  height: min(760px, 90vh);
  background: var(--bg-1);
  border: 1px solid var(--border-hi);
  border-radius: var(--radius-sm);
  box-shadow: 0 24px 80px rgba(0, 0, 0, 0.45);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.modal-head {
  flex-shrink: 0;
  height: 56px;
  padding: 12px 16px;
  border-bottom: 1px solid var(--border);
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.modal-title {
  font-size: 14px;
  font-weight: 700;
  color: var(--text-0);
}

.modal-sub {
  margin-top: 4px;
  font-size: 10px;
  color: var(--text-2);
}

.modal-close {
  background: var(--bg-3);
  border: 1px solid var(--border);
  color: var(--text-1);
  border-radius: var(--radius-sm);
  cursor: pointer;
  width: 30px;
  height: 30px;
}

.modal-close:hover {
  border-color: var(--red);
  color: var(--red);
}

.modal-chart {
  flex: 1;
  height: auto;
  min-height: 0;
  padding: 12px;
}

.modal-chart :deep(.echart-wrap) {
  height: 100%;
  min-height: 620px;
}
</style>
