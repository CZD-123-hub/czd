import { defineStore } from 'pinia'
import { ref } from 'vue'
import request from '@/api/request'
import { ElMessage } from 'element-plus'
import type { LearningPath } from '@/types'

export const usePathStore = defineStore('path', () => {
  const paths = ref<LearningPath[]>([])
  const currentPath = ref<LearningPath | null>(null)
  const loading = ref(false)

  // 加载学习路径列表
  async function loadPaths() {
    loading.value = true
    try {
      const res = await request.get('/path/list')
      paths.value = res.data.data || []
    } catch (error) {
      console.error('加载学习路径失败:', error)
      ElMessage.error('加载学习路径失败')
    } finally {
      loading.value = false
    }
  }

  // 生成学习路径
  async function generatePath(target: string, knownKnowledgeIds: string[]) {
    loading.value = true
    try {
      const res = await request.post('/path/generate', { 
        target, 
        knownKnowledgeIds 
      })
      const newPath = res.data.data
      // 添加到列表头部
      paths.value.unshift(newPath)
      currentPath.value = newPath
      ElMessage.success('学习路径生成成功')
      return newPath
    } catch (error: any) {
      const message = error.response?.data?.message || '生成失败'
      ElMessage.error(message)
      throw error
    } finally {
      loading.value = false
    }
  }

  // 更新节点状态
  async function updateNodeStatus(pathId: number, nodeId: number, status: 'todo' | 'doing' | 'done' | 'skipped') {
    try {
      await request.put(`/path/node/${nodeId}/status`, { status })
      
      // 更新列表中的路径节点状态
      const path = paths.value.find(p => p.id === pathId)
      if (path && path.nodes) {
        const node = path.nodes.find(n => n.id === nodeId)
        if (node) {
          node.status = status
        }
      }
      
      // 更新当前选中的路径节点状态
      if (currentPath.value?.id === pathId && currentPath.value.nodes) {
        const node = currentPath.value.nodes.find(n => n.id === nodeId)
        if (node) {
          node.status = status
        }
      }
      
      ElMessage.success('状态更新成功')
    } catch (error) {
      console.error('更新节点状态失败:', error)
      ElMessage.error('状态更新失败')
    }
  }

  // 删除学习路径
  async function deletePath(pathId: number) {
    try {
      await request.delete(`/path/${pathId}`)
      
      // 重新加载列表
      await loadPaths()
      
      // 如果当前选中的路径被删除，清空当前路径
      if (currentPath.value?.id === pathId) {
        currentPath.value = null
      }
      
      ElMessage.success('删除成功')
    } catch (error) {
      console.error('删除学习路径失败:', error)
      ElMessage.error('删除失败')
    }
  }

  // 选择学习路径
  function selectPath(path: LearningPath) {
    currentPath.value = path
  }

  // 清空当前路径
  function clearCurrentPath() {
    currentPath.value = null
  }

  return {
    paths,
    currentPath,
    loading,
    loadPaths,
    generatePath,
    updateNodeStatus,
    deletePath,
    selectPath,
    clearCurrentPath,
  }
})