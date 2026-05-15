const RELATION_LABELS: Record<string, string> = {
  DEPENDS_ON: '依赖',
  CONTAINS: '包含',
  RELATED_TO: '相关',
  INTEGRATES_WITH: '集成',
  SUPPORTS: '支撑',
  USES: '使用',
  EXTENDS: '扩展',
  IMPLEMENTS: '实现',
  RELATES_TO: '关联',
}

export function relationDisplayLabel(type?: string): string {
  const normalized = (type || '').trim().toUpperCase()
  if (!normalized) return '关联'
  return RELATION_LABELS[normalized] || normalized.replace(/_/g, ' ')
}
