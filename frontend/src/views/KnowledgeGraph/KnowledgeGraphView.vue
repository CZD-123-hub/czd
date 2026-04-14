<script setup lang="ts">
import { ref, onMounted, computed, onBeforeUnmount } from 'vue'
import { useGraphStore } from '@/stores/graph'
import GraphRenderer from '@/components/graph/GraphRenderer.vue'
import {
  Search,
  RefreshRight,
  DataAnalysis,
  Grid,
  Connection,
  InfoFilled,
  Expand,
  Close,
} from '@element-plus/icons-vue'

const graphStore = useGraphStore()

const searchKeyword = ref('')
const showDetail = ref(false)
const activeCategory = ref('')
const isMobile = ref(window.innerWidth < 992)

const nodeCount = computed(() => graphStore.nodes.length)
const edgeCount = computed(() => graphStore.edges.length)
const categories = computed(() => {
  const set = new Set<string>()
  for (const node of graphStore.nodes) {
    if (node.category) set.add(node.category)
  }
  return Array.from(set)
})

function handleResize() {
  isMobile.value = window.innerWidth < 992
}

onMounted(() => {
  graphStore.loadOverview()
  handleResize()
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
})

async function handleSearch() {
  const keyword = searchKeyword.value.trim()
  activeCategory.value = ''
  if (keyword) {
    await graphStore.search(keyword)
  } else {
    await graphStore.loadOverview()
  }
}

async function handleNodeClick(nodeId: string) {
  showDetail.value = true
  await graphStore.selectNode(nodeId)
  graphStore.loadNeighbors(nodeId)
}

function closeDetail() {
  showDetail.value = false
  graphStore.clearSelection()
}

async function handleRefresh() {
  activeCategory.value = ''
  searchKeyword.value = ''
  await graphStore.loadOverview()
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

function difficultyType(level?: string) {
  if (level === 'hard') return 'danger'
  if (level === 'medium') return 'warning'
  return 'success'
}

function difficultyLabel(level?: string) {
  if (level === 'hard') return '困难'
  if (level === 'medium') return '中等'
  return '基础'
}

async function expandCurrentNode() {
  if (!graphStore.selectedNode) return
  await graphStore.loadNeighbors(graphStore.selectedNode.id)
}
</script>

<template>
  <div class="graph-view page-shell" v-loading="graphStore.loading && nodeCount === 0">
    <div class="page-head">
      <div class="page-title-block">
        <h2 class="page-title">知识图谱探索</h2>
        <p class="page-subtitle">节点 {{ nodeCount }} · 关系 {{ edgeCount }} · 分类 {{ categories.length }}</p>
      </div>
      <div class="page-toolbar">
        <el-button :icon="RefreshRight" @click="handleRefresh">刷新图谱</el-button>
      </div>
    </div>

    <div class="graph-body">
      <div class="graph-toolbar soft-panel">
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
          <el-tag size="small" effect="plain" type="success">基础节点</el-tag>
          <el-tag size="small" effect="plain" type="warning">中等难度</el-tag>
          <el-tag size="small" effect="plain" type="danger">困难节点</el-tag>
          <el-tag size="small" effect="plain"><el-icon><Connection /></el-icon> 拖拽 / 缩放 / 点击查看详情</el-tag>
        </div>
      </div>

      <div class="graph-stage soft-panel">
        <GraphRenderer
          :nodes="graphStore.nodes"
          :edges="graphStore.edges"
          @node-click="handleNodeClick"
        />

        <transition name="slide-right">
          <aside v-if="showDetail && graphStore.selectedNode && !isMobile" class="detail-panel">
            <div class="detail-header">
              <div>
                <h3>{{ graphStore.selectedNode.name }}</h3>
                <p>节点详情与关联学习建议</p>
              </div>
              <el-button :icon="Close" text circle @click="closeDetail" />
            </div>
            <div class="detail-body">
              <section class="detail-section">
                <h4><el-icon><Grid /></el-icon> 概览</h4>
                <div class="detail-items">
                  <div class="detail-item">
                    <label>分类</label>
                    <el-tag size="small">{{ graphStore.selectedNode.category || '未分类' }}</el-tag>
                  </div>
                  <div class="detail-item">
                    <label>难度</label>
                    <el-tag size="small" :type="difficultyType(graphStore.selectedNode.difficulty)">
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

              <section class="detail-section actions">
                <el-button type="primary" :icon="Expand" @click="expandCurrentNode">继续展开关联节点</el-button>
              </section>
            </div>
          </aside>
        </transition>
      </div>
    </div>

    <el-drawer v-model="showDetail" direction="rtl" size="92%" :with-header="false" class="mobile-detail-drawer">
      <div v-if="graphStore.selectedNode" class="mobile-detail">
        <div class="detail-header">
          <div>
            <h3>{{ graphStore.selectedNode.name }}</h3>
            <p>节点详情与关联学习建议</p>
          </div>
          <el-button :icon="Close" text circle @click="closeDetail" />
        </div>

        <div class="detail-body">
          <section class="detail-section">
            <h4><el-icon><Grid /></el-icon> 概览</h4>
            <div class="detail-items">
              <div class="detail-item">
                <label>分类</label>
                <el-tag size="small">{{ graphStore.selectedNode.category || '未分类' }}</el-tag>
              </div>
              <div class="detail-item">
                <label>难度</label>
                <el-tag size="small" :type="difficultyType(graphStore.selectedNode.difficulty)">
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

          <section class="detail-section actions">
            <el-button type="primary" :icon="Expand" @click="expandCurrentNode">继续展开关联节点</el-button>
          </section>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<style lang="scss" scoped>
.graph-view {
  min-height: 0;
}

.graph-body {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 12px;
}

.graph-toolbar {
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  position: sticky;
  top: 0;
  z-index: 8;
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

.legend-title {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  font-weight: 700;
  color: var(--text-secondary);
}

.graph-stage {
  position: relative;
  flex: 1;
  min-height: 0;
  overflow: hidden;
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

  .search-row {
    flex-wrap: wrap;

    :deep(.el-input) {
      max-width: none;
      width: 100%;
    }
  }

  .detail-panel {
    display: none;
  }

  .mobile-detail-drawer {
    display: block;
  }
}
</style>
