<script setup lang="ts">
import { computed } from 'vue'
import type { Message } from '@/types'
import CodeBlock from '@/components/common/CodeBlock.vue'
import FeedbackButton from './FeedbackButton.vue'
import { User, Service } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { relationDisplayLabel } from '@/utils/graphRelation'

const props = defineProps<{
  message: Message
}>()

const emit = defineEmits<{
  'source-click': [source: SourceItem]
  'save-code': [payload: { code: string; language: string }]
  'save-document': [source: SourceItem]
}>()

const authStore = useAuthStore()
const userAvatar = computed(() => authStore.user?.avatar || '')

interface ParsedBlock {
  type: 'text' | 'code'
  content: string
  language?: string
}

interface SourceItem {
  id?: string
  type: string
  title: string
  excerpt?: string
  relationType?: string
  url?: string
}

interface SourceSection {
  key: 'document' | 'node' | 'relation' | 'external'
  title: string
  items: SourceItem[]
}

const parsedContent = computed<ParsedBlock[]>(() => {
  const content = props.message.content || ''
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

function escapeHtml(text: string): string {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

function sanitizeUrl(raw: string): string {
  const url = raw.trim()
  if (/^(https?:\/\/|mailto:)/i.test(url)) {
    return url.replace(/"/g, '%22')
  }
  return ''
}

function formatText(text: string): string {
  const escaped = escapeHtml(text)
  return escaped
    .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
    .replace(/\*(.*?)\*/g, '<em>$1</em>')
    .replace(/`([^`]+)`/g, '<code class="inline-code">$1</code>')
    .replace(/\[([^\]]+)\]\(([^)]+)\)/g, (_all, label: string, url: string) => {
      const safeUrl = sanitizeUrl(url)
      if (!safeUrl) return label
      return `<a href="${safeUrl}" target="_blank" rel="noopener noreferrer">${label}</a>`
    })
    .replace(/\n/g, '<br>')
}

const parsedSources = computed<SourceItem[]>(() => {
  const rawSources = props.message.sources
  if (!rawSources) return []

  if (typeof rawSources === 'string') {
    const text = rawSources.trim()
    if (!text) return []
    try {
      const parsed = JSON.parse(text)
      if (Array.isArray(parsed)) {
        return parsed.map((item) => ({
          id: item?.id ? String(item.id) : '',
          type: String(item?.type || 'document'),
          title: String(item?.title || ''),
          excerpt: item?.excerpt ? String(item.excerpt) : '',
          relationType: item?.relationType ? String(item.relationType) : '',
          url: item?.url ? String(item.url) : '',
        }))
      }
    } catch {
      return [{ id: text, type: 'document', title: text }]
    }
  }

  if (Array.isArray(rawSources)) {
    return rawSources.map((s) => ({ id: String(s), type: 'document', title: String(s) }))
  }

  return [{ id: String(rawSources), type: 'document', title: String(rawSources) }]
})

function extractExternalSources(content: string, fromSources: SourceItem[]): SourceItem[] {
  const text = String(content || '')
  if (!text.trim()) return []

  const existingUrlSet = new Set<string>()
  for (const source of fromSources) {
    const raw = String(source.url || source.id || '').trim()
    const safe = sanitizeUrl(raw)
    if (safe) existingUrlSet.add(safe)
  }

  const externalMap = new Map<string, SourceItem>()
  const markdownLinkRegex = /\[([^\]]+)\]\((https?:\/\/[^\s)]+)\)/g
  let markdownMatch: RegExpExecArray | null

  while ((markdownMatch = markdownLinkRegex.exec(text)) !== null) {
    const label = String(markdownMatch[1] || '').trim() || '外部文档'
    const rawUrl = String(markdownMatch[2] || '').trim()
    const safeUrl = sanitizeUrl(rawUrl)
    if (!safeUrl || existingUrlSet.has(safeUrl) || externalMap.has(safeUrl)) continue
    externalMap.set(safeUrl, {
      id: safeUrl,
      type: 'external',
      title: label,
      excerpt: safeUrl,
      url: safeUrl,
    })
  }

  const plainUrlRegex = /(https?:\/\/[^\s<>"'）)]+)/g
  let plainMatch: RegExpExecArray | null
  while ((plainMatch = plainUrlRegex.exec(text)) !== null) {
    const rawUrl = String(plainMatch[1] || '').trim()
    const safeUrl = sanitizeUrl(rawUrl)
    if (!safeUrl || existingUrlSet.has(safeUrl) || externalMap.has(safeUrl)) continue
    externalMap.set(safeUrl, {
      id: safeUrl,
      type: 'external',
      title: safeUrl,
      excerpt: safeUrl,
      url: safeUrl,
    })
  }

  return Array.from(externalMap.values())
}

const allSources = computed<SourceItem[]>(() => {
  const base = parsedSources.value
  const external = extractExternalSources(props.message.content || '', base)
  return [...base, ...external]
})

function normalizeSourceType(type: string): 'document' | 'node' | 'relation' | 'external' {
  const normalized = String(type || '').trim().toLowerCase()
  if (['external', 'web', 'web_document', 'url', 'reference'].includes(normalized)) return 'external'
  if (['node', 'graph_node', 'knowledge_node', 'entity'].includes(normalized)) return 'node'
  if (['relation', 'edge', 'relationship', 'graph_relation'].includes(normalized)) return 'relation'
  if (['document', 'doc', 'chunk', 'knowledge_document'].includes(normalized)) return 'document'
  return 'document'
}

function sourceTagType(type: string): 'success' | 'warning' | 'info' {
  const normalized = normalizeSourceType(type)
  if (normalized === 'external') return 'info'
  if (normalized === 'node') return 'success'
  if (normalized === 'relation') return 'warning'
  return 'info'
}

function sourceLabel(type: string): string {
  const normalized = normalizeSourceType(type)
  if (normalized === 'external') return '外部资料'
  if (normalized === 'node') return '节点依据'
  if (normalized === 'relation') return '关系依据'
  return '文档依据'
}

function extractRelationTypeFromTitle(title: string): string {
  const match = title.match(/--([A-Z_]+)-->/)
  return match?.[1] || ''
}

function sourceTitle(source: SourceItem): string {
  if (normalizeSourceType(source.type) !== 'relation') {
    return source.title
  }

  const relationType = source.relationType || extractRelationTypeFromTitle(source.title)
  if (!relationType) return source.title
  const relationLabel = relationDisplayLabel(relationType)
  return source.title.replace(`--${relationType}-->`, `--${relationLabel}-->`)
}

const sourceSections = computed<SourceSection[]>(() => {
  const seed: SourceSection[] = [
    { key: 'document', title: '文档依据', items: [] },
    { key: 'node', title: '节点依据', items: [] },
    { key: 'relation', title: '关系依据', items: [] },
    { key: 'external', title: '外部资料', items: [] },
  ]

  for (const source of allSources.value) {
    const key = normalizeSourceType(source.type)
    const section = seed.find((item) => item.key === key)
    if (section) {
      section.items.push(source)
    }
  }

  return seed.filter((section) => section.items.length > 0)
})

const isUser = computed(() => props.message.role === 'user')
const messageTime = computed(() => {
  const raw = props.message.createdAt
  if (!raw) return ''
  const date = new Date(raw)
  if (Number.isNaN(date.getTime())) return ''
  return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
})

const feedbackAvailable = computed(() => {
  return (
    !isUser.value &&
    Boolean(props.message.content) &&
    !props.message.pending &&
    !props.message.clientTempId &&
    Number(props.message.id) > 0
  )
})

function handleFeedbackUpdated(rating: 'useful' | 'useless' | null) {
  props.message.feedbackRating = rating
}

function handleSourceClick(source: SourceItem) {
  emit('source-click', source)
}

function canSaveDocument(source: SourceItem): boolean {
  const normalized = normalizeSourceType(source.type)
  if (normalized !== 'external') return false
  const safeUrl = sanitizeUrl(String(source.url || source.id || ''))
  return Boolean(safeUrl)
}

function handleSaveDocument(source: SourceItem) {
  if (!canSaveDocument(source)) return
  emit('save-document', source)
}

function handleSourceKeydown(event: KeyboardEvent, source: SourceItem) {
  if (event.key !== 'Enter' && event.key !== ' ') return
  event.preventDefault()
  handleSourceClick(source)
}

function handleSaveCode(block: ParsedBlock) {
  if (block.type !== 'code') return
  emit('save-code', {
    code: block.content,
    language: block.language || 'plaintext',
  })
}
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
      <div class="message-meta">
        <span class="message-role">{{ isUser ? '用户' : '助手' }}</span>
        <span v-if="messageTime" class="message-time">{{ messageTime }}</span>
        <span v-if="message.pending && !isUser" class="message-status">生成中</span>
      </div>

      <div class="message-card">
        <div class="message-content">
          <template v-for="(block, index) in parsedContent" :key="index">
            <div v-if="block.type === 'text'" class="text-block" v-html="formatText(block.content)" />
            <CodeBlock
              v-else
              :code="block.content"
              :language="block.language"
              :savable="!isUser && !message.pending"
              @save="handleSaveCode(block)"
            />
          </template>

          <div v-if="!message.content && !isUser" class="typing-indicator">
            <span></span><span></span><span></span>
          </div>
        </div>

        <div v-if="sourceSections.length > 0" class="message-sources">
          <div
            v-for="section in sourceSections"
            :key="section.key"
            class="source-section"
          >
            <div class="source-section-title">{{ section.title }} · {{ section.items.length }}</div>
            <div
              v-for="(source, i) in section.items"
              :key="`${section.key}-${i}`"
              class="source-item is-clickable"
              role="button"
              tabindex="0"
              @click="handleSourceClick(source)"
              @keydown="(event) => handleSourceKeydown(event, source)"
            >
              <el-tag
                size="small"
                :type="sourceTagType(source.type)"
                class="source-tag"
              >
                {{ sourceLabel(source.type) }} · {{ sourceTitle(source) }}
              </el-tag>
              <div v-if="canSaveDocument(source)" class="source-actions">
                <el-button
                  class="save-doc-btn"
                  size="small"
                  @click.stop="handleSaveDocument(source)"
                >
                  一键保存文档
                </el-button>
              </div>
              <p v-if="source.excerpt" class="source-excerpt">{{ source.excerpt }}</p>
            </div>
          </div>
        </div>
      </div>

      <FeedbackButton
        v-if="feedbackAvailable"
        :message-id="message.id"
        :initial-rating="message.feedbackRating ?? null"
        @updated="handleFeedbackUpdated"
      />
    </div>
  </div>
</template>

<style lang="scss" scoped>
.message-bubble {
  display: flex;
  gap: 12px;
  padding: 10px 14px;
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
  font-size: 11px;
  font-weight: 700;
  margin-bottom: 0;
  letter-spacing: 0.2px;
  color: var(--text-secondary);
}

.message-bubble.is-user .message-role {
  color: #3d6fc4;
}

.message-meta {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}

.message-bubble.is-user .message-meta {
  justify-content: flex-end;
}

.message-time {
  font-size: 11px;
  color: var(--text-placeholder);
}

.message-status {
  font-size: 11px;
  color: #4a7de0;
  background: #edf3ff;
  border: 1px solid #d8e5ff;
  border-radius: 999px;
  padding: 1px 7px;
  line-height: 1.4;
}

.message-card {
  width: 100%;
  max-width: min(88%, 1120px);
  border-radius: 15px;
  padding: 13px 15px;
  border: 1px solid var(--border-light);
  box-shadow: var(--shadow-sm);
  background: #fff;
  transition: box-shadow 0.2s ease, border-color 0.2s ease;
}

.message-bubble.is-user .message-card {
  max-width: min(82%, 980px);
}

.message-bubble.is-user .message-card {
  background: linear-gradient(180deg, #eef5ff 0%, #e7f1ff 100%);
  border-color: #cfe0ff;
  box-shadow: 0 8px 18px rgba(47, 125, 246, 0.12);
}

.message-bubble.is-assistant .message-card {
  background: #ffffff;
  border-color: #e6edf7;

  &:hover {
    border-color: #d6e1f5;
    box-shadow: 0 10px 22px rgba(22, 47, 86, 0.1);
  }
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
  margin: 8px 0;
}

.message-sources {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px dashed #dce8fb;
}

.source-section {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.source-section-title {
  font-size: 12px;
  font-weight: 700;
  color: var(--text-secondary);
}

.source-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.source-item.is-clickable {
  cursor: pointer;
  border-radius: 10px;
  padding: 4px 6px;
  transition: background-color 0.18s ease, transform 0.18s ease;

  &:hover {
    background: rgba(237, 244, 255, 0.78);
  }

  &:focus-visible {
    outline: 2px solid rgba(47, 107, 255, 0.35);
    outline-offset: 1px;
  }
}

.source-tag {
  cursor: inherit;
  width: fit-content;
}

.source-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.source-actions :deep(.save-doc-btn.el-button) {
  color: #fff;
  background-color: #3b82f6;
  border-color: #3b82f6;
}

.source-actions :deep(.save-doc-btn.el-button:hover),
.source-actions :deep(.save-doc-btn.el-button:focus-visible) {
  color: #fff;
  background-color: #2f74e6;
  border-color: #2f74e6;
}

.source-excerpt {
  margin: 0;
  font-size: 12px;
  line-height: 1.5;
  color: var(--text-secondary);
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
