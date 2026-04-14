<script setup lang="ts">
import { computed } from 'vue'
import type { Message } from '@/types'
import CodeBlock from '@/components/common/CodeBlock.vue'
import FeedbackButton from './FeedbackButton.vue'
import { User, Service } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'

const props = defineProps<{
  message: Message
}>()

const authStore = useAuthStore()
const userAvatar = computed(() => authStore.user?.avatar || '')

interface ParsedBlock {
  type: 'text' | 'code'
  content: string
  language?: string
}

const parsedContent = computed<ParsedBlock[]>(() => {
  const content = props.message.content
  const blocks: ParsedBlock[] = []
  const codeBlockRegex = /```(?:([a-zA-Z0-9_+-]+)[ \t]*\n)?([\s\S]*?)```/g

  let lastIndex = 0
  let match: RegExpExecArray | null

  while ((match = codeBlockRegex.exec(content)) !== null) {
    if (match.index > lastIndex) {
      const text = content.slice(lastIndex, match.index)
      if (text.trim()) blocks.push({ type: 'text', content: text })
    }
    blocks.push({
      type: 'code',
      content: match[2] || '',
      language: match[1] || 'plaintext',
    })
    lastIndex = match.index + match[0].length
  }

  if (lastIndex < content.length) {
    const text = content.slice(lastIndex)
    if (text.trim()) blocks.push({ type: 'text', content: text })
  }

  if (blocks.length === 0 && content.trim()) {
    blocks.push({ type: 'text', content })
  }

  return blocks
})

function formatText(text: string): string {
  return text
    .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
    .replace(/\*(.*?)\*/g, '<em>$1</em>')
    .replace(/`([^`]+)`/g, '<code class="inline-code">$1</code>')
    .replace(/\[([^\]]+)\]\(([^)]+)\)/g, '<a href="$2" target="_blank">$1</a>')
    .replace(/\n/g, '<br>')
}

const parsedSources = computed(() => {
  const rawSources = props.message.sources
  if (!rawSources) return []

  // 后端历史消息常见格式：JSON 字符串，如
  // [{"type":"node","title":"..."}]
  const first = Array.isArray(rawSources) ? rawSources[0] : rawSources
  if (typeof first === 'string' && first.trim()) {
    try {
      const parsed = JSON.parse(first)
      if (Array.isArray(parsed)) {
        return parsed as { type: string; title: string }[]
      }
    } catch {
      // ignore and fallback below
    }
  }

  if (Array.isArray(rawSources)) {
    return rawSources.map(s => ({ type: 'doc', title: s }))
  }

  return [{ type: 'doc', title: rawSources }]
})

const isUser = computed(() => props.message.role === 'user')
</script>

<template>
  <div class="message-bubble" :class="{ 'is-user': isUser, 'is-assistant': !isUser }">
    <div class="message-avatar">
      <el-avatar
        v-if="isUser && userAvatar"
        :size="36"
        :src="userAvatar"
        class="user-avatar"
      />
      <el-avatar v-else :size="36" :class="isUser ? 'user-avatar' : 'assistant-avatar'">
        <el-icon :size="18">
          <User v-if="isUser" />
          <Service v-else />
        </el-icon>
      </el-avatar>
    </div>

    <div class="message-body">
      <div class="message-role">{{ isUser ? '你' : '助手' }}</div>

      <div class="message-card">
        <div class="message-content">
          <template v-for="(block, index) in parsedContent" :key="index">
            <div v-if="block.type === 'text'" class="text-block" v-html="formatText(block.content)" />
            <CodeBlock v-else :code="block.content" :language="block.language" />
          </template>

          <div v-if="!message.content && !isUser" class="typing-indicator">
            <span></span><span></span><span></span>
          </div>
        </div>

        <div v-if="parsedSources.length > 0" class="message-sources">
          <span class="sources-label">参考来源</span>
          <el-tag
            v-for="(source, i) in parsedSources"
            :key="i"
            size="small"
            :type="source.type === 'node' ? 'success' : 'info'"
            class="source-tag"
          >
            {{ source.type === 'node' ? '🔗 ' : '📄 ' }}{{ source.title }}
          </el-tag>
        </div>
      </div>

      <FeedbackButton v-if="!isUser && message.content" :message-id="message.id" />
    </div>
  </div>
</template>

<style lang="scss" scoped>
.message-bubble {
  display: flex;
  gap: 12px;
  padding: 10px 16px;
  transition: background-color 0.2s ease;

  &.is-user {
    flex-direction: row-reverse;
  }

  &.is-assistant {
    flex-direction: row;
  }
}

.message-avatar {
  flex-shrink: 0;
  margin-top: 2px;
}

.user-avatar {
  background: linear-gradient(145deg, #4b8fff, #2f7df6);
}

.assistant-avatar {
  background: linear-gradient(145deg, #7ea7ff, #5a8bff);
}

.message-body {
  flex: 1;
  min-width: 0;
}

.message-bubble.is-user .message-body {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
}

.message-role {
  font-size: 12px;
  font-weight: 700;
  margin-bottom: 6px;
  letter-spacing: 0.2px;
  color: var(--text-secondary);
}

.message-bubble.is-user .message-role {
  color: #3d6fc4;
  text-align: right;
}

.message-card {
  width: 100%;
  max-width: min(82%, 920px);
  border-radius: 14px;
  padding: 12px 14px;
  border: 1px solid var(--border-light);
  box-shadow: var(--shadow-sm);
  background: #fff;
}

.message-bubble.is-user .message-card {
  background: linear-gradient(180deg, #eef5ff 0%, #e7f1ff 100%);
  border-color: #cfe0ff;
  box-shadow: 0 8px 18px rgba(47, 125, 246, 0.12);
}

.message-bubble.is-assistant .message-card {
  background: #ffffff;
  border-color: #e6edf7;
}

.message-content {
  font-size: 14px;
  line-height: 1.8;
  color: var(--text-regular);
  word-break: break-word;

  :deep(.inline-code) {
    background: #eef3ff;
    padding: 2px 7px;
    border-radius: 6px;
    border: 1px solid #dbe7ff;
    font-family: 'Cascadia Code', 'Fira Code', Consolas, monospace;
    font-size: 13px;
    color: #2a57a7;
  }

  :deep(strong) {
    color: var(--text-primary);
    font-weight: 700;
  }

  :deep(a) {
    color: var(--primary-color);
    text-decoration: none;

    &:hover {
      text-decoration: underline;
    }
  }
}

.text-block {
  margin: 6px 0;
}

.message-sources {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px dashed #dce8fb;
}

.sources-label {
  font-size: 12px;
  color: var(--text-secondary);
}

.source-tag {
  cursor: default;
  border-color: #dbe8ff;
  background: #f3f8ff;
  color: #3566b4;
}

.typing-indicator {
  display: flex;
  gap: 4px;
  padding: 3px 0;

  span {
    width: 7px;
    height: 7px;
    border-radius: 50%;
    background-color: #b5c2d6;
    animation: typing 1.4s infinite;

    &:nth-child(2) { animation-delay: 0.2s; }
    &:nth-child(3) { animation-delay: 0.4s; }
  }
}

@keyframes typing {
  0%, 60%, 100% { opacity: 0.35; transform: scale(0.8); }
  30% { opacity: 1; transform: scale(1); }
}
</style>
