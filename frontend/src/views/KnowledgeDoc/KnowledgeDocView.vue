<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Delete, Search, RefreshRight } from '@element-plus/icons-vue'
import { documentApi, type KnowledgeDocument } from '@/api/document'

const docs = ref<KnowledgeDocument[]>([])
const loading = ref(false)
const submitting = ref(false)
const showDialog = ref(false)

const searchKeyword = ref('')
const activeCategory = ref('')

const form = ref<KnowledgeDocument>({ title: '', content: '', category: '' })
const categories = ['java', 'spring', 'redis', 'mysql', 'vue', 'react', 'docker', 'algorithm', 'other']

async function loadDocs() {
  loading.value = true
  try {
    const res = await documentApi.list()
    docs.value = res.data.data || []
  } catch {
    ElMessage.error('知识文档加载失败')
    docs.value = []
  } finally {
    loading.value = false
  }
}

const filteredDocs = computed(() => {
  const keyword = searchKeyword.value.trim().toLowerCase()
  const category = activeCategory.value

  return docs.value.filter((doc) => {
    const matchedKeyword = !keyword || doc.title.toLowerCase().includes(keyword) || doc.content.toLowerCase().includes(keyword)
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
  if (!id) return
  try {
    await ElMessageBox.confirm('确认删除这篇知识文档吗？', '提示', { type: 'warning' })
    await documentApi.delete(id)
    ElMessage.success('删除成功')
    await loadDocs()
  } catch {
    // cancelled
  }
}

function handleResetFilters() {
  searchKeyword.value = ''
  activeCategory.value = ''
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

onMounted(loadDocs)
</script>

<template>
  <div class="doc-view page-shell">
    <div class="page-head">
      <div class="page-title-block">
        <h2 class="page-title">知识文档中心</h2>
        <p class="page-subtitle">共 {{ docs.length }} 篇文档，支持 RAG 检索增强回答</p>
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
          <el-table :data="filteredDocs" stripe class="doc-table" v-loading="loading">
            <el-table-column prop="title" label="标题" min-width="240" />
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
            <el-table-column label="操作" width="90" align="center">
              <template #default="{ row }">
                <el-button :icon="Delete" text type="danger" size="small" @click="handleDelete(row.id)" />
              </template>
            </el-table-column>
          </el-table>

          <div class="mobile-cards">
            <div v-for="doc in filteredDocs" :key="doc.id" class="doc-card soft-panel">
              <div class="doc-card-header">
                <h4>{{ doc.title }}</h4>
                <el-button :icon="Delete" text type="danger" size="small" @click="handleDelete(doc.id!)" />
              </div>
              <div class="doc-card-meta">
                <el-tag v-if="doc.category" size="small" effect="plain">{{ doc.category }}</el-tag>
                <span>{{ formatDate(doc.createdAt) }}</span>
              </div>
            </div>
          </div>

          <el-empty v-if="!loading && filteredDocs.length === 0" description="没有匹配的知识文档" />
        </template>
      </div>
    </div>

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

@media (max-width: 992px) {
  .doc-table {
    display: none;
  }

  .mobile-cards {
    display: block;
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
}
</style>
