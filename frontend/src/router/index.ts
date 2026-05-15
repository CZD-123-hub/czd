import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      redirect: '/chat',
    },
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/Auth/LoginView.vue'),
      meta: { requiresAuth: false },
    },
    {
      path: '/register',
      name: 'register',
      component: () => import('@/views/Auth/RegisterView.vue'),
      meta: { requiresAuth: false },
    },
    {
      path: '/',
      component: () => import('@/components/layout/AppLayout.vue'),
      meta: { requiresAuth: true },
      children: [
        {
          path: 'chat',
          name: 'chat',
          component: () => import('@/views/Chat/ChatView.vue'),
          meta: { title: '智能问答' },
        },
        {
          path: 'graph',
          name: 'graph',
          component: () => import('@/views/KnowledgeGraph/KnowledgeGraphView.vue'),
          meta: { title: '知识图谱' },
        },
        {
          path: 'path',
          name: 'path',
          component: () => import('@/views/LearningPath/LearningPathView.vue'),
          meta: { title: '学习路径' },
        },
        {
          path: 'learning-studio',
          name: 'learning-studio',
          component: () => import('@/views/LearningStudio/LearningStudioView.vue'),
          meta: { title: '学习看台' },
        },
        {
          path: 'practice',
          name: 'practice',
          component: () => import('@/views/PracticeTest/PracticeTestView.vue'),
          meta: { title: '练与测' },
        },
        {
          path: 'snippets',
          name: 'snippets',
          component: () => import('@/views/CodeSnippets/CodeSnippetsView.vue'),
          meta: { title: '代码片段' },
        },
        {
          path: 'documents',
          name: 'documents',
          component: () => import('@/views/KnowledgeDoc/KnowledgeDocView.vue'),
          meta: { title: '知识文档' },
        },
        {
          path: 'dashboard',
          name: 'dashboard',
          component: () => import('@/views/Dashboard/DashboardView.vue'),
          meta: { title: '学习看板' },
        },
        {
          path: 'profile',
          name: 'profile',
          component: () => import('@/views/Profile/ProfileView.vue'),
          meta: { title: '个人中心' },
        },
      ],
    },
  ],
})

router.beforeEach((to) => {
  const token = localStorage.getItem('token')

  if (to.meta.requiresAuth !== false && !token) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }

  if ((to.name === 'login' || to.name === 'register') && token) {
    return { name: 'chat' }
  }

  return true
})

export default router
