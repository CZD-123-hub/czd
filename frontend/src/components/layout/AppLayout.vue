<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import {
  ChatDotRound,
  Share,
  Guide,
  DocumentCopy,
  DataAnalysis,
  User,
  Fold,
  Expand,
  SwitchButton,
  Notebook,
  Operation,
  VideoPlay,
  EditPen,
} from '@element-plus/icons-vue'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

const isCollapsed = ref(false)
const isMobile = ref(window.innerWidth < 768)
const mobileMenuVisible = ref(false)

const menuItems = [
  { path: '/chat', icon: ChatDotRound, title: '智能问答', mobileTitle: '问答' },
  { path: '/graph', icon: Share, title: '知识图谱', mobileTitle: '图谱' },
  { path: '/path', icon: Guide, title: '学习路径', mobileTitle: '路径' },
  { path: '/learning-studio', icon: VideoPlay, title: '学习看台', mobileTitle: '看台' },
  { path: '/practice', icon: EditPen, title: '练与测', mobileTitle: '练测' },
  { path: '/snippets', icon: DocumentCopy, title: '代码片段', mobileTitle: '片段' },
  { path: '/documents', icon: Notebook, title: '知识文档', mobileTitle: '文档' },
  { path: '/dashboard', icon: DataAnalysis, title: '学习看板', mobileTitle: '看板' },
]

const pageTitleMap: Record<string, string> = {
  chat: '智能问答',
  graph: '知识图谱',
  path: '学习路径',
  'learning-studio': '学习看台',
  practice: '练与测',
  snippets: '代码片段',
  documents: '知识文档',
  dashboard: '学习看板',
  profile: '个人中心',
}

const activeMenu = computed(() => route.path)
const mobileTabItems = computed(() => menuItems)

const currentPageTitle = computed(() => {
  const name = String(route.name ?? '')
  if (name && pageTitleMap[name]) return pageTitleMap[name]
  return (route.meta.title as string) || '智能编程助手'
})

function handleResize() {
  isMobile.value = window.innerWidth < 768
  if (isMobile.value) {
    isCollapsed.value = true
    return
  }
  mobileMenuVisible.value = false
}

onMounted(() => {
  handleResize()
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
})

function handleMenuSelect(path: string) {
  if (route.path !== path) {
    router.push(path)
  }
  if (isMobile.value) {
    mobileMenuVisible.value = false
  }
}

function toggleCollapse() {
  if (isMobile.value) {
    return
  }
  isCollapsed.value = !isCollapsed.value
}

function handleLogout() {
  authStore.logout()
}

function goProfile() {
  router.push('/profile')
  mobileMenuVisible.value = false
}

function openMobileMenu() {
  mobileMenuVisible.value = true
}
</script>

<template>
  <el-container class="app-layout" :class="{ mobile: isMobile }">
    <el-aside v-if="!isMobile" :width="isCollapsed ? '76px' : '240px'" class="app-sidebar">
      <div class="sidebar-header">
        <div v-if="!isCollapsed" class="logo-text">
          <el-icon :size="24" color="#78a7ff"><ChatDotRound /></el-icon>
          <span class="logo-title">智能编程助手</span>
        </div>
        <div v-else class="logo-icon">
          <el-icon :size="24" color="#78a7ff"><ChatDotRound /></el-icon>
        </div>
      </div>

      <el-menu
        :default-active="activeMenu"
        :collapse="isCollapsed"
        :collapse-transition="true"
        class="sidebar-menu"
        @select="handleMenuSelect"
      >
        <el-menu-item v-for="item in menuItems" :key="item.path" :index="item.path">
          <el-icon><component :is="item.icon" /></el-icon>
          <template #title>{{ item.title }}</template>
        </el-menu-item>
      </el-menu>

      <div class="sidebar-footer">
        <el-button
          :icon="isCollapsed ? Expand : Fold"
          text
          class="collapse-btn"
          @click="toggleCollapse"
        />
      </div>
    </el-aside>

    <el-drawer
      v-if="isMobile"
      v-model="mobileMenuVisible"
      direction="ltr"
      size="260px"
      :with-header="false"
      class="mobile-menu-drawer"
    >
      <div class="mobile-menu-shell">
        <div class="mobile-menu-header">
          <el-icon :size="22" color="#78a7ff"><ChatDotRound /></el-icon>
          <span>智能编程助手</span>
        </div>
        <el-menu :default-active="activeMenu" class="mobile-menu-list" @select="handleMenuSelect">
          <el-menu-item v-for="item in menuItems" :key="item.path" :index="item.path">
            <el-icon><component :is="item.icon" /></el-icon>
            <span>{{ item.title }}</span>
          </el-menu-item>
        </el-menu>
      </div>
    </el-drawer>

    <el-container class="main-container" :class="{ 'with-tabbar': isMobile }">
      <el-header class="app-header">
        <div class="header-left">
          <el-button
            v-if="isMobile"
            :icon="Operation"
            text
            class="mobile-menu-btn"
            aria-label="打开导航菜单"
            @click="openMobileMenu"
          />
          <div class="title-wrap">
            <p class="page-eyebrow">学习工作台</p>
            <h3 class="page-title">{{ currentPageTitle }}</h3>
          </div>
        </div>
        <div class="header-right">
          <el-dropdown trigger="click" @command="(cmd: string) => cmd === 'profile' ? goProfile() : handleLogout()">
            <div class="user-info">
              <el-avatar :size="32" :src="authStore.user?.avatar || undefined">
                <el-icon :size="16"><User /></el-icon>
              </el-avatar>
              <span v-if="!isMobile" class="username">{{ authStore.user?.username || '用户' }}</span>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">
                  <el-icon><User /></el-icon>
                  个人中心
                </el-dropdown-item>
                <el-dropdown-item command="logout" divided>
                  <el-icon><SwitchButton /></el-icon>
                  退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <el-main class="app-main">
        <router-view />
      </el-main>

      <nav v-if="isMobile" class="mobile-tabbar" aria-label="主导航">
        <button
          v-for="item in mobileTabItems"
          :key="`mobile-${item.path}`"
          type="button"
          class="tab-item"
          :class="{ active: activeMenu === item.path }"
          :aria-label="item.title"
          @click="handleMenuSelect(item.path)"
        >
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.mobileTitle }}</span>
        </button>
      </nav>
    </el-container>
  </el-container>
</template>

<style lang="scss" scoped>
.app-layout {
  height: 100vh;
  overflow: hidden;
  background: transparent;
}

.app-sidebar {
  background: linear-gradient(175deg, #102347 0%, #183668 38%, #2456a7 100%);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  transition: width 0.28s ease;
  border-right: 1px solid rgba(255, 255, 255, 0.12);
  box-shadow: inset -1px 0 0 rgba(255, 255, 255, 0.08);
}

.sidebar-header {
  height: var(--header-height);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0 16px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
}

.logo-text {
  display: flex;
  align-items: center;
  gap: 10px;
  white-space: nowrap;
}

.logo-title {
  color: #f3fcff;
  font-size: 16px;
  font-weight: 700;
  letter-spacing: 0.02em;
}

.logo-icon {
  display: flex;
  align-items: center;
  justify-content: center;
}

.sidebar-menu {
  flex: 1;
  border-right: none;
  overflow-y: auto;
  background: transparent;

  :deep(.el-menu) {
    border-right: none;
    background: transparent;
  }

  :deep(.el-menu-item) {
    margin: 4px 10px;
    border-radius: 12px;
    color: rgba(236, 248, 250, 0.78);
    height: 44px;
    transition: all 0.2s ease;
  }

  :deep(.el-menu-item:hover) {
    background: rgba(255, 255, 255, 0.08);
    color: #f8ffff;
  }

  :deep(.el-menu-item.is-active) {
    background: linear-gradient(132deg, rgba(101, 150, 255, 0.34), rgba(127, 177, 255, 0.22));
    color: #f8ffff;
    box-shadow: inset 0 0 0 1px rgba(170, 201, 255, 0.3);
  }

  :deep(.el-menu-item .el-icon) {
    color: inherit;
  }

  &:not(.el-menu--collapse) {
    width: 240px;
  }
}

.sidebar-footer {
  padding: 10px;
  text-align: center;
  border-top: 1px solid rgba(255, 255, 255, 0.1);
}

.collapse-btn {
  color: rgba(228, 245, 249, 0.8);
  width: 100%;
  min-height: 36px;

  &:hover {
    color: #ffffff;
    background: rgba(255, 255, 255, 0.1);
  }
}

.main-container {
  overflow: hidden;
  background: transparent;
  position: relative;
}

.main-container.with-tabbar {
  padding-bottom: calc(68px + env(safe-area-inset-bottom, 0px));
}

.app-header {
  height: var(--header-height);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 26px;
  background: rgba(255, 255, 255, 0.65);
  border-bottom: 1px solid rgba(215, 227, 234, 0.78);
  box-shadow: var(--shadow-sm);
  backdrop-filter: blur(8px);
}

.header-left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.title-wrap {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.page-eyebrow {
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: rgba(47, 107, 255, 0.78);
}

.page-title {
  font-size: 17px;
  font-weight: 700;
  color: var(--text-primary);
}

.header-right {
  display: flex;
  align-items: center;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
  padding: 6px 10px;
  border-radius: 12px;
  border: 1px solid transparent;
  transition: all 0.2s ease;

  &:hover {
    background-color: rgba(255, 255, 255, 0.85);
    border-color: var(--border-light);
  }
}

.username {
  font-size: 14px;
  color: var(--text-primary);
  font-weight: 600;
}

.app-main {
  padding: 18px;
  overflow: auto;
  background: transparent;
}

.mobile-menu-btn {
  color: var(--text-secondary);
  border-radius: 10px;
}

.mobile-menu-drawer {
  :deep(.el-drawer__body) {
    padding: 0;
    background: linear-gradient(175deg, #102347 0%, #183668 38%, #2456a7 100%);
  }
}

.mobile-menu-shell {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.mobile-menu-header {
  height: var(--header-height);
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 16px;
  color: #f3fcff;
  font-size: 15px;
  font-weight: 700;
  border-bottom: 1px solid rgba(255, 255, 255, 0.12);
}

.mobile-menu-list {
  flex: 1;
  border-right: none;
  background: transparent;

  :deep(.el-menu-item) {
    margin: 4px 10px;
    border-radius: 12px;
    color: rgba(236, 248, 250, 0.86);
    height: 44px;
  }

  :deep(.el-menu-item.is-active) {
    background: linear-gradient(132deg, rgba(101, 150, 255, 0.34), rgba(127, 177, 255, 0.22));
    color: #f8ffff;
  }
}

.mobile-tabbar {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: calc(68px + env(safe-area-inset-bottom, 0px));
  padding: 6px 8px calc(6px + env(safe-area-inset-bottom, 0px));
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 4px;
  border-top: 1px solid rgba(215, 227, 234, 0.82);
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(8px);
  z-index: 12;
}

.tab-item {
  border: none;
  background: transparent;
  border-radius: 10px;
  color: #6c7ea1;
  display: inline-flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 3px;
  font-size: 11px;
  font-weight: 600;
  line-height: 1;
  padding: 4px 2px;
  transition: all 0.18s ease;

  .el-icon {
    font-size: 16px;
  }

  &.active {
    color: #2f6bff;
    background: rgba(47, 107, 255, 0.12);
  }
}

@media (max-width: 768px) {
  .app-main {
    padding: 10px 10px 0;
  }

  .app-header {
    padding: 0 10px;
  }

  .page-eyebrow {
    display: none;
  }

  .user-info {
    padding: 4px;
  }
}
</style>
