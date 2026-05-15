import { defineStore } from 'pinia'
import { ref, reactive } from 'vue'
import * as snippetApi from '@/api/snippet'
import type { CodeSnippet } from '@/types'
import { ElMessage } from 'element-plus'

export const useSnippetStore = defineStore('snippet', () => {
  const snippets = ref<CodeSnippet[]>([])
  const total = ref(0)
  const loading = ref(false)
  const filters = reactive({
    page: 1,
    size: 20,
    keyword: '',
    language: '',
    tag: '',
  })

  async function loadSnippets() {
    loading.value = true
    try {
      const res = await snippetApi.listSnippets({
        page: filters.page,
        size: filters.size,
        keyword: filters.keyword || undefined,
        language: filters.language || undefined,
        tag: filters.tag || undefined,
      })
      snippets.value = res.data.data.records
      total.value = res.data.data.total
    } catch {
      snippets.value = []
      total.value = 0
    } finally {
      loading.value = false
    }
  }

  async function runMutation(action: () => Promise<unknown>, successMessage: string): Promise<boolean> {
    loading.value = true
    try {
      await action()
      ElMessage.success(successMessage)
      await loadSnippets()
      return true
    } catch {
      // unified error message is handled by request interceptor
      return false
    } finally {
      loading.value = false
    }
  }

  async function createSnippet(data: Partial<CodeSnippet>) {
    return runMutation(() => snippetApi.createSnippet(data), '代码片段创建成功')
  }

  async function updateSnippet(id: number, data: Partial<CodeSnippet>) {
    return runMutation(() => snippetApi.updateSnippet(id, data), '更新成功')
  }

  async function deleteSnippet(id: number) {
    return runMutation(() => snippetApi.deleteSnippet(id), '删除成功')
  }

  async function markSnippetUsed(id: number) {
    try {
      const res = await snippetApi.markSnippetUsed(id)
      const updated = res.data.data
      const local = snippets.value.find((item) => item.id === id)
      if (local) {
        local.useCount = updated.useCount
        local.updatedAt = updated.updatedAt
      }
    } catch {
      // Ignore tracking failures to avoid interrupting user actions.
    }
  }

  async function exportAll() {
    try {
      const res = await snippetApi.exportAll()
      const blob = new Blob([res.data as BlobPart], { type: 'application/json' })
      const url = URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = url
      link.download = 'snippets-export.json'
      link.click()
      URL.revokeObjectURL(url)
      ElMessage.success('导出成功')
      return true
    } catch {
      return false
    }
  }

  async function importFile(file: File) {
    loading.value = true
    try {
      const res = await snippetApi.importFile(file)
      const result = res.data.data
      ElMessage.success(`导入完成：成功 ${result.successCount} 条，失败 ${result.failCount} 条`)
      await loadSnippets()
      return true
    } catch {
      return false
    } finally {
      loading.value = false
    }
  }

  function resetFilters() {
    filters.page = 1
    filters.keyword = ''
    filters.language = ''
    filters.tag = ''
  }

  return {
    snippets,
    total,
    loading,
    filters,
    loadSnippets,
    createSnippet,
    updateSnippet,
    deleteSnippet,
    markSnippetUsed,
    exportAll,
    importFile,
    resetFilters,
  }
})
