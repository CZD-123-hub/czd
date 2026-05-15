import request from './request'
import type { ApiResponse, CodeSnippet, PageResult, SnippetImportResult } from '@/types'

export function listSnippets(params: {
  page?: number
  size?: number
  keyword?: string
  language?: string
  tag?: string
}) {
  return request.get<ApiResponse<PageResult<CodeSnippet>>>('/snippets', { params })
}

export function createSnippet(data: Partial<CodeSnippet>) {
  return request.post<ApiResponse<CodeSnippet>>('/snippets', data)
}

export function updateSnippet(id: number, data: Partial<CodeSnippet>) {
  return request.put<ApiResponse<CodeSnippet>>(`/snippets/${id}`, data)
}

export function deleteSnippet(id: number) {
  return request.delete<ApiResponse<null>>(`/snippets/${id}`)
}

export function recommendSnippets(conversationId: number) {
  return request.get<ApiResponse<CodeSnippet[]>>('/snippets/recommend', {
    params: { conversationId },
  })
}

export function markSnippetUsed(id: number) {
  return request.post<ApiResponse<CodeSnippet>>(`/snippets/${id}/use`)
}

export function feedbackSnippet(id: number, rating: 'useful' | 'useless') {
  return request.post<ApiResponse<CodeSnippet>>(`/snippets/${id}/feedback`, null, {
    params: { rating },
  })
}

export function exportAll() {
  return request.get('/snippets/export', { responseType: 'blob' })
}

export function importFile(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post<ApiResponse<SnippetImportResult>>('/snippets/import', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}
