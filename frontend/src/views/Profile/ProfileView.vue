<script setup lang="ts">
import { ref, computed } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { User, Camera } from '@element-plus/icons-vue'

const authStore = useAuthStore()
const uploading = ref(false)
const fileInput = ref<HTMLInputElement>()

const userInfo = computed(() => authStore.user)

function getLevelLabel(level: string): string {
  const map: Record<string, string> = {
    beginner: '初学者',
    intermediate: '中级',
    advanced: '高级',
    expert: '专家',
  }
  return map[level] || level || '初学者'
}

function getLevelType(level: string): 'success' | 'warning' | 'danger' | 'info' {
  const map: Record<string, 'success' | 'warning' | 'danger' | 'info'> = {
    beginner: 'info',
    intermediate: 'success',
    advanced: 'warning',
    expert: 'danger',
  }
  return map[level] || 'info'
}

function triggerUpload() {
  fileInput.value?.click()
}

async function handleFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return

  const allowedTypes = ['image/jpeg', 'image/png', 'image/gif', 'image/webp']
  if (!allowedTypes.includes(file.type)) {
    import('element-plus').then(({ ElMessage }) => {
      ElMessage.error('仅支持 JPG、PNG、GIF、WebP 格式的图片')
    })
    return
  }

  if (file.size > 5 * 1024 * 1024) {
    import('element-plus').then(({ ElMessage }) => {
      ElMessage.error('图片大小不能超过 5MB')
    })
    return
  }

  uploading.value = true
  try {
    await authStore.uploadAvatar(file)
  } catch {
    // error handled by store/interceptor
  } finally {
    uploading.value = false
    input.value = ''
  }
}
</script>

<template>
  <div class="profile-view">
    <div class="profile-container">
      <div class="profile-card">
        <div class="profile-header">
          <div class="avatar-section">
            <div class="avatar-wrapper" @click="triggerUpload">
              <el-avatar :size="80" :src="userInfo?.avatar || undefined" class="profile-avatar">
                <el-icon :size="36"><User /></el-icon>
              </el-avatar>
              <div class="avatar-overlay" :class="{ uploading }">
                <el-icon v-if="!uploading" :size="20"><Camera /></el-icon>
                <el-icon v-else :size="20" class="is-loading"><Camera /></el-icon>
              </div>
              <input
                ref="fileInput"
                type="file"
                accept="image/jpeg,image/png,image/gif,image/webp"
                style="display: none"
                @change="handleFileChange"
              />
            </div>
            <div class="user-basic">
              <h2>{{ userInfo?.username || '用户' }}</h2>
              <el-tag :type="getLevelType(userInfo?.level || '')">
                {{ getLevelLabel(userInfo?.level || '') }}
              </el-tag>
            </div>
          </div>
        </div>

        <div class="profile-body">
          <h3 class="section-title">基本信息</h3>
          <el-descriptions :column="1" border>
            <el-descriptions-item label="用户ID">
              {{ userInfo?.id || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="用户名">
              {{ userInfo?.username || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="邮箱">
              {{ userInfo?.email || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="等级">
              <el-tag :type="getLevelType(userInfo?.level || '')" size="small">
                {{ getLevelLabel(userInfo?.level || '') }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="注册时间">
              {{ userInfo?.createdAt ? new Date(userInfo.createdAt).toLocaleDateString('zh-CN') : '-' }}
            </el-descriptions-item>
          </el-descriptions>

          <div class="profile-tips">
            <el-alert
              title="个人资料功能说明"
              type="info"
              :closable="false"
              show-icon
            >
              <template #default>
                点击头像即可上传自定义头像，支持 JPG、PNG、GIF、WebP 格式，大小不超过 5MB。
              </template>
            </el-alert>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.profile-view {
  height: 100%;
  padding: 24px;
  overflow-y: auto;
}

.profile-container {
  max-width: 600px;
  margin: 0 auto;
}

.profile-card {
  background: #fff;
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
  overflow: hidden;
}

.profile-header {
  padding: 32px;
  background: linear-gradient(135deg, #2f6bff 0%, #5a8bff 100%);
}

.avatar-section {
  display: flex;
  align-items: center;
  gap: 20px;
}

.avatar-wrapper {
  position: relative;
  cursor: pointer;
  border-radius: 50%;

  &:hover .avatar-overlay {
    opacity: 1;
  }
}

.avatar-overlay {
  position: absolute;
  top: 0;
  left: 0;
  width: 80px;
  height: 80px;
  border-radius: 50%;
  background-color: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  opacity: 0;
  transition: opacity 0.3s;

  &.uploading {
    opacity: 1;
  }
}

.profile-avatar {
  border: 3px solid rgba(255, 255, 255, 0.3);
  background-color: rgba(255, 255, 255, 0.2);
  color: #fff;
}

.user-basic {
  h2 {
    font-size: 22px;
    font-weight: 600;
    color: #fff;
    margin-bottom: 8px;
  }

  .el-tag {
    border: none;
  }
}

.profile-body {
  padding: 24px 32px 32px;
}

.section-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 16px;
}

.profile-tips {
  margin-top: 24px;
}
</style>
