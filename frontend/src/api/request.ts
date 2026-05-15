import axios from 'axios'
import type { AxiosError, AxiosResponse } from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'
import type { ApiResponse } from '@/types'

const service = axios.create({
  baseURL: '/api',
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json',
  },
})

function handleUnauthorized() {
  localStorage.removeItem('token')
  localStorage.removeItem('user')
  if (router.currentRoute.value.path !== '/login') {
    router.push('/login')
  }
}

function withTraceId(message: string, traceId?: string): string {
  if (!traceId) return message
  return `${message}（traceId: ${traceId}）`
}

function mapBizCodeMessage(bizCode?: string, fallback?: string): string {
  switch (bizCode) {
    case 'UNAUTHORIZED':
      return '登录已过期，请重新登录'
    case 'FORBIDDEN':
      return '当前账号无权限访问该资源'
    case 'VALIDATION_FAILED':
      return fallback || '请求参数校验失败'
    case 'NOT_FOUND':
      return fallback || '请求资源不存在'
    case 'TOO_MANY_REQUESTS':
      return '请求过于频繁，请稍后重试'
    case 'AI_PROVIDER_UNAVAILABLE':
      return fallback || '模型服务暂不可用，请稍后重试'
    case 'AI_GENERATION_FAILED':
      return fallback || '生成回答失败，请稍后重试'
    default:
      return fallback || '请求失败'
  }
}

function buildRecoverableMessage(base: string, status?: number, traceId?: string): string {
  if (status === 500) {
    return withTraceId(`${base}，请稍后重试`, traceId)
  }
  if (status === 429) {
    return withTraceId('请求过于频繁，请稍后重试', traceId)
  }
  if (status === 0 || !status) {
    return withTraceId('网络连接异常，请检查网络后重试', traceId)
  }
  return withTraceId(base, traceId)
}

type ExtractedError = {
  message: string
  traceId?: string
  code?: number
  bizCode?: string
}

function extractApiMessage(payload: unknown, fallback: string): ExtractedError {
  if (!payload || typeof payload !== 'object') {
    return { message: fallback }
  }
  const data = payload as Partial<ApiResponse<unknown>>
  const message = typeof data.message === 'string' && data.message.trim() ? data.message.trim() : fallback
  const traceId = typeof data.traceId === 'string' && data.traceId.trim() ? data.traceId.trim() : undefined
  const code = typeof data.code === 'number' ? data.code : undefined
  const bizCode = typeof data.error?.bizCode === 'string' ? data.error.bizCode : undefined
  return { message, traceId, code, bizCode }
}

function notifyErrorByPayload(extracted: ExtractedError, status?: number) {
  const mappedMessage = mapBizCodeMessage(extracted.bizCode, extracted.message)
  ElMessage.error(buildRecoverableMessage(mappedMessage, status ?? extracted.code, extracted.traceId))
}

service.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error),
)

service.interceptors.response.use(
  (response: AxiosResponse<ApiResponse<unknown>>) => {
    if (response.config.responseType === 'blob') {
      return response
    }

    const res = response.data
    if (res.code !== 0 && res.code !== 200) {
      const extracted = extractApiMessage(res, '请求失败')

      if (res.code === 401 || extracted.code === 401 || extracted.bizCode === 'UNAUTHORIZED') {
        handleUnauthorized()
      }

      notifyErrorByPayload(extracted, extracted.code ?? res.code)

      const err = new Error(extracted.message) as Error & {
        response?: { data: ApiResponse<unknown>; status: number }
      }
      err.response = {
        data: res,
        status: extracted.code ?? res.code,
      }
      return Promise.reject(err)
    }

    return response
  },
  (error: AxiosError<ApiResponse<unknown>>) => {
    if (!error.response) {
      ElMessage.error(buildRecoverableMessage('网络请求失败'))
      return Promise.reject(error)
    }

    const status = Number(error.response.status)
    const extracted = extractApiMessage(error.response.data, status === 500 ? '服务器内部错误' : '请求失败')

    if (status === 401 || extracted.code === 401 || extracted.bizCode === 'UNAUTHORIZED') {
      handleUnauthorized()
      ElMessage.error(buildRecoverableMessage('登录已过期，请重新登录', status, extracted.traceId))
      return Promise.reject(error)
    }

    if (status === 403 || extracted.code === 403 || extracted.bizCode === 'FORBIDDEN') {
      const token = localStorage.getItem('token')
      if (!token) {
        localStorage.removeItem('user')
        router.push('/login')
        ElMessage.error(buildRecoverableMessage('请先登录', status, extracted.traceId))
      } else {
        ElMessage.error(buildRecoverableMessage('当前账号无权限访问该资源', status, extracted.traceId))
      }
      return Promise.reject(error)
    }

    notifyErrorByPayload(extracted, status)
    return Promise.reject(error)
  },
)

export default service
