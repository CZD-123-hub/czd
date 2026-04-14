import request from './request'
import type { ApiResponse, Conversation, Message } from '@/types'

export function getConversations() {
  return request.get<ApiResponse<Conversation[]>>('/chat/conversations')
}

export function getMessages(conversationId: number) {
  return request.get<ApiResponse<Message[]>>(`/chat/conversations/${conversationId}/messages`)
}

export function deleteConversation(conversationId: number) {
  return request.delete<ApiResponse<null>>(`/chat/conversations/${conversationId}`)
}

export function submitFeedback(messageId: number, rating: 'useful' | 'useless' | 'none') {
  // 如果是取消，发送 null
  if (rating === 'none') {
    return request.post<ApiResponse<null>>('/chat/feedback', { messageId, rating: null })
  }
  return request.post<ApiResponse<null>>('/chat/feedback', { messageId, rating })
}

/**
 * Send message via fetch + SSE streaming (POST request with Bearer token)
 */
export function sendMessageSSE(
  conversationId: number | null,
  content: string,
  onMessage: (chunk: string) => void,
  onDone: () => void,
  onError: (err: string) => void,
): AbortController {
  const controller = new AbortController()
  const token = localStorage.getItem('token') || ''

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
        onError(`请求失败: ${response.status}`)
        return
      }

      const reader = response.body?.getReader()
      if (!reader) {
        onError('无法读取响应流')
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
          if (data === '[DONE]' || eventName === 'done') {
            onDone()
            return
          }

          if (data) {
            onMessage(data)
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
          onMessage(data)
        }
      }

      onDone()
    })
    .catch((err) => {
      if (err.name !== 'AbortError') {
        onError(err.message || '网络错误')
      }
    })

  return controller
}