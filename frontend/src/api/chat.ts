import request from './request'
import type { ApiResponse, Conversation, Message, RetrievalMetrics } from '@/types'

export function getConversations() {
  return request.get<ApiResponse<Conversation[]>>('/chat/conversations')
}

export function getMessages(conversationId: number) {
  return request.get<ApiResponse<Message[]>>(`/chat/conversations/${conversationId}/messages`)
}

export function deleteConversation(conversationId: number) {
  return request.delete<ApiResponse<null>>(`/chat/conversations/${conversationId}`)
}

export function submitFeedback(messageId: number, rating: 'useful' | 'useless' | null) {
  return request.post<ApiResponse<null>>('/chat/feedback', { messageId, rating })
}

export function getRetrievalMetrics() {
  return request.get<ApiResponse<RetrievalMetrics>>('/chat/retrieval-metrics')
}

export function resetRetrievalMetrics() {
  return request.post<ApiResponse<null>>('/chat/retrieval-metrics/reset')
}

export interface ChatSseMetaPayload {
  conversationId: number
}

export interface ChatSseDonePayload {
  assistantMessageId: number
}

export interface ChatSseCallbacks {
  onMeta?: (meta: ChatSseMetaPayload) => void
  onDelta: (chunk: string) => void
  onDone: (payload: ChatSseDonePayload) => void
  onError: (err: string) => void
  onAbort?: () => void
}

/**
 * Send message via fetch + SSE streaming (POST request with Bearer token)
 */
export function sendMessageSSE(
  conversationId: number | null,
  content: string,
  callbacks: ChatSseCallbacks,
): AbortController {
  const controller = new AbortController()
  const token = localStorage.getItem('token') || ''
  const { onMeta, onDelta, onDone, onError, onAbort } = callbacks

  function parseJsonPayload<T>(raw: string): T | null {
    try {
      return JSON.parse(raw) as T
    } catch {
      return null
    }
  }

  fetch('/api/chat/send', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${token}`,
      'Accept': 'text/event-stream',
    },
    body: JSON.stringify({ conversationId, content }),
    signal: controller.signal,
  })
    .then(async (response) => {
      if (!response.ok) {
        let message = `Request failed: ${response.status}`
        try {
          const contentType = response.headers.get('content-type') || ''
          if (contentType.includes('application/json')) {
            const payload = (await response.json()) as Partial<ApiResponse<unknown>>
            const serverMessage = typeof payload.message === 'string' ? payload.message.trim() : ''
            const traceId = typeof payload.traceId === 'string' ? payload.traceId.trim() : ''
            if (serverMessage) {
              message = traceId ? `${serverMessage} (traceId: ${traceId})` : serverMessage
            }
          }
        } catch {
          // keep fallback status-based message
        }
        onError(message)
        return
      }

      const reader = response.body?.getReader()
      if (!reader) {
        onError('Unable to read response stream')
        return
      }

      const decoder = new TextDecoder()
      let buffer = ''

      while (true) {
        const { done, value } = await reader.read()
        if (done) break

        buffer += decoder.decode(value, { stream: true })

        // SSE event boundary is a blank line
        const events = buffer.split('\n\n')
        buffer = events.pop() || ''

        for (const eventText of events) {
          const lines = eventText.split('\n')
          let eventName = ''
          const dataParts: string[] = []

          for (const rawLine of lines) {
            const line = rawLine.replace(/\r$/, '')
            if (line.startsWith('event:')) {
              eventName = line.slice(6).trim()
            } else if (line.startsWith('data:')) {
              let part = line.slice(5)
              if (part.startsWith(' ')) part = part.slice(1)
              dataParts.push(part)
            }
          }

          const data = dataParts.join('\n')
          if (!data) {
            continue
          }

          if (eventName === 'meta') {
            const metaPayload = parseJsonPayload<ChatSseMetaPayload>(data)
            if (metaPayload && Number.isFinite(metaPayload.conversationId)) {
              onMeta?.(metaPayload)
            }
            continue
          }

          if (eventName === 'done' || data === '[DONE]') {
            const donePayload = parseJsonPayload<ChatSseDonePayload>(data) || { assistantMessageId: 0 }
            onDone(donePayload)
            return
          }

          if (eventName === 'error') {
            onError(data || '服务异常，请稍后重试')
            return
          }

          if (eventName === 'delta' || eventName === '') {
            onDelta(data)
          }
        }
      }

      // Flush trailing buffer if any valid SSE event remains
      if (buffer.trim()) {
        const lines = buffer.split('\n')
        const dataParts: string[] = []
        for (const rawLine of lines) {
          const line = rawLine.replace(/\r$/, '')
          if (line.startsWith('data:')) {
            let part = line.slice(5)
            if (part.startsWith(' ')) part = part.slice(1)
            dataParts.push(part)
          }
        }
        const data = dataParts.join('\n')
        if (data && data !== '[DONE]') {
          onDelta(data)
        }
      }

      onDone({ assistantMessageId: 0 })
    })
    .catch((err) => {
      if (err.name === 'AbortError') {
        onAbort?.()
      } else {
        onError(err.message || 'Network error')
      }
    })

  return controller
}
