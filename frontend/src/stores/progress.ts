import { defineStore } from 'pinia'
import { ref } from 'vue'
import * as progressApi from '@/api/progress'
import type { DashboardData, HeatmapData, RadarData, SmartInsights } from '@/types'
import { resolveErrorMessage } from '@/utils/errorMessage'

export const useProgressStore = defineStore('progress', () => {
  const dashboard = ref<DashboardData | null>(null)
  const heatmap = ref<HeatmapData | null>(null)
  const radar = ref<RadarData | null>(null)
  const smartInsights = ref<SmartInsights | null>(null)
  const loading = ref(false)
  const errors = ref<Record<string, string>>({})
  const actionError = ref('')

  function setError(scope: string, message: string) {
    errors.value = { ...errors.value, [scope]: message }
  }

  function clearError(scope?: string) {
    if (!scope) {
      errors.value = {}
      return
    }
    const next = { ...errors.value }
    delete next[scope]
    errors.value = next
  }

  function getError(scope: string) {
    return errors.value[scope] || ''
  }

  function clearActionError() {
    actionError.value = ''
  }

  async function loadDashboard() {
    loading.value = true
    try {
      const res = await progressApi.getDashboard()
      dashboard.value = res.data.data
      clearError('dashboard')
    } catch (error) {
      setError('dashboard', resolveErrorMessage(error, '学习概览加载失败，请重试。'))
      dashboard.value = null
    } finally {
      loading.value = false
    }
  }

  async function loadHeatmap(year?: number) {
    try {
      const res = await progressApi.getHeatmap(year)
      heatmap.value = res.data.data
      clearError('heatmap')
    } catch (error) {
      setError('heatmap', resolveErrorMessage(error, '活跃热力图加载失败，请稍后重试。'))
      heatmap.value = null
    }
  }

  async function loadRadar() {
    try {
      const res = await progressApi.getRadar()
      radar.value = res.data.data
      clearError('radar')
    } catch (error) {
      setError('radar', resolveErrorMessage(error, '雷达图加载失败，可先查看基础数据。'))
      radar.value = null
    }
  }

  async function loadSmartInsights() {
    try {
      const res = await progressApi.getSmartInsights()
      smartInsights.value = res.data.data
      clearError('insights')
    } catch (error) {
      setError('insights', resolveErrorMessage(error, '学习建议加载失败，请稍后重试。'))
      smartInsights.value = null
    }
  }

  async function toggleWeeklyPlanItem(planId: string, completed: boolean): Promise<boolean> {
    clearActionError()
    try {
      await progressApi.toggleWeeklyPlanItem(planId, completed)
      if (smartInsights.value) {
        const item = smartInsights.value.weeklyPlan.find((p) => p.id === planId)
        if (item) {
          item.completed = completed
        }
      }
      return true
    } catch (error) {
      actionError.value = resolveErrorMessage(error, '更新计划状态失败，请稍后重试。')
      throw error
    }
  }

  async function fetchReportBlob(): Promise<Blob> {
    clearActionError()
    try {
      const res = await progressApi.getReport()
      return new Blob([res.data as BlobPart], { type: 'application/pdf' })
    } catch (error) {
      actionError.value = resolveErrorMessage(error, '导出报告失败，请稍后重试。')
      throw error
    }
  }

  return {
    dashboard,
    heatmap,
    radar,
    smartInsights,
    loading,
    errors,
    actionError,
    loadDashboard,
    loadHeatmap,
    loadRadar,
    loadSmartInsights,
    toggleWeeklyPlanItem,
    fetchReportBlob,
    setError,
    clearError,
    getError,
    clearActionError,
  }
})
