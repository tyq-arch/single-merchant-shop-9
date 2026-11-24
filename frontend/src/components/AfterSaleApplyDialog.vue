<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'

import { createAfterSale } from '@/api/customer'
import { uploadAfterSaleImage } from '@/api/file'
import ImageUploader from '@/components/ImageUploader.vue'
import { formatPrice } from '@/utils/format'
import { AFTER_SALE_MAX_IMAGES } from '@/utils/shop'

/**
 * 售后申请弹窗（FR-051 / FR-052）。
 *
 * 售后针对「订单内某一件商品（订单项）」发起；申请单只有
 * 标题、描述、图片 —— 没有「类型」字段（TBD-11），
 * 退货 / 退款 / 换货由商家线下协商后记录（FR-054）。
 */
const props = defineProps({
  modelValue: { type: Boolean, default: false },
  order: { type: Object, default: null },
  item: { type: Object, default: null },
})

const emit = defineEmits(['update:modelValue', 'success'])

const formRef = ref()
const submitting = ref(false)

const form = reactive({
  title: '',
  description: '',
  images: [],
})

const rules = {
  title: [
    { required: true, message: '请填写售后标题', trigger: 'blur' },
    { max: 50, message: '标题最多 50 个字', trigger: 'blur' },
  ],
  description: [
    { required: true, message: '请描述遇到的问题', trigger: 'blur' },
    { max: 500, message: '描述最多 500 个字', trigger: 'blur' },
  ],
}

const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value),
})

const itemId = computed(
  () => props.item?.itemId || props.item?.id || props.item?.productId || '',
)

watch(
  () => props.modelValue,
  (open) => {
    if (!open) return
    form.title = ''
    form.description = ''
    form.images = []
    submitting.value = false
    formRef.value?.clearValidate()
  },
)

async function onSubmit() {
  if (submitting.value) return
  if (!props.order?.id || !itemId.value) {
    ElMessage.error('订单项信息缺失，无法提交售后')
    return
  }

  try {
    await formRef.value.validate()
  } catch {
    return
  }

  submitting.value = true
  try {
    const data = await createAfterSale(props.order.id, itemId.value, {
      title: form.title.trim(),
      description: form.description.trim(),
      images: [...form.images],
    })
    ElMessage.success(data?.message || '售后申请已提交')
    visible.value = false
    emit('success', data)
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <el-dialog
    v-model="visible"
    title="申请售后"
    width="480px"
    append-to-body
    :close-on-click-modal="!submitting"
  >
    <div class="aftersale-apply">
      <div v-if="item" class="aftersale-apply__item">
        <div class="aftersale-apply__thumb">
          <img v-if="item.image" :src="item.image" :alt="item.name" />
        </div>
        <div>
          <p class="aftersale-apply__name">{{ item.name }}</p>
          <p class="aftersale-apply__meta">
            {{ item.quantity }} 件 · 小计 {{ formatPrice(item.subtotal) }}
          </p>
        </div>
      </div>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        @submit.prevent="onSubmit"
      >
        <el-form-item label="标题" prop="title">
          <el-input
            v-model="form.title"
            maxlength="50"
            show-word-limit
            placeholder="例如：收到的商品有破损"
          />
        </el-form-item>

        <el-form-item label="问题描述" prop="description">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="4"
            maxlength="500"
            show-word-limit
            placeholder="请描述商品问题、期望的处理方式等"
          />
        </el-form-item>

        <el-form-item label="图片（选填）">
          <ImageUploader
            v-model="form.images"
            :max="AFTER_SALE_MAX_IMAGES"
            :uploader="uploadAfterSaleImage"
            :show-primary="false"
            hint="最多 9 张，仅 JPG / PNG，单张 ≤5MB"
          />
        </el-form-item>
      </el-form>

      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="提交后商家会线下与你协商；退货、退款或换货结果由商家记录，可在「售后服务」查看"
      />
    </div>

    <template #footer>
      <el-button :disabled="submitting" @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="onSubmit">
        提交申请
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.aftersale-apply__item {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  margin-bottom: var(--sp-4);
  padding: var(--sp-2);
  background: var(--c-muted-soft);
  border-radius: var(--r-btn);
}

.aftersale-apply__thumb {
  width: 48px;
  height: 48px;
  flex: none;
  overflow: hidden;
  background: var(--c-surface);
  border-radius: var(--r-btn);
}

.aftersale-apply__thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.aftersale-apply__name {
  margin: 0;
  font-size: var(--fs-sm);
  font-weight: 600;
  word-break: break-all;
}

.aftersale-apply__meta {
  margin: 2px 0 0;
  font-size: 12px;
  color: var(--c-text-weak);
}
</style>
