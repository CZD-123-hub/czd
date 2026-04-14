<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import hljs from 'highlight.js'
import 'highlight.js/styles/github.css'
import { ElMessage } from 'element-plus'
import { DocumentCopy } from '@element-plus/icons-vue'

const props = withDefaults(
  defineProps<{
    code: string
    language?: string
  }>(),
  {
    language: 'plaintext',
  },
)

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
    await navigator.clipboard.writeText(props.code)
    ElMessage.success('已复制到剪贴板')
  } catch {
    ElMessage.error('复制失败')
  }
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
}

.copy-btn {
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