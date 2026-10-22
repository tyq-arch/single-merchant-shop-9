<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'

import { MAX_PRODUCT_IMAGES, uploadImage, validateImage } from '@/api/file'

/**
 * 商品多图上传（FR-008 / C-20）。
 *
 * - 最多 9 张，仅 JPG / PNG，单张 ≤5MB
 * - 上传完成后把 /uploads/... 地址写入 image_urls
 * - 第 1 张即主图，顺序按上传固定，不提供拖拽排序
 */
const props = defineProps({
  modelValue: { type: Array, default: () => [] },
  max: { type: Number, default: MAX_PRODUCT_IMAGES },
})

const emit = defineEmits(['update:modelValue'])

const inputRef = ref()
const uploading = ref(false)

function pick() {
  if (uploading.value) return
  if (props.modelValue.length >= props.max) {
    ElMessage.warning(`最多上传 ${props.max} 张图片`)
    return
  }
  inputRef.value?.click()
}

async function onFiles(event) {
  const files = Array.from(event.target.files || [])
  event.target.value = ''
  if (!files.length) return

  const remaining = props.max - props.modelValue.length
  if (remaining <= 0) return
  if (files.length > remaining) {
    ElMessage.warning(`最多上传 ${props.max} 张，本次仅上传前 ${remaining} 张`)
  }

  uploading.value = true
  const next = [...props.modelValue]
  for (const file of files.slice(0, remaining)) {
    const error = validateImage(file)
    if (error) {
      ElMessage.error(`${file.name}：${error}`)
      continue
    }
    try {
      const data = await uploadImage(file)
      next.push(data.url)
    } catch (err) {
      ElMessage.error(`${file.name}：${err.message}`)
    }
  }
  emit('update:modelValue', next)
  uploading.value = false
}

function remove(index) {
  emit(
    'update:modelValue',
    props.modelValue.filter((_, i) => i !== index),
  )
}
</script>

<template>
  <div class="image-uploader">
    <div class="image-uploader__grid">
      <div v-for="(url, index) in modelValue" :key="url" class="image-uploader__item">
        <img :src="url" :alt="`商品图片 ${index + 1}`" />
        <span v-if="index === 0" class="image-uploader__badge">主图</span>
        <button
          class="image-uploader__remove"
          type="button"
          title="移除"
          @click="remove(index)"
        >
          ×
        </button>
      </div>

      <button
        v-if="modelValue.length < max"
        class="image-uploader__add"
        type="button"
        :disabled="uploading"
        @click="pick"
      >
        <span>{{ uploading ? '上传中…' : '＋' }}</span>
        <small>{{ modelValue.length }}/{{ max }}</small>
      </button>
    </div>

    <p class="image-uploader__hint">
      最多 {{ max }} 张，仅 JPG / PNG，单张 ≤5MB；第 1 张为主图，顺序按上传固定。
    </p>

    <input
      ref="inputRef"
      class="image-uploader__input"
      type="file"
      accept="image/jpeg,image/png"
      multiple
      @change="onFiles"
    />
  </div>
</template>

<style scoped>
.image-uploader__grid {
  display: flex;
  flex-wrap: wrap;
  gap: var(--sp-3);
}

.image-uploader__item {
  position: relative;
  width: 96px;
  height: 96px;
  overflow: hidden;
  background: var(--c-muted-soft);
  border: 1px solid var(--c-border);
  border-radius: var(--r-btn);
}

.image-uploader__item img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.image-uploader__badge {
  position: absolute;
  bottom: 0;
  left: 0;
  padding: 1px 8px;
  font-size: 12px;
  color: #fff;
  background: rgba(37, 99, 235, 0.9);
  border-top-right-radius: var(--r-btn);
}

.image-uploader__remove {
  position: absolute;
  top: 2px;
  right: 2px;
  width: 22px;
  height: 22px;
  font-size: 16px;
  line-height: 1;
  color: #fff;
  background: rgba(15, 23, 42, 0.65);
  border: none;
  border-radius: 50%;
  cursor: pointer;
}

.image-uploader__add {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  width: 96px;
  height: 96px;
  font-size: 22px;
  color: var(--c-text-weak);
  background: var(--c-surface);
  border: 1px dashed var(--c-border);
  border-radius: var(--r-btn);
  cursor: pointer;
}

.image-uploader__add:disabled {
  cursor: progress;
  opacity: 0.6;
}

.image-uploader__add small {
  font-size: 12px;
}

.image-uploader__hint {
  margin: var(--sp-2) 0 0;
  font-size: var(--fs-sm);
  color: var(--c-text-weak);
}

.image-uploader__input {
  display: none;
}
</style>
