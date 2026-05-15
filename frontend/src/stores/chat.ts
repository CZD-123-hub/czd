import { defineStore } from 'pinia'
import { ref } from 'vue'
import * as chatApi from '@/api/chat'
import * as snippetApi from '@/api/snippet'
import type { CodeSnippet, Conversation, Message } from '@/types'
import { resolveErrorMessage } from '@/utils/errorMessage'

export const useChatStore = defineStore('chat', () => {
  const conversations = ref<Conversation[]>([])
  const currentConversation = ref<Conversation | null>(null)
  const messages = ref<Message[]>([])
  const loading = ref(false)
  const loadError = ref('')
  const streaming = ref(false)
  const streamingContent = ref('')
  const streamError = ref('')
  const messageCache = new Map<number, Message[]>()
  const recommendedSnippets = ref<CodeSnippet[]>([])
  const recommendedSnippetsLoading = ref(false)
  const recommendedSnippetsError = ref('')
  const recommendedSnippetCache = new Map<number, CodeSnippet[]>()
  let tempCounter = 0
  let activeStreamController: AbortController | null = null
  let activeAssistantTempId: string | null = null
  let activeUserTempId: string | null = null

  function clearLoadError() {
    loadError.value = ''
  }

  function clearStreamError() {
    streamError.value = ''
  }

  function setConversationMessages(conversationId: number, fetched: Message[]) {
    messageCache.set(conversationId, fetched)
    if (currentConversation.value?.id === conversationId) {
      messages.value = fetched
    }
  }

  function setRecommendedSnippets(conversationId: number, snippets: CodeSnippet[]) {
    recommendedSnippetCache.set(conversationId, snippets)
    if (currentConversation.value?.id === conversationId) {
      recommendedSnippets.value = snippets
    }
  }

  async function loadRecommendedSnippets(conversationId: number, force = false) {
    if (!Number.isFinite(conversationId) || conversationId <= 0) {
      return
    }

    if (!force && recommendedSnippetCache.has(conversationId)) {
      recommendedSnippets.value = recommendedSnippetCache.get(conversationId) || []
      recommendedSnippetsError.value = ''
      return
    }

    recommendedSnippetsLoading.value = true
    recommendedSnippetsError.value = ''
    try {
      const res = await snippetApi.recommendSnippets(conversationId)
      setRecommendedSnippets(conversationId, res.data.data || [])
    } catch (error) {
      if (!recommendedSnippetCache.has(conversationId)) {
        recommendedSnippets.value = []
      }
      recommendedSnippetsError.value = resolveErrorMessage(error, '代码推荐加载失败，请稍后重试。')
    } finally {
      recommendedSnippetsLoading.value = false
    }
  }

  async function markRecommendedSnippetUsed(id: number) {
    try {
      const res = await snippetApi.markSnippetUsed(id)
      const updated = res.data.data
      const activeConversationId = currentConversation.value?.id
      if (!activeConversationId) {
        return
      }

      const applyUseUpdate = (items: CodeSnippet[]) =>
        items.map((item) => (item.id === id ? { ...item, useCount: updated.useCount, updatedAt: updated.updatedAt } : item))

      recommendedSnippets.value = applyUseUpdate(recommendedSnippets.value)
      if (recommendedSnippetCache.has(activeConversationId)) {
        recommendedSnippetCache.set(activeConversationId, applyUseUpdate(recommendedSnippetCache.get(activeConversationId) || []))
      }
    } catch {
      // Ignore telemetry failures.
    }
  }

  async function markRecommendedSnippetFeedback(id: number, rating: 'useful' | 'useless') {
    try {
      await snippetApi.feedbackSnippet(id, rating)
      const activeConversationId = currentConversation.value?.id
      if (!activeConversationId) {
        return
      }

      if (rating === 'useless') {
        const filterOut = (items: CodeSnippet[]) => items.filter((item) => item.id !== id)
        recommendedSnippets.value = filterOut(recommendedSnippets.value)
        if (recommendedSnippetCache.has(activeConversationId)) {
          recommendedSnippetCache.set(activeConversationId, filterOut(recommendedSnippetCache.get(activeConversationId) || []))
        }
        return
      }

      await loadRecommendedSnippets(activeConversationId, true)
    } catch {
      // The request interceptor surfaces the actionable error to the user.
    }
  }

  async function loadConversations() {
    clearLoadError()
    try {
      const res = await chatApi.getConversations()
      conversations.value = res.data.data
    } catch (error) {
      conversations.value = []
      loadError.value = resolveErrorMessage(error, '会话列表加载失败，请稍后重试。')
    }
  }

  async function selectConversation(conv: Conversation) {
    stopStreaming()
    currentConversation.value = conv
    clearLoadError()
    if (messageCache.has(conv.id)) {
      messages.value = messageCache.get(conv.id)!
      void loadRecommendedSnippets(conv.id)
      return
    }
    loading.value = true
    try {
      const res = await chatApi.getMessages(conv.id)
      setConversationMessages(conv.id, res.data.data)
    } catch (error) {
      messages.value = []
      loadError.value = resolveErrorMessage(error, '会话加载失败，请稍后重试。')
    } finally {
      loading.value = false
    }
    void loadRecommendedSnippets(conv.id)
  }

  function newConversation() {
    stopStreaming()
    currentConversation.value = null
    messages.value = []
    recommendedSnippets.value = []
    recommendedSnippetsError.value = ''
    clearStreamError()
  }

  async function refreshAfterStreamDone(targetConversationId: number | null) {
    if (targetConversationId == null) {
      return
    }

    await loadConversations()
    const targetConversation = conversations.value.find((conv) => conv.id === targetConversationId)
    if (targetConversation) {
      currentConversation.value = targetConversation
    }

    try {
      const res = await chatApi.getMessages(targetConversationId)
      setConversationMessages(targetConversationId, res.data.data)
    } catch {
      // Keep optimistic rendering if refresh fails.
    }
    void loadRecommendedSnippets(targetConversationId, true)
  }

  function replacePendingConversationId(tempConversationId: number, realConversationId: number) {
    for (const msg of messages.value) {
      if (msg.conversationId === tempConversationId) {
        msg.conversationId = realConversationId
      }
    }
  }

  function markAssistantMessageSettled(tempAssistantId: string, assistantMessageId: number) {
    const pendingMessage = messages.value.find((msg) => msg.clientTempId === tempAssistantId)
    if (!pendingMessage) {
      return
    }

    if (assistantMessageId > 0) {
      pendingMessage.id = assistantMessageId
      delete pendingMessage.clientTempId
    }
    pendingMessage.pending = false
  }

  function clearActiveStreamRefs() {
    activeStreamController = null
    activeAssistantTempId = null
    activeUserTempId = null
  }

  function finalizeInterruptedStream() {
    if (activeAssistantTempId) {
      const pendingAssistant = messages.value.find((msg) => msg.clientTempId === activeAssistantTempId)
      if (pendingAssistant) {
        if (!pendingAssistant.content.trim()) {
          pendingAssistant.content = '已停止生成。'
        }
        pendingAssistant.pending = false
      }
    }

    if (activeUserTempId) {
      const pendingUserMessage = messages.value.find((msg) => msg.clientTempId === activeUserTempId)
      if (pendingUserMessage) {
        pendingUserMessage.pending = false
        delete pendingUserMessage.clientTempId
      }
    }

    streaming.value = false
    streamingContent.value = ''
    clearActiveStreamRefs()
  }

  function stopStreaming() {
    if (!streaming.value || !activeStreamController) {
      return
    }
    activeStreamController.abort()
    finalizeInterruptedStream()
  }

  function sendMessage(content: string) {
    if (streaming.value) {
      return null
    }
    clearStreamError()

    const activeConversationId = currentConversation.value?.id ?? null
    const draftConversationId = activeConversationId ?? -(Date.now() + ++tempCounter)
    const nowIso = new Date().toISOString()
    const userTempId = `user-${Date.now()}-${tempCounter}`
    const assistantTempId = `assistant-${Date.now()}-${tempCounter}`

    const userMessage: Message = {
      id: Date.now(),
      conversationId: draftConversationId,
      role: 'user',
      content,
      sources: [],
      createdAt: nowIso,
      pending: activeConversationId == null,
      clientTempId: userTempId,
    }
    messages.value.push(userMessage)

    streaming.value = true
    streamingContent.value = ''

    const assistantMessage: Message = {
      id: Date.now() + 1,
      conversationId: draftConversationId,
      role: 'assistant',
      content: '',
      sources: [],
      createdAt: nowIso,
      pending: true,
      clientTempId: assistantTempId,
    }
    messages.value.push(assistantMessage)

    let fullContent = ''
    let resolvedConversationId: number | null = activeConversationId

    const controller = chatApi.sendMessageSSE(activeConversationId, content, {
      onMeta: ({ conversationId }) => {
        const realConversationId = Number(conversationId)
        if (!Number.isFinite(realConversationId) || realConversationId <= 0) {
          return
        }

        resolvedConversationId = realConversationId
        replacePendingConversationId(draftConversationId, realConversationId)

        if (!currentConversation.value || currentConversation.value.id !== realConversationId) {
          currentConversation.value = {
            id: realConversationId,
            title: content.length > 50 ? `${content.slice(0, 47)}...` : content,
            createdAt: nowIso,
            updatedAt: nowIso,
          }
        }
      },
      onDelta: (chunk: string) => {
        fullContent += chunk
        streamingContent.value = fullContent
        const pendingAssistant = messages.value.find((msg) => msg.clientTempId === assistantTempId)
        if (pendingAssistant) {
          pendingAssistant.content = fullContent
        }
      },
      onDone: async ({ assistantMessageId }) => {
        markAssistantMessageSettled(assistantTempId, assistantMessageId)
        const pendingUserMessage = messages.value.find((msg) => msg.clientTempId === userTempId)
        if (pendingUserMessage) {
          pendingUserMessage.pending = false
          delete pendingUserMessage.clientTempId
        }

        streaming.value = false
        streamingContent.value = ''
        clearActiveStreamRefs()
        await refreshAfterStreamDone(resolvedConversationId)
      },
      onError: (err: string) => {
        const errMessage = (err || '').trim() || '对话请求失败，请稍后重试。'
        streamError.value = errMessage
        const pendingAssistant = messages.value.find((msg) => msg.clientTempId === assistantTempId)
        if (pendingAssistant) {
          pendingAssistant.content = `错误：${errMessage}`
          pendingAssistant.pending = false
        }
        const pendingUserMessage = messages.value.find((msg) => msg.clientTempId === userTempId)
        if (pendingUserMessage) {
          pendingUserMessage.pending = false
          delete pendingUserMessage.clientTempId
        }
        streaming.value = false
        streamingContent.value = ''
        clearActiveStreamRefs()
      },
      onAbort: () => {
        finalizeInterruptedStream()
      },
    })
    activeStreamController = controller
    activeAssistantTempId = assistantTempId
    activeUserTempId = userTempId

    return controller
  }

  async function deleteConversation(convId: number) {
    if (currentConversation.value?.id === convId) {
      stopStreaming()
    }
    await chatApi.deleteConversation(convId)
    messageCache.delete(convId)
    recommendedSnippetCache.delete(convId)
    conversations.value = conversations.value.filter((c) => c.id !== convId)
    if (currentConversation.value?.id === convId) {
      currentConversation.value = null
      messages.value = []
      recommendedSnippets.value = []
      recommendedSnippetsError.value = ''
    }
  }

  return {
    conversations,
    currentConversation,
    messages,
    loading,
    loadError,
    streaming,
    streamingContent,
    streamError,
    recommendedSnippets,
    recommendedSnippetsLoading,
    recommendedSnippetsError,
    loadConversations,
    selectConversation,
    newConversation,
    sendMessage,
    stopStreaming,
    deleteConversation,
    loadRecommendedSnippets,
    markRecommendedSnippetUsed,
    markRecommendedSnippetFeedback,
    clearLoadError,
    clearStreamError,
  }
})
