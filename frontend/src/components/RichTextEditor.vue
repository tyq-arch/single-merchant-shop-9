<script setup>
import { onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'

import { uploadMedia, validateMedia } from '@/api/file'

/**
 * 富媒体描述编辑器（FR-009 / C-21）。
 *
 * 基于 contenteditable + execCommand 的轻量实现，不引入额外依赖：
 * - 支持加粗、斜体、下划线、标题、列表、清除格式
 * - 图片 / 视频均通过 /api/files/media 本地上传后内嵌，不接受外链
 * - 粘贴统一转纯文本，避免带入外部图片
 */
const props = defineProps({
  modelValue: { type: String, default: '' },
  placeholder: { type: String, default: '请输入商品描述，可插入图片或视频' },
  maxLength: { type: Number, default: 20000 },
})

const emit = defineEmits(['update:modelValue'])

const editorRef = ref()
const mediaInputRef = ref()
const uploading = ref(false)

onMounted(() => {
  if (editorRef.value) editorRef.value.innerHTML = props.modelValue || ''
})

watch(
  () => props.modelValue,
  (value) => {
    if (!editorRef.value) return
    if ((value || '') !== editorRef.value.innerHTML) {
      editorRef.value.innerHTML = value || ''
    }
  },
)

function emitValue() {
  if (!editorRef.value) return
  emit('update:modelValue', editorRef.value.innerHTML)
}

function exec(command, value = null) {
  if (!editorRef.value) return
  editorRef.value.focus()
  document.execCommand(command, false, value)
  emitValue()
}

function insertHtml(html) {
  if (!editorRef.value) return
  editorRef.value.focus()
  document.execCommand('insertHTML', false, html)
  emitValue()
}

function onInput() {
  const text = editorRef.value?.innerText || ''
  if (text.length > props.maxLength) {
    ElMessage.warning(`描述不能超过 ${props.maxLength} 个字符`)
  }
  emitValue()
}

/** 粘贴只保留纯文本，避免外链图片与不可控标签 */
function onPaste(event) {
  event.preventDefault()
  const text = event.clipboardData?.getData('text/plain') || ''
  document.execCommand('insertText', false, text)
  emitValue()
}

function pickMedia() {
  if (uploading.value) return
  mediaInputRef.value?.click()
}

async function onMedia(event) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (!file) return

  const error = validateMedia(file)
  if (error) {
    ElMessage.error(error)
    return
  }

  uploading.value = true
  try {
    const data = await uploadMedia(file)
    if (data.media_type === 'video') {
      insertHtml(`<video src="${data.url}" controls></video><p><br></p>`)
    } else {
      insertHtml(`<img src="${data.url}" alt="${file.name}">`)
    }
  } catch (err) {
    ElMessage.error(err.message)
  } finally {
    uploading.value = false
  }
}
</script>

<template>
  <div class="rich-editor">
    <div class="rich-editor__toolbar">
      <button type="button" title="加粗" @mousedown.prevent="exec('bold')"><strong>B</strong></button>
      <button type="button" title="斜体" @mousedown.prevent="exec('italic')"><em>I</em></button>
      <button type="button" title="下划线" @mousedown.prevent="exec('underline')"><u>U</u></button>
      <span class="rich-editor__divider" />
      <button type="button" title="标题" @mousedown.prevent="exec('formatBlock', 'h3')">H3</button>
      <button type="button" title="无序列表" @mousedown.prevent="exec('insertUnorderedList')">• 列表</button>
      <button type="button" title="有序列表" @mousedown.prevent="exec('insertOrderedList')">1. 列表</button>
      <span class="rich-editor__divider" />
      <button type="button" :disabled="uploading" @mousedown.prevent="pickMedia">
        {{ uploading ? '上传中…' : '插入图片/视频' }}
      </button>
      <button type="button" title="清除格式" @mousedown.prevent="exec('removeFormat')">清除格式</button>
    </div>

    <div
      ref="editorRef"
      class="rich-editor__content"
      contenteditable="true"
      role="textbox"
      aria-multiline="true"
      :data-placeholder="placeholder"
      @input="onInput"
      @paste="onPaste"
      @blur="emitValue"
    />

    <input
      ref="mediaInputRef"
      class="rich-editor__input"
      type="file"
      accept="image/jpeg,image/png,image/gif,image/webp,video/mp4,video/webm"
      @change="onMedia"
    />
  </div>
</template>

<style scoped>
.rich-editor {
  width: 100%;
  border: 1px solid var(--c-border);
  border-radius: var(--r-btn);
  background: var(--c-surface);
}

.rich-editor__toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px;
  padding: var(--sp-2);
  border-bottom: 1px solid var(--c-border);
  background: var(--c-muted-soft);
  border-radius: var(--r-btn) var(--r-btn) 0 0;
}

.rich-editor__toolbar button {
  min-width: 32px;
  height: 28px;
  padding: 0 8px;
  font-size: var(--fs-sm);
  color: var(--c-text-sub);
  background: var(--c-surface);
  border: 1px solid var(--c-border);
  border-radius: 4px;
  cursor: pointer;
}

.rich-editor__toolbar button:hover {
  color: var(--c-primary);
  border-color: var(--c-primary);
}

.rich-editor__toolbar button:disabled {
  opacity: 0.6;
  cursor: progress;
}

.rich-editor__divider {
  width: 1px;
  height: 18px;
  background: var(--c-border);
  margin: 0 4px;
}

.rich-editor__content {
  min-height: 180px;
  max-height: 480px;
  overflow-y: auto;
  padding: var(--sp-3);
  font-size: var(--fs-base);
  line-height: var(--lh-base);
  outline: none;
}

.rich-editor__content:empty::before {
  content: attr(data-placeholder);
  color: var(--c-text-weak);
  pointer-events: none;
}

.rich-editor__content :deep(img),
.rich-editor__content :deep(video) {
  max-width: 100%;
  margin: var(--sp-2) 0;
}

.rich-editor__content :deep(video) {
  display: block;
}

.rich-editor__input {
  display: none;
}
</style>
