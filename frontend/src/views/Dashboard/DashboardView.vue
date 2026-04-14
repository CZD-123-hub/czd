<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useProgressStore } from '@/stores/progress'
import HeatmapChart from '@/components/dashboard/HeatmapChart.vue'
import RadarChart from '@/components/dashboard/RadarChart.vue'
import {
  Calendar,
  ChatDotRound,
  DocumentCopy,
  DataAnalysis,
  Download,
  TrendCharts,
  Warning,
  Checked,
} from '@element-plus/icons-vue'

const progressStore = useProgressStore()
const currentYear = ref(new Date().getFullYear())

onMounted(async () => {
  await Promise.all([
    progressStore.loadDashboard(),
    progressStore.loadHeatmap(currentYear.value),
    progressStore.loadRadar(),
    progressStore.loadSmartInsights(),
  ])
})

function handleYearChange(year: number) {
  currentYear.value = year
  progressStore.loadHeatmap(year)
}

function handleExportReport() {
  progressStore.downloadReport()
}

function togglePlan(planId: string, completed: boolean) {
  progressStore.toggleWeeklyPlanItem(planId, completed)
}

const statCards = computed(() => {
  const d = progressStore.dashboard
  return [
    { title: '学习天数', value: d?.totalDays ?? 0, unit: '天', icon: Calendar, color: '#2f6bff', bg: '#edf3ff' },
    { title: '对话次数', value: d?.totalChats ?? 0, unit: '次', icon: ChatDotRound, color: '#2563eb', bg: '#eaf1ff' },
    { title: '代码片段', value: d?.totalSnippets ?? 0, unit: '个', icon: DocumentCopy, color: '#0ea5e9', bg: '#e6f7ff' },
    { title: '知识覆盖率', value: d?.knowledgeCoverage ?? 0, unit: '%', icon: DataAnalysis, color: '#4f46e5', bg: '#ecebff' },
  ]
})

const yearOptions = computed(() => {
  const years: number[] = []
  const current = new Date().getFullYear()
  for (let y = current; y >= current - 3; y -= 1) years.push(y)
  return years
})

const healthColor = computed(() => {
  const score = progressStore.smartInsights?.healthScore ?? 0
  if (score >= 80) return '#2f9b5d'
  if (score >= 60) return '#d08b14'
  return '#d1444e'
})

const healthLabel = computed(() => {
  const score = progressStore.smartInsights?.healthScore ?? 0
  if (score >= 80) return '状态优秀，保持节奏'
  if (score >= 60) return '状态平稳，可继续加强'
  return '建议优化学习强度与连续性'
})
</script>

<template>
  <div class="dashboard-view page-shell" v-loading="progressStore.loading && !progressStore.dashboard">
    <div class="page-head">
      <div class="page-title-block">
        <h2 class="page-title">学习数据看板</h2>
        <p class="page-subtitle">聚合你的学习行为、知识掌握与改进建议</p>
      </div>
      <div class="page-toolbar">
        <el-select v-model="currentYear" size="small" style="width: 106px" @change="handleYearChange">
          <el-option v-for="year in yearOptions" :key="year" :label="`${year}年`" :value="year" />
        </el-select>
        <el-button type="primary" :icon="Download" @click="handleExportReport">导出学习报告</el-button>
      </div>
    </div>

    <div class="dashboard-content">
      <div class="stats-row">
        <div v-for="card in statCards" :key="card.title" class="stat-card soft-panel">
          <div class="stat-icon" :style="{ backgroundColor: card.bg }">
            <el-icon :size="24" :color="card.color"><component :is="card.icon" /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-value">{{ card.value }}<span class="stat-unit">{{ card.unit }}</span></div>
            <div class="stat-title">{{ card.title }}</div>
          </div>
        </div>
      </div>

      <div class="smart-row">
        <section class="smart-card soft-panel">
          <div class="smart-header">
            <h3><el-icon><TrendCharts /></el-icon> 学习健康度</h3>
          </div>
          <div class="health-body">
            <el-progress
              type="dashboard"
              :percentage="progressStore.smartInsights?.healthScore ?? 0"
              :color="healthColor"
              :stroke-width="14"
            />
            <p class="health-tip">{{ healthLabel }}</p>
          </div>
        </section>

        <section class="smart-card soft-panel">
          <div class="smart-header">
            <h3><el-icon><Warning /></el-icon> 待提升领域 Top 3</h3>
          </div>
          <div class="weak-list">
            <div v-for="area in progressStore.smartInsights?.weakAreas || []" :key="area.key" class="weak-item">
              <div class="weak-top">
                <span class="weak-name">{{ area.name }}</span>
                <el-tag size="small" :type="area.score >= 80 ? 'success' : area.score >= 60 ? 'warning' : 'danger'">
                  {{ area.score }} 分
                </el-tag>
              </div>
              <p class="weak-desc">{{ area.suggestion }}</p>
            </div>
            <el-empty v-if="(progressStore.smartInsights?.weakAreas || []).length === 0" description="暂无薄弱项" :image-size="68" />
          </div>
        </section>

        <section class="smart-card soft-panel">
          <div class="smart-header">
            <h3><el-icon><Checked /></el-icon> 本周行动计划</h3>
          </div>
          <div class="plan-list">
            <div v-for="plan in progressStore.smartInsights?.weeklyPlan || []" :key="plan.id" class="plan-item">
              <el-checkbox :model-value="plan.completed" @change="(val) => togglePlan(plan.id, Boolean(val))">
                <span class="plan-title">{{ plan.title }}</span>
              </el-checkbox>
              <p class="plan-desc">{{ plan.description }}</p>
            </div>
            <el-empty v-if="(progressStore.smartInsights?.weeklyPlan || []).length === 0" description="暂无计划项" :image-size="68" />
          </div>
        </section>
      </div>

      <div class="charts-row">
        <section class="chart-card soft-panel">
          <div class="chart-header">
            <h3>学习活跃度（{{ currentYear }}）</h3>
          </div>
          <div class="chart-body">
            <HeatmapChart :data="progressStore.heatmap" />
          </div>
        </section>

        <section class="chart-card soft-panel">
          <div class="chart-header">
            <h3>知识掌握雷达图</h3>
          </div>
          <div class="chart-body">
            <RadarChart :data="progressStore.radar" />
          </div>
        </section>
      </div>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.dashboard-view {
  min-height: 0;
}

.dashboard-content {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.stats-row {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 12px;
}

.stat-card {
  padding: 16px;
  display: flex;
  align-items: center;
  gap: 14px;
}

.stat-icon {
  width: 54px;
  height: 54px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.stat-value {
  font-size: 28px;
  font-weight: 700;
  color: var(--text-primary);
  line-height: 1.2;
}

.stat-unit {
  font-size: 13px;
  font-weight: 500;
  color: var(--text-secondary);
  margin-left: 3px;
}

.stat-title {
  font-size: 13px;
  color: var(--text-secondary);
  margin-top: 4px;
}

.smart-row {
  display: grid;
  grid-template-columns: repeat(3, minmax(280px, 1fr));
  gap: 12px;
}

.smart-card {
  padding: 14px;
}

.smart-header h3 {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 15px;
  margin-bottom: 10px;
}

.health-body {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}

.health-tip {
  color: var(--text-secondary);
  font-size: 12px;
  text-align: center;
}

.weak-list,
.plan-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-height: 160px;
}

.weak-item,
.plan-item {
  padding: 10px;
  border: 1px solid var(--border-lighter);
  border-radius: 10px;
  background: #fff;
}

.weak-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
}

.weak-name,
.plan-title {
  font-weight: 600;
  color: var(--text-primary);
}

.weak-desc,
.plan-desc {
  color: var(--text-secondary);
  font-size: 12px;
  margin-top: 6px;
  line-height: 1.55;
}

.charts-row {
  display: grid;
  grid-template-columns: 1.2fr 1fr;
  gap: 12px;
}

.chart-card {
  overflow: hidden;
}

.chart-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  border-bottom: 1px solid var(--border-lighter);

  h3 {
    font-size: 15px;
    font-weight: 700;
    color: var(--text-primary);
  }
}

.chart-body {
  padding: 12px;
}

@media (max-width: 1200px) {
  .smart-row {
    grid-template-columns: 1fr;
  }

  .charts-row {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .dashboard-content {
    padding: 10px;
  }

  .stat-card {
    padding: 12px;
  }

  .stat-value {
    font-size: 24px;
  }
}
</style>
