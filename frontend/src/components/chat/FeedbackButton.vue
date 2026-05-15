<script setup lang="ts">
import { ref, watch } from 'vue'
import { submitFeedback } from '@/api/chat'

const props = defineProps<{
  messageId: number
  initialRating?: 'useful' | 'useless' | null
}>()

const emit = defineEmits<{
  (e: 'updated', rating: 'useful' | 'useless' | null): void
}>()

function toUiState(rating: 'useful' | 'useless' | null | undefined): 'up' | 'down' | null {
  if (rating === 'useful') return 'up'
  if (rating === 'useless') return 'down'
  return null
}

const feedbackGiven = ref<'up' | 'down' | null>(toUiState(props.initialRating))
const loading = ref(false)

watch(
  () => props.initialRating,
  (rating) => {
    feedbackGiven.value = toUiState(rating)
  },
)

async function handleFeedback(type: 'up' | 'down') {
  if (loading.value || props.messageId <= 0) return

  const previousState = feedbackGiven.value
  const nextState = previousState === type ? null : type
  const ratingValue: 'useful' | 'useless' | null =
    nextState === 'up' ? 'useful' : nextState === 'down' ? 'useless' : null

  loading.value = true
  try {
    await submitFeedback(props.messageId, ratingValue)
    feedbackGiven.value = nextState
    emit('updated', ratingValue)
  } catch (error) {
    const errorMessage = error instanceof Error ? error.message : ''
    // Compatibility: old backend may still return duplicate-submit error.
    if (errorMessage.toLowerCase().includes('already submitted')) {
      const fallbackRating: 'useful' | 'useless' = type === 'up' ? 'useful' : 'useless'
      feedbackGiven.value = type
      emit('updated', fallbackRating)
      return
    }
    feedbackGiven.value = previousState
    console.error('Feedback submit failed:', error)
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

