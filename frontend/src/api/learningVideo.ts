import request from './request'
import type { ApiResponse, LearningVideo, PageResult } from '@/types'

export interface OnlineVideoSearchItem {
  externalId: string
  title: string
  description: string
  platform: string
  url: string
  embedUrl?: string
  coverUrl?: string
  durationSeconds?: number
  tags?: string[]
}

export interface OnlineVideoImportRequest {
  title: string
  description?: string
  platform?: string
  url: string
  coverUrl?: string
  durationSeconds?: number
  embedUrl?: string
  knowledgeId?: string
  tags?: string[]
  favorite?: boolean
}

export function searchLearningVideos(keyword = '', page = 1, size = 12, localFileOnly = false) {
  return request.get<ApiResponse<PageResult<LearningVideo>>>('/learning-videos/search', {
    params: { keyword, page, size, localFileOnly },
  })
}

export function getLearningVideoDetail(id: number) {
  return request.get<ApiResponse<LearningVideo>>(`/learning-videos/${id}`)
}

export function getFavoriteLearningVideos(size = 24) {
  return request.get<ApiResponse<LearningVideo[]>>('/learning-videos/favorites', {
    params: { size },
  })
}

export function getLearningVideoHistory(size = 24) {
  return request.get<ApiResponse<LearningVideo[]>>('/learning-videos/history', {
    params: { size },
  })
}

export function toggleLearningVideoFavorite(id: number, favorite: boolean) {
  return request.post<ApiResponse<null>>(`/learning-videos/${id}/favorite`, { favorite })
}

export function recordLearningVideoWatch(id: number, watchedSeconds: number) {
  return request.post<ApiResponse<null>>(`/learning-videos/${id}/watch`, { watchedSeconds })
}

export function deleteLearningVideoHistory(id: number) {
  return request.delete<ApiResponse<null>>(`/learning-videos/${id}/history`)
}

export function recommendLearningVideos(query = '', conversationId?: number, size = 6) {
  return request.get<ApiResponse<LearningVideo[]>>('/learning-videos/recommend', {
    params: {
      query,
      conversationId,
      size,
    },
  })
}

export function searchOnlineLearningVideos(keyword = '', size = 10, platform = 'bilibili') {
  return request.get<ApiResponse<OnlineVideoSearchItem[]>>('/learning-videos/search-online', {
    params: { keyword, size, platform },
  })
}

export function importOnlineLearningVideo(data: OnlineVideoImportRequest) {
  return request.post<ApiResponse<LearningVideo>>('/learning-videos/import-online', data)
}

export function uploadLocalLearningVideo(file: File, title = '', favorite = false) {
  const formData = new FormData()
  formData.append('file', file)
  if (title.trim()) {
    formData.append('title', title.trim())
  }
  formData.append('favorite', String(favorite))
  return request.post<ApiResponse<LearningVideo>>('/learning-videos/upload-local', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}
