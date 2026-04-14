import request from './request'
import type { ApiResponse, DashboardData, HeatmapData, RadarData, SmartInsights } from '@/types'

export function getDashboard() {
  return request.get<ApiResponse<DashboardData>>('/progress/dashboard')
}

export function getHeatmap(year?: number) {
  return request.get<ApiResponse<HeatmapData>>('/progress/heatmap', {
    params: { year },
  })
}

export function getRadar() {
  return request.get<ApiResponse<RadarData>>('/progress/radar')
}

export function getSmartInsights() {
  return request.get<ApiResponse<SmartInsights>>('/progress/smart-insights')
}

export function toggleWeeklyPlanItem(planId: string, completed: boolean) {
  return request.post<ApiResponse<null>>('/progress/weekly-plan/toggle', {
    planId,
    completed,
  })
}

export function getReport() {
  return request.get('/progress/report', { responseType: 'blob' })
}
