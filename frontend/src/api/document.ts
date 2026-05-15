import request from './request'
import type { ApiResponse } from '@/types'
import type { RelatedGraphNode } from '@/types'

export interface KnowledgeDocument {
  id: number
  title: string
  content: string
  category?: string
  saved?: boolean
  createdAt?: string
}

export interface KnowledgeDocumentCreateRequest {
  title: string
  content: string
  category?: string
}

export const documentApi = {
  list: (savedOnly = false) =>
    request.get<ApiResponse<KnowledgeDocument[]>>('/documents', {
      params: { savedOnly },
    }),
  add: (data: KnowledgeDocumentCreateRequest) => request.post<ApiResponse<KnowledgeDocument>>('/documents', data),
  delete: (id: number) => request.delete<ApiResponse<null>>(`/documents/${id}`),
  favorite: (id: number, favorite = true) =>
    request.post<ApiResponse<null>>(`/documents/${id}/favorite`, null, {
      params: { favorite },
    }),
  relatedNodes: (id: number, limit = 8) =>
    request.get<ApiResponse<RelatedGraphNode[]>>(`/documents/${id}/related-nodes`, {
      params: { limit },
    }),
}
