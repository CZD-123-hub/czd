<script setup lang="ts">
import { computed } from 'vue'
import { Box, CircleCheckFilled, Loading, WarningFilled } from '@element-plus/icons-vue'

type FeedbackType = 'loading' | 'empty' | 'error' | 'success'
type FeedbackSize = 'default' | 'compact'

const props = withDefaults(
  defineProps<{
    type: FeedbackType
    title?: string
    description?: string
    size?: FeedbackSize
    centered?: boolean
  }>(),
  {
    title: '',
    description: '',
    size: 'default',
    centered: true,
  },
)

const iconMap = {
  loading: Loading,
  empty: Box,
  error: WarningFilled,
  success: CircleCheckFilled,
} as const

const titleMap: Record<FeedbackType, string> = {
  loading: '正在加载',
  empty: '暂无数据',
  error: '加载失败',
  success: '操作成功',
}

const descriptionMap: Record<FeedbackType, string> = {
  loading: '数据正在准备中，请稍候。',
  empty: '当前条件下没有可展示内容。',
  error: '出现了一些问题，请重试。',
  success: '结果已更新。',
}

const resolvedTitle = computed(() => props.title || titleMap[props.type])
const resolvedDescription = computed(() => props.description || descriptionMap[props.type])
const iconComponent = computed(() => iconMap[props.type])
</script>

<template>
  <section
    class="feedback-state"
    :class="[`type-${type}`, `size-${size}`, { centered }]"
    role="status"
    aria-live="polite"
  >
    <div class="state-icon-wrap">
      <el-icon class="state-icon" :class="{ spinning: type === 'loading' }">
        <component :is="iconComponent" />
      </el-icon>
    </div>
    <div class="state-content">
      <h4 class="state-title">{{ resolvedTitle }}</h4>
      <p v-if="resolvedDescription" class="state-description">{{ resolvedDescription }}</p>
      <div v-if="$slots.actions" class="state-actions">
        <slot name="actions" />
      </div>
    </div>
  </section>
</template>

<style lang="scss" scoped>
.feedback-state {
  --state-bg: rgba(255, 255, 255, 0.88);
  --state-border: var(--border-light);
  --state-icon: var(--primary-color);
  --state-title: var(--text-primary);
  --state-text: var(--text-secondary);

  display: flex;
  align-items: center;
  gap: 14px;
  padding: 16px;
  border: 1px solid var(--state-border);
  border-radius: 14px;
  background: var(--state-bg);
  box-shadow: var(--shadow-sm);

  &.centered {
    justify-content: center;
    text-align: center;
  }

  &.size-compact {
    padding: 10px 12px;
    gap: 10px;

    .state-icon-wrap {
      width: 30px;
      height: 30px;
    }

    .state-title {
      font-size: 13px;
    }

    .state-description {
      font-size: 12px;
    }
  }

  &.type-loading {
    --state-icon: #3f6fce;
    --state-bg: rgba(237, 244, 255, 0.84);
    --state-border: #d8e5ff;
  }

  &.type-empty {
    --state-icon: #5c7aa3;
  }

  &.type-error {
    --state-icon: #d1444e;
    --state-bg: rgba(255, 246, 246, 0.92);
    --state-border: #ffd9de;
  }

  &.type-success {
    --state-icon: #2f9b5d;
    --state-bg: rgba(243, 253, 246, 0.9);
    --state-border: #d8f0df;
  }
}

.state-icon-wrap {
  flex-shrink: 0;
  width: 36px;
  height: 36px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.72);
  border: 1px solid rgba(255, 255, 255, 0.9);
}

.state-icon {
  color: var(--state-icon);
  font-size: 20px;

  &.spinning {
    animation: state-spin 1s linear infinite;
  }
}

.state-content {
  min-width: 0;
}

.state-title {
  margin: 0;
  color: var(--state-title);
  font-size: 14px;
  font-weight: 700;
}

.state-description {
  margin: 4px 0 0;
  color: var(--state-text);
  font-size: 13px;
  line-height: 1.55;
}

.state-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 8px;
  justify-content: center;
  flex-wrap: wrap;
}

@keyframes state-spin {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}
</style>
