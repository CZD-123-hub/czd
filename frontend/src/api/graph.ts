import request from './request'
import type { ApiResponse, GraphData, KnowledgeNode } from '@/types'

export function getOverview() {
  return request.get<ApiResponse<GraphData>>('/graph/overview')
}

export function getNodeDetail(nodeId: string) {
  return request.get<ApiResponse<KnowledgeNode>>(`/graph/nodes/${nodeId}`)
}

export function getNeighbors(nodeId: string, depth = 1) {
  return request.get<ApiResponse<GraphData>>(`/graph/nodes/${nodeId}/neighbors`, {
    params: { depth },
  })
}

export function searchNodes(keyword: string) {
  return request.get<ApiResponse<GraphData>>('/graph/search', {
    params: { keyword },
  })
}