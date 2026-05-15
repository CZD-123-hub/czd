<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import hljs from 'highlight.js/lib/core'
import bash from 'highlight.js/lib/languages/bash'
import c from 'highlight.js/lib/languages/c'
import cpp from 'highlight.js/lib/languages/cpp'
import csharp from 'highlight.js/lib/languages/csharp'
import css from 'highlight.js/lib/languages/css'
import go from 'highlight.js/lib/languages/go'
import java from 'highlight.js/lib/languages/java'
import javascript from 'highlight.js/lib/languages/javascript'
import json from 'highlight.js/lib/languages/json'
import kotlin from 'highlight.js/lib/languages/kotlin'
import markdown from 'highlight.js/lib/languages/markdown'
import php from 'highlight.js/lib/languages/php'
import plaintext from 'highlight.js/lib/languages/plaintext'
import python from 'highlight.js/lib/languages/python'
import ruby from 'highlight.js/lib/languages/ruby'
import rust from 'highlight.js/lib/languages/rust'
import scss from 'highlight.js/lib/languages/scss'
import shell from 'highlight.js/lib/languages/shell'
import sql from 'highlight.js/lib/languages/sql'
import swift from 'highlight.js/lib/languages/swift'
import typescript from 'highlight.js/lib/languages/typescript'
import xml from 'highlight.js/lib/languages/xml'
import yaml from 'highlight.js/lib/languages/yaml'
import 'highlight.js/styles/github.css'
import { ElMessage } from 'element-plus'
import { DocumentCopy, Plus } from '@element-plus/icons-vue'

hljs.registerLanguage('bash', bash)
hljs.registerLanguage('shell', shell)
hljs.registerLanguage('c', c)
hljs.registerLanguage('cpp', cpp)
hljs.registerLanguage('csharp', csharp)
hljs.registerLanguage('css', css)
hljs.registerLanguage('go', go)
hljs.registerLanguage('java', java)
hljs.registerLanguage('javascript', javascript)
hljs.registerLanguage('js', javascript)
hljs.registerLanguage('json', json)
hljs.registerLanguage('kotlin', kotlin)
hljs.registerLanguage('markdown', markdown)
hljs.registerLanguage('md', markdown)
hljs.registerLanguage('php', php)
hljs.registerLanguage('plaintext', plaintext)
hljs.registerLanguage('python', python)
hljs.registerLanguage('py', python)
hljs.registerLanguage('ruby', ruby)
hljs.registerLanguage('rust', rust)
hljs.registerLanguage('scss', scss)
hljs.registerLanguage('sql', sql)
hljs.registerLanguage('swift', swift)
hljs.registerLanguage('typescript', typescript)
hljs.registerLanguage('ts', typescript)
hljs.registerLanguage('html', xml)
hljs.registerLanguage('xml', xml)
hljs.registerLanguage('yaml', yaml)
hljs.registerLanguage('yml', yaml)

const props = withDefaults(
  defineProps<{
    code: string
    language?: string
    copyText?: string
    savable?: boolean
  }>(),
  {
    language: 'plaintext',
    copyText: undefined,
    savable: false,
  },
)

const emit = defineEmits<{
  copied: []
  save: []
}>()

const codeRef = ref<HTMLElement | null>(null)

const highlightedCode = computed(() => {
  if (props.language && hljs.getLanguage(props.language)) {
    try {
      return hljs.highlight(props.code, { language: props.language }).value
    } catch {
      return hljs.highlightAuto(props.code).value
    }
  }
  return hljs.highlightAuto(props.code).value
})

const displayLanguage = computed(() => {
  return props.language || 'plaintext'
})

async function copyCode() {
  try {
    await navigator.clipboard.writeText(props.copyText ?? props.code)
    ElMessage.success('复制成功')
    emit('copied')
  } catch {
    ElMessage.error('复制失败')
  }
}

function saveCode() {
  emit('save')
}

onMounted(() => {
  // Already highlighted via computed property
})
</script>

<template>
  <div class="code-block">
    <div class="code-header">
      <span class="code-language">{{ displayLanguage }}</span>
      <el-button
        v-if="savable"
        :icon="Plus"
        text
        size="small"
        class="save-btn"
        @click="saveCode"
      >
        保存片段
      </el-button>
      <el-button
        :icon="DocumentCopy"
        text
        size="small"
        class="copy-btn"
        @click="copyCode"
      >
        复制
      </el-button>
    </div>
    <pre class="code-content"><code ref="codeRef" v-html="highlightedCode"></code></pre>
  </div>
</template>

<style lang="scss" scoped>
.code-block {
  border-radius: var(--radius-md);
  overflow: hidden;
  margin: 8px 0;
  border: 1px solid var(--border-lighter);
}

.code-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 12px;
  background-color: #f6f8fa;
  border-bottom: 1px solid var(--border-lighter);
}

.code-language {
  font-size: 12px;
  color: var(--text-secondary);
  text-transform: uppercase;
  font-weight: 500;
  margin-right: auto;
}

.copy-btn,
.save-btn {
  font-size: 12px;
  color: var(--text-secondary);

  &:hover {
    color: var(--primary-color);
  }
}

.code-content {
  margin: 0;
  padding: 12px 16px;
  overflow-x: auto;
  background-color: #f8f9fa;
  font-size: 13px;
  line-height: 1.6;

  code {
    display: block;
    white-space: pre-wrap;
    word-break: break-word;
    font-family: 'SF Mono', 'Fira Code', 'Consolas', monospace;
    background: transparent;
  }
}
</style>

