<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

import { uploadImage, validateImage } from '@/api/file'
import { publishProduct } from '@/api/seller'
import ProductImage from '@/components/ProductImage.vue'

/**
 * S3 发布商品 —— FR-001 / FR-008 / FR-036 / NFR-012。
 *
 * 刻意不提供商品编辑页：发布后名称、描述、图片、价格均不可修改（FR-002 / AC-026），
 * 要调整只能下架后重新发布。
 */
const router = useRouter()

const formRef = ref()
const fileInput = ref(null)
const submitting = ref(false)
const uploading = ref(false)
const imageUrl = ref('')

const form = reactive({
  name: '',
  price: null,
  description: '',
})

const rules = {
  name: [
    { required: true, message: '请输入商品名称', trigger: 'blur' },
    { max: 120, message: '商品名称不能超过 120 字', trigger: 'blur' },
  ],
  price: [
    { required: true, message: '请输入商品价格', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        const num = Number(value)
        if (!Number.isFinite(num) || num <= 0) {
          callback(new Error('价格需大于 0'))
        } else if (num > 9999999) {
          callback(new Error('价格不能超过 9999999 元'))
        } else {
          callback()
        }
      },
      trigger: 'blur',
    },
  ],
  description: [{ max: 2000, message: '商品描述不能超过 2000 字', trigger: 'blur' }],
}

function pickFile() {
  fileInput.value?.click()
}

async function onFileChange(event) {
  const file = event.target.files?.[0]
  // 清空 value，使再次选择同一文件也能触发 change
  event.target.value = ''
  if (!file) return

  const invalidReason = validateImage(file)
  if (invalidReason) {
    ElMessage.error(invalidReason)
    return
  }

  uploading.value = true
  try {
    const data = await uploadImage(file)
    imageUrl.value = data.url
    ElMessage.success('图片上传成功')
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    uploading.value = false
  }
}

function removeImage() {
  imageUrl.value = ''
}

async function onSubmit() {
  if (submitting.value) return

  try {
    await formRef.value.validate()
  } catch {
    return
  }

  submitting.value = true
  try {
    const payload = {
      name: form.name.trim(),
      price: Number(form.price),
    }
    const description = form.description.trim()
    if (description) payload.description = description
    if (imageUrl.value) payload.image_url = imageUrl.value

    const result = await publishProduct(payload)
    ElMessage.success(result?.message || '商品发布成功')
    router.replace({ name: 'seller-dashboard' })
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <el-card shadow="never">
    <el-alert
      type="warning"
      :closable="false"
      show-icon
      title="同一时间只能有一件商品在售，且发布后信息不可修改"
      description="名称、描述、图片、价格在发布后均无法编辑。如需调整，请先下架当前商品，再重新发布。"
      class="publish__alert"
    />

    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-width="120px"
      class="publish__form"
    >
      <el-form-item label="商品名称" prop="name">
        <el-input
          v-model="form.name"
          maxlength="120"
          show-word-limit
          placeholder="请输入商品名称"
        />
      </el-form-item>

      <el-form-item label="价格（元）" prop="price">
        <el-input-number
          v-model="form.price"
          :min="0.01"
          :max="9999999"
          :precision="2"
          :step="1"
          controls-position="right"
          placeholder="0.00"
        />
      </el-form-item>

      <el-form-item label="商品描述" prop="description">
        <el-input
          v-model="form.description"
          type="textarea"
          :rows="4"
          maxlength="2000"
          show-word-limit
          placeholder="选填，介绍一下这件商品"
        />
      </el-form-item>

      <el-form-item label="商品主图">
        <div class="upload">
          <ProductImage v-if="imageUrl" :src="imageUrl" ratio="1 / 1" alt="商品主图" />

          <div class="upload__actions">
            <el-button :loading="uploading" @click="pickFile">
              {{ imageUrl ? '重新上传' : '＋ 点击上传' }}
            </el-button>
            <el-button v-if="imageUrl" link type="danger" @click="removeImage">移除</el-button>
            <p class="upload__hint">选填，最多 1 张主图；支持 jpg / png / gif / webp，≤ 5MB</p>
          </div>
        </div>

        <input
          ref="fileInput"
          class="upload__input"
          type="file"
          accept="image/jpeg,image/png,image/gif,image/webp"
          @change="onFileChange"
        />
      </el-form-item>

      <el-form-item>
        <el-button @click="router.back()">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="onSubmit">确认发布</el-button>
      </el-form-item>
    </el-form>
  </el-card>
</template>

<style scoped>
.publish__alert {
  margin-bottom: var(--sp-5);
}

.publish__form {
  max-width: 720px;
}

.upload {
  display: flex;
  gap: var(--sp-4);
  align-items: flex-start;
}

.upload :deep(.product-image) {
  width: 120px;
  flex: none;
}

.upload__actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--sp-2);
}

.upload__hint {
  flex-basis: 100%;
  margin: var(--sp-1) 0 0;
  font-size: var(--fs-sm);
  color: var(--c-text-weak);
}

.upload__input {
  display: none;
}
</style>
