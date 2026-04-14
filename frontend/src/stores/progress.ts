import { defineStore } from 'pinia'
import { ref } from 'vue'
import * as progressApi from '@/api/progress'
import type { DashboardData, HeatmapData, RadarData, SmartInsights } from '@/types'
import { ElMessage } from 'element-plus'

export const useProgressStore = defineStore('progress', () => {
  const dashboard = ref<DashboardData | null>(null)
  const heatmap = ref<HeatmapData | null>(null)
  const radar = ref<RadarData | null>(null)
  const smartInsights = ref<SmartInsights | null>(null)
  const loading = ref(false)

  async function loadDashboard() {
    loading.value = true
    try {
      const res = await progressApi.getDashboard()
      dashboard.value = res.data.data
    } catch {
      dashboard.value = null
    } finally {
      loading.value = false
    }
  }

  async function loadHeatmap(year?: number) {
    try {
      const res = await progressApi.getHeatmap(year)
      heatmap.value = res.data.data
    } catch {
      heatmap.value = null
    }
  }

  async function loadRadar() {
    try {
      const res = await progressApi.getRadar()
      radar.value = res.data.data
    } catch {
      radar.value = null
    }
  }

  async function loadSmartInsights() {
    try {
      const res = await progressApi.getSmartInsights()
      smartInsights.value = res.data.data
    } catch {
      smartInsights.value = null
    }
  }

  async function toggleWeeklyPlanItem(planId: string, completed: boolean) {
    try {
      await progressApi.toggleWeeklyPlanItem(planId, completed)
      if (smartInsights.value) {
        const item = smartInsights.value.weeklyPlan.find((p) => p.id === planId)
        if (item) {
          item.completed = completed
        }
      }
      ElMessage.success(completed ? '计划已完成' : '已取消完成')
    } catch {
      ElMessage.error('更新计划状态失败')
    }
  }

  async function downloadReport() {
    try {
      const res = await progressApi.getReport()
      const blob = new Blob([res.data as BlobPart], { type: 'application/pdf' })
      const url = URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = url
      link.download = 'learning-report.pdf'
      link.click()
      URL.revokeObjectURL(url)
      ElMessage.success('报告下载成功')
    } catch {
      ElMessage.error('下载失败')
    }
  }

  return {
    dashboard,
    heatmap,
    radar,
    smartInsights,
    loading,
    loadDashboard,
    loadHeatmap,
    loadRadar,
    loadSmartInsights,
    toggleWeeklyPlanItem,
    downloadReport,
  }
})
