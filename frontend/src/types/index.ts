// API Response
export interface ApiResponse<T> {
  code: number
  message: string
  data: T
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
  createdAt: string
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

// Learning Path
export interface LearningPath {
  id: number
  target: string
  status: string
  nodes: LearningNodeInfo[]
  createdAt: string
}

export interface LearningNodeInfo {
  id: number
  knowledgeId: string
  knowledgeName: string
  nodeOrder: number
  status: 'todo' | 'doing' | 'done' | 'skipped'
  resourceUrls: string[]
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
  createdAt: string
  updatedAt: string
}

// Progress
export interface DashboardData {
  totalDays: number
  totalChats: number
  totalSnippets: number
  knowledgeCoverage: number
  recentActivity: { date: string; count: number }[]
}

export interface HeatmapData {
  year: number
  data: { date: string; count: number }[]
}

export interface RadarData {
  categories: string[]
  values: number[]
}

export interface WeakArea {
  key: string
  name: string
  score: number
  suggestion: string
}

export interface WeeklyPlanItem {
  id: string
  title: string
  description: string
  completed: boolean
}

export interface SmartInsights {
  healthScore: number
  weakAreas: WeakArea[]
  weeklyPlan: WeeklyPlanItem[]
}
