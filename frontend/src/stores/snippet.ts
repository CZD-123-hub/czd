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

  async function createSnippet(data: Partial<CodeSnippet>) {
    loading.value = true
    try {
      await snippetApi.createSnippet(data)
      ElMessage.success('代码片段创建成功')
      await loadSnippets()
    } catch {
      ElMessage.error('创建失败')
    } finally {
      loading.value = false
    }
  }

  async function updateSnippet(id: number, data: Partial<CodeSnippet>) {
    loading.value = true
    try {
      await snippetApi.updateSnippet(id, data)
      ElMessage.success('更新成功')
      await loadSnippets()
    } catch {
      ElMessage.error('更新失败')
    } finally {
      loading.value = false
    }
  }

  async function deleteSnippet(id: number) {
    loading.value = true
    try {
      await snippetApi.deleteSnippet(id)
      ElMessage.success('删除成功')
      await loadSnippets()
    } catch {
      ElMessage.error('删除失败')
    } finally {
      loading.value = false
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
    } catch {
      ElMessage.error('导出失败')
    }
  }

  async function importFile(file: File) {
    loading.value = true
    try {
      await snippetApi.importFile(file)
      ElMessage.success('导入成功')
      await loadSnippets()
    } catch {
      ElMessage.error('导入失败')
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
    exportAll,
    importFile,
    resetFilters,
  }
})