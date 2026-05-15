import request from './request'
import type { ApiResponse, ExamResult, PracticeAnswerResult, PracticeSession } from '@/types'

export interface PracticeGeneratePayload {
  pathId?: number
  questionCount?: number
}

export interface PracticeAnswerPayload {
  questionId: number
  answer: string
}

export interface ExamGeneratePayload {
  pathId?: number
  questionCount?: number
  durationMinutes?: number
}

export interface ExamSubmitPayload {
  answers: Array<{
    questionId: number
    answer: string
  }>
}

export const practiceApi = {
  generate: (payload: PracticeGeneratePayload) =>
    request.post<ApiResponse<PracticeSession>>('/practice/sessions/generate', payload),
  latest: () => request.get<ApiResponse<PracticeSession | null>>('/practice/sessions/latest'),
  generateExam: (payload: ExamGeneratePayload) =>
    request.post<ApiResponse<PracticeSession>>('/practice/exams/generate', payload),
  latestExam: () => request.get<ApiResponse<PracticeSession | null>>('/practice/exams/latest'),
  detail: (sessionId: number) => request.get<ApiResponse<PracticeSession>>(`/practice/sessions/${sessionId}`),
  answer: (sessionId: number, payload: PracticeAnswerPayload) =>
    request.post<ApiResponse<PracticeAnswerResult>>(`/practice/sessions/${sessionId}/answer`, payload),
  submitExam: (sessionId: number, payload: ExamSubmitPayload) =>
    request.post<ApiResponse<ExamResult>>(
      `/practice/exams/${sessionId}/submit`,
      payload,
    ),
}
