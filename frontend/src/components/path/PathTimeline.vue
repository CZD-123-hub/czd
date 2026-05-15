<script setup lang="ts">
import type { LearningNodeInfo } from '@/types'
import { CircleCheck, Loading, MoreFilled, RemoveFilled } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'

type NodeStatus = LearningNodeInfo['status']

const props = defineProps<{
  nodes: LearningNodeInfo[]
}>()

const emit = defineEmits<{
  statusChange: [nodeId: number, status: NodeStatus]
}>()

const router = useRouter()

const statusMap: Record<NodeStatus, { label: string; type: 'success' | 'warning' | 'info' | 'danger'; color: string }> = {
  todo: { label: '待学习', type: 'info', color: '#6b7b9d' },
  doing: { label: '学习中', type: 'warning', color: '#d08b14' },
  done: { label: '已完成', type: 'success', color: '#2f9b5d' },
  skipped: { label: '已跳过', type: 'danger', color: '#d1444e' },
}

const statusOptions: Array<{ value: NodeStatus; label: string }> = [
  { value: 'todo', label: '待学习' },
  { value: 'doing', label: '学习中' },
  { value: 'done', label: '已完成' },
  { value: 'skipped', label: '已跳过' },
]

function getStatusIcon(status: NodeStatus) {
  switch (status) {
    case 'done':
      return CircleCheck
    case 'doing':
      return Loading
    case 'skipped':
      return RemoveFilled
    default:
      return MoreFilled
  }
}

function handleStatusChange(nodeId: number, status: NodeStatus) {
  emit('statusChange', nodeId, status)
}

function handleStatusCommand(nodeId: number, command: string | number | boolean) {
  handleStatusChange(nodeId, String(command) as NodeStatus)
}

function openDocument(docId: number) {
  void router.push({ name: 'documents', query: { docId: String(docId) } })
}

function openVideoInStudio(videoId: number) {
  void router.push({ name: 'learning-studio', query: { videoId: String(videoId), from: 'path' } })
}
</script>

<template>
  <div class="path-timeline">
    <div v-if="nodes.length === 0" class="empty-state">
      <el-empty description="暂无学习节点" />
    </div>

    <div v-else class="timeline-container">
      <div
        v-for="(node, index) in nodes"
        :key="node.id"
        class="timeline-item"
        :class="[`status-${node.status}`]"
      >
        <div class="timeline-line">
          <div class="line-segment top" :class="{ invisible: index === 0 }" />
          <div class="line-dot" :style="{ borderColor: statusMap[node.status].color }">
            <el-icon :size="16" :color="statusMap[node.status].color">
              <component :is="getStatusIcon(node.status)" />
            </el-icon>
          </div>
          <div class="line-segment bottom" :class="{ invisible: index === nodes.length - 1 }" />
        </div>

        <div class="timeline-content soft-panel">
          <div class="node-header">
            <div class="node-title-wrap">
              <span class="node-order">阶段 {{ node.nodeOrder }}</span>
              <h4 class="node-name">{{ node.knowledgeName }}</h4>
            </div>

            <div class="node-header-right">
              <el-tag :type="statusMap[node.status].type" size="small">
                {{ statusMap[node.status].label }}
              </el-tag>
              <el-dropdown trigger="click" @command="handleStatusCommand(node.id, $event)">
                <el-button size="small" type="primary" class="status-toggle-btn">
                  切换状态
                  <el-icon class="el-icon--right"><MoreFilled /></el-icon>
                </el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item
                      v-for="option in statusOptions"
                      :key="option.value"
                      :command="option.value"
                      :disabled="option.value === node.status"
                    >
                      {{ option.label }}
                    </el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </div>

          <div class="node-meta">
            <span v-if="node.status === 'done'" class="milestone">里程碑已达成</span>
            <span v-else-if="node.status === 'doing'" class="focus">建议本周重点推进</span>
            <span v-else-if="node.status === 'skipped'" class="skip">该节点已暂时跳过</span>
            <span v-else class="pending">建议安排学习时间与目标产出</span>
          </div>

          <div v-if="node.resourceUrls && node.resourceUrls.length > 0" class="node-resources">
            <span class="resources-label">学习资源</span>
            <a
              v-for="(url, i) in node.resourceUrls"
              :key="i"
              :href="url"
              target="_blank"
              class="resource-link"
            >
              资源 {{ i + 1 }}
            </a>
          </div>

          <div v-if="node.recommendedDocuments && node.recommendedDocuments.length > 0" class="node-recommend">
            <span class="resources-label">推荐文档</span>
            <el-button
              v-for="doc in node.recommendedDocuments"
              :key="`doc-${node.id}-${doc.id}`"
              size="small"
              text
              type="primary"
              @click="openDocument(doc.id)"
            >
              {{ doc.title }}
            </el-button>
          </div>

          <div v-if="node.recommendedVideos && node.recommendedVideos.length > 0" class="node-recommend">
            <span class="resources-label">推荐视频</span>
            <el-button
              v-for="video in node.recommendedVideos"
              :key="`video-${node.id}-${video.id}`"
              size="small"
              text
              type="success"
              @click="openVideoInStudio(video.id)"
            >
              {{ video.title }}
            </el-button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.path-timeline {
  padding: 14px;
  --path-btn-bg: #2f6bff;
  --path-btn-bg-hover: #255ae6;
  --path-btn-bg-active: #1f4fcf;
}

.timeline-container {
  position: relative;
}

.timeline-item {
  display: flex;
  gap: 14px;
  min-height: 108px;
}

.timeline-line {
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 30px;
  flex-shrink: 0;
}

.line-segment {
  width: 2px;
  flex: 1;
  background: linear-gradient(180deg, rgba(47, 107, 255, 0.22), rgba(47, 107, 255, 0.08));

  &.invisible {
    visibility: hidden;
  }
}

.line-dot {
  width: 30px;
  height: 30px;
  border-radius: 50%;
  border: 2px solid;
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 6px 14px rgba(47, 107, 255, 0.16);
  flex-shrink: 0;
}

.timeline-content {
  flex: 1;
  margin-bottom: 12px;
  padding: 12px 14px;
  border-radius: 14px;
}

.node-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.node-title-wrap {
  min-width: 0;
}

.node-order {
  display: inline-block;
  font-size: 11px;
  font-weight: 700;
  color: var(--text-secondary);
  margin-bottom: 4px;
  letter-spacing: 0.03em;
}

.node-name {
  font-size: 16px;
  font-weight: 700;
  color: var(--text-primary);
}

.node-header-right {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.node-meta {
  margin-top: 8px;
  font-size: 12px;
  color: var(--text-secondary);

  .milestone {
    color: #2f9b5d;
    font-weight: 600;
  }

  .focus {
    color: #d08b14;
    font-weight: 600;
  }

  .skip {
    color: #d1444e;
    font-weight: 600;
  }
}

.node-resources {
  margin-top: 10px;
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.node-recommend {
  margin-top: 8px;
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.resources-label {
  font-size: 12px;
  color: var(--text-secondary);
  font-weight: 600;
}

.resource-link {
  font-size: 12px;
  color: var(--primary-color);
  border: 1px solid #d8e4ff;
  background: #f5f9ff;
  border-radius: 999px;
  padding: 4px 10px;

  &:hover {
    text-decoration: none;
    border-color: var(--primary-color);
    background: #edf3ff;
  }
}

.status-done .timeline-content {
  border-color: rgba(47, 155, 93, 0.25);
}

.status-doing .timeline-content {
  border-color: rgba(208, 139, 20, 0.25);
}

.status-skipped .timeline-content {
  opacity: 0.82;
}

@media (max-width: 768px) {
  .path-timeline {
    padding: 10px;
  }

  .timeline-item {
    gap: 10px;
  }

  .timeline-content {
    padding: 10px;
  }

  .node-name {
    font-size: 14px;
  }

  .node-header {
    flex-direction: column;
  }

  .node-header-right {
    width: 100%;
    justify-content: space-between;
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
