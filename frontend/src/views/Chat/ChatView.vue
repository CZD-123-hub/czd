<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Check, Close, CloseBold, CopyDocument, Delete, Plus, Promotion, Refresh } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import ConversationList from '@/components/chat/ConversationList.vue'
import MessageBubble from '@/components/chat/MessageBubble.vue'
import { useChatStore } from '@/stores/chat'
import { getRetrievalMetrics, resetRetrievalMetrics } from '@/api/chat'
import { createSnippet } from '@/api/snippet'
import { recommendLearningVideos } from '@/api/learningVideo'
import { documentApi } from '@/api/document'
import type { CodeSnippet, Conversation, LearningVideo, RetrievalMetrics } from '@/types'

const chatStore = useChatStore()
const router = useRouter()
const messageInput = ref('')
const messageContainer = ref<HTMLDivElement | null>(null)
const messageInputRef = ref<any>(null)

const metrics = ref<RetrievalMetrics | null>(null)
const metricsLoading = ref(false)
const resettingMetrics = ref(false)
const activeTopPanel = ref<'metrics' | 'snippets' | 'videos' | null>(null)
const recommendedVideos = ref<LearningVideo[]>([])
const recommendedVideosLoading = ref(false)
const recommendedVideosError = ref('')
type SnippetInsertMode = 'append' | 'replace' | 'cursor'
const snippetInsertMode = ref<SnippetInsertMode>('append')
const saveSnippetDialogVisible = ref(false)
const savingSnippet = ref(false)
const saveSnippetForm = reactive({
  title: '',
  language: 'plaintext',
  description: '',
  tagsText: '',
  code: '',
})

const insertModeOptions: Array<{ label: string; value: SnippetInsertMode }> = [
  { label: '追加到末尾', value: 'append' },
  { label: '替换输入框', value: 'replace' },
  { label: '插入到光标处', value: 'cursor' },
]

const insertModeLabelMap: Record<SnippetInsertMode, string> = {
  append: '追加到末尾',
  replace: '替换输入框',
  cursor: '插入到光标处',
}

const hasActiveConversation = computed(() => Number(chatStore.currentConversation?.id || 0) > 0)
const pathTotal = computed(() => {
  const b = metrics.value?.semanticPathBreakdown
  if (!b) return 0
  return (b.chunk || 0) + (b.docVectorFallback || 0) + (b.localTfidfFallback || 0)
})

interface SourceClickItem {
  id?: string
  type: string
  title: string
  relationType?: string
  url?: string
}

interface SaveCodePayload {
  code: string
  language: string
}

function normalizeSourceType(rawType?: string): 'document' | 'node' | 'relation' | 'external' | 'unknown' {
  const normalized = String(rawType || '').trim().toLowerCase()
  if (!normalized) return 'document'
  if (['external', 'web', 'web_document', 'url', 'reference'].includes(normalized)) return 'external'
  if (['document', 'doc', 'chunk', 'knowledge_document'].includes(normalized)) return 'document'
  if (['node', 'graph_node', 'knowledge_node', 'entity'].includes(normalized)) return 'node'
  if (['relation', 'edge', 'relationship', 'graph_relation'].includes(normalized)) return 'relation'
  return 'unknown'
}

function sanitizeExternalUrl(raw: string): string {
  const url = String(raw || '').trim()
  if (/^(https?:\/\/|mailto:)/i.test(url)) {
    return url
  }
  return ''
}

function toPositiveInt(raw?: string): number | null {
  if (!raw) return null
  const n = Number(raw)
  if (!Number.isFinite(n) || n <= 0) return null
  return Math.trunc(n)
}

function parseRelationSourceId(sourceId?: string) {
  if (!sourceId) return { node: '', relation: '', target: '' }
  const parts = sourceId.split('->')
  if (parts.length < 3) return { node: '', relation: '', target: '' }
  return {
    node: parts[0] || '',
    relation: parts[1] || '',
    target: parts.slice(2).join('->') || '',
  }
}

function handleSourceClick(source: SourceClickItem) {
  const normalizedType = normalizeSourceType(source.type)
  if (normalizedType === 'external') {
    const targetUrl = sanitizeExternalUrl(String(source.url || source.id || ''))
    if (!targetUrl) {
      ElMessage.warning('外部资料缺少有效链接，暂时无法打开。')
      return
    }
    window.open(targetUrl, '_blank', 'noopener,noreferrer')
    return
  }

  if (normalizedType === 'document') {
    const docId = toPositiveInt(source.id)
    void router.push({
      name: 'documents',
      query: docId ? { docId: String(docId) } : { keyword: source.title || '' },
    })
    return
  }

  if (normalizedType === 'node') {
    const nodeId = (source.id || '').trim()
    if (!nodeId) {
      ElMessage.warning('节点依据缺少节点 ID，暂时无法跳转。')
      return
    }
    void router.push({
      name: 'graph',
      query: { node: nodeId, focus: 'node' },
    })
    return
  }

  if (normalizedType === 'relation') {
    const parsed = parseRelationSourceId(source.id)
    const relationType = (source.relationType || parsed.relation || '').trim()
    const nodeId = (parsed.node || '').trim()
    const targetId = (parsed.target || '').trim()

    if (!nodeId && !targetId && !relationType) {
      ElMessage.warning('关系依据缺少定位信息，暂时无法跳转。')
      return
    }

    void router.push({
      name: 'graph',
      query: {
        ...(nodeId ? { node: nodeId } : {}),
        ...(targetId ? { target: targetId } : {}),
        ...(relationType ? { relation: relationType } : {}),
        focus: 'relation',
      },
    })
    return
  }

  ElMessage.warning(`暂不支持该依据类型的跳转：${source.type || 'unknown'}`)
}

async function handleSaveDocumentFromSource(source: SourceClickItem) {
  const normalizedType = normalizeSourceType(source.type)
  if (normalizedType !== 'external') {
    ElMessage.info('当前文档依据已在知识文档库中，无需再次保存。')
    return
  }

  const sourceUrl = sanitizeExternalUrl(String(source.url || source.id || ''))
  if (!sourceUrl) {
    ElMessage.warning('外部文档链接无效，暂时无法保存。')
    return
  }

  const title = String(source.title || '').trim() || sourceUrl
  const content = [
    `来源链接：${sourceUrl}`,
    '',
    '说明：该文档由智能问答中的外部参考一键保存生成。',
    '',
    `原始标题：${title}`,
  ].join('\n')

  try {
    const addRes = await documentApi.add({
      title: title.slice(0, 120),
      content,
      category: '外部参考',
    })
    const createdId = addRes.data.data?.id
    if (createdId && Number.isFinite(createdId)) {
      await documentApi.favorite(createdId, true)
    }
    ElMessage.success('外部文档已保存到知识文档（并加入收藏）')
  } catch {
    // handled by request interceptor
  }
}

function formatPercent(value?: number) {
  const n = Number(value ?? 0)
  return `${(n * 100).toFixed(1)}%`
}

function previewSnippetCode(code: string) {
  const lines = (code || '').split('\n')
  return lines.slice(0, 4).join('\n')
}

function getInsertModeLabel(mode: SnippetInsertMode) {
  return insertModeLabelMap[mode]
}

function toggleTopPanel(panel: 'metrics' | 'snippets' | 'videos') {
  activeTopPanel.value = activeTopPanel.value === panel ? null : panel
  if (panel === 'videos' && activeTopPanel.value === 'videos') {
    void refreshRecommendedVideos()
  }
}

async function loadMetrics() {
  metricsLoading.value = true
  try {
    const res = await getRetrievalMetrics()
    metrics.value = res.data.data
  } catch {
    // handled by interceptor
  } finally {
    metricsLoading.value = false
  }
}

async function handleResetMetrics() {
  resettingMetrics.value = true
  try {
    await resetRetrievalMetrics()
    ElMessage.success('检索统计已重置')
    await loadMetrics()
  } catch {
    // handled by interceptor
  } finally {
    resettingMetrics.value = false
  }
}

async function refreshRecommendedSnippets() {
  const conversationId = chatStore.currentConversation?.id
  if (!conversationId) return
  await chatStore.loadRecommendedSnippets(conversationId, true)
}

async function refreshRecommendedVideos() {
  const conversationId = chatStore.currentConversation?.id
  if (!conversationId) {
    recommendedVideos.value = []
    recommendedVideosError.value = ''
    return
  }

  recommendedVideosLoading.value = true
  recommendedVideosError.value = ''
  try {
    const res = await recommendLearningVideos('', conversationId, 6)
    recommendedVideos.value = res.data.data || []
  } catch (error: any) {
    recommendedVideos.value = []
    recommendedVideosError.value = error?.message || '视频推荐加载失败，请稍后重试。'
  } finally {
    recommendedVideosLoading.value = false
  }
}

function jumpToLearningStudio(video: LearningVideo) {
  void router.push({
    name: 'learning-studio',
    query: { videoId: String(video.id), from: 'chat' },
  })
}

async function handleSnippetFeedback(snippet: CodeSnippet, rating: 'useful' | 'useless') {
  await chatStore.markRecommendedSnippetFeedback(snippet.id, rating)
  ElMessage.success(rating === 'useful' ? '已记录：推荐有用' : '已记录：减少类似推荐')
}

async function handleCopySnippet(snippet: CodeSnippet) {
  try {
    await navigator.clipboard.writeText(snippet.code)
    ElMessage.success('代码片段已复制')
    void chatStore.markRecommendedSnippetUsed(snippet.id)
  } catch {
    ElMessage.error('复制失败，请手动复制')
  }
}

function handleInsertSnippet(snippet: CodeSnippet) {
  if (chatStore.streaming) {
    ElMessage.warning('正在回答中，请先停止后再插入代码。')
    return
  }

  const mode = snippetInsertMode.value
  const language = (snippet.language || 'text').trim()
  const block = `\`\`\`${language}\n${snippet.code}\n\`\`\``

  if (mode === 'replace') {
    messageInput.value = block
    ElMessage.success(`已${getInsertModeLabel(mode)}`)
    void chatStore.markRecommendedSnippetUsed(snippet.id)
    return
  }

  if (mode === 'cursor') {
    const textarea = messageInputRef.value?.textarea as HTMLTextAreaElement | undefined
    if (!textarea) {
      const current = messageInput.value.trim()
      messageInput.value = current ? `${current}\n\n${block}` : block
      ElMessage.success('未定位到光标，已追加到末尾')
      void chatStore.markRecommendedSnippetUsed(snippet.id)
      return
    }

    const value = messageInput.value || ''
    const start = textarea.selectionStart ?? value.length
    const end = textarea.selectionEnd ?? value.length
    messageInput.value = `${value.slice(0, start)}${block}${value.slice(end)}`

    nextTick(() => {
      const insertedCursor = start + block.length
      textarea.focus()
      textarea.setSelectionRange(insertedCursor, insertedCursor)
    })

    ElMessage.success(`已${getInsertModeLabel(mode)}`)
    void chatStore.markRecommendedSnippetUsed(snippet.id)
    return
  }

  const current = messageInput.value.trim()
  messageInput.value = current ? `${current}\n\n${block}` : block
  ElMessage.success(`已${getInsertModeLabel(mode)}`)
  void chatStore.markRecommendedSnippetUsed(snippet.id)
}

function handleInsertModeChange(command: string | number | object) {
  if (typeof command !== 'string') return
  if (command !== 'append' && command !== 'replace' && command !== 'cursor') return
  snippetInsertMode.value = command
  ElMessage.info(`插入模式已切换：${getInsertModeLabel(command)}`)
}

function buildSnippetTitle(language: string, code: string) {
  const firstLine = (code || '')
    .split('\n')
    .map((line) => line.trim())
    .find(Boolean)
  const normalizedLanguage = (language || 'plaintext').trim()
  if (firstLine) {
    return `${normalizedLanguage} 片段 - ${firstLine.slice(0, 28)}`
  }
  return `${normalizedLanguage} 片段`
}

function handleSaveCode(payload: SaveCodePayload) {
  saveSnippetForm.language = (payload.language || 'plaintext').trim()
  saveSnippetForm.code = payload.code || ''
  saveSnippetForm.title = buildSnippetTitle(saveSnippetForm.language, saveSnippetForm.code)
  saveSnippetForm.description = '来自智能问答回答'
  saveSnippetForm.tagsText = 'chat,ai'
  saveSnippetDialogVisible.value = true
}

async function confirmSaveSnippet() {
  const title = saveSnippetForm.title.trim()
  const code = saveSnippetForm.code.trim()
  const language = saveSnippetForm.language.trim() || 'plaintext'
  if (!title || !code) {
    ElMessage.warning('标题和代码不能为空')
    return
  }

  savingSnippet.value = true
  try {
    await createSnippet({
      title,
      code,
      language,
      description: saveSnippetForm.description.trim(),
      tags: saveSnippetForm.tagsText
        .split(',')
        .map((tag) => tag.trim())
        .filter(Boolean),
    })
    ElMessage.success('代码片段已保存')
    saveSnippetDialogVisible.value = false
    const conversationId = chatStore.currentConversation?.id
    if (conversationId) {
      await chatStore.loadRecommendedSnippets(conversationId, true)
    }
  } catch {
    // handled by request interceptor
  } finally {
    savingSnippet.value = false
  }
}

function scrollToBottom() {
  nextTick(() => {
    if (messageContainer.value) {
      messageContainer.value.scrollTop = messageContainer.value.scrollHeight
    }
  })
}

onMounted(() => {
  chatStore.loadConversations()
  void loadMetrics()
})

watch(
  () => chatStore.messages.length,
  () => scrollToBottom(),
)

watch(
  () => chatStore.streamingContent,
  () => scrollToBottom(),
)

watch(
  () => chatStore.currentConversation?.id,
  () => {
    if (activeTopPanel.value === 'videos') {
      void refreshRecommendedVideos()
    }
  },
)

function handleSelectConversation(conv: Conversation) {
  chatStore.selectConversation(conv)
}

function handleDeleteConversation(convId: number) {
  chatStore.deleteConversation(convId)
}

function handleNewConversation() {
  chatStore.newConversation()
  messageInput.value = ''
}

function handleSend() {
  const content = messageInput.value.trim()
  if (!content || chatStore.streaming) return
  messageInput.value = ''
  chatStore.sendMessage(content)
  scrollToBottom()
}

function handleStop() {
  chatStore.stopStreaming()
  ElMessage.info('已停止回答')
}

function handleKeydown(event: Event | KeyboardEvent) {
  if (!(event instanceof KeyboardEvent)) return
  if (event.key === 'Enter' && !event.shiftKey) {
    event.preventDefault()
    handleSend()
    return
  }
  if (event.key === 'Escape' && chatStore.streaming) {
    event.preventDefault()
    handleStop()
  }
}
</script>

<template>
  <div class="chat-view">
    <aside class="chat-sidebar">
      <div class="sidebar-header">
        <el-button type="primary" :icon="Plus" class="new-chat-btn" @click="handleNewConversation">新建对话</el-button>
      </div>
      <div class="sidebar-content">
        <ConversationList
          :conversations="chatStore.conversations"
          :active-id="chatStore.currentConversation?.id ?? null"
          @select="handleSelectConversation"
          @delete="handleDeleteConversation"
        />
      </div>
    </aside>

    <section class="chat-main">
      <div class="tool-strip">
        <el-button
          size="small"
          class="tool-trigger"
          :type="activeTopPanel === 'metrics' ? 'primary' : 'default'"
          @click="toggleTopPanel('metrics')"
        >
          检索评估
        </el-button>
        <el-button
          size="small"
          class="tool-trigger"
          :type="activeTopPanel === 'snippets' ? 'primary' : 'default'"
          @click="toggleTopPanel('snippets')"
        >
          代码推荐
        </el-button>
        <el-button
          size="small"
          class="tool-trigger"
          :type="activeTopPanel === 'videos' ? 'primary' : 'default'"
          @click="toggleTopPanel('videos')"
        >
          视频推荐
        </el-button>
      </div>

      <el-collapse-transition>
        <div v-if="activeTopPanel === 'metrics'" class="panel metrics-panel" v-loading="metricsLoading">
          <div class="panel-header">
            <div class="panel-title">检索评估</div>
            <div class="panel-actions">
              <el-button size="small" :icon="Refresh" :loading="metricsLoading" @click="loadMetrics">刷新</el-button>
              <el-button size="small" type="danger" plain :icon="Delete" :loading="resettingMetrics" @click="handleResetMetrics">重置</el-button>
            </div>
          </div>

          <div v-if="metrics" class="metrics-grid">
            <div class="metric-item"><span class="label">总请求</span><span class="value">{{ metrics.totalRequests }}</span></div>
            <div class="metric-item"><span class="label">平均耗时</span><span class="value">{{ metrics.avgElapsedMs }} ms</span></div>
            <div class="metric-item"><span class="label">Chunk 路径</span><span class="value">{{ metrics.semanticPathBreakdown.chunk }}</span></div>
            <div class="metric-item"><span class="label">Doc 回退</span><span class="value">{{ metrics.semanticPathBreakdown.docVectorFallback }}</span></div>
            <div class="metric-item"><span class="label">Local 回退</span><span class="value">{{ metrics.semanticPathBreakdown.localTfidfFallback }}</span></div>
            <div class="metric-item"><span class="label">平均最终命中</span><span class="value">{{ metrics.hitStats.avgFinalHitsPerRequest }}</span></div>
          </div>

          <div v-if="metrics" class="metrics-subline">
            回退率：Doc {{ formatPercent(metrics.fallbackRate.docVectorFallbackRate) }} / Local
            {{ formatPercent(metrics.fallbackRate.localTfidfFallbackRate) }}
            <span class="sep">|</span>
            路径总计：{{ pathTotal }}
          </div>
        </div>
      </el-collapse-transition>

      <el-collapse-transition>
        <div v-if="activeTopPanel === 'snippets'" class="panel snippet-panel">
          <div class="panel-header">
            <div class="panel-title">代码推荐</div>
            <div class="panel-actions">
              <el-button
                size="small"
                :icon="Refresh"
                :disabled="!hasActiveConversation"
                :loading="chatStore.recommendedSnippetsLoading"
                @click="refreshRecommendedSnippets"
              >
                刷新推荐
              </el-button>
            </div>
          </div>

          <div v-if="!hasActiveConversation" class="panel-empty">选择一个对话后即可查看推荐代码片段。</div>
          <div v-else-if="chatStore.recommendedSnippetsLoading" class="panel-empty">正在加载推荐...</div>
          <div v-else-if="chatStore.recommendedSnippetsError" class="panel-error">{{ chatStore.recommendedSnippetsError }}</div>
          <div v-else-if="chatStore.recommendedSnippets.length === 0" class="panel-empty">暂无推荐片段，可先继续对话。</div>

          <div v-else class="snippet-list">
            <div v-for="snippet in chatStore.recommendedSnippets.slice(0, 4)" :key="snippet.id" class="snippet-card">
              <div class="snippet-card__head">
                <div class="snippet-title">{{ snippet.title }}</div>
                <div class="snippet-meta">{{ snippet.language }} · 使用 {{ snippet.useCount }}</div>
              </div>

              <div v-if="snippet.recommendReason" class="snippet-reason">
                <span>{{ snippet.recommendReason }}</span>
                <span v-if="snippet.matchScore != null" class="snippet-score">score {{ snippet.matchScore }}</span>
              </div>

              <pre class="snippet-code">{{ previewSnippetCode(snippet.code) }}</pre>
              <div class="snippet-actions">
                <div class="snippet-feedback">
                  <el-button size="small" text :icon="Check" @click="handleSnippetFeedback(snippet, 'useful')">有用</el-button>
                  <el-button size="small" text :icon="Close" @click="handleSnippetFeedback(snippet, 'useless')">不合适</el-button>
                </div>
                <el-button size="small" text :icon="CopyDocument" @click="handleCopySnippet(snippet)">复制</el-button>
                <el-dropdown
                  split-button
                  size="small"
                  type="primary"
                  class="insert-btn split-insert-btn"
                  @click="handleInsertSnippet(snippet)"
                  @command="handleInsertModeChange"
                >
                  插入输入框（{{ getInsertModeLabel(snippetInsertMode) }}）
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item
                        v-for="mode in insertModeOptions"
                        :key="mode.value"
                        :command="mode.value"
                        :class="{ 'is-current-mode': snippetInsertMode === mode.value }"
                      >
                        {{ mode.label }}
                      </el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
              </div>
            </div>
          </div>
        </div>
      </el-collapse-transition>

      <el-collapse-transition>
        <div v-if="activeTopPanel === 'videos'" class="panel video-panel">
          <div class="panel-header">
            <div class="panel-title">学习视频推荐</div>
            <div class="panel-actions">
              <el-button
                size="small"
                :icon="Refresh"
                :disabled="!hasActiveConversation"
                :loading="recommendedVideosLoading"
                @click="refreshRecommendedVideos"
              >
                刷新推荐
              </el-button>
            </div>
          </div>

          <div v-if="!hasActiveConversation" class="panel-empty">选择一个对话后即可查看视频推荐。</div>
          <div v-else-if="recommendedVideosLoading" class="panel-empty">正在加载视频推荐...</div>
          <div v-else-if="recommendedVideosError" class="panel-error">{{ recommendedVideosError }}</div>
          <div v-else-if="recommendedVideos.length === 0" class="panel-empty">暂无推荐视频，可先继续对话。</div>

          <div v-else class="video-recommend-list">
            <article v-for="video in recommendedVideos" :key="video.id" class="video-recommend-item">
              <div class="video-recommend-main">
                <h4>{{ video.title }}</h4>
                <p>{{ video.description || '暂无描述' }}</p>
                <div class="video-recommend-meta">
                  <el-tag size="small">{{ video.platform || 'web' }}</el-tag>
                  <el-tag size="small" type="info">{{ Math.max(0, Math.round(video.durationSeconds / 60)) }} 分钟</el-tag>
                  <el-tag size="small" type="success">进度 {{ Math.round((video.completionRate || 0) * 100) }}%</el-tag>
                </div>
              </div>
              <div class="video-recommend-actions">
                <el-button type="primary" size="small" @click="jumpToLearningStudio(video)">
                  去学习看台
                </el-button>
              </div>
            </article>
          </div>
        </div>
      </el-collapse-transition>

      <div ref="messageContainer" class="message-list">
        <div v-if="chatStore.messages.length === 0" class="welcome-section">
          <div class="welcome-content">
            <h2>欢迎使用智能编程助手</h2>
            <p>支持编程问答、代码解释与学习建议。输入问题即可开始。</p>
            <div class="quick-actions">
              <el-button
                v-for="q in ['解释 JavaScript 中的闭包', 'Python 装饰器怎么用？', 'React 和 Vue 有什么区别？', '如何设计 RESTful API？']"
                :key="q"
                round
                @click="messageInput = q"
              >
                {{ q }}
              </el-button>
            </div>
          </div>
        </div>
        <template v-else>
          <MessageBubble
            v-for="msg in chatStore.messages"
            :key="msg.id"
            :message="msg"
            @source-click="handleSourceClick"
            @save-code="handleSaveCode"
            @save-document="handleSaveDocumentFromSource"
          />
        </template>
      </div>

      <div class="input-area">
        <div class="input-container">
          <el-input
            ref="messageInputRef"
            v-model="messageInput"
            type="textarea"
            :autosize="{ minRows: 1, maxRows: 6 }"
            placeholder="输入你的问题... (Enter 发送, Shift+Enter 换行)"
            resize="none"
            :disabled="chatStore.streaming"
            @keydown="handleKeydown"
          />
          <el-button
            v-if="!chatStore.streaming"
            type="primary"
            :icon="Promotion"
            circle
            class="send-btn"
            :disabled="!messageInput.trim()"
            @click="handleSend"
          />
          <el-button
            v-else
            type="danger"
            :icon="CloseBold"
            circle
            class="stop-btn"
            @click="handleStop"
          />
        </div>
        <div class="input-hint">AI 可能存在不准确内容，请结合实际验证。</div>
      </div>
    </section>

    <el-dialog
      v-model="saveSnippetDialogVisible"
      title="保存代码片段"
      width="640px"
      append-to-body
    >
      <el-form label-position="top">
        <el-form-item label="标题">
          <el-input v-model="saveSnippetForm.title" maxlength="80" show-word-limit />
        </el-form-item>
        <el-form-item label="语言">
          <el-input v-model="saveSnippetForm.language" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="saveSnippetForm.description" />
        </el-form-item>
        <el-form-item label="标签">
          <el-input v-model="saveSnippetForm.tagsText" placeholder="用英文逗号分隔，例如 chat,ai,java" />
        </el-form-item>
        <el-form-item label="代码">
          <el-input
            v-model="saveSnippetForm.code"
            type="textarea"
            :autosize="{ minRows: 8, maxRows: 14 }"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="saveSnippetDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingSnippet" @click="confirmSaveSnippet">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
.chat-view {
  display: flex;
  height: 100%;
  gap: 14px;
  padding: 14px;
  overflow: hidden;
}

.chat-sidebar {
  width: 292px;
  background: var(--bg-card);
  border: 1px solid var(--border-light);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
  display: flex;
  flex-direction: column;
  flex-shrink: 0;

  @media (max-width: 960px) {
    display: none;
  }
}

.sidebar-header {
  padding: 14px;
  border-bottom: 1px solid var(--border-lighter);
}

.new-chat-btn {
  width: 100%;
  border-radius: 10px;
  font-weight: 600;
}

.sidebar-content {
  flex: 1;
  overflow-y: auto;
}

.chat-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: var(--bg-card);
  border: 1px solid var(--border-light);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
}

.tool-strip {
  padding: 8px 12px;
  border-bottom: 1px solid var(--border-lighter);
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  background: #f9fbff;
}

.tool-trigger {
  border-radius: 999px;
}

.panel {
  margin: 8px 12px 0;
  border: 1px solid #dfe8f8;
  background: #fff;
  border-radius: 12px;
  padding: 10px 12px;
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
}

.panel-title {
  font-size: 14px;
  font-weight: 700;
  color: var(--text-primary);
}

.panel-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.metrics-grid {
  margin-top: 8px;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px 12px;
}

.metric-item {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  font-size: 12px;

  .label {
    color: var(--text-secondary);
  }

  .value {
    color: var(--text-primary);
    font-weight: 600;
  }
}

.metrics-subline {
  margin-top: 8px;
  font-size: 12px;
  color: var(--text-secondary);

  .sep {
    margin: 0 6px;
    color: var(--text-placeholder);
  }
}

.panel-empty,
.panel-error {
  margin-top: 10px;
  border-radius: 10px;
  padding: 8px 10px;
  font-size: 12px;
}

.panel-empty {
  color: var(--text-secondary);
  background: #f6f9ff;
}

.panel-error {
  color: #b74444;
  background: #fff4f4;
  border: 1px solid #ffd8d8;
}

.snippet-list {
  margin-top: 10px;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;

  @media (max-width: 1200px) {
    grid-template-columns: 1fr;
  }
}

.snippet-card {
  border: 1px solid #e3ebfa;
  border-radius: 10px;
  background: #fbfdff;
  padding: 8px 10px;
}

.snippet-card__head {
  display: flex;
  justify-content: space-between;
  gap: 8px;
}

.snippet-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-primary);
}

.snippet-meta {
  font-size: 12px;
  color: var(--text-secondary);
}

.snippet-reason {
  margin-top: 6px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
  padding: 6px 8px;
  border-radius: 8px;
  background: #f2f7ff;
  color: #2f5da7;
  font-size: 12px;
}

.snippet-score {
  font-weight: 600;
  color: #1f4f9b;
}

.snippet-code {
  margin-top: 6px;
  margin-bottom: 6px;
  border-radius: 8px;
  border: 1px solid #e1e9f8;
  background: #f5f8ff;
  padding: 8px;
  font-size: 12px;
  line-height: 1.5;
  color: #38548a;
  overflow: hidden;
}

.snippet-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  flex-wrap: wrap;
}

.video-recommend-list {
  margin-top: 10px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.video-recommend-item {
  border: 1px solid #e3ebfa;
  border-radius: 10px;
  background: #fbfdff;
  padding: 10px;
  display: flex;
  justify-content: space-between;
  gap: 10px;

  h4 {
    margin: 0;
    font-size: 14px;
    color: var(--text-primary);
  }

  p {
    margin: 6px 0;
    font-size: 12px;
    color: var(--text-secondary);
  }
}

.video-recommend-main {
  min-width: 0;
}

.video-recommend-meta {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.video-recommend-actions {
  display: flex;
  align-items: center;
  flex-shrink: 0;
}

.snippet-feedback {
  margin-right: auto;
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.insert-btn {
  color: #fff !important;
  border-color: #2f7df6 !important;
  background: linear-gradient(135deg, #2f7df6, #4b8fff) !important;
  box-shadow: 0 6px 14px rgba(47, 125, 246, 0.28);
}

:deep(.split-insert-btn .el-button) {
  color: #fff !important;
  border-color: #2f7df6 !important;
  background: linear-gradient(135deg, #2f7df6, #4b8fff) !important;
  box-shadow: 0 6px 14px rgba(47, 125, 246, 0.28);
}

:deep(.split-insert-btn .el-button + .el-button) {
  box-shadow: none;
}

:deep(.el-dropdown-menu__item.is-current-mode) {
  color: #2f7df6;
  font-weight: 600;
}

.message-list {
  flex: 1;
  overflow-y: auto;
  padding: 12px 0;
  background: linear-gradient(180deg, #fbfdff 0%, #f8fbff 100%);
}

.welcome-section {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 42px 28px;
}

.welcome-content {
  text-align: center;
  max-width: 560px;

  h2 {
    font-size: 26px;
    line-height: 1.3;
    font-weight: 700;
    letter-spacing: 0.2px;
    color: var(--text-primary);
    margin-bottom: 10px;
  }

  p {
    font-size: 14px;
    color: var(--text-secondary);
    line-height: 1.75;
    margin-bottom: 24px;
  }
}

.quick-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 10px;
}

.input-area {
  border-top: 1px solid var(--border-lighter);
  padding: 14px 18px 10px;
  background: var(--bg-card);
}

.input-container {
  display: flex;
  align-items: flex-end;
  gap: 10px;
  max-width: 900px;
  margin: 0 auto;

  :deep(.el-textarea__inner) {
    border-radius: 14px;
    padding: 12px 16px;
    border: 1px solid var(--border-color);
    box-shadow: 0 6px 16px rgba(31, 71, 133, 0.08);
    font-size: 14px;
    line-height: 1.55;
    transition: all 0.2s ease;

    &:focus {
      border-color: #9ec3ff;
      box-shadow: 0 0 0 3px rgba(47, 125, 246, 0.12);
    }
  }
}

.send-btn {
  width: 42px;
  height: 42px;
  flex-shrink: 0;
  box-shadow: 0 8px 16px rgba(47, 125, 246, 0.26);
}

.stop-btn {
  width: 42px;
  height: 42px;
  flex-shrink: 0;
  box-shadow: 0 8px 16px rgba(224, 82, 82, 0.22);
}

.input-hint {
  margin-top: 8px;
  text-align: center;
  font-size: 12px;
  color: var(--text-placeholder);
}
</style>
