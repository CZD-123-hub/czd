<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useProgressStore } from '@/stores/progress'
import HeatmapChart from '@/components/dashboard/HeatmapChart.vue'
import RadarChart from '@/components/dashboard/RadarChart.vue'
import AppFeedbackState from '@/components/common/AppFeedbackState.vue'
import type { MetricComparison } from '@/types'
import { ElMessage } from 'element-plus'
import {
  Calendar,
  ChatDotRound,
  DocumentCopy,
  DataAnalysis,
  Download,
  TrendCharts,
  Warning,
  Checked,
  QuestionFilled,
} from '@element-plus/icons-vue'

type StatMetricKey = 'totalDays' | 'totalChats' | 'totalSnippets' | 'knowledgeCoverage'

const progressStore = useProgressStore()
const currentYear = ref(new Date().getFullYear())

const actionKeyLabels: Record<string, string> = {
  chat: '问答互动',
  code_save: '代码沉淀',
  path_learn: '学习路径推进',
  graph_explore: '知识图谱探索',
  feedback: '效果反馈',
}

const metricTips: Record<StatMetricKey, string> = {
  totalDays: '累计产生学习行为的自然日总数。',
  totalChats: '累计发起问答会话总数。',
  totalSnippets: '累计沉淀到片段库的代码数。',
  knowledgeCoverage: '核心学习行为覆盖率，覆盖越多说明学习闭环越完整。',
}

const periodDays = computed(() => progressStore.dashboard?.periodDays ?? 30)

async function loadAllPanels() {
  progressStore.clearError()
  await Promise.all([
    progressStore.loadDashboard(),
    progressStore.loadHeatmap(currentYear.value),
    progressStore.loadRadar(),
    progressStore.loadSmartInsights(),
  ])
}

onMounted(async () => {
  await loadAllPanels()
})

async function loadBaselinePanels() {
  progressStore.clearError()
  await Promise.all([progressStore.loadDashboard(), progressStore.loadSmartInsights()])
}

function handleYearChange(year: number) {
  currentYear.value = year
  progressStore.loadHeatmap(year)
}

async function handleExportReport() {
  try {
    const blob = await progressStore.fetchReportBlob()
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = '学习数据报告.pdf'
    link.click()
    URL.revokeObjectURL(url)
    ElMessage.success('报告下载成功')
  } catch {
    ElMessage.error(progressStore.actionError || '下载失败')
  }
}

async function togglePlan(planId: string, completed: boolean) {
  try {
    await progressStore.toggleWeeklyPlanItem(planId, completed)
    ElMessage.success(completed ? '计划已完成' : '已取消完成')
  } catch {
    ElMessage.error(progressStore.actionError || '更新计划状态失败')
  }
}

async function handlePlanChange(planId: string, value: string | number | boolean) {
  await togglePlan(planId, Boolean(value))
}

function metricUnit(key: StatMetricKey): string {
  if (key === 'totalDays') return '天'
  if (key === 'totalSnippets') return '个'
  if (key === 'knowledgeCoverage') return '%'
  return '次'
}

function formatMetricValue(key: StatMetricKey, value: number): string {
  if (key === 'knowledgeCoverage') {
    return Number.isInteger(value) ? String(value) : value.toFixed(1)
  }
  return String(Math.round(value))
}

function formatComparisonValue(key: StatMetricKey, value: number): string {
  if (key === 'knowledgeCoverage') {
    return `${value.toFixed(1)}%`
  }
  return `${Math.round(value)}${metricUnit(key)}`
}

function formatDeltaValue(key: StatMetricKey, value: number): string {
  if (key === 'knowledgeCoverage') {
    return `${Math.abs(value).toFixed(1)}%`
  }
  return `${Math.abs(Math.round(value))}${metricUnit(key)}`
}

function comparisonDeltaText(key: StatMetricKey, comparison?: MetricComparison): string {
  if (!comparison) {
    return `近 ${periodDays.value} 天暂无环比数据`
  }
  if (comparison.trend === 'flat') {
    return `近 ${periodDays.value} 天与上期持平`
  }
  const verb = comparison.trend === 'up' ? '提升' : '下降'
  return `近 ${periodDays.value} 天较上期${verb} ${formatDeltaValue(key, comparison.delta)}`
}

function comparisonRangeText(key: StatMetricKey, comparison?: MetricComparison): string {
  if (!comparison) {
    return ''
  }
  return `本期 ${formatComparisonValue(key, comparison.current)} / 上期 ${formatComparisonValue(key, comparison.previous)}`
}

function comparisonClass(comparison?: MetricComparison): string {
  if (!comparison || comparison.trend === 'flat') return 'trend-flat'
  return comparison.trend === 'up' ? 'trend-up' : 'trend-down'
}

function weakAreaProgress(doneNodes?: number | null, totalNodes?: number | null): number {
  if (!totalNodes || totalNodes <= 0) return 0
  return Math.round(((doneNodes || 0) * 100) / totalNodes)
}

const statCards = computed(() => {
  const d = progressStore.dashboard
  return [
    {
      key: 'totalDays' as const,
      title: '学习天数',
      value: d?.totalDays ?? 0,
      unit: '天',
      icon: Calendar,
      color: '#2f6bff',
      bg: '#edf3ff',
      comparison: d?.comparisons?.totalDays,
      tip: metricTips.totalDays,
    },
    {
      key: 'totalChats' as const,
      title: '对话次数',
      value: d?.totalChats ?? 0,
      unit: '次',
      icon: ChatDotRound,
      color: '#2563eb',
      bg: '#eaf1ff',
      comparison: d?.comparisons?.totalChats,
      tip: metricTips.totalChats,
    },
    {
      key: 'totalSnippets' as const,
      title: '代码片段',
      value: d?.totalSnippets ?? 0,
      unit: '个',
      icon: DocumentCopy,
      color: '#0ea5e9',
      bg: '#e6f7ff',
      comparison: d?.comparisons?.totalSnippets,
      tip: metricTips.totalSnippets,
    },
    {
      key: 'knowledgeCoverage' as const,
      title: '知识覆盖率',
      value: d?.knowledgeCoverage ?? 0,
      unit: '%',
      icon: DataAnalysis,
      color: '#4f46e5',
      bg: '#ecebff',
      comparison: d?.comparisons?.knowledgeCoverage,
      tip: metricTips.knowledgeCoverage,
    },
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

const healthBreakdownRows = computed(() => {
  const breakdown = progressStore.smartInsights?.healthBreakdown
  if (!breakdown) return []
  return [
    {
      key: 'active-days',
      label: '活跃天数',
      value: `${breakdown.activeDays} 天`,
      score: `${breakdown.activeDaysScore}/40`,
    },
    {
      key: 'streak',
      label: '连续学习',
      value: `${breakdown.streak} 天`,
      score: `${breakdown.streakScore}/30`,
    },
    {
      key: 'avg-actions',
      label: '日均动作',
      value: `${breakdown.avgDailyActions.toFixed(2)} 次`,
      score: `${breakdown.avgActionsScore}/30`,
    },
  ]
})

const coverageDetail = computed(() => progressStore.dashboard?.coverageDetail)

const coveragePercent = computed(() => {
  const detail = coverageDetail.value
  if (!detail || !detail.totalCoreActions) return 0
  return Math.round((detail.coveredCoreActions * 100) / detail.totalCoreActions)
})

const coveredActionLabels = computed(() => {
  const keys = coverageDetail.value?.coveredActionKeys || []
  return keys.map((key) => actionKeyLabels[key] || key)
})

const examSummary = computed(() => progressStore.dashboard?.examSummary)
const latestExamDateText = computed(() => {
  const raw = examSummary.value?.latestSubmittedAt
  if (!raw) return '暂无'
  const date = new Date(raw)
  if (Number.isNaN(date.getTime())) return raw
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
})

const pageErrors = computed(() => Object.values(progressStore.errors))
const hasRecoverableError = computed(() => pageErrors.value.length > 0)
const recoverableErrorText = computed(() => pageErrors.value[pageErrors.value.length - 1] || '')
</script>

<template>
  <div class="dashboard-view page-shell" v-loading="progressStore.loading && !progressStore.dashboard">
    <div class="page-head">
      <div class="page-title-block">
        <h2 class="page-title">学习数据看板</h2>
        <p class="page-subtitle">聚合你的学习行为、知识掌握与改进建议（近 {{ periodDays }} 天趋势对比）</p>
      </div>
      <div class="page-toolbar">
        <el-select v-model="currentYear" size="small" style="width: 106px" @change="handleYearChange">
          <el-option v-for="year in yearOptions" :key="year" :label="`${year}年`" :value="year" />
        </el-select>
        <el-button type="primary" :icon="Download" @click="handleExportReport">导出学习报告</el-button>
      </div>
    </div>

    <div class="dashboard-content">
      <div v-if="hasRecoverableError" class="recover-box soft-panel">
        <AppFeedbackState
          type="error"
          :centered="false"
          title="看板部分模块加载失败"
          :description="recoverableErrorText || '请刷新后重试。'"
        >
          <template #actions>
            <el-button type="primary" size="small" @click="loadAllPanels">重试全部</el-button>
            <el-button size="small" @click="loadBaselinePanels">仅加载基础数据</el-button>
          </template>
        </AppFeedbackState>
      </div>

      <div class="stats-row">
        <div v-for="card in statCards" :key="card.title" class="stat-card soft-panel">
          <div class="stat-icon" :style="{ backgroundColor: card.bg }">
            <el-icon :size="24" :color="card.color"><component :is="card.icon" /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-value">{{ formatMetricValue(card.key, card.value) }}<span class="stat-unit">{{ card.unit }}</span></div>
            <div class="stat-title-row">
              <div class="stat-title">{{ card.title }}</div>
              <el-tooltip :content="card.tip" placement="top">
                <el-icon class="hint-icon"><QuestionFilled /></el-icon>
              </el-tooltip>
            </div>
            <div class="stat-compare" :class="comparisonClass(card.comparison)">{{ comparisonDeltaText(card.key, card.comparison) }}</div>
            <div v-if="card.comparison" class="stat-subline">{{ comparisonRangeText(card.key, card.comparison) }}</div>
            <div
              v-if="card.key === 'knowledgeCoverage' && coverageDetail"
              class="stat-subline coverage-quick"
            >
              核心行为覆盖 {{ coverageDetail.coveredCoreActions }}/{{ coverageDetail.totalCoreActions }}
            </div>
          </div>
        </div>
      </div>

      <section v-if="coverageDetail" class="coverage-panel soft-panel">
        <div class="coverage-head">
          <h3>结构化学习覆盖</h3>
          <span>{{ coverageDetail.coveredCoreActions }}/{{ coverageDetail.totalCoreActions }}</span>
        </div>
        <el-progress :percentage="coveragePercent" :stroke-width="10" :show-text="false" color="#2f6bff" />
        <div class="coverage-tags">
          <el-tag v-for="label in coveredActionLabels" :key="label" size="small" effect="plain">{{ label }}</el-tag>
          <span v-if="coveredActionLabels.length === 0" class="coverage-empty">当前暂无核心行为覆盖</span>
        </div>
      </section>

      <section v-if="examSummary" class="exam-panel soft-panel">
        <div class="exam-panel-header">
          <h3>测评结果回流</h3>
          <el-tag size="small" :type="(examSummary.latestScore || 0) >= 80 ? 'success' : (examSummary.latestScore || 0) >= 60 ? 'warning' : 'danger'">
            最新等级：{{ examSummary.latestGrade || '暂无' }}
          </el-tag>
        </div>
        <div class="exam-metrics">
          <div class="exam-metric">
            <span class="label">最近成绩</span>
            <strong class="value">{{ examSummary.latestScore ?? '--' }}</strong>
          </div>
          <div class="exam-metric">
            <span class="label">平均分</span>
            <strong class="value">{{ examSummary.avgScore?.toFixed(1) ?? '--' }}</strong>
          </div>
          <div class="exam-metric">
            <span class="label">及格率</span>
            <strong class="value">{{ examSummary.passRate?.toFixed(1) ?? '--' }}%</strong>
          </div>
          <div class="exam-metric">
            <span class="label">完成次数</span>
            <strong class="value">{{ examSummary.completedCount ?? 0 }}</strong>
          </div>
          <div class="exam-metric">
            <span class="label">最近测评日期</span>
            <strong class="value">{{ latestExamDateText }}</strong>
          </div>
        </div>
        <div v-if="(examSummary.recentTrend || []).length > 0" class="exam-trend">
          <span class="trend-title">近几次趋势</span>
          <div class="trend-list">
            <div v-for="item in examSummary.recentTrend" :key="`${item.date}-${item.score}`" class="trend-item">
              <span class="date">{{ item.date }}</span>
              <span class="score">{{ item.score }} 分</span>
              <span class="grade">{{ item.grade }}</span>
            </div>
          </div>
        </div>
      </section>

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
          <div v-if="healthBreakdownRows.length" class="health-breakdown">
            <div v-for="row in healthBreakdownRows" :key="row.key" class="health-row">
              <span class="health-row-label">{{ row.label }}</span>
              <span class="health-row-value">{{ row.value }}</span>
              <span class="health-row-score">{{ row.score }}</span>
            </div>
          </div>
        </section>

        <section class="smart-card soft-panel">
          <div class="smart-header">
            <h3><el-icon><Warning /></el-icon> 待提升领域前三</h3>
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
              <div v-if="area.totalNodes && area.totalNodes > 0" class="weak-progress-wrap">
                <div class="weak-progress-caption">已完成 {{ area.doneNodes || 0 }}/{{ area.totalNodes }} 节点</div>
                <el-progress
                  :percentage="weakAreaProgress(area.doneNodes, area.totalNodes)"
                  :stroke-width="6"
                  :show-text="false"
                />
              </div>
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
              <el-checkbox :model-value="plan.completed" @change="handlePlanChange(plan.id, $event)">
                <span class="plan-title">{{ plan.title }}</span>
              </el-checkbox>
              <p class="plan-desc">{{ plan.description }}</p>
              <p v-if="plan.expectedImpact" class="plan-impact">预期收益：{{ plan.expectedImpact }}</p>
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
            <h3>知识掌握雷达图（近 {{ progressStore.radar?.periodDays ?? periodDays }} 天）</h3>
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

.recover-box {
  padding: 10px;
}

.stats-row {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 12px;
}

.stat-card {
  padding: 16px;
  display: flex;
  align-items: flex-start;
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

.stat-info {
  flex: 1;
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

.stat-title-row {
  margin-top: 4px;
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.stat-title {
  font-size: 13px;
  color: var(--text-secondary);
}

.hint-icon {
  color: #90a0bc;
  cursor: help;
}

.stat-compare {
  margin-top: 6px;
  font-size: 12px;
  font-weight: 600;
}

.trend-up {
  color: #2f9b5d;
}

.trend-down {
  color: #d1444e;
}

.trend-flat {
  color: #73809a;
}

.stat-subline {
  margin-top: 4px;
  font-size: 12px;
  color: var(--text-secondary);
}

.coverage-quick {
  color: #4f5f82;
}

.coverage-panel {
  padding: 12px 14px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.coverage-head {
  display: flex;
  align-items: center;
  justify-content: space-between;

  h3 {
    font-size: 14px;
    font-weight: 700;
    color: var(--text-primary);
    margin: 0;
  }

  span {
    font-size: 13px;
    color: #4f5f82;
    font-weight: 600;
  }
}

.coverage-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.coverage-empty {
  font-size: 12px;
  color: var(--text-secondary);
}

.exam-panel {
  padding: 12px 14px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.exam-panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;

  h3 {
    margin: 0;
    font-size: 14px;
    font-weight: 700;
    color: var(--text-primary);
  }
}

.exam-metrics {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 8px;
}

.exam-metric {
  border: 1px solid var(--border-lighter);
  border-radius: 10px;
  padding: 8px 10px;
  background: #fff;
  display: flex;
  flex-direction: column;
  gap: 4px;

  .label {
    font-size: 12px;
    color: var(--text-secondary);
  }

  .value {
    font-size: 14px;
    color: var(--text-primary);
  }
}

.exam-trend {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.trend-title {
  font-size: 12px;
  color: var(--text-secondary);
}

.trend-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.trend-item {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 4px 10px;
  border-radius: 999px;
  border: 1px solid #d8e5ff;
  background: #f7faff;
  font-size: 12px;
  color: #4f5f82;

  .score {
    font-weight: 700;
    color: #2f6bff;
  }

  .grade {
    color: var(--text-primary);
    font-weight: 600;
  }
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

.health-breakdown {
  margin-top: 10px;
  border-top: 1px solid var(--border-lighter);
  padding-top: 10px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.health-row {
  display: grid;
  grid-template-columns: 1fr auto auto;
  gap: 10px;
  align-items: center;
  font-size: 12px;
}

.health-row-label {
  color: var(--text-secondary);
}

.health-row-value {
  color: var(--text-primary);
  font-weight: 600;
}

.health-row-score {
  color: #2f6bff;
  font-weight: 700;
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

.weak-progress-wrap {
  margin-top: 8px;
}

.weak-progress-caption {
  font-size: 12px;
  color: #4f5f82;
  margin-bottom: 6px;
}

.plan-impact {
  margin-top: 6px;
  font-size: 12px;
  line-height: 1.55;
  color: #2f6bff;
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
  .exam-metrics {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .smart-row {
    grid-template-columns: 1fr;
  }

  .charts-row {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .exam-metrics {
    grid-template-columns: 1fr;
  }

  .dashboard-content {
    padding: 10px;
  }

  .stat-card {
    padding: 12px;
  }

  .stat-value {
    font-size: 24px;
  }

  .recover-box {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
