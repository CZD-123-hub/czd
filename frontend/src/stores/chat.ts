import { defineStore } from 'pinia'
import { ref } from 'vue'
import * as chatApi from '@/api/chat'
import type { Conversation, Message } from '@/types'

export const useChatStore = defineStore('chat', () => {
  const conversations = ref<Conversation[]>([])
  const currentConversation = ref<Conversation | null>(null)
  const messages = ref<Message[]>([])
  const loading = ref(false)
  const streaming = ref(false)
  const streamingContent = ref('')
  const messageCache = new Map<number, Message[]>()

  async function loadConversations() {
    try {
      const res = await chatApi.getConversations()
      conversations.value = res.data.data
    } catch {
      conversations.value = []
    }
  }

  async function selectConversation(conv: Conversation) {
    currentConversation.value = conv
    // 命中缓存直接显示，不再重复请求
    if (messageCache.has(conv.id)) {
      messages.value = messageCache.get(conv.id)!
      return
    }
    loading.value = true
    try {
      const res = await chatApi.getMessages(conv.id)
      const fetched = res.data.data
      messageCache.set(conv.id, fetched)
      if (currentConversation.value?.id === conv.id) {
        messages.value = fetched
      }
    } catch {
      messages.value = []
    } finally {
      loading.value = false
    }
  }

  function newConversation() {
    currentConversation.value = null
    messages.value = []
  }

  function sendMessage(content: string) {
    // Add user message immediately
    const userMessage: Message = {
      id: Date.now(),
      conversationId: currentConversation.value?.id ?? 0,
      role: 'user',
      content,
      sources: [],
      createdAt: new Date().toISOString(),
    }
    messages.value.push(userMessage)

    // Add placeholder assistant message
    streaming.value = true
    streamingContent.value = ''
    const assistantMessage: Message = {
      id: Date.now() + 1,
      conversationId: currentConversation.value?.id ?? 0,
      role: 'assistant',
      content: '',
      sources: [],
      createdAt: new Date().toISOString(),
    }
    messages.value.push(assistantMessage)

    let fullContent = ''

    const controller = chatApi.sendMessageSSE(
      currentConversation.value?.id ?? null,
      content,
      (chunk: string) => {
        fullContent += chunk
        streamingContent.value = fullContent
        const lastMsg = messages.value[messages.value.length - 1]
        if (lastMsg && lastMsg.role === 'assistant') {
          lastMsg.content = fullContent
        }
      },
      () => {
        streaming.value = false
        streamingContent.value = ''
        // 更新缓存
        if (currentConversation.value) {
          messageCache.set(currentConversation.value.id, [...messages.value])
        }
        loadConversations()
      },
      (err: string) => {
        const lastMsg = messages.value[messages.value.length - 1]
        if (lastMsg && lastMsg.role === 'assistant') {
          lastMsg.content = `错误: ${err}`
        }
        streaming.value = false
        streamingContent.value = ''
      },
    )

    return controller
  }

  async function deleteConversation(convId: number) {
    await chatApi.deleteConversation(convId)
    messageCache.delete(convId)
    conversations.value = conversations.value.filter((c) => c.id !== convId)
    if (currentConversation.value?.id === convId) {
      currentConversation.value = null
      messages.value = []
    }
  }

  return {
    conversations,
    currentConversation,
    messages,
    loading,
    streaming,
    streamingContent,
    loadConversations,
    selectConversation,
    newConversation,
    sendMessage,
    deleteConversation,
  }
})