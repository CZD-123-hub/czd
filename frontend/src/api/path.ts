import request from './request'
import type { ApiResponse, LearningPath } from '@/types'

export function generatePath(target: string, knownKnowledge: string[] = []) {
  return request.post<ApiResponse<LearningPath>>('/path/generate', {
    target,
    knownKnowledgeIds: knownKnowledge,
  })
}

export function listPaths() {
  return request.get<ApiResponse<LearningPath[]>>('/path/list')
}

export function updateNodeStatus(_pathId: number, nodeId: number, status: string) {
  return request.put<ApiResponse<null>>(`/path/node/${nodeId}/status`, { status })
}