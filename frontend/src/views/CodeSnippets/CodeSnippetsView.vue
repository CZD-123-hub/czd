<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useSnippetStore } from '@/stores/snippet'
import CodeEditor from '@/components/common/CodeEditor.vue'
import CodeBlock from '@/components/common/CodeBlock.vue'
import type { CodeSnippet } from '@/types'
import {
  Search,
  Plus,
  Edit,
  Delete,
  Download,
  Upload,
  RefreshRight,
} from '@element-plus/icons-vue'
import { ElMessageBox } from 'element-plus'

const snippetStore = useSnippetStore()

const showDialog = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const viewMode = ref<'comfortable' | 'compact'>('comfortable')
const editingSnippet = ref<Partial<CodeSnippet>>({
  title: '',
  code: '',
  language: 'javascript',
  description: '',
  tags: [],
})
const tagInput = ref('')
const fileInputRef = ref<HTMLInputElement | null>(null)

const languages = [
  'javascript', 'typescript', 'python', 'java', 'go', 'rust',
  'c', 'cpp', 'csharp', 'php', 'ruby', 'swift', 'kotlin',
  'sql', 'html', 'css', 'scss', 'shell', 'yaml', 'json',
  'markdown', 'xml', 'plaintext',
]

onMounted(() => {
  snippetStore.loadSnippets()
})

function handleSearch(resetPage = true) {
  if (resetPage) {
    snippetStore.filters.page = 1
  }
  snippetStore.loadSnippets()
}

function handlePageChange(page: number) {
  snippetStore.filters.page = page
  snippetStore.loadSnippets()
}

function handleResetFilters() {
  snippetStore.resetFilters()
  handleSearch(false)
}

function toggleTag(tag: string) {
  snippetStore.filters.tag = snippetStore.filters.tag === tag ? '' : tag
  handleSearch(true)
}

function openCreateDialog() {
  dialogMode.value = 'create'
  editingSnippet.value = {
    title: '',
    code: '',
    language: 'javascript',
    description: '',
    tags: [],
  }
  showDialog.value = true
}

function openEditDialog(snippet: CodeSnippet) {
  dialogMode.value = 'edit'
  editingSnippet.value = { ...snippet, tags: [...snippet.tags] }
  showDialog.value = true
}

async function handleSave() {
  if (!editingSnippet.value.title?.trim() || !editingSnippet.value.code?.trim()) return

  if (dialogMode.value === 'create') {
    await snippetStore.createSnippet(editingSnippet.value)
  } else {
    await snippetStore.updateSnippet(editingSnippet.value.id!, editingSnippet.value)
  }
  showDialog.value = false
}

async function handleDelete(snippet: CodeSnippet) {
  try {
    await ElMessageBox.confirm(`确定删除代码片段“${snippet.title}”吗？`, '提示', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await snippetStore.deleteSnippet(snippet.id)
  } catch {
    // cancelled
  }
}

function addTag() {
  const tag = tagInput.value.trim()
  if (tag && !editingSnippet.value.tags?.includes(tag)) {
    if (!editingSnippet.value.tags) {
      editingSnippet.value.tags = []
    }
    editingSnippet.value.tags.push(tag)
    tagInput.value = ''
  }
}

function removeTag(tag: string) {
  if (editingSnippet.value.tags) {
    editingSnippet.value.tags = editingSnippet.value.tags.filter((t) => t !== tag)
  }
}

function handleExport() {
  snippetStore.exportAll()
}

function handleImportClick() {
  fileInputRef.value?.click()
}

function handleFileChange(event: Event) {
  const target = event.target as HTMLInputElement
  const file = target.files?.[0]
  if (file) {
    snippetStore.importFile(file)
    target.value = ''
  }
}

const dialogTitle = computed(() => (dialogMode.value === 'create' ? '新建代码片段' : '编辑代码片段'))
const editorLanguage = computed(() => editingSnippet.value.language ?? 'javascript')

const availableTags = computed(() => {
  const counter = new Map<string, number>()
  for (const snippet of snippetStore.snippets) {
    for (const tag of snippet.tags || []) {
      counter.set(tag, (counter.get(tag) || 0) + 1)
    }
  }
  return [...counter.entries()]
    .sort((a, b) => b[1] - a[1])
    .slice(0, 12)
    .map(([tag, count]) => ({ tag, count }))
})

const activeFilterCount = computed(() => {
  let count = 0
  if (snippetStore.filters.keyword) count += 1
  if (snippetStore.filters.language) count += 1
  if (snippetStore.filters.tag) count += 1
  return count
})

const skeletonCount = computed(() => (viewMode.value === 'compact' ? 6 : 4))

function formatDate(dateStr: string) {
  return new Date(dateStr).toLocaleDateString('zh-CN', {
    month: 'numeric',
    day: 'numeric',
  })
}
</script>

<template>
  <div class="snippets-view page-shell">
    <div class="page-head">
      <div class="page-title-block">
        <h2 class="page-title">代码片段库</h2>
        <p class="page-subtitle">总计 {{ snippetStore.total }} 条，当前筛选 {{ activeFilterCount }} 项</p>
      </div>
      <div class="page-toolbar">
        <el-radio-group v-model="viewMode" size="small" class="view-switch">
          <el-radio-button label="comfortable">舒适</el-radio-button>
          <el-radio-button label="compact">紧凑</el-radio-button>
        </el-radio-group>
        <el-button type="primary" :icon="Plus" @click="openCreateDialog">新建片段</el-button>
      </div>
    </div>

    <div class="snippet-content">
      <div class="toolbar soft-panel">
        <div class="toolbar-left">
          <el-input
            v-model="snippetStore.filters.keyword"
            placeholder="按标题或描述搜索..."
            :prefix-icon="Search"
            clearable
            style="width: 260px"
            @keyup.enter="handleSearch(true)"
            @clear="handleSearch(true)"
          />
          <el-select
            v-model="snippetStore.filters.language"
            placeholder="语言"
            clearable
            style="width: 150px"
            @change="handleSearch(true)"
          >
            <el-option v-for="lang in languages" :key="lang" :label="lang" :value="lang" />
          </el-select>
          <el-button @click="handleSearch(true)">搜索</el-button>
        </div>
        <div class="toolbar-right">
          <el-button :icon="Download" @click="handleExport">导出</el-button>
          <el-button :icon="Upload" @click="handleImportClick">导入</el-button>
          <el-button :icon="RefreshRight" @click="handleResetFilters">重置筛选</el-button>
          <input ref="fileInputRef" type="file" accept=".json" style="display: none" @change="handleFileChange" />
        </div>
      </div>

      <div v-if="availableTags.length > 0" class="tag-filter soft-panel">
        <span class="tag-filter-label">快捷标签</span>
        <el-tag
          v-for="item in availableTags"
          :key="item.tag"
          class="filter-tag"
          :type="snippetStore.filters.tag === item.tag ? 'primary' : 'info'"
          :effect="snippetStore.filters.tag === item.tag ? 'dark' : 'plain'"
          @click="toggleTag(item.tag)"
        >
          {{ item.tag }} · {{ item.count }}
        </el-tag>
      </div>

      <div class="snippet-list">
        <div v-if="snippetStore.loading && snippetStore.snippets.length === 0" class="snippet-grid skeleton-grid" :class="`mode-${viewMode}`">
          <el-skeleton v-for="i in skeletonCount" :key="i" animated class="snippet-card">
            <template #template>
              <el-skeleton-item variant="h3" style="width: 56%" />
              <el-skeleton-item variant="text" style="width: 88%; margin-top: 10px" />
              <el-skeleton-item variant="text" style="width: 94%" />
              <el-skeleton-item variant="rect" style="width: 100%; height: 140px; margin-top: 12px" />
            </template>
          </el-skeleton>
        </div>

        <div v-else-if="snippetStore.snippets.length === 0" class="empty-state soft-panel">
          <el-empty description="暂时没有代码片段">
            <el-button type="primary" @click="openCreateDialog">创建第一条片段</el-button>
          </el-empty>
        </div>

        <div v-else class="snippet-grid" :class="`mode-${viewMode}`">
          <div v-for="snippet in snippetStore.snippets" :key="snippet.id" class="snippet-card soft-panel">
            <div class="snippet-header">
              <div class="snippet-header-main">
                <h4 class="snippet-title">{{ snippet.title }}</h4>
                <div class="snippet-submeta">
                  <el-tag size="small" type="primary" effect="plain">{{ snippet.language }}</el-tag>
                  <span>更新于 {{ formatDate(snippet.updatedAt) }}</span>
                </div>
              </div>
              <div class="snippet-actions">
                <el-button :icon="Edit" text size="small" @click="openEditDialog(snippet)" />
                <el-button :icon="Delete" text size="small" type="danger" @click="handleDelete(snippet)" />
              </div>
            </div>

            <p v-if="snippet.description" class="snippet-desc">{{ snippet.description }}</p>

            <div class="snippet-code">
              <CodeBlock :code="snippet.code" :language="snippet.language" />
            </div>

            <div class="snippet-footer">
              <div class="snippet-tags">
                <el-tag v-for="tag in snippet.tags" :key="tag" size="small" type="info" effect="plain">{{ tag }}</el-tag>
              </div>
              <span class="snippet-meta">使用 {{ snippet.useCount }} 次</span>
            </div>
          </div>
        </div>

        <div v-if="snippetStore.total > snippetStore.filters.size" class="pagination">
          <el-pagination
            background
            layout="prev, pager, next"
            :total="snippetStore.total"
            :page-size="snippetStore.filters.size"
            :current-page="snippetStore.filters.page"
            @current-change="handlePageChange"
          />
        </div>
      </div>
    </div>

    <el-dialog
      v-model="showDialog"
      :title="dialogTitle"
      width="1000px"
      :close-on-click-modal="false"
      top="5vh"
    >
      <el-form label-position="top">
        <el-form-item label="标题" required>
          <el-input v-model="editingSnippet.title" placeholder="代码片段标题" />
        </el-form-item>

        <el-form-item label="语言">
          <el-select v-model="editingSnippet.language" placeholder="选择语言" style="width: 100%">
            <el-option v-for="lang in languages" :key="lang" :label="lang" :value="lang" />
          </el-select>
        </el-form-item>

        <el-form-item label="代码" required>
          <CodeEditor
            v-model="editingSnippet.code"
            :language="editorLanguage"
            height="450px"
            width="100%"
          />
        </el-form-item>

        <el-form-item label="描述">
          <el-input v-model="editingSnippet.description" type="textarea" :rows="4" placeholder="简要说明代码用途与适用场景" />
        </el-form-item>

        <el-form-item label="标签">
          <div class="tag-editor">
            <el-tag
              v-for="tag in editingSnippet.tags"
              :key="tag"
              closable
              size="default"
              @close="removeTag(tag)"
            >
              {{ tag }}
            </el-tag>
            <el-input
              v-model="tagInput"
              size="small"
              style="width: 140px"
              placeholder="添加标签"
              @keyup.enter="addTag"
            />
          </div>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="showDialog = false">取消</el-button>
        <el-button
          type="primary"
          :disabled="!editingSnippet.title?.trim() || !editingSnippet.code?.trim()"
          @click="handleSave"
        >
          保存
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
.snippets-view {
  min-height: 0;
}

.snippet-content {
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
  gap: 12px;
  padding: 12px;
  position: sticky;
  top: 0;
  z-index: 8;
}

.toolbar-left,
.toolbar-right {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.tag-filter {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  padding: 10px 12px;
}

.tag-filter-label {
  font-size: 12px;
  font-weight: 700;
  color: var(--text-secondary);
}

.filter-tag {
  cursor: pointer;
}

.snippet-list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding-right: 2px;
}

.empty-state {
  min-height: 320px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.snippet-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(360px, 1fr));
  gap: 14px;
}

.snippet-card {
  padding: 14px;
  border-radius: var(--radius-md);
  transition: box-shadow 0.2s ease, transform 0.2s ease;

  &:hover {
    box-shadow: var(--shadow-md);
    transform: translateY(-2px);

    .snippet-actions {
      opacity: 1;
    }
  }
}

.snippet-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 8px;
}

.snippet-header-main {
  min-width: 0;
  flex: 1;
}

.snippet-title {
  font-size: 16px;
  font-weight: 700;
  color: var(--text-primary);
  margin-bottom: 6px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.snippet-submeta {
  display: flex;
  align-items: center;
  gap: 8px;

  span {
    font-size: 12px;
    color: var(--text-secondary);
  }
}

.snippet-actions {
  opacity: 0;
  transition: opacity 0.2s;
  display: flex;
  gap: 2px;
}

.snippet-desc {
  font-size: 13px;
  color: var(--text-secondary);
  margin-bottom: 8px;
  line-height: 1.55;
}

.snippet-code {
  overflow: hidden;
  border-radius: var(--radius-sm);
}

.snippet-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 10px;
  flex-wrap: wrap;
  gap: 8px;
}

.snippet-tags {
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
}

.snippet-meta {
  font-size: 12px;
  color: var(--text-secondary);
}

.mode-compact {
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));

  .snippet-card {
    padding: 12px;
  }

  .snippet-title {
    font-size: 14px;
  }

  .snippet-desc {
    font-size: 12px;
    margin-bottom: 6px;
  }

  .snippet-code {
    max-height: 150px;
  }
}

.mode-comfortable {
  .snippet-code {
    max-height: 220px;
  }
}

.pagination {
  display: flex;
  justify-content: center;
  margin-top: 16px;
  padding-bottom: 8px;
}

.tag-editor {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
}

.view-switch {
  :deep(.el-radio-button__inner) {
    min-width: 54px;
  }
}

@media (max-width: 900px) {
  .toolbar {
    position: static;
  }

  .snippet-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .snippet-content {
    padding: 10px;
  }

  .toolbar,
  .tag-filter {
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
}
</style>
