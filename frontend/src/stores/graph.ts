import { defineStore } from 'pinia'
import { ref } from 'vue'
import * as graphApi from '@/api/graph'
import type { KnowledgeNode, GraphEdge, GraphHealth } from '@/types'

type NeighborExpandResult = {
  addedNodes: number
  addedEdges: number
  depth: number
  reachedMaxDepth: boolean
  failed: boolean
}

type ApiLikeError = {
  response?: {
    status?: number
    data?: {
      message?: string
      code?: number
    }
  }
  message?: string
}

const MAX_EXPAND_DEPTH = 4
const HIGHLIGHT_DURATION_MS = 1800

export const useGraphStore = defineStore('graph', () => {
  // 图谱画布的数据源。
  const nodes = ref<KnowledgeNode[]>([])
  const edges = ref<GraphEdge[]>([])
  // 记录每个节点当前展开层级（用于增量展开邻居）。
  const expandedDepth = ref<Record<string, number>>({})
  // 右侧节点详情面板状态。
  const selectedNode = ref<KnowledgeNode | null>(null)
  const loading = ref(false)
  const detailLoading = ref(false)
  const detailError = ref('')
  const detailErrorCode = ref<number | null>(null)
  const loadError = ref('')
  const health = ref<GraphHealth | null>(null)
  const healthLoading = ref(false)
  const healthError = ref('')
  const recentlyAddedNodeIds = ref<string[]>([])
  let highlightTimer: ReturnType<typeof setTimeout> | null = null

  function clearRecentHighlights() {
    recentlyAddedNodeIds.value = []
    if (highlightTimer) {
      clearTimeout(highlightTimer)
      highlightTimer = null
    }
  }

  function setRecentHighlights(nodeIds: string[]) {
    clearRecentHighlights()
    if (nodeIds.length === 0) return
    recentlyAddedNodeIds.value = nodeIds
    highlightTimer = setTimeout(() => {
      recentlyAddedNodeIds.value = []
      highlightTimer = null
    }, HIGHLIGHT_DURATION_MS)
  }

  function normalizeError(err: unknown): ApiLikeError {
    return (err || {}) as ApiLikeError
  }

  function statusCodeOf(err: unknown): number {
    const normalized = normalizeError(err)
    return Number(normalized.response?.status || normalized.response?.data?.code || 0)
  }

  function messageOf(err: unknown): string {
    const normalized = normalizeError(err)
    return String(normalized.response?.data?.message || normalized.message || '').trim()
  }

  async function loadOverview() {
    // 从 /graph/overview 重置画布数据。
    loading.value = true
    loadError.value = ''
    try {
      const res = await graphApi.getOverview()
      nodes.value = res.data.data.nodes
      edges.value = res.data.data.edges
      expandedDepth.value = {}
      clearRecentHighlights()
    } catch {
      nodes.value = []
      edges.value = []
      loadError.value = '图谱加载失败，请检查网络后重试。'
    } finally {
      loading.value = false
    }
  }

  async function loadHealth() {
    // 加载高级面板中的图谱健康指标。
    healthLoading.value = true
    healthError.value = ''
    try {
      const res = await graphApi.getHealth()
      health.value = res.data.data
    } catch {
      health.value = null
      healthError.value = '图谱健康指标加载失败，请稍后重试。'
    } finally {
      healthLoading.value = false
    }
  }

  async function loadNeighbors(nodeId: string, options: { incremental?: boolean } = {}): Promise<NeighborExpandResult> {
    // `incremental=true` means expand one more layer, up to MAX_EXPAND_DEPTH.
    const currentDepth = expandedDepth.value[nodeId] || 0
    const nextDepth = options.incremental
      ? Math.min(currentDepth + 1, MAX_EXPAND_DEPTH)
      : Math.max(currentDepth, 1)

    if (options.incremental && nextDepth === currentDepth) {
      return {
        addedNodes: 0,
        addedEdges: 0,
        depth: currentDepth,
        reachedMaxDepth: true,
        failed: false,
      }
    }

    const prevNodeCount = nodes.value.length
    const prevEdgeCount = edges.value.length
    const addedNodeIds: string[] = []

    loading.value = true
    try {
      const res = await graphApi.getNeighbors(nodeId, nextDepth)
      const newNodes = res.data.data.nodes
      const newEdges = res.data.data.edges

      // 合并并去重，保留当前画布上已有数据。
      for (const n of newNodes) {
        if (!nodes.value.find((existing) => existing.id === n.id)) {
          nodes.value.push(n)
          addedNodeIds.push(n.id)
        }
      }
      for (const e of newEdges) {
        if (
          !edges.value.find(
            (existing) => existing.source === e.source && existing.target === e.target && existing.type === e.type,
          )
        ) {
          edges.value.push(e)
        }
      }

      expandedDepth.value[nodeId] = nextDepth
      // 对新合并进来的节点做短暂高亮。
      setRecentHighlights(addedNodeIds)
      return {
        addedNodes: nodes.value.length - prevNodeCount,
        addedEdges: edges.value.length - prevEdgeCount,
        depth: nextDepth,
        reachedMaxDepth: nextDepth >= MAX_EXPAND_DEPTH,
        failed: false,
      }
    } catch {
      loadError.value = '关联节点加载失败，可点击“继续展开关联节点”重试。'
      return {
        addedNodes: 0,
        addedEdges: 0,
        depth: currentDepth,
        reachedMaxDepth: currentDepth >= MAX_EXPAND_DEPTH,
        failed: true,
      }
    } finally {
      loading.value = false
    }
  }

  async function selectNode(nodeId: string) {
    // 详情面板加载状态与画布加载状态分离管理。
    detailLoading.value = true
    detailError.value = ''
    detailErrorCode.value = null
    selectedNode.value = null
    try {
      const res = await graphApi.getNodeDetail(nodeId)
      selectedNode.value = res.data.data
      return true
    } catch (error) {
      const status = statusCodeOf(error)
      detailErrorCode.value = status > 0 ? status : null
      selectedNode.value = null

      if (status === 404) {
        detailError.value = '该节点不存在或已被移除，请返回图谱重新选择。'
      } else if (status >= 500) {
        detailError.value = '服务暂时不可用，请稍后重试。'
      } else {
        detailError.value = messageOf(error) || '节点详情加载失败，请稍后重试。'
      }
      return false
    } finally {
      detailLoading.value = false
    }
  }

  async function search(keyword: string) {
    // 搜索结果会替换当前画布快照。
    loading.value = true
    loadError.value = ''
    try {
      const res = await graphApi.searchNodes(keyword)
      nodes.value = res.data.data.nodes
      edges.value = res.data.data.edges
      expandedDepth.value = {}
      clearRecentHighlights()
    } catch {
      nodes.value = []
      edges.value = []
      loadError.value = '搜索结果加载失败，请重试或先恢复全部图谱。'
    } finally {
      loading.value = false
    }
  }

  function getExpandedDepth(nodeId: string): number {
    // 供 UI 展示当前展开层级。
    return expandedDepth.value[nodeId] || 0
  }

  function hasReachedMaxDepth(nodeId: string): boolean {
    // 供 UI 在达到最大层级时禁用“继续展开”。
    return getExpandedDepth(nodeId) >= MAX_EXPAND_DEPTH
  }

  function clearSelection() {
    selectedNode.value = null
    detailError.value = ''
    detailErrorCode.value = null
    detailLoading.value = false
  }

  return {
    nodes,
    edges,
    health,
    healthLoading,
    healthError,
    selectedNode,
    loading,
    loadError,
    detailLoading,
    detailError,
    detailErrorCode,
    recentlyAddedNodeIds,
    loadOverview,
    loadHealth,
    loadNeighbors,
    selectNode,
    search,
    getExpandedDepth,
    hasReachedMaxDepth,
    clearSelection,
  }
})
