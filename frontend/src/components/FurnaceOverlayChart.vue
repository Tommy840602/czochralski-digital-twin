<template>
  <div class="echart-wrap">
    <VChart
      class="echart"
      :option="chartOption"
      :autoresize="true"
      :update-options="updateOptions"
    />
  </div>
</template>

<script setup>
import { shallowRef, watch } from 'vue'
import VChart from 'vue-echarts'

import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { LineChart } from 'echarts/charts'
import {
  GridComponent,
  TooltipComponent,
  LegendComponent,
  DataZoomComponent,
} from 'echarts/components'

use([
  CanvasRenderer,
  LineChart,
  GridComponent,
  TooltipComponent,
  LegendComponent,
  DataZoomComponent,
])

const updateOptions = {
  notMerge: true,
  lazyUpdate: false,
}

const props = defineProps({
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
    default: () => ({
      leftMin: '',
      leftMax: '',
      rightMin: '',
      rightMax: '',
    }),
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

const chartOption = shallowRef({})

function finiteNumber(v) {
  return typeof v === 'number' && Number.isFinite(v)
}

function axisValue(v) {
  if (v === null || v === undefined || v === '') return undefined

  const n = Number(v)
  return Number.isFinite(n) ? n : undefined
}

function formatAxisValue(value) {
  const n = Number(value)

  if (!Number.isFinite(n)) return ''
  if (Math.abs(n) >= 1000) return n.toFixed(0)
  if (Math.abs(n) >= 100) return n.toFixed(0)
  if (Math.abs(n) >= 10) return n.toFixed(1)

  return n.toFixed(2)
}

function seriesValues(fid, metricKey) {
  const arr = props.buffers[`${fid}::${metricKey}`] || []
  return arr.filter(v => finiteNumber(v))
}

function buildOption() {
  const targetMetricMeta = props.metricMap[props.targetMetric]
  const targetValues = seriesValues(props.targetId, props.targetMetric)

  const overlaySeries = props.overlayIds.map(oid => {
    const metricKey = props.overlayMetricMap[oid] || props.targetMetric
    const metricMeta = props.metricMap[metricKey]
    const values = seriesValues(oid, metricKey)

    return {
      id: `${oid}::${metricKey}::right`,
      name: `${oid} / ${metricMeta?.label ?? metricKey}`,
      type: 'line',
      yAxisIndex: 1,
      showSymbol: false,
      smooth: true,
      connectNulls: true,
      sampling: 'lttb',
      lineStyle: {
        width: 1.8,
      },
      itemStyle: {
        color: props.furnaceColor(oid),
      },
      emphasis: {
        focus: 'series',
      },
      data: values,
    }
  })

  const maxLen = Math.max(
    targetValues.length,
    ...overlaySeries.map(s => s.data.length),
    1,
  )

  // x 軸 index：0, 1, 2, 3...
  const categories = Array.from({ length: maxLen }, (_, i) => String(i))

  return {
    animation: false,
    backgroundColor: 'transparent',

    grid: {
      left: 46,
      right: 42,
      top: 12,
      bottom: 24,
      containLabel: false,
    },

    tooltip: {
      trigger: 'axis',
      confine: true,
      axisPointer: {
        type: 'line',
      },
      backgroundColor: 'rgba(15, 23, 42, 0.95)',
      borderColor: 'rgba(148, 163, 184, 0.35)',
      textStyle: {
        color: '#e2e8f0',
        fontSize: 11,
      },
      valueFormatter: value => {
        const n = Number(value)
        return Number.isFinite(n) ? n.toFixed(3) : '—'
      },
    },

    legend: {
      show: false,
    },

    dataZoom: [
      {
        type: 'inside',
        xAxisIndex: 0,
        filterMode: 'none',
      },
    ],

    xAxis: {
      type: 'category',
      name: '',
      boundaryGap: false,
      data: categories,

      axisLabel: {
        color: '#94a3b8',
        fontSize: 10,
        hideOverlap: true,

        // 每 5 個 index 顯示一次：0, 5, 10, 15...
        interval: (index, value) => {
          const n = Number(value)
          return Number.isFinite(n) && n % 5 === 0
        },

        formatter: value => value,
      },

      axisTick: {
        alignWithLabel: true,
        interval: (index, value) => {
          const n = Number(value)
          return Number.isFinite(n) && n % 5 === 0
        },
        lineStyle: {
          color: '#334155',
        },
      },

      axisLine: {
        lineStyle: {
          color: '#334155',
        },
      },

      splitLine: {
        show: true,
        interval: (index, value) => {
          const n = Number(value)
          return Number.isFinite(n) && n % 5 === 0
        },
        lineStyle: {
          color: 'rgba(148, 163, 184, 0.12)',
        },
      },
    },

    yAxis: [
      {
        type: 'value',
        name: '',
        min: axisValue(props.axisRange.leftMin),
        max: axisValue(props.axisRange.leftMax),
        scale: true,
        axisLabel: {
          color: '#94a3b8',
          fontSize: 10,
          hideOverlap: true,
          formatter: formatAxisValue,
        },
        axisLine: {
          show: true,
          lineStyle: {
            color: '#475569',
          },
        },
        axisTick: {
          show: true,
          lineStyle: {
            color: '#475569',
          },
        },
        splitLine: {
          lineStyle: {
            color: 'rgba(148, 163, 184, 0.12)',
          },
        },
      },
      {
        type: 'value',
        name: '',
        min: axisValue(props.axisRange.rightMin),
        max: axisValue(props.axisRange.rightMax),
        scale: true,
        axisLabel: {
          color: '#94a3b8',
          fontSize: 10,
          hideOverlap: true,
          formatter: formatAxisValue,
        },
        axisLine: {
          show: true,
          lineStyle: {
            color: '#475569',
          },
        },
        axisTick: {
          show: true,
          lineStyle: {
            color: '#475569',
          },
        },
        splitLine: {
          show: false,
        },
      },
    ],

    series: [
      {
        id: `${props.targetId}::${props.targetMetric}::left`,
        name: `${props.targetId} / ${targetMetricMeta?.label ?? props.targetMetric}`,
        type: 'line',
        yAxisIndex: 0,
        showSymbol: false,
        smooth: true,
        connectNulls: true,
        sampling: 'lttb',
        lineStyle: {
          width: 2.2,
        },
        itemStyle: {
          color: props.furnaceColor(props.targetId),
        },
        emphasis: {
          focus: 'series',
        },
        data: targetValues,
      },
      ...overlaySeries,
    ],
  }
}

watch(
  () => [
    props.revision,
    props.targetId,
    props.targetMetric,
    props.overlayIds.join('|'),
    JSON.stringify(props.overlayMetricMap),
    props.axisRange.leftMin,
    props.axisRange.leftMax,
    props.axisRange.rightMin,
    props.axisRange.rightMax,
  ],
  () => {
    chartOption.value = buildOption()
  },
  {
    immediate: true,
    flush: 'sync',
  },
)
</script>

<style scoped>
.echart-wrap {
  width: 100%;
  height: 160px;
  min-height: 160px;
}

.echart {
  width: 100%;
  height: 100%;
}
</style>
