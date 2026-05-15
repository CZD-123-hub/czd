type ApiErrorPayload = {
  response?: {
    data?: {
      message?: string
    }
  }
  message?: string
}

export function resolveErrorMessage(error: unknown, fallback: string): string {
  const normalized = (error || {}) as ApiErrorPayload
  const apiMessage = normalized.response?.data?.message
  if (typeof apiMessage === 'string' && apiMessage.trim()) {
    return apiMessage.trim()
  }

  const directMessage = normalized.message
  if (typeof directMessage === 'string' && directMessage.trim()) {
    return directMessage.trim()
  }

  return fallback
}
