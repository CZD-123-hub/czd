import request from './request'
import type { ApiResponse, GraphData, GraphHealth, KnowledgeNode, RelatedDocument } from '@/types'

// 获取整张知识图谱快照，用于图谱页首屏展示。
export function getOverview() {
  return request.get<ApiResponse<GraphData>>('/graph/overview')
}

// 获取单个节点详情数据（详情面板）。
export function getNodeDetail(nodeId: string) {
  return request.get<ApiResponse<KnowledgeNode>>(`/graph/node/${nodeId}`)
}

// 获取某节点的邻域子图，depth 由后端控制。
export function getNeighbors(nodeId: string, depth = 1) {
  return request.get<ApiResponse<GraphData>>(`/graph/node/${nodeId}/neighbors`, {
    params: { depth },
  })
}

// 图谱全文搜索（节点及相关边）。
export function searchNodes(keyword: string) {
  return request.get<ApiResponse<GraphData>>('/graph/search', {
    params: { keyword },
  })
}

// 获取图谱健康指标（健康面板）。
export function getHealth() {
  return request.get<ApiResponse<GraphHealth>>('/graph/health')
}

// 获取节点关联知识文档。
export function getRelatedDocuments(nodeId: string, limit = 6) {
  return request.get<ApiResponse<RelatedDocument[]>>(`/graph/node/${nodeId}/related-documents`, {
    params: { limit },
  })
}

// 在 Neo4j 中创建一个 Knowledge 节点。
export function createNode(payload: {
  id: string
  name: string
  category: string
  difficulty: string
  description?: string
  keywords?: string[]
}) {
  return request.post<ApiResponse<KnowledgeNode>>('/graph/node', payload)
}

// 在 Neo4j 中创建一条有向关系。
export function createRelation(payload: {
  sourceId: string
  targetId: string
  relationType: string
}) {
  return request.post<ApiResponse<{ source: string; target: string; type: string }>>('/graph/relation', payload)
}

// 按 id 删除节点（包含其关联关系）。
export function deleteNode(nodeId: string) {
  return request.delete<ApiResponse<null>>(`/graph/node/${nodeId}`)
}

// 按 source/target/type 删除一条有向关系。
export function deleteRelation(payload: {
  sourceId: string
  targetId: string
  relationType: string
}) {
  return request.delete<ApiResponse<null>>('/graph/relation', {
    data: payload,
  })
}
