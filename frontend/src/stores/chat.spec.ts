import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useChatStore } from './chat'
import * as chatApi from '@/api/chat'

vi.mock('@/api/chat', () => ({
  getConversations: vi.fn(),
  getMessages: vi.fn(),
  deleteConversation: vi.fn(),
  submitFeedback: vi.fn(),
  sendMessageSSE: vi.fn(),
}))

describe('chat store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('keeps conversation continuity by trusting meta.conversationId', async () => {
    const store = useChatStore()

    vi.mocked(chatApi.getConversations).mockResolvedValue({
      data: {
        code: 200,
        message: 'ok',
        data: [
          { id: 100, title: 'other', createdAt: '2026-04-15T00:00:00', updatedAt: '2026-04-15T00:00:00' },
          { id: 42, title: 'target', createdAt: '2026-04-15T00:00:00', updatedAt: '2026-04-15T00:00:00' },
        ],
      },
    } as any)

    vi.mocked(chatApi.getMessages).mockResolvedValue({
      data: {
        code: 200,
        message: 'ok',
        data: [
          {
            id: 1,
            conversationId: 42,
            role: 'user',
            content: 'hello',
            sources: null,
            createdAt: '2026-04-15T00:00:00',
          },
          {
            id: 5001,
            conversationId: 42,
            role: 'assistant',
            content: '你好',
            sources: null,
            feedbackRating: 'useful',
            createdAt: '2026-04-15T00:00:01',
          },
        ],
      },
    } as any)

    vi.mocked(chatApi.sendMessageSSE).mockImplementation((_conversationId, _content, callbacks) => {
      callbacks.onMeta?.({ conversationId: 42 })
      callbacks.onDelta('你')
      void callbacks.onDone({ assistantMessageId: 5001 })
      return new AbortController()
    })

    store.sendMessage('hello')

    await Promise.resolve()
    await Promise.resolve()

    expect(chatApi.getMessages).toHaveBeenCalledTimes(1)
    expect(chatApi.getMessages).toHaveBeenCalledWith(42)
    expect(store.currentConversation?.id).toBe(42)
    expect(store.streaming).toBe(false)
    expect(store.messages.some((m) => m.id === 5001 && m.role === 'assistant')).toBe(true)
  })
})
