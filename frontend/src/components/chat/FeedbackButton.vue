<script setup lang="ts">
import { ref } from 'vue'
import { submitFeedback } from '@/api/chat'

const props = defineProps<{
  messageId: number
}>()

const feedbackGiven = ref<'up' | 'down' | null>(null)
const loading = ref(false)

async function handleFeedback(type: 'up' | 'down') {
  if (loading.value) return
  
  loading.value = true
  try {
    let ratingValue: 'useful' | 'useless' | null = null
    let newState: 'up' | 'down' | null = null
    
    // 判断当前点击的按钮状态
    if (feedbackGiven.value === type) {
      // 如果点击的是已经选中的按钮，则取消点赞
      ratingValue = null
      newState = null
    } else {
      // 如果是新选中的按钮，则发送对应的 rating
      ratingValue = type === 'up' ? 'useful' : 'useless'
      newState = type
    }
    
    // 发送请求到后端
    if (ratingValue) {
      await submitFeedback(props.messageId, ratingValue)
    } else {
      // 取消点赞：发送 'none'
      await submitFeedback(props.messageId, 'none' as any)
    }
    
    // 更新前端状态
    feedbackGiven.value = newState
  } catch (error) {
    console.error('反馈提交失败:', error)
    // 即使后端失败，也更新前端状态（用户体验更好）
    if (feedbackGiven.value === type) {
      feedbackGiven.value = null
    } else {
      feedbackGiven.value = type
    }
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="feedback-buttons">
    <el-button
      text
      size="small"
      :class="{ active: feedbackGiven === 'up' }"
      :disabled="loading"
      @click="handleFeedback('up')"
    >
      <el-icon>
        <svg viewBox="0 0 24 24" width="14" height="14" fill="currentColor">
          <path d="M2 20h2V8H2v12zm22-11c0-1.1-.9-2-2-2h-6.31l.95-4.57.03-.32c0-.41-.17-.79-.44-1.06L14.17 0 7.59 6.59C7.22 6.95 7 7.45 7 8v10c0 1.1.9 2 2 2h9c.83 0 1.54-.5 1.84-1.22l3.02-7.05c.09-.23.14-.47.14-.73v-2z"/>
        </svg>
      </el-icon>
    </el-button>
    <el-button
      text
      size="small"
      :class="{ active: feedbackGiven === 'down' }"
      :disabled="loading"
      @click="handleFeedback('down')"
    >
      <el-icon>
        <svg viewBox="0 0 24 24" width="14" height="14" fill="currentColor">
          <path d="M22 4h-2v12h2V4zm-4 12V4c0-1.1-.9-2-2-2H7c-.83 0-1.54.5-1.84 1.22l-3.02 7.05c-.09.23-.14.47-.14.73v2c0 1.1.9 2 2 2h6.31l-.95 4.57-.03.32c0 .41.17.79.44 1.06L9.83 24l6.59-6.59c.36-.36.58-.86.58-1.41z"/>
        </svg>
      </el-icon>
    </el-button>
  </div>
</template>

<style lang="scss" scoped>
.feedback-buttons {
  display: flex;
  gap: 4px;
  margin-top: 6px;

  .el-button {
    color: var(--text-placeholder);
    padding: 2px 6px;

    &:hover:not(:disabled) {
      color: var(--text-secondary);
    }

    &.active {
      color: var(--primary-color);
    }
  }
}
</style>