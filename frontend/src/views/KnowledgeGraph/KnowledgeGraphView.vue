<script setup lang="ts">
import { ref, onMounted, computed, onBeforeUnmount, watch } from 'vue'
import { useGraphStore } from '@/stores/graph'
import GraphRenderer from '@/components/graph/GraphRenderer.vue'
import AppFeedbackState from '@/components/common/AppFeedbackState.vue'
import { DIFFICULTY_COLORS, difficultyLabel, difficultyTagType } from '@/utils/graphDifficulty'
import { relationDisplayLabel } from '@/utils/graphRelation'
import type { GraphEdge, RelatedDocument } from '@/types'
import { useRoute, useRouter } from 'vue-router'
import { createNode, createRelation, deleteNode as deleteGraphNode, deleteRelation as deleteGraphRelation, getRelatedDocuments } from '@/api/graph'
import {
  Search,
  RefreshRight,
  DataAnalysis,
  Grid,
  Connection,
  InfoFilled,
  Expand,
  Close,
  ArrowDown,
  ArrowUp,
  FullScreen,
  CloseBold,
  Plus,
  Delete,
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

const graphStore = useGraphStore()
const route = useRoute()
const router = useRouter()

const searchKeyword = ref('')
const showDetail = ref(false)
const activeCategory = ref('')
const isMobile = ref(window.innerWidth < 992)
const activeNodeId = ref('')
const selectedRelationTypes = ref<string[]>([])
const relationFilterMode = ref<'single' | 'multi'>('multi')
const relationModeTouched = ref(false)
const showAdvancedFilters = ref(false)
const showHealthPanel = ref(false)
const focusCanvas = ref(false)
const routeHighlightedNodeIds = ref<string[]>([])
const routeHighlightedEdgeKeys = ref<string[]>([])
const lastRouteIntent = ref('')
let routeHighlightTimer: ReturnType<typeof setTimeout> | null = null
const relatedDocs = ref<RelatedDocument[]>([])
const relatedDocsLoading = ref(false)
const relatedDocsError = ref('')
const showCreateNodeDialog = ref(false)
const showCreateRelationDialog = ref(false)
const showDeleteNodeDialog = ref(false)
const showDeleteRelationDialog = ref(false)
const creatingNode = ref(false)
const creatingRelation = ref(false)
const deletingNode = ref(false)
const deletingRelation = ref(false)

const nodeCategoryOptions = [
  { label: '编程语言', value: 'language' },
  { label: '框架', value: 'framework' },
  { label: '运行时', value: 'runtime' },
  { label: '数据库', value: 'database' },
  { label: '工具', value: 'tool' },
  { label: '基础能力', value: 'fundamental' },
  { label: '概念', value: 'concept' },
]

const nodeDifficultyOptions = [
  { label: '初级', value: 'beginner' },
  { label: '中级', value: 'intermediate' },
  { label: '高级', value: 'advanced' },
]

const relationTypeOptions = [
  { label: relationDisplayLabel('DEPENDS_ON'), value: 'DEPENDS_ON' },
  { label: relationDisplayLabel('RELATED_TO'), value: 'RELATED_TO' },
  { label: relationDisplayLabel('CONTAINS'), value: 'CONTAINS' },
]

const createNodeForm = ref({
  id: '',
  name: '',
  category: 'concept',
  difficulty: 'beginner',
  description: '',
  keywordsText: '',
})

const createRelationForm = ref({
  sourceId: '',
  targetId: '',
  relationType: 'RELATED_TO',
})

const deleteNodeForm = ref({
  id: '',
})

const deleteRelationForm = ref({
  sourceId: '',
  targetId: '',
  relationType: 'RELATED_TO',
})

function normalizeRelationType(type?: string): string {
  return (type || '').trim().toUpperCase()
}

function edgeKey(edge: { source: string; target: string; type?: string }): string {
  return `${edge.source}|${normalizeRelationType(edge.type)}|${edge.target}`
}

const relationOptions = computed(() => {
  // 根据当前已加载的边，生成关系筛选标签。
  const countByType = new Map<string, number>()
  for (const edge of graphStore.edges) {
    const relationType = normalizeRelationType(edge.type || '关联')
    countByType.set(relationType, (countByType.get(relationType) || 0) + 1)
  }
  return Array.from(countByType.entries())
    .sort((a, b) => b[1] - a[1])
    .slice(0, 12)
    .map(([type, count]) => ({ type, label: relationDisplayLabel(type), count }))
})

const hasRelationFilter = computed(() => selectedRelationTypes.value.length > 0)
const mergedHighlightNodeIds = computed(() =>
  Array.from(new Set([...graphStore.recentlyAddedNodeIds, ...routeHighlightedNodeIds.value])),
)
const mergedHighlightEdgeKeys = computed(() => routeHighlightedEdgeKeys.value)

const displayedEdges = computed<GraphEdge[]>(() => {
  // 对边应用关系类型筛选。
  if (!hasRelationFilter.value) return graphStore.edges
  const selected = new Set(selectedRelationTypes.value.map((type) => normalizeRelationType(type)))
  return graphStore.edges.filter((edge) => selected.has(normalizeRelationType(edge.type)))
})

const displayedNodes = computed(() => {
  // 仅保留与当前展示边相连的节点（再补上当前选中节点）。
  if (!hasRelationFilter.value) return graphStore.nodes
  const visibleIds = new Set<string>()
  for (const edge of displayedEdges.value) {
    visibleIds.add(edge.source)
    visibleIds.add(edge.target)
  }

  const selectedId = graphStore.selectedNode?.id || activeNodeId.value
  if (selectedId) visibleIds.add(selectedId)

  return graphStore.nodes.filter((node) => visibleIds.has(node.id))
})

const nodeCount = computed(() => displayedNodes.value.length)
const edgeCount = computed(() => displayedEdges.value.length)
const totalNodeCount = computed(() => graphStore.nodes.length)
const totalEdgeCount = computed(() => graphStore.edges.length)
const healthIssueTotal = computed(() => {
  const h = graphStore.health
  if (!h) return 0
  return (
    h.isolatedNodeCount +
    h.selfLoopEdgeCount +
    h.duplicateEdgeExtraCount +
    h.missingIdCount +
    h.missingNameCount +
    h.missingCategoryCount +
    h.missingDescriptionCount +
    h.missingDifficultyCount +
    h.invalidRelationTypeCount +
    h.invalidCategoryCount +
    (h.hasDependencyCycle ? 1 : 0)
  )
})
const healthGrade = computed(() => {
  const score = graphStore.health?.healthScore ?? 0
  if (score >= 90) return '优秀'
  if (score >= 75) return '良好'
  if (score >= 60) return '一般'
  return '待治理'
})

const categories = computed(() => {
  const set = new Set<string>()
  for (const node of displayedNodes.value) {
    if (node.category) set.add(node.category)
  }
  return Array.from(set)
})

const detailErrorTitle = computed(() => {
  if (graphStore.detailErrorCode === 404) return '节点不存在'
  if ((graphStore.detailErrorCode || 0) >= 500) return '服务暂时不可用'
  return '加载失败'
})

function handleResize() {
  isMobile.value = window.innerWidth < 992
}

function isRelationSelected(type: string): boolean {
  return selectedRelationTypes.value.includes(normalizeRelationType(type))
}

function enforceRelationSelectionForMode(mode: 'single' | 'multi') {
  if (mode !== 'single' || selectedRelationTypes.value.length <= 1) return
  const lastSelected = selectedRelationTypes.value[selectedRelationTypes.value.length - 1]
  selectedRelationTypes.value = lastSelected ? [lastSelected] : []
}

function setRelationFilterMode(mode: 'single' | 'multi', options: { manual?: boolean } = {}) {
  relationFilterMode.value = mode
  if (options.manual) relationModeTouched.value = true
  enforceRelationSelectionForMode(mode)
}

function handleRelationModeChange(mode: string | number | boolean | undefined) {
  const normalizedMode = mode === 'single' ? 'single' : 'multi'
  setRelationFilterMode(normalizedMode, { manual: true })
}

function toggleRelationFilter(type: string) {
  const normalized = normalizeRelationType(type)
  if (!normalized) return

  if (relationFilterMode.value === 'single') {
    if (isRelationSelected(normalized)) {
      selectedRelationTypes.value = []
      return
    }
    selectedRelationTypes.value = [normalized]
    return
  }

  if (isRelationSelected(normalized)) {
    selectedRelationTypes.value = selectedRelationTypes.value.filter((item) => item !== normalized)
    return
  }
  selectedRelationTypes.value = Array.from(new Set([...selectedRelationTypes.value, normalized]))
}

function clearRelationFilter() {
  selectedRelationTypes.value = []
}

function clearRouteHighlights() {
  routeHighlightedNodeIds.value = []
  routeHighlightedEdgeKeys.value = []
  if (routeHighlightTimer) {
    clearTimeout(routeHighlightTimer)
    routeHighlightTimer = null
  }
}

function applyRouteHighlights(nodeId: string, relationType: string, targetId: string) {
  if (routeHighlightTimer) {
    clearTimeout(routeHighlightTimer)
    routeHighlightTimer = null
  }

  const highlightedNodes = new Set<string>()
  const highlightedEdges = new Set<string>()

  if (nodeId) highlightedNodes.add(nodeId)
  if (targetId) highlightedNodes.add(targetId)

  if (relationType) {
    const matchedEdges = graphStore.edges.filter((edge) => {
      const edgeType = normalizeRelationType(edge.type)
      if (edgeType !== relationType) return false
      if (nodeId && targetId) {
        return (
          (edge.source === nodeId && edge.target === targetId) ||
          (edge.source === targetId && edge.target === nodeId)
        )
      }
      if (nodeId) {
        return edge.source === nodeId || edge.target === nodeId
      }
      return true
    })
    matchedEdges.forEach((edge) => {
      highlightedEdges.add(edgeKey(edge))
      highlightedNodes.add(edge.source)
      highlightedNodes.add(edge.target)
    })
  }

  routeHighlightedNodeIds.value = Array.from(highlightedNodes)
  routeHighlightedEdgeKeys.value = Array.from(highlightedEdges)
  if (routeHighlightedNodeIds.value.length > 0 || routeHighlightedEdgeKeys.value.length > 0) {
    routeHighlightTimer = setTimeout(() => {
      routeHighlightedNodeIds.value = []
      routeHighlightedEdgeKeys.value = []
      routeHighlightTimer = null
    }, 2800)
  }
}

function routeIntentKey(): string {
  const node = String(route.query.node || '').trim()
  const target = String(route.query.target || '').trim()
  const relation = normalizeRelationType(String(route.query.relation || '').trim())
  const focus = String(route.query.focus || '').trim()
  if (!node && !target && !relation && !focus) return ''
  return `${node}|${target}|${relation}|${focus}`
}

async function applyRouteIntent() {
  // 支持深链参数，例如 ?node=java&relation=DEPENDS_ON。
  const intentKey = routeIntentKey()
  if (!intentKey || intentKey === '||' || intentKey === lastRouteIntent.value) return
  lastRouteIntent.value = intentKey

  const relationType = normalizeRelationType(String(route.query.relation || '').trim())
  if (relationType) {
    selectedRelationTypes.value = [relationType]
    if (!relationModeTouched.value) {
      setRelationFilterMode('single')
    } else {
      enforceRelationSelectionForMode(relationFilterMode.value)
    }
  }

  const nodeId = String(route.query.node || '').trim()
  const targetId = String(route.query.target || '').trim()
  const focusNodeId = nodeId || targetId
  if (!focusNodeId) {
    applyRouteHighlights('', relationType, '')
    return
  }

  activeNodeId.value = focusNodeId
  showDetail.value = true
  const success = await graphStore.selectNode(focusNodeId)
  if (success) {
    await graphStore.loadNeighbors(focusNodeId)
    await loadNodeRelatedDocs(focusNodeId)
  }
  applyRouteHighlights(nodeId || focusNodeId, relationType, targetId)
}

onMounted(async () => {
  // 首次渲染：并行加载图谱数据和健康面板数据。
  await Promise.all([graphStore.loadOverview(), graphStore.loadHealth()])
  await applyRouteIntent()
  handleResize()
  window.addEventListener('resize', handleResize)
})

watch(
  () => route.query,
  async () => {
    // 在图谱页内响应路由 query 变化。
    if (String(route.name || '') !== 'graph') return
    if (graphStore.nodes.length === 0 && !graphStore.loading) {
      await graphStore.loadOverview()
    }
    await applyRouteIntent()
  },
  { deep: true },
)

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  clearRouteHighlights()
})

async function handleSearch() {
  // 关键词为空时，恢复完整图谱总览。
  const keyword = searchKeyword.value.trim()
  activeCategory.value = ''
  if (keyword) {
    await graphStore.search(keyword)
  } else {
    await graphStore.loadOverview()
  }
}

async function loadNodeRelatedDocs(nodeId: string) {
  // 节点关联文档，展示在详情面板里。
  relatedDocsLoading.value = true
  relatedDocsError.value = ''
  try {
    const res = await getRelatedDocuments(nodeId, 6)
    relatedDocs.value = res.data.data || []
  } catch {
    relatedDocs.value = []
    relatedDocsError.value = '关联文档加载失败，请稍后重试。'
  } finally {
    relatedDocsLoading.value = false
  }
}

function openRelatedDoc(doc: RelatedDocument) {
  if (!doc?.id) return
  void router.push({
    name: 'documents',
    query: { docId: String(doc.id) },
  })
}

async function handleNodeClick(nodeId: string) {
  // 点击节点后，打开详情并加载节点详情与关联文档。
  const isSameNode = activeNodeId.value === nodeId && graphStore.selectedNode?.id === nodeId
  activeNodeId.value = nodeId
  showDetail.value = true
  if (isSameNode && !graphStore.detailError) {
    if (relatedDocs.value.length === 0 && !relatedDocsLoading.value) {
      await loadNodeRelatedDocs(nodeId)
    }
    return
  }
  const success = await graphStore.selectNode(nodeId)
  if (success) {
    await loadNodeRelatedDocs(nodeId)
  }
}

function closeDetail() {
  showDetail.value = false
  activeNodeId.value = ''
  graphStore.clearSelection()
  relatedDocs.value = []
  relatedDocsError.value = ''
  relatedDocsLoading.value = false
}

async function handleRefresh() {
  // 统一重置搜索/筛选状态与图谱数据。
  activeCategory.value = ''
  searchKeyword.value = ''
  clearRelationFilter()
  relationModeTouched.value = false
  relationFilterMode.value = 'multi'
  lastRouteIntent.value = ''
  await Promise.all([graphStore.loadOverview(), graphStore.loadHealth()])
}

async function filterByCategory(category: string) {
  activeCategory.value = category
  searchKeyword.value = category
  await graphStore.search(category)
}

async function clearCategoryFilter() {
  activeCategory.value = ''
  if (searchKeyword.value.trim() === '') {
    await graphStore.loadOverview()
  }
}

const legendItems = [
  { key: 'basic', label: '基础节点', color: DIFFICULTY_COLORS.basic },
  { key: 'medium', label: '中等难度', color: DIFFICULTY_COLORS.medium },
  { key: 'hard', label: '困难节点', color: DIFFICULTY_COLORS.hard },
]

const currentExpandDepth = computed(() => {
  const nodeId = graphStore.selectedNode?.id || activeNodeId.value
  if (!nodeId) return 0
  return graphStore.getExpandedDepth(nodeId)
})

const expandDisabled = computed(() => {
  const nodeId = graphStore.selectedNode?.id || activeNodeId.value
  if (!nodeId) return true
  return graphStore.hasReachedMaxDepth(nodeId) || graphStore.loading
})

async function expandCurrentNode() {
  // 围绕当前节点再向外展开一层邻居。
  const nodeId = graphStore.selectedNode?.id || activeNodeId.value
  if (!nodeId) return
  const result = await graphStore.loadNeighbors(nodeId, { incremental: true })
  if (result.failed) {
    ElMessage.error('展开关联节点失败，请稍后重试。')
    return
  }
  if (result.reachedMaxDepth && result.addedNodes === 0 && result.addedEdges === 0) {
    ElMessage.info('已达到最大展开层级（4 层）。')
    return
  }
  if (result.addedNodes === 0 && result.addedEdges === 0) {
    ElMessage.info(`已展开到第 ${result.depth} 层，暂无新增关联节点。`)
    return
  }
  ElMessage.success(`已新增 ${result.addedNodes} 个节点、${result.addedEdges} 条关系（第 ${result.depth} 层）。`)
}

async function retryLoadDetail() {
  const nodeId = activeNodeId.value
  if (!nodeId) return
  const success = await graphStore.selectNode(nodeId)
  if (success) {
    await loadNodeRelatedDocs(nodeId)
  }
}

function healthTagType(score: number): 'success' | 'warning' | 'danger' | 'info' {
  if (score >= 90) return 'success'
  if (score >= 75) return 'info'
  if (score >= 60) return 'warning'
  return 'danger'
}

function resetCreateNodeForm() {
  createNodeForm.value = {
    id: '',
    name: '',
    category: 'concept',
    difficulty: 'beginner',
    description: '',
    keywordsText: '',
  }
}

function resetCreateRelationForm() {
  createRelationForm.value = {
    sourceId: activeNodeId.value || '',
    targetId: '',
    relationType: 'RELATED_TO',
  }
}

function openCreateNodeDialog() {
  resetCreateNodeForm()
  showCreateNodeDialog.value = true
}

function openCreateRelationDialog() {
  resetCreateRelationForm()
  showCreateRelationDialog.value = true
}

function resetDeleteNodeForm() {
  deleteNodeForm.value = {
    id: activeNodeId.value || graphStore.selectedNode?.id || '',
  }
}

function resetDeleteRelationForm() {
  deleteRelationForm.value = {
    sourceId: '',
    targetId: '',
    relationType: 'RELATED_TO',
  }
}

function openDeleteNodeDialog() {
  resetDeleteNodeForm()
  showDeleteNodeDialog.value = true
}

function openDeleteRelationDialog() {
  resetDeleteRelationForm()
  showDeleteRelationDialog.value = true
}

async function submitCreateNode() {
  // 通过 /api/graph/node 将用户新增节点写入 Neo4j。
  const payload = createNodeForm.value
  if (!payload.id.trim() || !payload.name.trim()) {
    ElMessage.warning('请先填写节点ID和节点名称')
    return
  }
  creatingNode.value = true
  try {
    const keywords = payload.keywordsText
      .split(/[,，]/)
      .map((item) => item.trim())
      .filter(Boolean)

    await createNode({
      id: payload.id.trim(),
      name: payload.name.trim(),
      category: payload.category,
      difficulty: payload.difficulty,
      description: payload.description.trim(),
      keywords,
    })

    ElMessage.success('节点新增成功')
    showCreateNodeDialog.value = false
    await Promise.all([graphStore.loadOverview(), graphStore.loadHealth()])
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message || '新增节点失败，请稍后重试')
  } finally {
    creatingNode.value = false
  }
}

async function submitCreateRelation() {
  // 通过 /api/graph/relation 将有向关系写入 Neo4j。
  const payload = createRelationForm.value
  if (!payload.sourceId.trim() || !payload.targetId.trim()) {
    ElMessage.warning('请先填写源节点ID和目标节点ID')
    return
  }
  creatingRelation.value = true
  try {
    await createRelation({
      sourceId: payload.sourceId.trim(),
      targetId: payload.targetId.trim(),
      relationType: payload.relationType,
    })
    ElMessage.success('关系新增成功')
    showCreateRelationDialog.value = false
    await Promise.all([graphStore.loadOverview(), graphStore.loadHealth()])
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message || '新增关系失败，请稍后重试')
  } finally {
    creatingRelation.value = false
  }
}

async function submitDeleteNode() {
  // 按 id 删除节点（后端会同时删除关联边）。
  const nodeId = deleteNodeForm.value.id.trim()
  if (!nodeId) {
    ElMessage.warning('请先填写要删除的节点ID')
    return
  }
  deletingNode.value = true
  try {
    await deleteGraphNode(nodeId)
    ElMessage.success('节点删除成功')
    showDeleteNodeDialog.value = false
    if ((graphStore.selectedNode?.id || activeNodeId.value) === nodeId) {
      closeDetail()
    }
    await Promise.all([graphStore.loadOverview(), graphStore.loadHealth()])
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message || '删除节点失败，请稍后重试')
  } finally {
    deletingNode.value = false
  }
}

async function submitDeleteRelation() {
  // 按 source/target/type 删除一条有向关系。
  const payload = deleteRelationForm.value
  if (!payload.sourceId.trim() || !payload.targetId.trim()) {
    ElMessage.warning('请先填写源节点ID和目标节点ID')
    return
  }
  deletingRelation.value = true
  try {
    await deleteGraphRelation({
      sourceId: payload.sourceId.trim(),
      targetId: payload.targetId.trim(),
      relationType: payload.relationType,
    })
    ElMessage.success('关系删除成功')
    showDeleteRelationDialog.value = false
    await Promise.all([graphStore.loadOverview(), graphStore.loadHealth()])
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message || '删除关系失败，请稍后重试')
  } finally {
    deletingRelation.value = false
  }
}
</script>

<template>
  <div class="graph-view page-shell" v-loading="graphStore.loading && nodeCount === 0">
    <div class="page-head">
      <div class="page-title-block">
        <h2 class="page-title">知识图谱探索</h2>
        <p class="page-subtitle">
          节点 {{ nodeCount }}<span v-if="hasRelationFilter"> / {{ totalNodeCount }}</span>
          · 关系 {{ edgeCount }}<span v-if="hasRelationFilter"> / {{ totalEdgeCount }}</span>
          · 分类 {{ categories.length }}
        </p>
      </div>
      <div class="page-toolbar">
        <el-button
          text
          :icon="showAdvancedFilters ? ArrowUp : ArrowDown"
          @click="showAdvancedFilters = !showAdvancedFilters"
        >
          {{ showAdvancedFilters ? '收起高级筛选' : '展开高级筛选' }}
        </el-button>
        <el-button class="graph-action-btn" :icon="focusCanvas ? CloseBold : FullScreen" @click="focusCanvas = !focusCanvas">
          {{ focusCanvas ? '退出专注' : '专注画布' }}
        </el-button>
        <el-button class="graph-action-btn" type="primary" plain :icon="Plus" @click="openCreateNodeDialog">新增节点</el-button>
        <el-button class="graph-action-btn" type="primary" plain :icon="Connection" @click="openCreateRelationDialog">新增关系</el-button>
        <el-button class="graph-action-btn" :icon="Delete" @click="openDeleteNodeDialog">删除节点</el-button>
        <el-button class="graph-action-btn" :icon="Delete" @click="openDeleteRelationDialog">删除关系</el-button>
        <el-button class="graph-action-btn" :icon="RefreshRight" @click="handleRefresh">刷新图谱</el-button>
      </div>
    </div>

    <div class="graph-body" :class="{ 'focus-canvas': focusCanvas }">
      <div v-show="!focusCanvas" class="graph-toolbar soft-panel">
        <div class="search-row">
          <el-input
            v-model="searchKeyword"
            placeholder="搜索知识节点、概念或关键词..."
            :prefix-icon="Search"
            clearable
            @keyup.enter="handleSearch"
            @clear="handleRefresh"
          />
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button :icon="RefreshRight" @click="handleRefresh">重置</el-button>
        </div>

        <div v-if="relationOptions.length > 0" class="relation-row">
          <span class="relation-title">关系类型</span>
          <div class="relation-mode">
            <el-radio-group :model-value="relationFilterMode" size="small" @change="handleRelationModeChange">
              <el-radio-button label="single">单选</el-radio-button>
              <el-radio-button label="multi">多选</el-radio-button>
            </el-radio-group>
          </div>
          <span v-if="hasRelationFilter" class="relation-summary">
            已选 {{ selectedRelationTypes.length }} 类关系
          </span>
          <span class="relation-mode-hint">
            {{ relationFilterMode === 'single' ? '单选模式：点击新关系会替换旧关系' : '多选模式：点击新关系会累加' }}
          </span>
          <el-tag
            size="small"
            class="relation-chip relation-chip-reset"
            :effect="hasRelationFilter ? 'plain' : 'dark'"
            :type="hasRelationFilter ? 'info' : 'primary'"
            @click="clearRelationFilter"
          >
            全部关系 · {{ totalEdgeCount }}
          </el-tag>
          <el-tag
            v-for="item in relationOptions"
            :key="item.type"
            size="small"
            :effect="isRelationSelected(item.type) ? 'dark' : 'plain'"
            :type="isRelationSelected(item.type) ? 'primary' : 'info'"
            class="relation-chip"
            @click="toggleRelationFilter(item.type)"
          >
            {{ item.label }} · {{ item.count }}
          </el-tag>
        </div>

        <transition name="fade">
          <div v-show="showAdvancedFilters" class="toolbar-advanced">
            <div class="category-row">
              <div class="category-tags">
                <el-tag
                  class="category-tag"
                  :effect="activeCategory === '' ? 'dark' : 'plain'"
                  :type="activeCategory === '' ? 'primary' : 'info'"
                  @click="clearCategoryFilter"
                >
                  全部
                </el-tag>
                <el-tag
                  v-for="cat in categories"
                  :key="cat"
                  class="category-tag"
                  :effect="activeCategory === cat ? 'dark' : 'plain'"
                  :type="activeCategory === cat ? 'primary' : 'info'"
                  @click="filterByCategory(cat)"
                >
                  {{ cat }}
                </el-tag>
              </div>
            </div>

            <div class="legend-row">
              <span class="legend-title"><el-icon><InfoFilled /></el-icon> 图例说明</span>
              <span
                v-for="item in legendItems"
                :key="item.key"
                class="legend-chip"
                :style="{ '--legend-color': item.color }"
              >
                <i class="legend-dot" />
                {{ item.label }}
              </span>
              <el-tag size="small" effect="plain"><el-icon><Connection /></el-icon> 拖拽 / 缩放 / 点击查看详情</el-tag>
            </div>

            <div v-if="relationOptions.length > 0" class="relation-selected-strip">
              <span class="selected-label">当前筛选</span>
              <div class="relation-selected-scroll" :class="{ empty: !hasRelationFilter }">
                <span v-if="!hasRelationFilter" class="selected-empty">未选择关系类型（当前展示全部关系）</span>
                <el-tag
                  v-for="type in selectedRelationTypes"
                  :key="`selected-${type}`"
                  size="small"
                  type="primary"
                  effect="plain"
                >
                  {{ relationDisplayLabel(type) }}
                </el-tag>
              </div>
            </div>
          </div>
        </transition>
      </div>

      <div v-show="!focusCanvas" class="health-toggle-row">
        <el-button
          text
          size="small"
          class="health-toggle-btn"
          :icon="showHealthPanel ? ArrowUp : ArrowDown"
          @click="showHealthPanel = !showHealthPanel"
        >
          {{ showHealthPanel ? '收起图谱健康度' : '展开图谱健康度' }}
        </el-button>
        <el-tag v-if="graphStore.health" size="small" :type="healthTagType(graphStore.health.healthScore)">
          {{ graphStore.health.healthScore }} / 100 · {{ healthGrade }}
        </el-tag>
        <el-tag v-else-if="graphStore.healthError" size="small" type="danger">指标暂不可用</el-tag>
      </div>

      <el-collapse-transition>
        <div
          v-if="!focusCanvas && showHealthPanel"
          class="graph-health soft-panel"
          v-loading="graphStore.healthLoading"
        >
          <template v-if="graphStore.health">
            <div class="health-head">
              <span class="health-title">图谱健康度</span>
              <el-button size="small" text :icon="RefreshRight" @click="graphStore.loadHealth">刷新</el-button>
            </div>
            <div class="health-grid">
              <div class="health-item">
                <span>孤立节点</span>
                <strong>{{ graphStore.health.isolatedNodeCount }}</strong>
              </div>
              <div class="health-item">
                <span>重复关系冗余</span>
                <strong>{{ graphStore.health.duplicateEdgeExtraCount }}</strong>
              </div>
              <div class="health-item">
                <span>自环关系</span>
                <strong>{{ graphStore.health.selfLoopEdgeCount }}</strong>
              </div>
              <div class="health-item">
                <span>缺失字段</span>
                <strong>{{
                  graphStore.health.missingIdCount +
                  graphStore.health.missingNameCount +
                  graphStore.health.missingCategoryCount +
                  graphStore.health.missingDescriptionCount +
                  graphStore.health.missingDifficultyCount
                }}</strong>
              </div>
              <div class="health-item">
                <span>非法关系类型</span>
                <strong>{{ graphStore.health.invalidRelationTypeCount }}</strong>
              </div>
              <div class="health-item">
                <span>非法节点分类</span>
                <strong>{{ graphStore.health.invalidCategoryCount }}</strong>
              </div>
              <div class="health-item">
                <span>依赖环路</span>
                <strong>{{ graphStore.health.hasDependencyCycle ? '存在' : '正常' }}</strong>
              </div>
              <div class="health-item">
                <span>问题总量</span>
                <strong>{{ healthIssueTotal }}</strong>
              </div>
            </div>
          </template>
          <template v-else-if="graphStore.healthError">
            <AppFeedbackState
              type="error"
              size="compact"
              :centered="false"
              title="图谱健康指标暂不可用"
              :description="graphStore.healthError"
            >
              <template #actions>
                <el-button size="small" type="primary" plain @click="graphStore.loadHealth">重试</el-button>
              </template>
            </AppFeedbackState>
          </template>
        </div>
      </el-collapse-transition>

      <div v-if="graphStore.loadError" class="load-error soft-panel">
        <AppFeedbackState
          type="error"
          :centered="false"
          title="图谱加载失败"
          :description="graphStore.loadError"
          size="compact"
        >
          <template #actions>
            <el-button type="primary" size="small" @click="handleRefresh">重试加载</el-button>
            <el-button size="small" @click="clearCategoryFilter">恢复全部视图</el-button>
          </template>
        </AppFeedbackState>
      </div>

      <div class="graph-stage soft-panel">
        <div v-if="focusCanvas" class="focus-actions">
          <el-button size="small" :icon="ArrowUp" @click="focusCanvas = false">退出专注</el-button>
          <el-button size="small" :icon="RefreshRight" @click="handleRefresh">刷新</el-button>
        </div>
        <div v-if="!graphStore.loading && nodeCount === 0" class="graph-empty">
          <AppFeedbackState
            type="empty"
            title="当前筛选下没有图谱节点"
            description="可尝试清空关系筛选或重新搜索。"
          >
            <template #actions>
              <el-button size="small" @click="clearRelationFilter">清空关系筛选</el-button>
              <el-button type="primary" size="small" @click="handleRefresh">恢复图谱</el-button>
            </template>
          </AppFeedbackState>
        </div>
        <GraphRenderer
          v-else
          :nodes="displayedNodes"
          :edges="displayedEdges"
          :highlight-node-ids="mergedHighlightNodeIds"
          :highlight-edge-keys="mergedHighlightEdgeKeys"
          @node-click="handleNodeClick"
        />

        <transition name="slide-right">
          <aside v-if="showDetail && !isMobile" class="detail-panel">
            <div class="detail-header">
              <div>
                <h3>{{ graphStore.selectedNode?.name || '节点详情' }}</h3>
                <p>节点详情与关联学习建议</p>
              </div>
              <el-button :icon="Close" text circle @click="closeDetail" />
            </div>
            <div class="detail-body">
              <el-skeleton v-if="graphStore.detailLoading" animated :rows="6" />

              <section v-else-if="graphStore.detailError" class="detail-section error">
                <h4><el-icon><InfoFilled /></el-icon> {{ detailErrorTitle }}</h4>
                <p>{{ graphStore.detailError }}</p>
                <el-button type="primary" plain size="small" @click="retryLoadDetail">重试</el-button>
              </section>

              <template v-else-if="graphStore.selectedNode">
                <section class="detail-section">
                  <h4><el-icon><Grid /></el-icon> 概览</h4>
                  <div class="detail-items">
                    <div class="detail-item">
                      <label>分类</label>
                      <el-tag size="small">{{ graphStore.selectedNode.category || '未分类' }}</el-tag>
                    </div>
                    <div class="detail-item">
                      <label>难度</label>
                      <el-tag size="small" :type="difficultyTagType(graphStore.selectedNode.difficulty)">
                        {{ difficultyLabel(graphStore.selectedNode.difficulty) }}
                      </el-tag>
                    </div>
                  </div>
                </section>

                <section class="detail-section">
                  <h4><el-icon><DataAnalysis /></el-icon> 描述</h4>
                  <p>{{ graphStore.selectedNode.description || '暂无描述' }}</p>
                </section>

                <section v-if="graphStore.selectedNode.keywords?.length" class="detail-section">
                  <h4><el-icon><Search /></el-icon> 关键词</h4>
                  <div class="keyword-list">
                    <el-tag v-for="kw in graphStore.selectedNode.keywords" :key="kw" size="small" type="info" effect="plain">
                      {{ kw }}
                    </el-tag>
                  </div>
                </section>

                <section class="detail-section">
                  <h4><el-icon><Connection /></el-icon> 关联知识文档</h4>
                  <div v-if="relatedDocsLoading" class="related-doc-loading">
                    <el-skeleton animated :rows="2" />
                  </div>
                  <AppFeedbackState
                    v-else-if="relatedDocsError"
                    type="error"
                    size="compact"
                    :centered="false"
                    title="关联文档加载失败"
                    :description="relatedDocsError"
                  >
                    <template #actions>
                      <el-button
                        size="small"
                        type="primary"
                        plain
                        @click="graphStore.selectedNode?.id && loadNodeRelatedDocs(graphStore.selectedNode.id)"
                      >
                        重试
                      </el-button>
                    </template>
                  </AppFeedbackState>
                  <div v-else-if="relatedDocs.length > 0" class="related-doc-list">
                    <button
                      v-for="doc in relatedDocs"
                      :key="doc.id"
                      class="related-doc-item"
                      type="button"
                      @click="openRelatedDoc(doc)"
                    >
                      <span class="title">{{ doc.title }}</span>
                      <span class="meta">{{ doc.category || '未分类' }} · score {{ doc.score.toFixed(1) }}</span>
                    </button>
                  </div>
                  <p v-else class="empty-note">暂无匹配文档，可在知识文档页新增与该节点相关内容。</p>
                </section>

                <section class="detail-section actions">
                  <el-button type="primary" :icon="Expand" :disabled="expandDisabled" @click="expandCurrentNode">
                    继续展开关联节点
                  </el-button>
                  <p class="expand-meta">当前展开层级：{{ currentExpandDepth }} / 4</p>
                </section>
              </template>

              <section v-else class="detail-section">
                <h4><el-icon><Grid /></el-icon> 概览</h4>
                <p>点击图中的节点查看详情。</p>
              </section>
            </div>
          </aside>
        </transition>
      </div>
    </div>

    <el-drawer
      v-if="isMobile"
      v-model="showDetail"
      direction="rtl"
      size="92%"
      :with-header="false"
      class="mobile-detail-drawer"
    >
      <div class="mobile-detail">
        <div class="detail-header">
          <div>
            <h3>{{ graphStore.selectedNode?.name || '节点详情' }}</h3>
            <p>节点详情与关联学习建议</p>
          </div>
          <el-button :icon="Close" text circle @click="closeDetail" />
        </div>

        <div class="detail-body">
          <el-skeleton v-if="graphStore.detailLoading" animated :rows="6" />

          <section v-else-if="graphStore.detailError" class="detail-section error">
            <h4><el-icon><InfoFilled /></el-icon> {{ detailErrorTitle }}</h4>
            <p>{{ graphStore.detailError }}</p>
            <el-button type="primary" plain size="small" @click="retryLoadDetail">重试</el-button>
          </section>

          <template v-else-if="graphStore.selectedNode">
            <section class="detail-section">
              <h4><el-icon><Grid /></el-icon> 概览</h4>
              <div class="detail-items">
                <div class="detail-item">
                  <label>分类</label>
                  <el-tag size="small">{{ graphStore.selectedNode.category || '未分类' }}</el-tag>
                </div>
                <div class="detail-item">
                  <label>难度</label>
                  <el-tag size="small" :type="difficultyTagType(graphStore.selectedNode.difficulty)">
                    {{ difficultyLabel(graphStore.selectedNode.difficulty) }}
                  </el-tag>
                </div>
              </div>
            </section>

            <section class="detail-section">
              <h4><el-icon><DataAnalysis /></el-icon> 描述</h4>
              <p>{{ graphStore.selectedNode.description || '暂无描述' }}</p>
            </section>

            <section v-if="graphStore.selectedNode.keywords?.length" class="detail-section">
              <h4><el-icon><Search /></el-icon> 关键词</h4>
              <div class="keyword-list">
                <el-tag v-for="kw in graphStore.selectedNode.keywords" :key="kw" size="small" type="info" effect="plain">
                  {{ kw }}
                </el-tag>
              </div>
            </section>

            <section class="detail-section">
              <h4><el-icon><Connection /></el-icon> 关联知识文档</h4>
              <div v-if="relatedDocsLoading" class="related-doc-loading">
                <el-skeleton animated :rows="2" />
              </div>
              <AppFeedbackState
                v-else-if="relatedDocsError"
                type="error"
                size="compact"
                :centered="false"
                title="关联文档加载失败"
                :description="relatedDocsError"
              >
                <template #actions>
                  <el-button
                    size="small"
                    type="primary"
                    plain
                    @click="graphStore.selectedNode?.id && loadNodeRelatedDocs(graphStore.selectedNode.id)"
                  >
                    重试
                  </el-button>
                </template>
              </AppFeedbackState>
              <div v-else-if="relatedDocs.length > 0" class="related-doc-list">
                <button
                  v-for="doc in relatedDocs"
                  :key="doc.id"
                  class="related-doc-item"
                  type="button"
                  @click="openRelatedDoc(doc)"
                >
                  <span class="title">{{ doc.title }}</span>
                  <span class="meta">{{ doc.category || '未分类' }} · score {{ doc.score.toFixed(1) }}</span>
                </button>
              </div>
              <p v-else class="empty-note">暂无匹配文档，可在知识文档页新增与该节点相关内容。</p>
            </section>

            <section class="detail-section actions">
              <el-button type="primary" :icon="Expand" :disabled="expandDisabled" @click="expandCurrentNode">
                继续展开关联节点
              </el-button>
              <p class="expand-meta">当前展开层级：{{ currentExpandDepth }} / 4</p>
            </section>
          </template>

          <section v-else class="detail-section">
            <h4><el-icon><Grid /></el-icon> 概览</h4>
            <p>点击图中的节点查看详情。</p>
          </section>
        </div>
      </div>
    </el-drawer>

    <el-dialog v-model="showCreateNodeDialog" title="新增图谱节点" width="520px" destroy-on-close>
      <el-form label-width="90px">
        <el-form-item label="节点ID">
          <el-input v-model="createNodeForm.id" placeholder="例如：grpc" />
        </el-form-item>
        <el-form-item label="节点名称">
          <el-input v-model="createNodeForm.name" placeholder="例如：gRPC" />
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="createNodeForm.category" style="width: 100%">
            <el-option v-for="item in nodeCategoryOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="难度">
          <el-select v-model="createNodeForm.difficulty" style="width: 100%">
            <el-option v-for="item in nodeDifficultyOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="关键词">
          <el-input
            v-model="createNodeForm.keywordsText"
            placeholder="多个关键词用逗号分隔，例如：rpc,protobuf,微服务"
          />
        </el-form-item>
        <el-form-item label="描述">
          <el-input
            v-model="createNodeForm.description"
            type="textarea"
            :rows="3"
            placeholder="简要描述该知识节点"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateNodeDialog = false">取消</el-button>
        <el-button type="primary" :loading="creatingNode" @click="submitCreateNode">确认新增</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showCreateRelationDialog" title="新增图谱关系" width="520px" destroy-on-close>
      <el-form label-width="90px">
        <el-form-item label="源节点ID">
          <el-input v-model="createRelationForm.sourceId" placeholder="例如：spring-boot" />
        </el-form-item>
        <el-form-item label="目标节点ID">
          <el-input v-model="createRelationForm.targetId" placeholder="例如：java" />
        </el-form-item>
        <el-form-item label="关系类型">
          <el-select v-model="createRelationForm.relationType" style="width: 100%">
            <el-option v-for="item in relationTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateRelationDialog = false">取消</el-button>
        <el-button type="primary" :loading="creatingRelation" @click="submitCreateRelation">确认新增</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showDeleteNodeDialog" title="删除图谱节点" width="460px" destroy-on-close>
      <el-form label-width="90px">
        <el-form-item label="节点ID">
          <el-input v-model="deleteNodeForm.id" placeholder="例如：java" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showDeleteNodeDialog = false">取消</el-button>
        <el-button type="danger" :loading="deletingNode" @click="submitDeleteNode">确认删除</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showDeleteRelationDialog" title="删除图谱关系" width="520px" destroy-on-close>
      <el-form label-width="90px">
        <el-form-item label="源节点ID">
          <el-input v-model="deleteRelationForm.sourceId" placeholder="例如：spring-boot" />
        </el-form-item>
        <el-form-item label="目标节点ID">
          <el-input v-model="deleteRelationForm.targetId" placeholder="例如：java" />
        </el-form-item>
        <el-form-item label="关系类型">
          <el-select v-model="deleteRelationForm.relationType" style="width: 100%">
            <el-option v-for="item in relationTypeOptions" :key="`delete-${item.value}`" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showDeleteRelationDialog = false">取消</el-button>
        <el-button type="danger" :loading="deletingRelation" @click="submitDeleteRelation">确认删除</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
.graph-view {
  min-height: 0;
}

.page-toolbar :deep(.graph-action-btn.el-button) {
  color: #fff;
  background-color: #3b82f6;
  border-color: #3b82f6;
}

.page-toolbar :deep(.graph-action-btn.el-button.is-plain) {
  color: #fff;
  background-color: #3b82f6;
  border-color: #3b82f6;
}

.page-toolbar :deep(.graph-action-btn.el-button:hover),
.page-toolbar :deep(.graph-action-btn.el-button:focus-visible) {
  color: #fff;
  background-color: #2f74e6;
  border-color: #2f74e6;
}

.graph-body {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 10px;
}

.graph-toolbar {
  padding: 10px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  position: sticky;
  top: 0;
  z-index: 8;
}

.toolbar-advanced {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.search-row {
  display: flex;
  gap: 8px;

  :deep(.el-input) {
    max-width: 420px;
  }
}

.category-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.category-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.category-tag {
  cursor: pointer;
}

.legend-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.relation-row {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 8px;
  align-items: center;
}

.relation-mode {
  display: inline-flex;
  gap: 6px;
}

.relation-title {
  font-size: 12px;
  font-weight: 700;
  color: var(--text-secondary);
}

.relation-summary {
  font-size: 12px;
  color: var(--text-secondary);
}

.relation-mode-hint {
  font-size: 12px;
  color: var(--text-placeholder);
}

.relation-selected-strip {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 8px;
  border: 1px dashed #d6e3fb;
  border-radius: 10px;
  background: rgba(246, 250, 255, 0.88);
}

.relation-selected-scroll {
  min-width: 0;
  flex: 1;
  display: flex;
  align-items: center;
  gap: 6px;
  overflow-x: auto;
  white-space: nowrap;
  scrollbar-width: thin;
}

.relation-selected-scroll.empty {
  color: var(--text-placeholder);
}

.selected-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--text-secondary);
  flex-shrink: 0;
}

.selected-empty {
  font-size: 12px;
}

.relation-chip {
  border-color: #dbe6fb;
  color: #4a6498;
  cursor: pointer;
}

.relation-chip-reset {
  font-weight: 700;
}

.load-error {
  padding: 10px;
}

.health-toggle-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 0 4px;
}

.health-toggle-btn {
  padding: 0;
  font-size: 13px;
}

.graph-health {
  padding: 10px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.health-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.health-title {
  font-size: 13px;
  font-weight: 700;
  color: var(--text-primary);
}

.health-grid {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 8px;
}

.health-item {
  border: 1px solid #dbe6fa;
  background: #f8fbff;
  border-radius: 10px;
  padding: 8px;
  display: flex;
  flex-direction: column;
  gap: 4px;

  span {
    font-size: 12px;
    color: var(--text-secondary);
  }

  strong {
    font-size: 14px;
    color: var(--text-primary);
  }
}

.legend-title {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  font-weight: 700;
  color: var(--text-secondary);
}

.legend-chip {
  --legend-color: #67c23a;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 3px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  color: var(--legend-color);
  background: rgba(255, 255, 255, 0.92);
  border: 1px solid #dfe8f7;
}

.legend-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--legend-color);
  box-shadow: 0 0 0 2px rgba(255, 255, 255, 0.92);
}

.graph-stage {
  position: relative;
  flex: 1;
  min-height: 0;
  height: clamp(520px, calc(100vh - 320px), 760px);
  overflow: hidden;
}

.focus-actions {
  position: absolute;
  top: 10px;
  left: 10px;
  z-index: 9;
  display: inline-flex;
  gap: 8px;
  padding: 8px;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.92);
  border: 1px solid var(--border-light);
  box-shadow: var(--shadow-sm);
}

.graph-body.focus-canvas {
  gap: 0;
  padding-top: 8px;
}

.graph-body.focus-canvas .graph-stage {
  height: clamp(640px, calc(100vh - 180px), 920px);
  border-radius: 14px;
}

.graph-empty {
  height: 100%;
  padding: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.detail-panel {
  position: absolute;
  top: 10px;
  right: 10px;
  bottom: 10px;
  width: 360px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.96);
  border: 1px solid var(--border-light);
  box-shadow: var(--shadow-md);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.detail-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
  padding: 14px;
  border-bottom: 1px solid var(--border-lighter);

  h3 {
    font-size: 17px;
    font-weight: 700;
    color: var(--text-primary);
    margin-bottom: 4px;
  }

  p {
    font-size: 12px;
    color: var(--text-secondary);
  }
}

.detail-body {
  padding: 14px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.detail-section {
  padding: 12px;
  border: 1px solid var(--border-lighter);
  border-radius: 12px;
  background: #fff;

  h4 {
    display: flex;
    align-items: center;
    gap: 6px;
    font-size: 13px;
    font-weight: 700;
    color: var(--text-secondary);
    margin-bottom: 10px;
  }

  p {
    font-size: 14px;
    line-height: 1.65;
    color: var(--text-regular);
  }

  &.actions {
    border-style: dashed;
  }

  &.error {
    border-color: #ffd8d8;
    background: #fff8f8;
  }
}

.expand-meta {
  margin-top: 8px;
  font-size: 12px;
  color: var(--text-secondary);
}

.detail-items {
  display: grid;
  gap: 10px;
}

.detail-item {
  display: flex;
  align-items: center;
  justify-content: space-between;

  label {
    font-size: 12px;
    color: var(--text-secondary);
  }
}

.keyword-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.related-doc-loading {
  padding: 4px 0;
}

.related-doc-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.related-doc-item {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 2px;
  width: 100%;
  border: 1px solid #d9e5fb;
  background: #f8fbff;
  border-radius: 10px;
  padding: 8px 10px;
  cursor: pointer;
  transition: all 0.2s ease;

  .title {
    font-size: 13px;
    font-weight: 600;
    color: var(--text-primary);
  }

  .meta {
    font-size: 12px;
    color: var(--text-secondary);
  }

  &:hover {
    border-color: #9ec3ff;
    background: #eff5ff;
  }
}

.empty-note {
  font-size: 12px;
  color: var(--text-secondary);
}

.mobile-detail-drawer {
  display: none;
}

.mobile-detail {
  padding: 10px;
  height: 100%;
  display: flex;
  flex-direction: column;
}

@media (max-width: 992px) {
  .graph-body {
    padding: 10px;
  }

  .graph-stage {
    height: clamp(460px, calc(100vh - 300px), 680px);
  }

  .graph-body.focus-canvas .graph-stage {
    height: clamp(520px, calc(100vh - 200px), 760px);
  }

  .search-row {
    flex-wrap: wrap;

    :deep(.el-input) {
      max-width: none;
      width: 100%;
    }
  }

  .load-error {
    flex-direction: column;
    align-items: stretch;
  }

  .health-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .detail-panel {
    display: none;
  }

  .mobile-detail-drawer {
    display: block;
  }
}
</style>
