<script setup lang="ts">
import { ref, watch, onMounted, onBeforeUnmount, shallowRef, computed } from 'vue'
import { init, type EChartsCoreOption, type EChartsType } from '@/utils/echarts'
import type { KnowledgeNode, GraphEdge } from '@/types'
import {
  normalizeDifficulty,
  difficultyLabel,
  difficultyNodeColor,
  difficultyNodeShadowColor,
} from '@/utils/graphDifficulty'
import { relationDisplayLabel } from '@/utils/graphRelation'

const props = defineProps<{
  nodes: KnowledgeNode[]
  edges: GraphEdge[]
  highlightNodeIds?: string[]
  highlightEdgeKeys?: string[]
}>()

const emit = defineEmits<{
  nodeClick: [nodeId: string]
}>()

const chartContainer = ref<HTMLDivElement | null>(null)
const chart = shallowRef<EChartsType | null>(null)

const categories = computed(() => {
  const set = new Set<string>()
  for (const node of props.nodes) {
    set.add(node.category || '未分类')
  }
  return Array.from(set)
})

function difficultyToSize(level?: string) {
  const normalized = normalizeDifficulty(level)
  if (normalized === 'hard') return 54
  if (normalized === 'medium') return 44
  return 34
}

function normalizeRelationType(type?: string): string {
  return (type || '').trim().toUpperCase()
}

function edgeKey(edge: { source: string; target: string; type?: string }): string {
  return `${edge.source}|${normalizeRelationType(edge.type)}|${edge.target}`
}

function buildOption(): EChartsCoreOption {
  // 将业务节点/边数据映射为 ECharts 图谱节点/连线。
  const categoryIndex = new Map<string, number>()
  categories.value.forEach((name, idx) => categoryIndex.set(name, idx))

  const showLabel = props.nodes.length <= 120
  const highlightSet = new Set(props.highlightNodeIds || [])
  const highlightEdgeSet = new Set(props.highlightEdgeKeys || [])

  const graphNodes = props.nodes.map((node) => {
    const name = node.category || '未分类'
    const idx = categoryIndex.get(name) ?? 0
    const difficulty = normalizeDifficulty(node.difficulty)

    return {
      id: node.id,
      name: node.name,
      category: idx,
      value: node.description,
      difficultyLevel: difficulty,
      difficultyLabel: difficultyLabel(node.difficulty),
      symbolSize: difficultyToSize(node.difficulty),
      itemStyle: {
        color: difficultyNodeColor(node.difficulty),
        borderColor: highlightSet.has(node.id) ? '#2f6bff' : '#ffffff',
        borderWidth: highlightSet.has(node.id) ? 3 : 1.5,
        shadowBlur: highlightSet.has(node.id) ? 24 : 12,
        shadowColor: highlightSet.has(node.id) ? 'rgba(47,107,255,0.62)' : difficultyNodeShadowColor(node.difficulty),
      },
      label: {
        show: showLabel,
        fontSize: 12,
        color: '#1f2f4a',
      },
    }
  })

  const graphEdges = props.edges.map((edge) => ({
    key: edgeKey(edge),
    source: edge.source,
    target: edge.target,
    value: edge.type,
    label: relationDisplayLabel(edge.type),
    lineStyle: {
      width: highlightEdgeSet.has(edgeKey(edge)) ? 2.8 : 1.1,
      color: highlightEdgeSet.has(edgeKey(edge)) ? 'rgba(47,107,255,0.78)' : 'rgba(107, 123, 157, 0.42)',
      curveness: 0.12,
    },
    emphasis: {
      lineStyle: {
        width: highlightEdgeSet.has(edgeKey(edge)) ? 3.2 : 2.2,
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
          const diff = params.data?.difficultyLabel || '基础'
          return `<div style="max-width:260px"><strong>${params.name}</strong><br/>难度：${diff}<br/>${desc || '暂无描述'}</div>`
        }

        if (params.dataType === 'edge') {
          const label = relationDisplayLabel(params.data?.value)
          return `关系：${label}`
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
        categories: categories.value.map((name) => ({
          name,
          itemStyle: { color: '#7f95c7' },
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
          edgeLabel: {
            show: true,
            color: '#4b5f86',
            fontSize: 11,
            formatter: (params: any) => relationDisplayLabel(params.data?.value),
          },
        },
        edgeLabel: {
          show: false,
          color: '#6f7f9e',
          fontSize: 10,
          formatter: (params: any) => relationDisplayLabel(params.data?.value),
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

  chart.value = init(chartContainer.value)
  chart.value.setOption(buildOption())

  // 将节点点击事件抛给父组件，用于加载详情。
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
  // 全量刷新 option，确保力导布局与筛选后数据一致。
  chart.value.setOption(buildOption(), {
    notMerge: true,
    lazyUpdate: true,
  })
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
  () => [props.nodes, props.edges, props.highlightNodeIds, props.highlightEdgeKeys],
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
