export type DifficultyLevel = 'basic' | 'medium' | 'hard'

export const DIFFICULTY_COLORS: Record<DifficultyLevel, string> = {
  basic: '#67C23A',
  medium: '#E6A23C',
  hard: '#F56C6C',
}

const DIFFICULTY_SHADOW_COLORS: Record<DifficultyLevel, string> = {
  basic: 'rgba(103,194,58,0.34)',
  medium: 'rgba(230,162,60,0.34)',
  hard: 'rgba(245,108,108,0.34)',
}

function normalizeSource(level?: string): string {
  return (level || '').trim().toLowerCase()
}

export function normalizeDifficulty(level?: string): DifficultyLevel {
  const source = normalizeSource(level)
  if (source === 'hard' || source === 'advanced' || source === 'expert') return 'hard'
  if (source === 'medium' || source === 'intermediate' || source === 'mid') return 'medium'
  return 'basic'
}

export function difficultyLabel(level?: string): string {
  const normalized = normalizeDifficulty(level)
  if (normalized === 'hard') return '困难'
  if (normalized === 'medium') return '中等'
  return '基础'
}

export function difficultyTagType(level?: string): 'success' | 'warning' | 'danger' {
  const normalized = normalizeDifficulty(level)
  if (normalized === 'hard') return 'danger'
  if (normalized === 'medium') return 'warning'
  return 'success'
}

export function difficultyNodeColor(level?: string): string {
  return DIFFICULTY_COLORS[normalizeDifficulty(level)]
}

export function difficultyNodeShadowColor(level?: string): string {
  return DIFFICULTY_SHADOW_COLORS[normalizeDifficulty(level)]
}

