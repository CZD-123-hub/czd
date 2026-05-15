<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Plus, Delete, Search, RefreshRight, Star } from '@element-plus/icons-vue'
import { documentApi, type KnowledgeDocument, type KnowledgeDocumentCreateRequest } from '@/api/document'
import CodeBlock from '@/components/common/CodeBlock.vue'
import AppFeedbackState from '@/components/common/AppFeedbackState.vue'
import type { RelatedGraphNode } from '@/types'

const docs = ref<KnowledgeDocument[]>([])
const loading = ref(false)
const loadError = ref('')
const submitting = ref(false)
const showDialog = ref(false)
const showDetailDialog = ref(false)
const activeDoc = ref<KnowledgeDocument | null>(null)
const highlightedDocId = ref<number | null>(null)

const searchKeyword = ref('')
const activeCategory = ref('')
const savedOnly = ref(false)
const route = useRoute()
const router = useRouter()
let routeHighlightTimer: ReturnType<typeof setTimeout> | null = null
const relatedNodes = ref<RelatedGraphNode[]>([])
const relatedNodesLoading = ref(false)
const relatedNodesError = ref('')

const form = ref<KnowledgeDocumentCreateRequest>({ title: '', content: '', category: '' })
const categories = ['java', 'spring', 'redis', 'mysql', 'vue', 'react', 'docker', 'algorithm', 'other']

interface DetailBlock {
  type: 'text' | 'code'
  content: string
  language?: string
}

interface DetailSection {
  key: string
  title: string
  blocks: DetailBlock[]
}

async function loadDocs() {
  loading.value = true
  loadError.value = ''
  try {
    const res = await documentApi.list(savedOnly.value)
    docs.value = res.data.data || []
  } catch {
    loadError.value = '知识文档加载失败，请稍后重试'
    ElMessage.error(loadError.value)
    docs.value = []
  } finally {
    loading.value = false
  }
}

async function handleToggleSave(doc: KnowledgeDocument) {
  if (!doc?.id) return
  const nextSaved = !Boolean(doc.saved)
  try {
    await documentApi.favorite(doc.id, nextSaved)
    doc.saved = nextSaved
    ElMessage.success(nextSaved ? '已加入文档收藏' : '已取消文档收藏')
    if (savedOnly.value && !nextSaved) {
      await loadDocs()
    }
  } catch {
    // handled by request interceptor
  }
}

function parseRouteDocId(): number | null {
  const raw = String(route.query.docId || '').trim()
  const parsed = Number(raw)
  if (!Number.isFinite(parsed) || parsed <= 0) return null
  return parsed
}

function clearRouteHighlight() {
  highlightedDocId.value = null
  if (routeHighlightTimer) {
    clearTimeout(routeHighlightTimer)
    routeHighlightTimer = null
  }
}

function highlightRouteDoc(docId?: number) {
  if (!docId) return
  clearRouteHighlight()
  highlightedDocId.value = docId
  routeHighlightTimer = setTimeout(() => {
    highlightedDocId.value = null
    routeHighlightTimer = null
  }, 3200)
}

function findRouteTargetDoc(docId: number | null, keyword: string): KnowledgeDocument | null {
  if (docId) {
    return docs.value.find((doc) => doc.id === docId) || null
  }

  if (!keyword) return null
  const normalized = keyword.toLowerCase()
  const exactTitle = docs.value.find((doc) => (doc.title || '').trim().toLowerCase() === normalized)
  if (exactTitle) return exactTitle

  return filteredDocs.value.find((doc) => {
    const title = (doc.title || '').toLowerCase()
    const content = (doc.content || '').toLowerCase()
    return title.includes(normalized) || content.includes(normalized)
  }) || null
}

function applyRouteFilters() {
  const keyword = String(route.query.keyword || '').trim()
  const category = String(route.query.category || '').trim()
  const docId = parseRouteDocId()

  if (keyword) {
    searchKeyword.value = keyword
  }

  if (category) {
    activeCategory.value = category
  }

  return { keyword, docId }
}

function applyRouteTarget() {
  const { keyword, docId } = applyRouteFilters()
  const targetDoc = findRouteTargetDoc(docId, keyword)
  if (!targetDoc) return
  highlightRouteDoc(targetDoc.id)
  openDetail(targetDoc)
}

const filteredDocs = computed(() => {
  const keyword = searchKeyword.value.trim().toLowerCase()
  const category = activeCategory.value

  return docs.value.filter((doc) => {
    const title = (doc.title || '').toLowerCase()
    const content = (doc.content || '').toLowerCase()
    const matchedKeyword = !keyword || title.includes(keyword) || content.includes(keyword)
    const matchedCategory = !category || (doc.category || '') === category
    return matchedKeyword && matchedCategory
  })
})

const categoryStats = computed(() => {
  const map = new Map<string, number>()
  for (const doc of docs.value) {
    const key = doc.category || 'other'
    map.set(key, (map.get(key) || 0) + 1)
  }
  return Array.from(map.entries())
    .sort((a, b) => b[1] - a[1])
    .map(([name, count]) => ({ name, count }))
})

async function handleAdd() {
  const title = form.value.title.trim()
  const content = form.value.content.trim()
  if (!title || !content) {
    ElMessage.warning('标题和内容不能为空')
    return
  }

  submitting.value = true
  try {
    await documentApi.add({
      title,
      content,
      category: form.value.category,
    })
    ElMessage.success('文档添加成功，已加入知识库索引')
    showDialog.value = false
    form.value = { title: '', content: '', category: '' }
    await loadDocs()
  } catch {
    ElMessage.error('添加失败，请稍后重试')
  } finally {
    submitting.value = false
  }
}

async function handleDelete(id?: number) {
  if (!id) {
    ElMessage.warning('文档 ID 无效，无法删除')
    return
  }

  try {
    await documentApi.delete(id)
    ElMessage.success('删除成功')
    if (activeDoc.value?.id === id) {
      showDetailDialog.value = false
      activeDoc.value = null
      relatedNodes.value = []
      relatedNodesError.value = ''
      relatedNodesLoading.value = false
    }
    await loadDocs()
  } catch {
    ElMessage.error('删除失败，请稍后重试')
  }
}

async function loadRelatedNodes(docId: number) {
  relatedNodesLoading.value = true
  relatedNodesError.value = ''
  try {
    const res = await documentApi.relatedNodes(docId, 8)
    relatedNodes.value = res.data.data || []
  } catch {
    relatedNodes.value = []
    relatedNodesError.value = '关联图谱节点加载失败，请稍后重试。'
  } finally {
    relatedNodesLoading.value = false
  }
}

async function openDetail(doc: KnowledgeDocument) {
  activeDoc.value = doc
  showDetailDialog.value = true
  await loadRelatedNodes(doc.id)
}

function handleRowClick(row: KnowledgeDocument) {
  void openDetail(row)
}

function jumpToGraphNode(nodeId: string) {
  if (!nodeId) return
  void router.push({
    name: 'graph',
    query: { node: nodeId, focus: 'node' },
  })
}

function tableRowClassName({ row }: { row: KnowledgeDocument }) {
  if (!highlightedDocId.value || row.id !== highlightedDocId.value) return ''
  return 'is-route-highlight'
}

function handleResetFilters() {
  searchKeyword.value = ''
  activeCategory.value = ''
}

function handleToggleSavedOnly() {
  savedOnly.value = !savedOnly.value
  void loadDocs()
}

function formatDate(dateStr?: string) {
  if (!dateStr) return '-'
  const date = new Date(dateStr)
  if (Number.isNaN(date.getTime())) return dateStr
  return date.toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}

function escapeHtml(text: string): string {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

function sanitizeUrl(raw: string): string {
  const url = raw.trim()
  if (/^(https?:\/\/|mailto:)/i.test(url)) {
    return url.replace(/"/g, '%22')
  }
  return ''
}

function formatDocText(text: string): string {
  const escaped = escapeHtml(text)
  const inlineFormatted = escaped
    .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
    .replace(/\*(.*?)\*/g, '<em>$1</em>')
    .replace(/`([^`]+)`/g, '<code class="doc-inline-code">$1</code>')
    .replace(/\[([^\]]+)\]\(([^)]+)\)/g, (_all, label: string, url: string) => {
      const safeUrl = sanitizeUrl(url)
      if (!safeUrl) return label
      return `<a href="${safeUrl}" target="_blank" rel="noopener noreferrer">${label}</a>`
    })

  return inlineFormatted
    .split(/\n{2,}/)
    .map((paragraph) => `<p>${paragraph.replace(/\n/g, '<br>')}</p>`)
    .join('')
}

function parseDetailSections(content: string): DetailSection[] {
  const sections: DetailSection[] = []
  let sectionIndex = 0
  let currentSection: DetailSection = {
    key: `section-${sectionIndex++}`,
    title: '内容概览',
    blocks: [],
  }
  sections.push(currentSection)

  let textBuffer: string[] = []
  let inCode = false
  let codeLanguage = 'plaintext'
  let codeBuffer: string[] = []

  const flushText = () => {
    const text = textBuffer.join('\n').trim()
    if (text) currentSection.blocks.push({ type: 'text', content: text })
    textBuffer = []
  }

  const flushCode = () => {
    const code = codeBuffer.join('\n').trimEnd()
    if (code) currentSection.blocks.push({ type: 'code', content: code, language: codeLanguage })
    codeBuffer = []
  }

  for (const line of content.split('\n')) {
    const codeFence = line.match(/^```([a-zA-Z0-9_+-]+)?\s*$/)
    if (codeFence) {
      if (!inCode) {
        flushText()
        inCode = true
        codeLanguage = codeFence[1] || 'plaintext'
      } else {
        flushCode()
        inCode = false
        codeLanguage = 'plaintext'
      }
      continue
    }

    if (inCode) {
      codeBuffer.push(line)
      continue
    }

    const heading = line.match(/^##\s+(.+?)\s*$/)
    if (heading) {
      flushText()
      currentSection = {
        key: `section-${sectionIndex++}`,
        title: heading[1] || '未命名章节',
        blocks: [],
      }
      sections.push(currentSection)
      continue
    }

    textBuffer.push(line)
  }

  if (inCode) flushCode()
  flushText()

  return sections.filter((section) => section.blocks.length > 0)
}

const detailSections = computed<DetailSection[]>(() => {
  const content = activeDoc.value?.content?.trim()
  if (!content) return []
  return parseDetailSections(content)
})

const detailWordCount = computed(() => {
  const content = activeDoc.value?.content || ''
  return content.replace(/\s+/g, '').length
})

onMounted(async () => {
  await loadDocs()
  applyRouteTarget()
})

watch(
  () => route.query,
  () => {
    applyRouteTarget()
  },
  { deep: true },
)

watch(showDetailDialog, (visible) => {
  if (visible) return
  relatedNodes.value = []
  relatedNodesError.value = ''
  relatedNodesLoading.value = false
})

onBeforeUnmount(() => {
  clearRouteHighlight()
})
</script>

<template>
  <div class="doc-view page-shell">
    <div class="page-head">
      <div class="page-title-block">
        <h2 class="page-title">知识文档中心</h2>
        <p class="page-subtitle">
          {{ savedOnly ? `我的收藏 ${docs.length} 篇` : `共 ${docs.length} 篇文档` }}，支持 RAG 检索增强回答
        </p>
      </div>
      <div class="page-toolbar">
        <el-button type="primary" :icon="Plus" @click="showDialog = true">添加文档</el-button>
      </div>
    </div>

    <div class="doc-content">
      <div class="toolbar soft-panel">
        <div class="toolbar-left">
          <el-input
            v-model="searchKeyword"
            :prefix-icon="Search"
            placeholder="按标题或内容搜索..."
            clearable
            style="width: 280px"
          />
          <el-select v-model="activeCategory" clearable placeholder="筛选分类" style="width: 160px">
            <el-option v-for="c in categories" :key="c" :label="c" :value="c" />
          </el-select>
        </div>
        <div class="toolbar-right">
          <el-button
            :type="savedOnly ? 'primary' : 'default'"
            plain
            :icon="Star"
            @click="handleToggleSavedOnly"
          >
            {{ savedOnly ? '我的收藏' : '全部文档' }}
          </el-button>
          <el-button :icon="RefreshRight" @click="handleResetFilters">重置筛选</el-button>
          <el-tag type="primary" effect="plain">当前 {{ filteredDocs.length }} 条</el-tag>
        </div>
      </div>

      <div v-if="categoryStats.length > 0" class="category-strip soft-panel">
        <span class="strip-label">分类分布</span>
        <el-tag
          v-for="item in categoryStats"
          :key="item.name"
          class="strip-tag"
          :effect="activeCategory === item.name ? 'dark' : 'plain'"
          :type="activeCategory === item.name ? 'primary' : 'info'"
          @click="activeCategory = activeCategory === item.name ? '' : item.name"
        >
          {{ item.name }} · {{ item.count }}
        </el-tag>
      </div>

      <div class="list-wrap">
        <div v-if="loading && docs.length === 0" class="skeleton-list">
          <el-skeleton v-for="i in 4" :key="i" animated class="skeleton-card">
            <template #template>
              <el-skeleton-item variant="h3" style="width: 56%" />
              <el-skeleton-item variant="text" style="width: 38%; margin-top: 8px" />
              <el-skeleton-item variant="text" style="width: 95%; margin-top: 10px" />
              <el-skeleton-item variant="text" style="width: 88%" />
            </template>
          </el-skeleton>
        </div>

        <template v-else>
          <el-table
            :data="filteredDocs"
            stripe
            class="doc-table"
            v-loading="loading"
            :row-class-name="tableRowClassName"
            @row-click="handleRowClick"
          >
            <el-table-column prop="title" label="标题" min-width="260">
              <template #default="{ row }">
                <el-button class="doc-title-btn" type="primary" link @click.stop="openDetail(row)">
                  {{ row.title }}
                </el-button>
              </template>
            </el-table-column>
            <el-table-column prop="category" label="分类" width="130">
              <template #default="{ row }">
                <el-tag v-if="row.category" size="small" effect="plain">{{ row.category }}</el-tag>
                <span v-else class="text-secondary">-</span>
              </template>
            </el-table-column>
            <el-table-column label="创建时间" width="170">
              <template #default="{ row }">
                <span>{{ formatDate(row.createdAt) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="170" align="center">
              <template #default="{ row }">
                <el-button
                  class="doc-op-btn"
                  :icon="Star"
                  size="small"
                  type="primary"
                  @click.stop="handleToggleSave(row)"
                >
                  {{ row.saved ? '已收藏' : '收藏' }}
                </el-button>
                <el-popconfirm title="确认删除这篇知识文档吗？" width="220" @confirm="handleDelete(row.id)">
                  <template #reference>
                    <el-button :icon="Delete" text type="danger" size="small" @click.stop />
                  </template>
                </el-popconfirm>
              </template>
            </el-table-column>
          </el-table>

          <div class="mobile-cards">
            <div
              v-for="doc in filteredDocs"
              :key="doc.id"
              class="doc-card soft-panel"
              :class="{ 'is-route-highlight': doc.id === highlightedDocId }"
              @click="openDetail(doc)"
            >
              <div class="doc-card-header">
                <h4>{{ doc.title }}</h4>
                <div class="doc-card-actions">
                  <el-button
                    class="doc-op-btn"
                    :icon="Star"
                    type="primary"
                    size="small"
                    @click.stop="handleToggleSave(doc)"
                  >
                    {{ doc.saved ? '已收藏' : '收藏' }}
                  </el-button>
                  <el-popconfirm title="确认删除这篇知识文档吗？" width="220" @confirm="handleDelete(doc.id)">
                    <template #reference>
                      <el-button :icon="Delete" text type="danger" size="small" @click.stop />
                    </template>
                  </el-popconfirm>
                </div>
              </div>
              <div class="doc-card-meta">
                <el-tag v-if="doc.category" size="small" effect="plain">{{ doc.category }}</el-tag>
                <span>{{ formatDate(doc.createdAt) }}</span>
              </div>
            </div>
          </div>

          <AppFeedbackState
            v-if="!loading && filteredDocs.length === 0"
            :type="loadError ? 'error' : 'empty'"
            :title="loadError ? '知识文档加载失败' : '没有匹配的知识文档'"
            :description="loadError || '试试更换关键词或重置筛选条件。'"
          >
            <template #actions>
              <el-button v-if="loadError" type="primary" @click="loadDocs">重新加载</el-button>
              <el-button v-else @click="handleResetFilters">重置筛选</el-button>
            </template>
          </AppFeedbackState>
        </template>
      </div>
    </div>

    <el-dialog v-model="showDetailDialog" width="860px" :close-on-click-modal="true" class="doc-detail-dialog">
      <template #header>
        <div class="detail-header soft-panel">
          <div class="detail-title-wrap">
            <p class="detail-kicker">Knowledge Document</p>
            <h3>{{ activeDoc?.title || '文档详情' }}</h3>
          </div>
          <div class="detail-meta">
            <el-tag v-if="activeDoc?.category" size="small" effect="plain">{{ activeDoc.category }}</el-tag>
            <span>{{ formatDate(activeDoc?.createdAt) }}</span>
            <span>约 {{ detailWordCount }} 字</span>
          </div>
        </div>
      </template>

      <div class="detail-body">
        <section class="linked-graph soft-panel">
          <div class="linked-graph-head">
            <h4>关联知识图谱节点</h4>
            <span v-if="relatedNodes.length > 0">{{ relatedNodes.length }} 个</span>
          </div>

          <div v-if="relatedNodesLoading" class="linked-graph-loading">
            <el-skeleton animated :rows="2" />
          </div>

          <AppFeedbackState
            v-else-if="relatedNodesError"
            type="error"
            size="compact"
            :centered="false"
            title="关联节点加载失败"
            :description="relatedNodesError"
          >
            <template #actions>
              <el-button size="small" type="primary" plain @click="activeDoc?.id && loadRelatedNodes(activeDoc.id)">重试</el-button>
            </template>
          </AppFeedbackState>

          <div v-else-if="relatedNodes.length > 0" class="linked-graph-list">
            <button
              v-for="node in relatedNodes"
              :key="node.id"
              class="linked-graph-item"
              type="button"
              @click="jumpToGraphNode(node.id)"
            >
              <span class="name">{{ node.name }}</span>
              <span class="meta">{{ node.category || '未分类' }} · score {{ node.score.toFixed(1) }}</span>
            </button>
          </div>
          <p v-else class="linked-graph-empty">暂无匹配图谱节点，可在知识图谱补充节点后再关联。</p>
        </section>

        <div v-if="detailSections.length > 0" class="detail-sections">
          <article v-for="section in detailSections" :key="section.key" class="detail-section soft-panel">
            <h4 class="detail-section-title">{{ section.title }}</h4>
            <div class="detail-section-content">
              <template v-for="(block, index) in section.blocks" :key="`${section.key}-${index}`">
                <div v-if="block.type === 'text'" class="detail-text-block" v-html="formatDocText(block.content)" />
                <CodeBlock v-else :code="block.content" :language="block.language || 'plaintext'" />
              </template>
            </div>
          </article>
        </div>
        <el-empty v-else description="该文档暂无详细内容" />
      </div>

      <template #footer>
        <el-button @click="showDetailDialog = false">关闭</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showDialog" title="添加知识文档" width="680px" :close-on-click-modal="false">
      <el-form label-width="72px">
        <el-form-item label="标题">
          <el-input v-model="form.title" placeholder="例如：Redis 缓存一致性实践" />
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="form.category" placeholder="选择分类" clearable style="width: 100%">
            <el-option v-for="c in categories" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>
        <el-form-item label="内容">
          <el-input
            v-model="form.content"
            type="textarea"
            :rows="10"
            placeholder="输入知识文档内容，支持技术说明、最佳实践和示例代码。"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showDialog = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleAdd">
          {{ submitting ? '正在提交...' : '确认添加' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
.doc-view {
  min-height: 0;
}

.doc-content {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 12px;
}

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 12px;
}

.toolbar-left,
.toolbar-right {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.category-strip {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  padding: 10px 12px;
}

.strip-label {
  font-size: 12px;
  font-weight: 700;
  color: var(--text-secondary);
}

.strip-tag {
  cursor: pointer;
}

.list-wrap {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
}

.doc-table {
  width: 100%;
}

.mobile-cards {
  display: none;
}

.doc-card {
  padding: 12px;
  margin-bottom: 8px;
  cursor: pointer;

  &.is-route-highlight {
    border-color: #b8d0ff;
    box-shadow: 0 0 0 2px rgba(47, 107, 255, 0.18), var(--shadow-sm);
  }
}

.doc-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;

  h4 {
    font-size: 15px;
    font-weight: 700;
    color: var(--text-primary);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.doc-card-actions {
  display: flex;
  align-items: center;
  gap: 4px;
}

:deep(.doc-op-btn.el-button) {
  color: #fff;
  background-color: #3b82f6;
  border-color: #3b82f6;
}

:deep(.doc-op-btn.el-button:hover),
:deep(.doc-op-btn.el-button:focus-visible) {
  color: #fff;
  background-color: #2f74e6;
  border-color: #2f74e6;
}

.doc-card-meta {
  margin-top: 8px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 12px;
  color: var(--text-secondary);
}

.skeleton-list {
  display: grid;
  gap: 8px;
}

.skeleton-card {
  padding: 14px;
  border: 1px solid var(--border-light);
  border-radius: 12px;
  background: #fff;
}

.text-secondary {
  color: var(--text-secondary);
}

.doc-title-btn {
  padding: 0;
  font-weight: 600;
}

:deep(.doc-table .el-table__row) {
  cursor: pointer;
}

:deep(.doc-table .el-table__row.is-route-highlight > td) {
  background: rgba(226, 237, 255, 0.92) !important;
}

.detail-header {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 14px;
  border-radius: 14px;
  border: 1px solid var(--border-light);
  background:
    linear-gradient(130deg, rgba(47, 107, 255, 0.1), rgba(56, 189, 248, 0.08)),
    #fff;
}

.detail-title-wrap {
  display: flex;
  flex-direction: column;
  gap: 2px;

  h3 {
    font-size: 30px;
    font-weight: 800;
    line-height: 1.2;
    color: var(--text-primary);
    margin: 0;
  }
}

.detail-kicker {
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: #4f77c8;
}

.detail-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  color: var(--text-secondary);
  font-size: 12px;
}

.detail-body {
  max-height: 62vh;
  overflow: auto;
  padding-right: 4px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.linked-graph {
  padding: 12px;
  border-radius: 12px;
}

.linked-graph-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;

  h4 {
    margin: 0;
    font-size: 14px;
    font-weight: 700;
    color: var(--text-primary);
  }

  span {
    font-size: 12px;
    color: var(--text-secondary);
  }
}

.linked-graph-loading {
  padding: 4px 0;
}

.linked-graph-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.linked-graph-item {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 2px;
  width: 100%;
  border: 1px solid #d9e5fb;
  border-radius: 10px;
  background: #f7fbff;
  padding: 8px 10px;
  cursor: pointer;
  transition: all 0.2s ease;

  .name {
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
    background: #eef5ff;
  }
}

.linked-graph-empty {
  font-size: 12px;
  color: var(--text-secondary);
}

.detail-sections {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.detail-section {
  padding: 14px;
  border-radius: 14px;
}

.detail-section-title {
  font-size: 18px;
  font-weight: 700;
  color: #1f3f78;
  margin-bottom: 10px;
  padding-left: 10px;
  border-left: 4px solid var(--primary-color);
}

.detail-section-content {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.detail-text-block {
  font-size: 16px;
  line-height: 1.85;
  color: var(--text-regular);
  background: rgba(255, 255, 255, 0.9);
  border: 1px solid var(--border-light);
  border-radius: 12px;
  padding: 12px 14px;

  :deep(p) {
    margin: 0;
  }

  :deep(p + p) {
    margin-top: 12px;
  }

  :deep(strong) {
    color: var(--text-primary);
    font-weight: 700;
  }

  :deep(a) {
    color: var(--primary-color);
    text-decoration: underline;
  }
}

:deep(.doc-inline-code) {
  font-family: 'SF Mono', 'Fira Code', 'Consolas', monospace;
  font-size: 0.9em;
  padding: 2px 6px;
  border-radius: 6px;
  background: #eef3ff;
  color: #294f98;
}

@media (max-width: 992px) {
  .doc-table {
    display: none;
  }

  .mobile-cards {
    display: block;
  }

  .detail-title-wrap h3 {
    font-size: 24px;
  }
}

@media (max-width: 768px) {
  .doc-content {
    padding: 10px;
  }

  .toolbar,
  .category-strip {
    padding: 10px;
  }

  .toolbar-left,
  .toolbar-right {
    width: 100%;
  }

  .toolbar-left :deep(.el-input),
  .toolbar-left :deep(.el-select) {
    width: 100% !important;
  }

  .detail-title-wrap h3 {
    font-size: 20px;
  }

  .detail-text-block {
    font-size: 15px;
    line-height: 1.75;
  }
}
</style>
