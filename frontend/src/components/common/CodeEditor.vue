<script setup lang="ts">
import { ref, watch, onMounted, onBeforeUnmount, shallowRef } from 'vue'
import loader from '@monaco-editor/loader'
import type * as Monaco from 'monaco-editor'

const props = withDefaults(
  defineProps<{
    modelValue: string
    language?: string
    readOnly?: boolean
    height?: string
    width?: string
  }>(),
  {
    language: 'javascript',
    readOnly: false,
    height: '400px',
    width: '100%',
  },
)

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

const editorContainer = ref<HTMLDivElement | null>(null)
const editor = shallowRef<Monaco.editor.IStandaloneCodeEditor | null>(null)
let monacoInstance: typeof Monaco | null = null

onMounted(async () => {
  // 配置 Monaco 使用国内 CDN
  loader.config({
    paths: {
      vs: 'https://cdn.jsdelivr.net/npm/monaco-editor@0.45.0/min/vs'
    }
  })
  
  monacoInstance = await loader.init()
  if (!monacoInstance) return

  if (!editorContainer.value) return

  editor.value = monacoInstance.editor.create(editorContainer.value, {
    value: props.modelValue,
    language: props.language,
    readOnly: props.readOnly,
    theme: 'vs',
    automaticLayout: true,
    minimap: { enabled: false },
    scrollBeyondLastLine: false,
    fontSize: 14,
    lineNumbers: 'on',
    tabSize: 2,
    wordWrap: 'on',
    renderWhitespace: 'selection',
    padding: { top: 12, bottom: 12 },
    fontFamily: 'Consolas, "Courier New", monospace',
  })

  editor.value.onDidChangeModelContent(() => {
    const value = editor.value?.getValue() || ''
    emit('update:modelValue', value)
  })
})

watch(
  () => props.modelValue,
  (newVal) => {
    if (editor.value && editor.value.getValue() !== newVal) {
      editor.value.setValue(newVal)
    }
  },
)

watch(
  () => props.language,
  (newLang) => {
    if (editor.value && monacoInstance) {
      const model = editor.value.getModel()
      if (model) {
        monacoInstance.editor.setModelLanguage(model, newLang)
      }
    }
  },
)

watch(
  () => props.readOnly,
  (newVal) => {
    if (editor.value) {
      editor.value.updateOptions({ readOnly: newVal })
    }
  },
)

onBeforeUnmount(() => {
  editor.value?.dispose()
})
</script>

<template>
  <div ref="editorContainer" class="code-editor" :style="{ height: props.height, width: props.width }" />
</template>

<style lang="scss" scoped>
.code-editor {
  width: 100%;
  min-height: 400px;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-sm);
  overflow: hidden;
}
</style>
