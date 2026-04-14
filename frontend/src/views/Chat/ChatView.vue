<script setup lang="ts">
import { ref, nextTick, onMounted, watch, computed } from 'vue'
import { useChatStore } from '@/stores/chat'
import ConversationList from '@/components/chat/ConversationList.vue'
import MessageBubble from '@/components/chat/MessageBubble.vue'
import { Plus, Promotion, Operation, ChatDotRound } from '@element-plus/icons-vue'
import type { Conversation } from '@/types'

const chatStore = useChatStore()

const messageInput = ref('')
const messageContainer = ref<HTMLDivElement | null>(null)
const inputRef = ref<HTMLTextAreaElement | null>(null)
const conversationsLoading = ref(false)
const mobileDrawerVisible = ref(false)

const quickPrompts = [
  '解释 JavaScript 中的闭包，并给一个实际案例。',
  '如何设计一个高可用的缓存架构？',
  '帮我比较 Vue 和 React 的状态管理方案。',
  '给我一个后端接口性能排查清单。',
]

const canSend = computed(() => Boolean(messageInput.value.trim()) && !chatStore.streaming)
const conversationTotal = computed(() => chatStore.conversations.length)
const activeConversationTitle = computed(() => chatStore.currentConversation?.title || '新会话')

onMounted(async () => {
  conversationsLoading.value = true
  try {
    await chatStore.loadConversations()
  } finally {
    conversationsLoading.value = false
  }
})

function scrollToBottom() {
  nextTick(() => {
    if (messageContainer.value) {
      messageContainer.value.scrollTop = messageContainer.value.scrollHeight
    }
  })
}

watch(
  () => chatStore.messages.length,
  () => {
    scrollToBottom()
  },
)

watch(
  () => chatStore.streamingContent,
  () => {
    scrollToBottom()
  },
)

async function handleSelectConversation(conv: Conversation) {
  await chatStore.selectConversation(conv)
  mobileDrawerVisible.value = false
  scrollToBottom()
}

async function handleDeleteConversation(convId: number) {
  await chatStore.deleteConversation(convId)
}

function handleNewConversation() {
  chatStore.newConversation()
  messageInput.value = ''
  mobileDrawerVisible.value = false
}

function applyPrompt(prompt: string) {
  messageInput.value = prompt
  nextTick(() => {
    inputRef.value?.focus()
  })
}

function handleSend() {
  const content = messageInput.value.trim()
  if (!content || chatStore.streaming) return

  messageInput.value = ''
  chatStore.sendMessage(content)
  scrollToBottom()
}

function handleKeydown(event: Event | KeyboardEvent) {
  if (!(event instanceof KeyboardEvent)) return
  if (event.key === 'Enter' && !event.shiftKey) {
    event.preventDefault()
    handleSend()
  }
}
</script>

<template>
  <div class="chat-view page-shell">
    <div class="page-head">
      <div class="page-title-block">
        <h2 class="page-title">智能问答工作台</h2>
        <p class="page-subtitle">{{ activeConversationTitle }} · 共 {{ conversationTotal }} 条会话</p>
      </div>
      <div class="page-toolbar">
        <el-button class="mobile-only" :icon="Operation" @click="mobileDrawerVisible = true">会话列表</el-button>
        <el-button type="primary" :icon="Plus" @click="handleNewConversation">新建对话</el-button>
      </div>
    </div>

    <div class="chat-body">
      <aside class="chat-sidebar soft-panel">
        <div class="sidebar-header">
          <div class="sidebar-title-wrap">
            <h3>历史会话</h3>
            <span>{{ conversationTotal }} 条</span>
          </div>
          <el-button type="primary" plain :icon="Plus" class="new-chat-btn" @click="handleNewConversation">
            新会话
          </el-button>
        </div>
        <div class="sidebar-content">
          <ConversationList
            :conversations="chatStore.conversations"
            :active-id="chatStore.currentConversation?.id ?? null"
            :loading="conversationsLoading"
            @select="handleSelectConversation"
            @delete="handleDeleteConversation"
          />
        </div>
      </aside>

      <el-drawer
        v-model="mobileDrawerVisible"
        direction="ltr"
        size="320px"
        :with-header="false"
        class="mobile-drawer"
      >
        <div class="drawer-content">
          <div class="sidebar-header">
            <div class="sidebar-title-wrap">
              <h3>历史会话</h3>
              <span>{{ conversationTotal }} 条</span>
            </div>
            <el-button type="primary" plain :icon="Plus" class="new-chat-btn" @click="handleNewConversation">
              新会话
            </el-button>
          </div>
          <ConversationList
            :conversations="chatStore.conversations"
            :active-id="chatStore.currentConversation?.id ?? null"
            :loading="conversationsLoading"
            @select="handleSelectConversation"
            @delete="handleDeleteConversation"
          />
        </div>
      </el-drawer>

      <section class="chat-main soft-panel">
        <div ref="messageContainer" class="message-list">
          <div v-if="chatStore.loading" class="message-skeleton">
            <el-skeleton v-for="i in 4" :key="i" animated>
              <template #template>
                <div class="skeleton-row" :class="{ right: i % 2 === 0 }">
                  <el-skeleton-item variant="circle" style="width: 34px; height: 34px" />
                  <div class="skeleton-bubble">
                    <el-skeleton-item variant="text" style="width: 70%" />
                    <el-skeleton-item variant="text" style="width: 95%" />
                    <el-skeleton-item variant="text" style="width: 60%" />
                  </div>
                </div>
              </template>
            </el-skeleton>
          </div>

          <div v-else-if="chatStore.messages.length === 0" class="welcome-section">
            <div class="welcome-content">
              <div class="welcome-icon">
                <el-icon :size="30"><ChatDotRound /></el-icon>
              </div>
              <h2>开始你的新对话</h2>
              <p>你可以提问编程问题、让助手审查代码，或让它帮你拆解学习计划。</p>
              <div class="quick-actions">
                <el-button
                  v-for="prompt in quickPrompts"
                  :key="prompt"
                  round
                  @click="applyPrompt(prompt)"
                >
                  {{ prompt }}
                </el-button>
              </div>
            </div>
          </div>

          <template v-else>
            <MessageBubble v-for="msg in chatStore.messages" :key="msg.id" :message="msg" />
          </template>
        </div>

        <div class="input-area">
          <div class="input-container">
            <el-input
              ref="inputRef"
              v-model="messageInput"
              type="textarea"
              :autosize="{ minRows: 1, maxRows: 6 }"
              placeholder="输入你的问题... Enter 发送，Shift+Enter 换行"
              resize="none"
              :disabled="chatStore.streaming"
              @keydown="handleKeydown"
            />
            <el-button
              type="primary"
              :icon="Promotion"
              circle
              class="send-btn"
              :disabled="!canSend"
              :loading="chatStore.streaming"
              @click="handleSend"
            />
          </div>
          <div class="input-hint">
            回答由 AI 生成，请结合你的业务背景判断和验证关键结论。
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.chat-view {
  min-height: 0;
}

.chat-body {
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-columns: 300px minmax(0, 1fr);
  gap: 14px;
  padding: 14px;
}

.chat-sidebar {
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.sidebar-header {
  padding: 12px;
  border-bottom: 1px solid var(--border-lighter);
}

.sidebar-title-wrap {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;

  h3 {
    font-size: 14px;
    font-weight: 700;
    color: var(--text-primary);
  }

  span {
    font-size: 12px;
    color: var(--text-secondary);
  }
}

.new-chat-btn {
  width: 100%;
}

.sidebar-content {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
}

.chat-main {
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.message-list {
  flex: 1;
  overflow-y: auto;
  padding: 10px 10px 20px;
}

.message-skeleton {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 8px 8px 0;
}

.skeleton-row {
  display: flex;
  align-items: flex-start;
  gap: 10px;

  &.right {
    flex-direction: row-reverse;
  }
}

.skeleton-bubble {
  width: min(82%, 700px);
  border: 1px solid var(--border-lighter);
  border-radius: 14px;
  padding: 10px 12px;
  background: #fff;
}

.welcome-section {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 30px 24px;
}

.welcome-content {
  max-width: 680px;
  text-align: center;

  h2 {
    font-size: 26px;
    font-weight: 700;
    margin-bottom: 10px;
    color: var(--text-primary);
  }

  p {
    font-size: 14px;
    color: var(--text-secondary);
    line-height: 1.7;
    margin-bottom: 24px;
  }
}

.welcome-icon {
  width: 62px;
  height: 62px;
  margin: 0 auto 16px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--primary-color);
  background: radial-gradient(circle, rgba(47, 107, 255, 0.18), rgba(47, 107, 255, 0.06));
}

.quick-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 10px;

  .el-button {
    margin: 0;
    font-size: 13px;
  }
}

.input-area {
  position: sticky;
  bottom: 0;
  padding: 14px 18px 12px;
  border-top: 1px solid var(--border-lighter);
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.72), rgba(255, 255, 255, 0.95));
  backdrop-filter: blur(8px);
}

.input-container {
  display: flex;
  align-items: flex-end;
  gap: 12px;
  max-width: 880px;
  margin: 0 auto;

  :deep(.el-textarea__inner) {
    padding: 11px 16px;
    border-radius: 14px;
    font-size: 14px;
    line-height: 1.5;
    min-height: 44px;
  }
}

.send-btn {
  flex-shrink: 0;
  width: 42px;
  height: 42px;
}

.input-hint {
  text-align: center;
  font-size: 12px;
  color: var(--text-placeholder);
  margin-top: 8px;
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
  .chat-body {
    grid-template-columns: 1fr;
  }

  .chat-sidebar {
    display: none;
  }

  .mobile-only {
    display: inline-flex;
  }

  .message-list {
    padding: 6px 2px 14px;
  }

  .welcome-content h2 {
    font-size: 22px;
  }
}

@media (max-width: 768px) {
  .chat-body {
    padding: 10px;
    gap: 10px;
  }

  .input-area {
    padding: 12px 10px 10px;
  }

  .quick-actions {
    gap: 8px;

    .el-button {
      width: 100%;
      justify-content: flex-start;
      margin: 0;
      border-radius: 12px;
    }
  }
}
</style>
