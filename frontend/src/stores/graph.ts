import { defineStore } from 'pinia'
import { ref } from 'vue'
import * as graphApi from '@/api/graph'
import type { KnowledgeNode, GraphEdge } from '@/types'

export const useGraphStore = defineStore('graph', () => {
  const nodes = ref<KnowledgeNode[]>([])
  const edges = ref<GraphEdge[]>([])
  const selectedNode = ref<KnowledgeNode | null>(null)
  const loading = ref(false)

  async function loadOverview() {
    loading.value = true
    try {
      const res = await graphApi.getOverview()
      nodes.value = res.data.data.nodes
      edges.value = res.data.data.edges
    } catch {
      nodes.value = []
      edges.value = []
    } finally {
      loading.value = false
    }
  }

  async function loadNeighbors(nodeId: string) {
    loading.value = true
    try {
      const res = await graphApi.getNeighbors(nodeId)
      const newNodes = res.data.data.nodes
      const newEdges = res.data.data.edges

      // Merge nodes (avoid duplicates)
      for (const n of newNodes) {
        if (!nodes.value.find((existing) => existing.id === n.id)) {
          nodes.value.push(n)
        }
      }
      // Merge edges (avoid duplicates)
      for (const e of newEdges) {
        if (
          !edges.value.find(
            (existing) => existing.source === e.source && existing.target === e.target && existing.type === e.type,
          )
        ) {
          edges.value.push(e)
        }
      }
    } finally {
      loading.value = false
    }
  }

  async function selectNode(nodeId: string) {
    loading.value = true
    try {
      const res = await graphApi.getNodeDetail(nodeId)
      selectedNode.value = res.data.data
    } catch {
      selectedNode.value = null
    } finally {
      loading.value = false
    }
  }

  async function search(keyword: string) {
    loading.value = true
    try {
      const res = await graphApi.searchNodes(keyword)
      nodes.value = res.data.data.nodes
      edges.value = res.data.data.edges
    } catch {
      nodes.value = []
      edges.value = []
    } finally {
      loading.value = false
    }
  }

  function clearSelection() {
    selectedNode.value = null
  }

  return {
    nodes,
    edges,
    selectedNode,
    loading,
    loadOverview,
    loadNeighbors,
    selectNode,
    search,
    clearSelection,
  }
})