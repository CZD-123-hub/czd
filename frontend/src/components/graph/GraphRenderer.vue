<script setup lang="ts">
import { ref, watch, onMounted, onBeforeUnmount, shallowRef, computed } from 'vue'
import * as echarts from 'echarts'
import type { KnowledgeNode, GraphEdge } from '@/types'

const props = defineProps<{
  nodes: KnowledgeNode[]
  edges: GraphEdge[]
}>()

const emit = defineEmits<{
  nodeClick: [nodeId: string]
}>()

const chartContainer = ref<HTMLDivElement | null>(null)
const chart = shallowRef<echarts.ECharts | null>(null)

const palette = ['#2f6bff', '#5a8bff', '#38bdf8', '#4f46e5', '#0ea5e9', '#60a5fa', '#64748b', '#2563eb']

const categories = computed(() => {
  const set = new Set<string>()
  for (const node of props.nodes) {
    set.add(node.category || '未分类')
  }
  return Array.from(set)
})

function difficultyToSize(level?: string) {
  if (level === 'hard') return 54
  if (level === 'medium') return 44
  return 34
}

function buildOption(): echarts.EChartsCoreOption {
  const categoryIndex = new Map<string, number>()
  categories.value.forEach((name, idx) => categoryIndex.set(name, idx))

  const showLabel = props.nodes.length <= 120

  const graphNodes = props.nodes.map((node) => {
    const name = node.category || '未分类'
    const idx = categoryIndex.get(name) ?? 0

    return {
      id: node.id,
      name: node.name,
      category: idx,
      value: node.description,
      symbolSize: difficultyToSize(node.difficulty),
      itemStyle: {
        color: palette[idx % palette.length],
        borderColor: '#ffffff',
        borderWidth: 1.5,
        shadowBlur: 12,
        shadowColor: 'rgba(47,107,255,0.25)',
      },
      label: {
        show: showLabel,
        fontSize: 12,
        color: '#1f2f4a',
      },
    }
  })

  const graphEdges = props.edges.map((edge) => ({
    source: edge.source,
    target: edge.target,
    value: edge.type,
    lineStyle: {
      width: 1.1,
      color: 'rgba(107, 123, 157, 0.42)',
      curveness: 0.12,
    },
    emphasis: {
      lineStyle: {
        width: 2.2,
        color: 'rgba(47,107,255,0.7)',
      },
    },
  }))

  return {
    animationDuration: 900,
    tooltip: {
      trigger: 'item',
      confine: true,
      backgroundColor: 'rgba(20, 32, 56, 0.92)',
      borderColor: 'rgba(118, 151, 255, 0.55)',
      textStyle: { color: '#edf2ff', fontSize: 12 },
      formatter: (params: any) => {
        if (params.dataType === 'node') {
          const desc = typeof params.data?.value === 'string' ? params.data.value : ''
          return `<div style="max-width:260px"><strong>${params.name}</strong><br/>${desc || '暂无描述'}</div>`
        }

        if (params.dataType === 'edge') {
          return `关系：${params.data?.value || '关联'}`
        }

        return ''
      },
    },
    series: [
      {
        type: 'graph',
        layout: 'force',
        roam: true,
        draggable: true,
        focusNodeAdjacency: true,
        data: graphNodes,
        links: graphEdges,
        categories: categories.value.map((name, idx) => ({
          name,
          itemStyle: { color: palette[idx % palette.length] },
        })),
        force: {
          repulsion: props.nodes.length > 80 ? 300 : 360,
          edgeLength: [90, 220],
          gravity: 0.08,
          friction: 0.2,
        },
        emphasis: {
          focus: 'adjacency',
          scale: 1.15,
          label: {
            show: true,
            fontWeight: 700,
          },
        },
        lineStyle: {
          opacity: 0.9,
          curveness: 0.1,
        },
      },
    ],
  }
}

function initChart() {
  if (!chartContainer.value) return

  chart.value = echarts.init(chartContainer.value)
  chart.value.setOption(buildOption())

  chart.value.on(
    'click',
    'series',
    ((params: { dataType?: string; data?: { id?: string } }) => {
      if (params.dataType === 'node' && params.data?.id) {
        emit('nodeClick', params.data.id)
      }
    }) as any,
  )
}

function updateChart() {
  if (!chart.value) return
  chart.value.setOption(buildOption(), true)
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
  () => [props.nodes, props.edges],
  () => {
    updateChart()
  },
  { deep: true },
)
</script>

<template>
  <div ref="chartContainer" class="graph-renderer" />
</template>

<style lang="scss" scoped>
.graph-renderer {
  width: 100%;
  height: 100%;
  min-height: 460px;
}
</style>
