import request from './request'
import type { ApiResponse } from '@/types'

export interface KnowledgeDocument {
  id?: number
  title: string
  content: string
  category?: string
  createdAt?: string
}

export const documentApi = {
  list: () => request.get<ApiResponse<KnowledgeDocument[]>>('/documents'),
  add: (data: KnowledgeDocument) => request.post<ApiResponse<KnowledgeDocument>>('/documents', data),
  delete: (id: number) => request.delete<ApiResponse<null>>(`/documents/${id}`),
}
