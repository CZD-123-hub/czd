<script setup lang="ts">
import { ref, watch, onMounted, onBeforeUnmount, shallowRef } from 'vue'
import * as echarts from 'echarts'
import type { HeatmapData } from '@/types'

const props = defineProps<{
  data: HeatmapData | null
}>()

const chartContainer = ref<HTMLDivElement | null>(null)
const chart = shallowRef<echarts.ECharts | null>(null)

function buildOption(): echarts.EChartsCoreOption {
  if (!props.data) return {}

  const year = props.data.year
  const values = props.data.data.map((item) => item.count)
  const maxCount = Math.max(1, ...values)

  return {
    tooltip: {
      formatter: (params: { value?: [string, number] }) => {
        if (!params.value) return ''
        return `${params.value[0]}<br/>活跃次数：${params.value[1]}`
      },
    },
    visualMap: {
      min: 0,
      max: maxCount,
      type: 'piecewise',
      orient: 'horizontal',
      left: 'center',
      bottom: 2,
      itemGap: 6,
      pieces: [
        { min: 0, max: 0, label: '0', color: '#eef2ff' },
        { min: 1, max: 2, label: '1-2', color: '#dbe7ff' },
        { min: 3, max: 5, label: '3-5', color: '#9bbcff' },
        { min: 6, max: 8, label: '6-8', color: '#5a8bff' },
        { min: 9, label: '9+', color: '#2f6bff' },
      ],
      textStyle: {
        fontSize: 11,
        color: '#5f6d8c',
      },
    },
    calendar: {
      top: 28,
      left: 22,
      right: 22,
      cellSize: ['auto', 14],
      range: [`${year}-01-01`, `${year}-12-31`],
      itemStyle: {
        borderWidth: 2,
        borderColor: '#ffffff',
      },
      splitLine: {
        show: false,
      },
      yearLabel: {
        show: false,
      },
      dayLabel: {
        firstDay: 1,
        nameMap: ['日', '一', '二', '三', '四', '五', '六'],
        fontSize: 11,
        color: '#6b7b9d',
      },
      monthLabel: {
        nameMap: ['1月', '2月', '3月', '4月', '5月', '6月', '7月', '8月', '9月', '10月', '11月', '12月'],
        fontSize: 11,
        color: '#6b7b9d',
      },
    },
    series: [
      {
        type: 'heatmap',
        coordinateSystem: 'calendar',
        data: props.data.data.map((item) => [item.date, item.count]),
      },
    ],
  }
}

function initChart() {
  if (!chartContainer.value) return
  chart.value = echarts.init(chartContainer.value)
  if (props.data) {
    chart.value.setOption(buildOption())
  }
}

function handleResize() {
  chart.value?.resize()
}

onMounted(() => {
  initChart()
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  chart.value?.dispose()
})

watch(
  () => props.data,
  () => {
    if (chart.value && props.data) {
      chart.value.setOption(buildOption(), true)
    }
  },
  { deep: true },
)
</script>

<template>
  <div ref="chartContainer" class="heatmap-chart" />
</template>

<style lang="scss" scoped>
.heatmap-chart {
  width: 100%;
  height: 238px;
}
</style>
