<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

import { createProduct, listCategories } from '@/api/seller'
import ImageUploader from '@/components/ImageUploader.vue'
import RichTextEditor from '@/components/RichTextEditor.vue'
import { toCascaderOptions } from '@/utils/category'

/**
 * S3 发布商品 —— FR-002 / FR-003 / FR-008 / FR-009 / FR-015A。
 *
 * 商品发布后名称、描述、图片、价格、类别均不可修改，唯一可动字段为库存。
 */
const router = useRouter()

const formRef = ref()
const submitting = ref(false)
const categoriesLoading = ref(true)
const categoryOptions = ref([])

const form = reactive({
  categoryId: null,
  name: '',
  price: 0.01,
  stock: 0,
  description: '',
})

const images = ref([])

const cascaderProps = {
  value: 'value',
  label: 'label',
  children: 'children',
  emitPath: false,
}

function priceValidator(_rule, value, callback) {
  const price = Number(value)
  if (!Number.isFinite(price) || price <= 0) {
    callback(new Error('价格必须大于 0'))
    return
  }
  if (price > 9999999) {
    callback(new Error('价格不能超过 9999999'))
    return
  }
  callback()
}

function stockValidator(_rule, value, callback) {
  const stock = Number(value)
  if (!Number.isInteger(stock) || stock < 0) {
    callback(new Error('库存必须为非负整数'))
    return
  }
  callback()
}

const rules = {
  categoryId: [{ required: true, message: '请选择二级类别', trigger: 'change' }],
  name: [
    { required: true, message: '请输入商品名称', trigger: 'blur' },
    { max: 120, message: '商品名称不能超过 120 个字符', trigger: 'blur' },
  ],
  price: [
    { required: true, message: '请输入价格', trigger: 'blur' },
    { validator: priceValidator, trigger: 'blur' },
  ],
  stock: [
    { required: true, message: '请输入库存', trigger: 'blur' },
    { validator: stockValidator, trigger: 'blur' },
  ],
}

async function loadCategories() {
  categoriesLoading.value = true
  try {
    const data = await listCategories()
    categoryOptions.value = toCascaderOptions(data.items)
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    categoriesLoading.value = false
  }
}

async function onSubmit() {
  if (submitting.value) return

  try {
    await formRef.value.validate()
  } catch {
    return
  }

  const plainText = form.description.replace(/<[^>]*>/g, '').trim()
  if (plainText.length > 20000) {
    ElMessage.warning('商品描述不能超过 20000 个字符')
    return
  }

  submitting.value = true
  try {
    const data = await createProduct({
      name: form.name.trim(),
      price: Number(form.price),
      description: form.description || null,
      stock: Number(form.stock),
      category_id: form.categoryId,
      image_urls: images.value,
    })
    ElMessage.success(data?.message || '商品发布成功')
    router.push({ name: 'seller-products' })
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    submitting.value = false
  }
}

onMounted(loadCategories)
</script>

<template>
  <el-card v-loading="categoriesLoading" shadow="never">
    <el-alert
      v-if="!categoryOptions.length && !categoriesLoading"
      type="warning"
      :closable="false"
      show-icon
      class="publish__alert"
    >
      还没有可用类别。商品必须归属二级类别，请先到
      <router-link :to="{ name: 'seller-categories' }">类别管理</router-link>
      创建一级和二级类别。
    </el-alert>

    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-width="120px"
      class="publish-form"
    >
      <el-form-item label="商品类别" prop="categoryId">
        <el-cascader
          v-model="form.categoryId"
          :options="categoryOptions"
          :props="cascaderProps"
          placeholder="请选择二级类别（大类 / 小类）"
          class="publish-form__control"
        />
        <p class="publish-form__hint">商品必须挂在二级类别（小类）下。</p>
      </el-form-item>

      <el-form-item label="商品名称" prop="name">
        <el-input
          v-model="form.name"
          placeholder="请输入商品名称"
          maxlength="120"
          show-word-limit
          class="publish-form__control"
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
          class="publish-form__control"
        />
      </el-form-item>

      <el-form-item label="库存数量" prop="stock">
        <el-input-number
          v-model="form.stock"
          :min="0"
          :max="999999"
          :precision="0"
          :step="1"
          controls-position="right"
          class="publish-form__control"
        />
        <p class="publish-form__hint">
          库存为 0 时商品直接进入「售罄下架」，补货到大于 0 会自动恢复在售。
        </p>
      </el-form-item>

      <el-form-item label="商品图片">
        <ImageUploader v-model="images" />
      </el-form-item>

      <el-form-item label="商品描述">
        <RichTextEditor v-model="form.description" />
      </el-form-item>

      <el-form-item>
        <el-alert type="info" :closable="false" show-icon class="publish-form__notice">
          商品发布后不可编辑（名称、描述、图片、价格、类别均定死），仅库存可调整。
        </el-alert>
      </el-form-item>

      <el-form-item>
        <el-button type="primary" :loading="submitting" @click="onSubmit">
          发布商品
        </el-button>
        <el-button @click="router.push({ name: 'seller-products' })">取消</el-button>
      </el-form-item>
    </el-form>
  </el-card>
</template>

<style scoped>
.publish__alert {
  margin-bottom: var(--sp-4);
}

.publish-form {
  max-width: 860px;
}

.publish-form__control {
  width: 100%;
  max-width: 420px;
}

.publish-form__hint {
  width: 100%;
  margin: var(--sp-1) 0 0;
  font-size: var(--fs-sm);
  color: var(--c-text-weak);
}

.publish-form__notice {
  margin-bottom: 0;
}
</style>
