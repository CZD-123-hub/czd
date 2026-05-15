<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { usePathStore } from '@/stores/path'
import PathTimeline from '@/components/path/PathTimeline.vue'
import AppFeedbackState from '@/components/common/AppFeedbackState.vue'
import type { LearningPath, NodeStatus } from '@/types'
import { Plus, Operation, CollectionTag, CircleCheck, Timer, Delete } from '@element-plus/icons-vue'
import { ElMessageBox } from 'element-plus'

const pathStore = usePathStore()

const showGenerateDialog = ref(false)
const showPathDrawer = ref(false)
const generateForm = ref({
  target: '',
  knownKnowledge: '',
})

const totalPaths = computed(() => pathStore.paths.length)
const completedPaths = computed(() => pathStore.paths.filter((item) => item.status === 'completed').length)

const currentPathProgress = computed(() => {
  if (!pathStore.currentPath?.nodes?.length) return 0
  const done = pathStore.currentPath.nodes.filter((item) => item.status === 'done').length
  return Math.round((done / pathStore.currentPath.nodes.length) * 100)
})

const currentNodeStats = computed(() => {
  const nodes = pathStore.currentPath?.nodes || []
  return {
    total: nodes.length,
    done: nodes.filter((node) => node.status === 'done').length,
    doing: nodes.filter((node) => node.status === 'doing').length,
  }
})

onMounted(() => {
  pathStore.loadPaths()
})

function handleSelectPath(path: LearningPath) {
  pathStore.selectPath(path)
  showPathDrawer.value = false
}

function openGenerateDialog() {
  generateForm.value = { target: '', knownKnowledge: '' }
  showGenerateDialog.value = true
}

async function handleGenerate() {
  const target = generateForm.value.target.trim()
  if (!target) return

  const known = generateForm.value.knownKnowledge
    .split(/[,，\n]/)
    .map((item) => item.trim())
    .filter(Boolean)

  await pathStore.generatePath(target, known)
  showGenerateDialog.value = false
  showPathDrawer.value = false
}

function handleStatusChange(nodeId: number, status: NodeStatus) {
  if (pathStore.currentPath) {
    pathStore.updateNodeStatus(pathStore.currentPath.id, nodeId, status)
  }
}

async function handleDeletePath(path: LearningPath) {
  try {
    await ElMessageBox.confirm(
      `确认删除学习路径「${path.target}」吗？删除后不可恢复。`,
      '删除确认',
      {
        confirmButtonText: '确认删除',
        cancelButtonText: '取消',
        type: 'warning',
      },
    )
  } catch {
    return
  }

  await pathStore.deletePath(path.id)
}

function getProgressPercent(path: LearningPath): number {
  if (!path.nodes || path.nodes.length === 0) return 0
  const doneCount = path.nodes.filter((node) => node.status === 'done').length
  return Math.round((doneCount / path.nodes.length) * 100)
}

function getStatusType(status: string): 'success' | 'warning' | 'info' | 'danger' {
  switch (status) {
    case 'completed':
      return 'success'
    case 'in_progress':
      return 'warning'
    default:
      return 'info'
  }
}

function getStatusLabel(status: string): string {
  switch (status) {
    case 'completed':
      return '已完成'
    case 'in_progress':
      return '进行中'
    default:
      return '未开始'
  }
}
</script>

<template>
  <div class="path-view page-shell" v-loading="pathStore.loading && totalPaths === 0">
    <div class="page-head">
      <div class="page-title-block">
        <h2 class="page-title">学习路径工作台</h2>
        <p class="page-subtitle">共 {{ totalPaths }} 条路径 · 完成 {{ completedPaths }} 条</p>
      </div>
      <div class="page-toolbar">
        <el-button class="mobile-only" :icon="Operation" @click="showPathDrawer = true">路径列表</el-button>
        <el-button type="primary" :icon="Plus" @click="openGenerateDialog">生成路径</el-button>
      </div>
    </div>

    <div class="path-body">
      <aside class="path-sidebar soft-panel">
        <div class="sidebar-header">
          <h3>路径总览</h3>
          <el-button type="primary" :icon="Plus" size="small" class="new-path-btn" @click="openGenerateDialog">新建</el-button>
        </div>
        <div class="path-list">
          <div v-if="pathStore.paths.length === 0" class="empty-state">
            <AppFeedbackState
              :type="pathStore.loadError ? 'error' : 'empty'"
              :title="pathStore.loadError ? '学习路径加载失败' : '还没有学习路径'"
              :description="pathStore.loadError || '先生成一条路径开始学习。'"
            >
              <template #actions>
                <el-button v-if="pathStore.loadError" type="primary" @click="pathStore.loadPaths">重试加载</el-button>
                <el-button v-else type="primary" @click="openGenerateDialog">生成第一条路径</el-button>
              </template>
            </AppFeedbackState>
          </div>

          <div
            v-for="path in pathStore.paths"
            :key="path.id"
            class="path-card"
            :class="{ active: pathStore.currentPath?.id === path.id }"
            @click="handleSelectPath(path)"
          >
            <div class="path-card-header">
              <span class="path-target">{{ path.target }}</span>
            </div>
            <div class="path-card-meta">
              <div class="path-meta-left">
                <el-tag :type="getStatusType(path.status)" size="small">{{ getStatusLabel(path.status) }}</el-tag>
                <span class="node-count">{{ path.nodes?.length || 0 }} 节点</span>
              </div>
              <el-button
                :icon="Delete"
                text
                size="small"
                type="danger"
                class="delete-btn"
                @click.stop="handleDeletePath(path)"
              >
                删除
              </el-button>
            </div>
            <el-progress
              :percentage="getProgressPercent(path)"
              :stroke-width="6"
              :show-text="false"
              class="path-progress"
            />
          </div>
        </div>
      </aside>

      <el-drawer v-model="showPathDrawer" direction="ltr" size="320px" :with-header="false" class="mobile-drawer">
        <div class="drawer-content">
          <div class="sidebar-header">
            <h3>路径总览</h3>
            <el-button type="primary" :icon="Plus" size="small" class="new-path-btn" @click="openGenerateDialog">新建</el-button>
          </div>
          <div class="path-list">
            <div v-if="pathStore.paths.length === 0" class="empty-state">
              <AppFeedbackState
                :type="pathStore.loadError ? 'error' : 'empty'"
                :title="pathStore.loadError ? '学习路径加载失败' : '还没有学习路径'"
                :description="pathStore.loadError || '先生成一条路径开始学习。'"
              >
                <template #actions>
                  <el-button v-if="pathStore.loadError" type="primary" @click="pathStore.loadPaths">重试加载</el-button>
                  <el-button v-else type="primary" @click="openGenerateDialog">生成第一条路径</el-button>
                </template>
              </AppFeedbackState>
            </div>

            <div
              v-for="path in pathStore.paths"
              :key="path.id"
              class="path-card"
              :class="{ active: pathStore.currentPath?.id === path.id }"
              @click="handleSelectPath(path)"
            >
              <div class="path-card-header">
                <span class="path-target">{{ path.target }}</span>
              </div>
              <div class="path-card-meta">
                <div class="path-meta-left">
                  <el-tag :type="getStatusType(path.status)" size="small">{{ getStatusLabel(path.status) }}</el-tag>
                  <span class="node-count">{{ path.nodes?.length || 0 }} 节点</span>
                </div>
                <el-button
                  :icon="Delete"
                  text
                  size="small"
                  type="danger"
                  class="delete-btn"
                  @click.stop="handleDeletePath(path)"
                >
                  删除
                </el-button>
              </div>
              <el-progress :percentage="getProgressPercent(path)" :stroke-width="6" :show-text="false" class="path-progress" />
            </div>
          </div>
        </div>
      </el-drawer>

      <section class="path-content soft-panel">
        <div v-if="!pathStore.currentPath" class="empty-content">
          <AppFeedbackState
            type="empty"
            title="请选择或创建一条学习路径"
            description="选择左侧路径查看详情，或者新建一条路径。"
          >
            <template #actions>
              <el-button type="primary" @click="openGenerateDialog">立即生成路径</el-button>
            </template>
          </AppFeedbackState>
        </div>

        <div v-else class="path-detail">
          <div class="detail-header">
            <div class="detail-header-main">
              <h2>{{ pathStore.currentPath.target }}</h2>
              <div class="detail-meta">
                <el-tag :type="getStatusType(pathStore.currentPath.status)" size="small">
                  {{ getStatusLabel(pathStore.currentPath.status) }}
                </el-tag>
                <span>创建于 {{ new Date(pathStore.currentPath.createdAt).toLocaleDateString('zh-CN') }}</span>
              </div>
            </div>
            <el-progress type="circle" :percentage="currentPathProgress" :width="72" :stroke-width="8" />
          </div>

          <div class="stats-row">
            <div class="stat-card soft-panel">
              <div class="stat-icon total"><el-icon><CollectionTag /></el-icon></div>
              <div>
                <span class="stat-label">总节点</span>
                <p class="stat-value">{{ currentNodeStats.total }}</p>
              </div>
            </div>
            <div class="stat-card soft-panel">
              <div class="stat-icon done"><el-icon><CircleCheck /></el-icon></div>
              <div>
                <span class="stat-label">已完成</span>
                <p class="stat-value">{{ currentNodeStats.done }}</p>
              </div>
            </div>
            <div class="stat-card soft-panel">
              <div class="stat-icon doing"><el-icon><Timer /></el-icon></div>
              <div>
                <span class="stat-label">进行中</span>
                <p class="stat-value">{{ currentNodeStats.doing }}</p>
              </div>
            </div>
          </div>

          <div class="detail-body">
            <PathTimeline :nodes="pathStore.currentPath.nodes || []" @status-change="handleStatusChange" />
          </div>
        </div>
      </section>
    </div>

    <el-dialog
      v-model="showGenerateDialog"
      title="生成学习路径"
      width="560px"
      :close-on-click-modal="false"
    >
      <el-form label-position="top">
        <el-form-item label="学习目标" required>
          <el-input v-model="generateForm.target" placeholder="例如：掌握 Spring Cloud 微服务架构" />
        </el-form-item>
        <el-form-item label="已掌握知识（可选，逗号或换行分隔）">
          <el-input
            v-model="generateForm.knownKnowledge"
            type="textarea"
            :rows="3"
            placeholder="例如：Java 基础，MySQL，HTTP 协议"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showGenerateDialog = false">取消</el-button>
        <el-button type="primary" :loading="pathStore.loading" :disabled="!generateForm.target.trim()" @click="handleGenerate">
          生成路径
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
.path-view {
  min-height: 0;
  --path-btn-bg: #2f6bff;
  --path-btn-bg-hover: #255ae6;
  --path-btn-bg-active: #1f4fcf;
}

.path-body {
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-columns: 320px minmax(0, 1fr);
  gap: 12px;
  padding: 12px;
}

.path-sidebar {
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.sidebar-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px;
  border-bottom: 1px solid var(--border-lighter);

  h3 {
    font-size: 15px;
    font-weight: 700;
    color: var(--text-primary);
  }
}

.new-path-btn {
  border-radius: 10px;
}

.path-list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 10px;
}

.path-card {
  padding: 12px;
  border-radius: 12px;
  border: 1px solid var(--border-lighter);
  background: rgba(255, 255, 255, 0.84);
  margin-bottom: 8px;
  cursor: pointer;
  transition: all 0.2s;

  &:hover {
    border-color: var(--primary-color);
    box-shadow: var(--shadow-sm);
  }

  &.active {
    border-color: var(--primary-color);
    background: #edf3ff;
    box-shadow: inset 0 0 0 1px #d5e1ff;
  }
}

.path-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 8px;
}

.path-target {
  flex: 1;
  min-width: 0;
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.delete-btn {
  opacity: 1;
  border-radius: 8px;
  padding: 0 6px;
  transition: background-color 0.2s ease, filter 0.2s ease;
}

.path-card-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 8px;
}

.path-meta-left {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.node-count {
  font-size: 12px;
  color: var(--text-secondary);
}

.empty-state {
  min-height: 280px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.path-content {
  min-height: 0;
  overflow: hidden;
}

.path-detail {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.detail-header {
  padding: 14px;
  border-bottom: 1px solid var(--border-lighter);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.detail-header-main {
  min-width: 0;

  h2 {
    font-size: 20px;
    font-weight: 700;
    color: var(--text-primary);
    margin-bottom: 8px;
  }
}

.detail-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  color: var(--text-secondary);
  font-size: 12px;
}

.stats-row {
  display: grid;
  grid-template-columns: repeat(3, minmax(120px, 1fr));
  gap: 10px;
  padding: 12px;
}

.stat-card {
  padding: 10px 12px;
  display: flex;
  align-items: center;
  gap: 10px;
}

.stat-icon {
  width: 34px;
  height: 34px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;

  &.total {
    color: #2f6bff;
    background: rgba(47, 107, 255, 0.14);
  }

  &.done {
    color: #2f9b5d;
    background: rgba(47, 155, 93, 0.14);
  }

  &.doing {
    color: #d08b14;
    background: rgba(208, 139, 20, 0.14);
  }
}

.stat-label {
  display: block;
  font-size: 12px;
  color: var(--text-secondary);
}

.stat-value {
  font-size: 20px;
  font-weight: 700;
  color: var(--text-primary);
  line-height: 1.2;
}

.detail-body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding-bottom: 4px;
}

.empty-content {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.mobile-drawer {
  display: none;
}

.drawer-content {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.mobile-only {
  display: none;
}

@media (max-width: 992px) {
  .path-body {
    grid-template-columns: 1fr;
    padding: 10px;
  }

  .path-sidebar {
    display: none;
  }

  .mobile-drawer,
  .mobile-only {
    display: block;
  }

  .mobile-only {
    display: inline-flex;
  }

  .detail-header {
    flex-direction: column;
    align-items: flex-start;
  }
}

@media (max-width: 768px) {
  .stats-row {
    grid-template-columns: 1fr;
  }
}

:deep(.el-button) {
  color: #fff !important;
  background: var(--path-btn-bg) !important;
  border-color: var(--path-btn-bg) !important;
}

:deep(.el-button:hover),
:deep(.el-button:focus-visible) {
  color: #fff !important;
  background: var(--path-btn-bg-hover) !important;
  border-color: var(--path-btn-bg-hover) !important;
}

:deep(.el-button:active) {
  color: #fff !important;
  background: var(--path-btn-bg-active) !important;
  border-color: var(--path-btn-bg-active) !important;
}
</style>
