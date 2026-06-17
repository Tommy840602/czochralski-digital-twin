<template>
  <div class="kpi-card">
    <div class="kpi-label mono">{{ label }}</div>
    <div class="kpi-value mono">
      {{ value }}<span v-if="unit" class="kpi-unit">{{ unit }}</span>
    </div>
    <div class="kpi-bar">
      <div class="kpi-fill" :style="{ width: pct + '%', background: `var(${color})` }"></div>
    </div>
  </div>
</template>

<script setup>
defineProps({
  label:  String,
  value:  [String, Number],
  unit:   { type: String, default: '' },
  color:  { type: String, default: '--teal' },
  pct:    { type: Number, default: 0 }
})
</script>

<style scoped>
.kpi-card {
  background: var(--bg-2);
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  padding: 10px 10px 8px;
  position: relative; overflow: hidden;
}
.kpi-label {
  font-size: 9px; letter-spacing: 0.1em;
  color: var(--text-2); text-transform: uppercase; margin-bottom: 4px;
}
.kpi-value {
  font-size: 18px; font-weight: 600; color: var(--text-0); line-height: 1;
}
.kpi-unit { font-size: 10px; color: var(--text-1); margin-left: 3px; }
.kpi-bar {
  margin-top: 6px; height: 2px;
  background: rgba(255,255,255,0.05); border-radius: 1px; overflow: hidden;
}
.kpi-fill {
  height: 100%; border-radius: 1px;
  transition: width 0.4s ease;
  opacity: 0.8;
}
</style>
