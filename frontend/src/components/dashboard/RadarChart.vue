<script setup lang="ts">
import { ref, watch, onMounted, onBeforeUnmount, shallowRef } from 'vue'
import { init, type EChartsCoreOption, type EChartsType } from '@/utils/echarts'
import type { RadarData } from '@/types'

const props = defineProps<{
  data: RadarData | null
}>()

const chartContainer = ref<HTMLDivElement | null>(null)
const chart = shallowRef<EChartsType | null>(null)

function buildOption(): EChartsCoreOption {
  if (!props.data) return {}

  const periodDays = props.data.periodDays ?? 30
  const indicators = props.data.categories.map((cat) => ({
    name: cat,
    max: 100,
  }))

  return {
    tooltip: {
      trigger: 'item',
      confine: true,
      formatter: (params: any) => {
        const normalizedValues = Array.isArray(params?.value) ? params.value : props.data?.values ?? []
        const lines = props.data?.categories.map((category, index) => {
          const raw = props.data?.rawCounts?.[index]
          const normalized = Number(normalizedValues[index] ?? 0)
          const rawText = typeof raw === 'number' ? `${raw} 次` : '-'
          return `${category}: ${rawText}（归一化 ${normalized.toFixed(0)}）`
        })
        return [`最近 ${periodDays} 天知识活跃分布`, ...(lines || [])].join('<br/>')
      },
    },
    radar: {
      indicator: indicators,
      shape: 'polygon',
      splitNumber: 5,
      axisName: {
        color: '#4f5f82',
        fontSize: 12,
      },
      axisLine: {
        lineStyle: {
          color: 'rgba(47, 107, 255, 0.25)',
        },
      },
      splitLine: {
        lineStyle: {
          color: 'rgba(130, 152, 201, 0.28)',
        },
      },
      splitArea: {
        show: true,
        areaStyle: {
          color: ['rgba(245, 248, 255, 0.9)', 'rgba(235, 241, 255, 0.7)'],
        },
      },
    },
    series: [
      {
        type: 'radar',
        data: [
          {
            value: props.data.values,
            name: '知识掌握度',
            areaStyle: {
              color: 'rgba(47, 107, 255, 0.22)',
            },
            lineStyle: {
              color: '#2f6bff',
              width: 2.2,
            },
            itemStyle: {
              color: '#2f6bff',
            },
            symbolSize: 6,
          },
        ],
      },
    ],
  }
}

function initChart() {
  if (!chartContainer.value) return
  chart.value = init(chartContainer.value)
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
  <div ref="chartContainer" class="radar-chart" />
</template>

<style lang="scss" scoped>
.radar-chart {
  width: 100%;
  height: 338px;
}
</style>
