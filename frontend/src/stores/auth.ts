import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import * as authApi from '@/api/auth'
import type { UserInfo, LoginForm, RegisterForm } from '@/types'
import { ElMessage } from 'element-plus'
import router from '@/router'

export const useAuthStore = defineStore('auth', () => {
  const user = ref<UserInfo | null>(null)
  const token = ref<string>('')

  const isAuthenticated = computed(() => !!token.value)

  function initFromStorage() {
    const storedToken = localStorage.getItem('token')
    const storedUser = localStorage.getItem('user')
    if (storedToken) {
      token.value = storedToken
    }
    if (storedUser) {
      try {
        user.value = JSON.parse(storedUser)
      } catch {
        user.value = null
      }
    }
  }

  async function login(form: LoginForm) {
    const res = await authApi.login(form)
    const data = res.data.data
    token.value = data.token
    user.value = data.user
    localStorage.setItem('token', data.token)
    localStorage.setItem('user', JSON.stringify(data.user))
    router.push('/chat')
  }

  async function register(form: RegisterForm) {
    const res = await authApi.register(form)
    const data = res.data.data
    token.value = data.token
    user.value = data.user
    localStorage.setItem('token', data.token)
    localStorage.setItem('user', JSON.stringify(data.user))
    router.push('/chat')
  }

  function updateUser(newUser: UserInfo) {
    user.value = newUser
    localStorage.setItem('user', JSON.stringify(newUser))
  }

  async function logout() {
    try {
      await authApi.logout()
    } catch {
      // Ignore logout API errors
    }
    token.value = ''
    user.value = null
    localStorage.removeItem('token')
    localStorage.removeItem('user')
    router.push('/login')
  }

  async function uploadAvatar(file: File) {
    const res = await authApi.uploadAvatar(file)
    const updatedUser = res.data.data
    updateUser(updatedUser)
    ElMessage.success('头像上传成功')
    return updatedUser
  }

  return {
    user,
    token,
    isAuthenticated,
    initFromStorage,
    login,
    register,
    logout,
    updateUser,
    uploadAvatar,
  }
})