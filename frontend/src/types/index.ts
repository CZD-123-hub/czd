// API Response
export interface ApiError {
  bizCode: string
  details?: unknown
}

export interface ApiResponse<T> {
  code: number
  message: string
  data: T
  error?: ApiError | null
  traceId?: string
  timestamp?: string
  path?: string
}

export interface PageResult<T> {
  records: T[]
  total: number
  page: number
  size: number
}

// Auth
export interface LoginForm {
  username: string
  password: string
}

export interface RegisterForm {
  username: string
  password: string
  email: string
}

export interface UserInfo {
  id: number
  username: string
  email: string
  avatar: string
  level: string
  createdAt: string
}

export interface UserProfileSummary {
  level: string
  primaryGoal: string
  weakPoints: string[]
  preferredLanguages: string[]
  learningStyle: string
  nextTaskHint: string
  updatedAt: string
}

export interface TokenInfo {
  token: string
  user: UserInfo
}

// Chat
export interface Conversation {
  id: number
  title: string
  createdAt: string
  updatedAt: string
}

export interface Message {
  id: number
  conversationId: number
  role: 'user' | 'assistant'
  content: string
  sources: string | string[] | null
  feedbackRating?: 'useful' | 'useless' | null
  createdAt: string
  clientTempId?: string
  pending?: boolean
}

export interface RetrievalMetrics {
  totalRequests: number
  avgElapsedMs: number
  totalElapsedMs: number
  semanticPathBreakdown: {
    chunk: number
    docVectorFallback: number
    localTfidfFallback: number
  }
  hitStats: {
    semanticHits: number
    keywordHits: number
    finalHits: number
    avgFinalHitsPerRequest: number
  }
  fallbackRate: {
    docVectorFallbackRate: number
    localTfidfFallbackRate: number
  }
  updatedAt: string
  viewerUserId?: number
}

// Learning Studio
export interface LearningVideo {
  id: number
  title: string
  description: string
  platform: string
  url: string
  coverUrl?: string | null
  durationSeconds: number
  knowledgeId?: string | null
  tags: string[]
  favorite: boolean
  watchedSeconds: number
  lastWatchedAt?: string | null
  completionRate: number
}

// Graph
export interface KnowledgeNode {
  id: string
  name: string
  category: string
  description: string
  difficulty: string
  keywords: string[]
}

export interface GraphEdge {
  source: string
  target: string
  type: string
}

export interface GraphData {
  nodes: KnowledgeNode[]
  edges: GraphEdge[]
}

export interface RelatedDocument {
  id: number
  title: string
  category?: string
  createdAt?: string
  score: number
  reason?: string
}

export interface RelatedGraphNode {
  id: string
  name: string
  category: string
  difficulty: string
  score: number
}

export interface GraphHealth {
  healthScore: number
  totalNodes: number
  totalEdges: number
  relationTypeCount: number
  isolatedNodeCount: number
  selfLoopEdgeCount: number
  duplicateEdgeGroupCount: number
  duplicateEdgeExtraCount: number
  missingIdCount: number
  missingNameCount: number
  missingCategoryCount: number
  missingDescriptionCount: number
  missingDifficultyCount: number
  invalidRelationTypeCount: number
  invalidCategoryCount: number
  hasDependencyCycle: boolean
  isolatedNodeSamples: string[]
  duplicateEdgeSamples: string[]
  cycleNodeSamples: string[]
  invalidRelationTypeSamples: string[]
  invalidCategorySamples: string[]
}

// Learning Path
export type PathStatus = 'active' | 'in_progress' | 'completed'
export type NodeStatus = 'todo' | 'doing' | 'done' | 'skipped'

export interface LearningPath {
  id: number
  target: string
  status: PathStatus
  nodes: LearningNodeInfo[]
  createdAt: string
}

export interface LearningNodeInfo {
  id: number
  knowledgeId: string
  knowledgeName: string
  nodeOrder: number
  status: NodeStatus
  resourceUrls: string[]
  recommendedDocuments?: Array<{
    id: number
    title: string
    category?: string
  }>
  recommendedVideos?: Array<{
    id: number
    title: string
    platform?: string
    url: string
    durationSeconds?: number
  }>
}

// Practice / Exam
export interface PracticeQuestion {
  id: number
  knowledgeId?: string
  stem: string
  order: number
  options: string[]
  userAnswer?: string | null
  correctAnswer?: string | null
  answered: boolean
  correct?: boolean | null
  explanation?: string
}

export interface PracticeSession {
  id: number
  pathId?: number | null
  mode: string
  status: string
  totalQuestions: number
  answeredCount: number
  correctCount: number
  durationMinutes?: number | null
  score?: number | null
  grade?: string | null
  createdAt?: string
  submittedAt?: string | null
  questions: PracticeQuestion[]
}

export interface PracticeAnswerResult {
  sessionId: number
  questionId: number
  answer: string
  correctAnswer: string
  correct: boolean
  explanation?: string
  answeredCount: number
  correctCount: number
  completed: boolean
}

export interface ExamResult {
  sessionId: number
  totalQuestions: number
  answeredCount: number
  correctCount: number
  score: number
  grade: string
  timeout: boolean
}

// Snippets
export interface CodeSnippet {
  id: number
  title: string
  code: string
  language: string
  description: string
  tags: string[]
  useCount: number
  matchScore?: number | null
  recommendReason?: string | null
  createdAt: string
  updatedAt: string
}

export interface SnippetImportResult {
  successCount: number
  failCount: number
  errors: string[]
}

// Progress
export interface DashboardData {
  totalDays: number
  totalChats: number
  totalSnippets: number
  knowledgeCoverage: number
  periodDays: number
  comparisons?: DashboardComparisons
  coverageDetail?: CoverageDetail
  examSummary?: ExamSummary
  recentActivity: { date: string; count: number }[]
}

export interface ExamTrendPoint {
  date: string
  score: number
  grade: string
}

export interface ExamSummary {
  completedCount: number
  avgScore: number
  passRate: number
  latestScore?: number | null
  latestGrade?: string | null
  latestSubmittedAt?: string
  recentTrend: ExamTrendPoint[]
}

export interface HeatmapData {
  year: number
  data: { date: string; count: number }[]
}

export interface MetricComparison {
  current: number
  previous: number
  delta: number
  trend: 'up' | 'down' | 'flat'
}

export interface DashboardComparisons {
  totalDays: MetricComparison
  totalChats: MetricComparison
  totalSnippets: MetricComparison
  knowledgeCoverage: MetricComparison
}

export interface CoverageDetail {
  coveredCoreActions: number
  totalCoreActions: number
  coveredActionKeys: string[]
}

export interface RadarData {
  categories: string[]
  values: number[]
  rawCounts?: number[]
  maxCount?: number
  periodDays?: number
}

export interface WeakArea {
  key: string
  name: string
  score: number
  doneNodes?: number | null
  totalNodes?: number | null
  suggestion: string
}

export interface WeeklyPlanItem {
  id: string
  title: string
  description: string
  expectedImpact?: string
  completed: boolean
}

export interface HealthBreakdown {
  activeDaysScore: number
  streakScore: number
  avgActionsScore: number
  activeDays: number
  streak: number
  avgDailyActions: number
}

export interface SmartInsights {
  healthScore: number
  healthBreakdown?: HealthBreakdown
  weakAreas: WeakArea[]
  weeklyPlan: WeeklyPlanItem[]
}
