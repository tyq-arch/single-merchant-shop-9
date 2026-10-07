<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

import { submitIntent } from '@/api/buyer'
import { getCurrentProduct } from '@/api/product'
import ProductImage from '@/components/ProductImage.vue'
import { useBuyerStore } from '@/stores/buyer'
import { formatPrice } from '@/utils/format'

/**
 * B2 提交购买意向 —— FR-013 / FR-047。
 * 校验规则与后端 BuyerIntentCreateRequest 保持一致（见技术栈报告 3.8 节）。
 */
const PHONE_RE = /^[0-9+\-\s]{5,20}$/

const router = useRouter()
const buyerStore = useBuyerStore()

const loading = ref(true)
const submitting = ref(false)
const product = ref(null)
const errors = reactive({ name: '', phone: '' })
const form = reactive({ name: '', phone: '' })

async function load() {
  loading.value = true
  try {
    const data = await getCurrentProduct()
    if (data.state !== 'ON_SALE' || !data.product) {
      ElMessage.warning(data.message || '当前商品不可购买')
      router.replace('/')
      return
    }
    product.value = data.product
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    loading.value = false
  }
}

function validate() {
  errors.name = ''
  errors.phone = ''

  const name = form.name.trim()
  if (!name) {
    errors.name = '请输入您的姓名'
  } else if (name.length > 64) {
    errors.name = '姓名不能超过 64 个字'
  }

  const phone = form.phone.trim()
  if (!phone) {
    errors.phone = '请输入联系电话'
  } else if (!PHONE_RE.test(phone)) {
    errors.phone = '联系电话格式不正确'
  }

  return !errors.name && !errors.phone
}

async function onSubmit() {
  if (submitting.value || !validate()) return

  submitting.value = true
  try {
    const data = await submitIntent({
      product_id: product.value.id,
      buyer_name: form.name.trim(),
      buyer_phone: form.phone.trim(),
    })
    buyerStore.saveSubmit(data)
    router.replace({ name: 'buyer-success' })
  } catch (error) {
    ElMessage.error(error.message)
    // 409：商品已被他人抢先进入交易，或已被下架 —— 回首页刷新状态
    if (error.status === 409) {
      router.replace('/')
    }
  } finally {
    submitting.value = false
  }
}

onMounted(load)
</script>

<template>
  <div v-loading="loading" class="intent-form">
    <template v-if="!loading && product">
      <!-- 商品摘要：防止买家填错商品 -->
      <div class="summary">
        <div class="summary__thumb">
          <ProductImage :src="product.image_url" :alt="product.name" ratio="1 / 1" />
        </div>
        <div class="summary__info">
          <p class="summary__name">{{ product.name }}</p>
          <p class="price summary__price">{{ formatPrice(product.price) }}</p>
        </div>
      </div>

      <div class="form">
        <label class="field">
          <span class="field__label">姓名 <em>*</em></span>
          <input
            v-model="form.name"
            class="field__input"
            :class="{ 'field__input--error': errors.name }"
            type="text"
            placeholder="请输入您的姓名"
            maxlength="64"
            autocomplete="name"
          />
          <span v-if="errors.name" class="field__error">{{ errors.name }}</span>
        </label>

        <label class="field">
          <span class="field__label">联系电话 <em>*</em></span>
          <input
            v-model="form.phone"
            class="field__input"
            :class="{ 'field__input--error': errors.phone }"
            type="tel"
            placeholder="请输入联系电话"
            maxlength="20"
            autocomplete="tel"
          />
          <span v-if="errors.phone" class="field__error">{{ errors.phone }}</span>
        </label>

        <p class="form__privacy">ℹ️ 姓名和电话仅用于本次线下交易，交易结束后将脱敏处理。</p>
      </div>

      <div class="action-bar">
        <button class="action-bar__btn" type="button" :disabled="submitting" @click="onSubmit">
          {{ submitting ? '提交中…' : '提 交 意 向' }}
        </button>
      </div>
    </template>
  </div>
</template>

<style scoped>
.intent-form {
  min-height: 200px;
}

.summary {
  display: flex;
  gap: var(--sp-3);
  padding: var(--sp-3);
  background: var(--c-surface);
  border-radius: var(--r-card);
  box-shadow: var(--shadow-sm);
}

.summary__thumb {
  width: 72px;
  flex: none;
}

.summary__info {
  display: flex;
  flex-direction: column;
  justify-content: center;
  min-width: 0;
}

.summary__name {
  margin: 0;
  font-size: var(--fs-sm);
  font-weight: 600;
}

.summary__price {
  margin: var(--sp-1) 0 0;
}

.form {
  margin-top: var(--sp-4);
}

.field {
  display: block;
  margin-bottom: var(--sp-5);
}

.field__label {
  display: block;
  margin-bottom: var(--sp-2);
  font-size: var(--fs-sm);
  color: var(--c-text-sub);
}

.field__label em {
  color: var(--c-danger);
  font-style: normal;
}

.field__input {
  width: 100%;
  min-height: var(--touch-min);
  padding: 0 var(--sp-3);
  font-size: var(--fs-base);
  color: var(--c-text);
  background: var(--c-surface);
  border: 1px solid var(--c-border);
  border-radius: var(--r-btn);
  outline: none;
}

.field__input:focus {
  border-color: var(--c-primary);
}

.field__input--error {
  border-color: var(--c-danger);
}

.field__error {
  display: block;
  margin-top: var(--sp-1);
  font-size: var(--fs-sm);
  color: var(--c-danger);
}

.form__privacy {
  margin: 0;
  font-size: var(--fs-sm);
  color: var(--c-text-weak);
}

.action-bar {
  position: fixed;
  right: 0;
  bottom: 0;
  left: 0;
  z-index: 10;
  max-width: var(--buyer-max-width);
  margin: 0 auto;
  padding: var(--sp-3) var(--sp-4) calc(var(--sp-3) + env(safe-area-inset-bottom));
  background: var(--c-surface);
  border-top: 1px solid var(--c-border);
}

.action-bar__btn {
  width: 100%;
  min-height: var(--touch-min);
  font-size: var(--fs-base);
  font-weight: 600;
  color: #fff;
  background: var(--c-primary);
  border: none;
  border-radius: var(--r-btn);
  cursor: pointer;
}

.action-bar__btn:disabled {
  background: var(--c-text-weak);
  cursor: not-allowed;
}
</style>
