<script setup lang="ts">
import { computed } from 'vue'
import type { Conversation } from '@/types'
import { ChatDotRound, Delete } from '@element-plus/icons-vue'
import { ElMessageBox } from 'element-plus'

const props = withDefaults(defineProps<{
  conversations: Conversation[]
  activeId: number | null
  loading?: boolean
}>(), {
  loading: false,
})

const emit = defineEmits<{
  select: [conv: Conversation]
  delete: [convId: number]
}>()

function getDayDiff(dateStr: string) {
  const date = new Date(dateStr)
  const now = new Date()
  const startA = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime()
  const startB = new Date(date.getFullYear(), date.getMonth(), date.getDate()).getTime()
  return Math.floor((startA - startB) / (1000 * 60 * 60 * 24))
}

function formatMetaTime(dateStr: string): string {
  const date = new Date(dateStr)
  const diff = getDayDiff(dateStr)
  if (diff === 0) {
    return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
  }
  return date.toLocaleDateString('zh-CN', { month: 'numeric', day: 'numeric' })
}

function groupLabelByDiff(diff: number): string {
  if (diff === 0) return '今天'
  if (diff === 1) return '昨天'
  if (diff < 7) return '最近 7 天'
  return '更早'
}

async function handleDelete(conv: Conversation, event: Event) {
  event.stopPropagation()
  try {
    await ElMessageBox.confirm('确定删除这条会话吗？', '提示', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
    })
    emit('delete', conv.id)
  } catch {
    // cancelled
  }
}

const groupedConversations = computed(() => {
  const groups = new Map<string, Conversation[]>()

  for (const conv of props.conversations) {
    const dateStr = conv.updatedAt || conv.createdAt
    const diff = getDayDiff(dateStr)
    const label = groupLabelByDiff(diff)
    if (!groups.has(label)) {
      groups.set(label, [])
    }
    groups.get(label)!.push(conv)
  }

  const order = ['今天', '昨天', '最近 7 天', '更早']
  return order
    .filter((label) => groups.has(label))
    .map((label) => ({
      label,
      items: (groups.get(label) || []).sort(
        (a, b) => +new Date(b.updatedAt || b.createdAt) - +new Date(a.updatedAt || a.createdAt),
      ),
    }))
})
</script>

<template>
  <div class="conversation-list">
    <template v-if="loading">
      <div class="skeleton-wrap">
        <el-skeleton v-for="idx in 6" :key="idx" animated>
          <template #template>
            <div class="skeleton-item">
              <el-skeleton-item variant="circle" style="width: 18px; height: 18px" />
              <el-skeleton-item variant="text" style="width: 70%" />
              <el-skeleton-item variant="text" style="width: 52px" />
            </div>
          </template>
        </el-skeleton>
      </div>
    </template>

    <div v-else-if="conversations.length === 0" class="empty-state">
      <el-icon :size="32" color="#9aa8c7"><ChatDotRound /></el-icon>
      <p>还没有会话记录</p>
      <span>点击上方按钮即可新建对话</span>
    </div>

    <template v-else>
      <div v-for="group in groupedConversations" :key="group.label" class="conv-group">
        <div class="group-label">{{ group.label }}</div>
        <div
          v-for="conv in group.items"
          :key="conv.id"
          class="conv-item"
          :class="{ active: conv.id === activeId }"
          @click="emit('select', conv)"
        >
          <el-icon class="conv-icon"><ChatDotRound /></el-icon>
          <div class="conv-main">
            <span class="conv-title">{{ conv.title || '新对话' }}</span>
            <span class="conv-time">{{ formatMetaTime(conv.updatedAt || conv.createdAt) }}</span>
          </div>
          <el-button
            :icon="Delete"
            text
            size="small"
            class="delete-btn"
            @click="handleDelete(conv, $event)"
          />
        </div>
      </div>
    </template>
  </div>
</template>

<style lang="scss" scoped>
.conversation-list {
  padding: 6px;
}

.skeleton-wrap {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 8px 6px;
}

.skeleton-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 48px 14px;
  color: var(--text-placeholder);
  text-align: center;

  p {
    margin-top: 10px;
    font-size: 14px;
    color: var(--text-secondary);
  }

  span {
    margin-top: 4px;
    font-size: 12px;
  }
}

.conv-group {
  margin-bottom: 8px;
}

.group-label {
  padding: 7px 10px 4px;
  font-size: 11px;
  color: var(--text-secondary);
  font-weight: 700;
  letter-spacing: 0.02em;
}

.conv-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border-radius: 10px;
  cursor: pointer;
  transition: background-color 0.2s, box-shadow 0.2s;
  position: relative;

  &:hover {
    background-color: var(--bg-secondary);

    .delete-btn {
      opacity: 1;
    }
  }

  &.active {
    background-color: #eaf0ff;
    box-shadow: inset 0 0 0 1px #d6e2ff;

    .conv-title {
      color: var(--primary-color);
      font-weight: 700;
    }

    .conv-icon {
      color: var(--primary-color);
    }
  }
}

.conv-main {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
}

.conv-icon {
  flex-shrink: 0;
  color: var(--text-secondary);
  font-size: 15px;
}

.conv-title {
  flex: 1;
  font-size: 12px;
  color: var(--text-regular);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.conv-time {
  flex-shrink: 0;
  font-size: 11px;
  color: var(--text-placeholder);
}

.delete-btn {
  opacity: 0;
  flex-shrink: 0;
  color: var(--text-secondary);
  transition: opacity 0.2s;

  &:hover {
    color: var(--danger-color);
  }
}
</style>
