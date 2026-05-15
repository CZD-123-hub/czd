import request from './request'
import type { ApiResponse, LoginForm, RegisterForm, TokenInfo, UserProfileSummary } from '@/types'

export function login(data: LoginForm) {
  return request.post<ApiResponse<TokenInfo>>('/auth/login', data)
}

export function register(data: RegisterForm) {
  return request.post<ApiResponse<TokenInfo>>('/auth/register', data)
}

export function logout() {
  return request.post<ApiResponse<null>>('/auth/logout')
}

export function getUserInfo() {
  return request.get<ApiResponse<TokenInfo['user']>>('/auth/me')
}

export function getProfileSummary() {
  return request.get<ApiResponse<UserProfileSummary>>('/auth/profile-summary')
}

export function uploadAvatar(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post<ApiResponse<TokenInfo['user']>>('/auth/avatar', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}
