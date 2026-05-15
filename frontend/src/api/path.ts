import request from './request'
import type { ApiResponse, LearningPath, NodeStatus } from '@/types'

export function generatePath(target: string, knownKnowledgeIds: string[] = []) {
  return request.post<ApiResponse<LearningPath>>('/path/generate', {
    target,
    knownKnowledgeIds,
  })
}

export function listPaths() {
  return request.get<ApiResponse<LearningPath[]>>('/path/list')
}

export function updateNodeStatus(nodeId: number, status: NodeStatus) {
  return request.put<ApiResponse<null>>(`/path/node/${nodeId}/status`, { status })
}

export function deletePath(pathId: number) {
  return request.delete<ApiResponse<null>>(`/path/${pathId}`)
}
