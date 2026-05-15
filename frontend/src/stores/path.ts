import { defineStore } from 'pinia'
import { ref } from 'vue'
import * as pathApi from '@/api/path'
import { ElMessage } from 'element-plus'
import type { LearningPath, NodeStatus, PathStatus } from '@/types'

export const usePathStore = defineStore('path', () => {
  const paths = ref<LearningPath[]>([])
  const currentPath = ref<LearningPath | null>(null)
  const loading = ref(false)
  const loadError = ref('')

  function computePathStatus(path: LearningPath): PathStatus {
    const nodes = path.nodes || []
    if (nodes.length === 0) return 'active'
    const allFinished = nodes.every((n) => n.status === 'done' || n.status === 'skipped')
    const anyStarted = nodes.some((n) => n.status === 'doing' || n.status === 'done' || n.status === 'skipped')
    if (allFinished) return 'completed'
    if (anyStarted) return 'in_progress'
    return 'active'
  }

  function applyNodeStatus(path: LearningPath, nodeId: number, status: NodeStatus) {
    const node = path.nodes?.find((n) => n.id === nodeId)
    if (!node) return
    node.status = status
    path.status = computePathStatus(path)
  }

  async function loadPaths() {
    loading.value = true
    loadError.value = ''
    try {
      const res = await pathApi.listPaths()
      paths.value = res.data.data || []
      if (currentPath.value) {
        currentPath.value = paths.value.find((p) => p.id === currentPath.value!.id) || null
      }
    } catch {
      loadError.value = '学习路径加载失败，请稍后重试'
      ElMessage.error(loadError.value)
      paths.value = []
    } finally {
      loading.value = false
    }
  }

  async function generatePath(target: string, knownKnowledgeIds: string[]) {
    loading.value = true
    try {
      const res = await pathApi.generatePath(target, knownKnowledgeIds)
      const newPath = res.data.data
      paths.value.unshift(newPath)
      currentPath.value = newPath
      ElMessage.success('学习路径生成成功')
      return newPath
    } finally {
      loading.value = false
    }
  }

  async function updateNodeStatus(pathId: number, nodeId: number, status: NodeStatus) {
    try {
      await pathApi.updateNodeStatus(nodeId, status)

      const listPath = paths.value.find((p) => p.id === pathId)
      if (listPath) {
        applyNodeStatus(listPath, nodeId, status)
      }

      if (currentPath.value?.id === pathId) {
        applyNodeStatus(currentPath.value, nodeId, status)
      }

      ElMessage.success('状态更新成功')
    } catch {
      // unified error message is handled by request interceptor
    }
  }

  async function deletePath(pathId: number) {
    try {
      await pathApi.deletePath(pathId)
      paths.value = paths.value.filter((p) => p.id !== pathId)
      if (currentPath.value?.id === pathId) {
        currentPath.value = null
      }
      ElMessage.success('删除成功')
    } catch {
      // unified error message is handled by request interceptor
    }
  }

  function selectPath(path: LearningPath) {
    currentPath.value = path
  }

  function clearCurrentPath() {
    currentPath.value = null
  }

  return {
    paths,
    currentPath,
    loading,
    loadError,
    loadPaths,
    generatePath,
    updateNodeStatus,
    deletePath,
    selectPath,
    clearCurrentPath,
  }
})
