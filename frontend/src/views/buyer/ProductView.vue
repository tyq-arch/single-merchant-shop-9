<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

import { getCurrentProduct } from '@/api/product'
import EmptyState from '@/components/EmptyState.vue'
import ProductImage from '@/components/ProductImage.vue'
import StatusTag from '@/components/StatusTag.vue'
import { formatPrice } from '@/utils/format'

/**
 * B1 商品页 —— 三态：NONE（无货）/ ON_SALE（在售）/ FROZEN（交易中）。
 * 对应 FR-043~FR-046；提示文案直接取后端 message，前端不硬编码。
 */
const router = useRouter()

const loading = ref(true)
const state = ref('NONE')
const product = ref(null)
const message = ref('')

async function load() {
  loading.value = true
  try {
    const data = await getCurrentProduct()
    state.value = data.state
    product.value = data.product
    message.value = data.message || ''
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    loading.value = false
  }
}

function goBuy() {
  // 提醒买家尽快完成，商品可能被他人抢先
  router.push({ name: 'buyer-buy' })
}

onMounted(load)
</script>

<template>
  <div v-loading="loading" class="product-view">
    <template v-if="!loading">
      <!-- 无商品在售：仅显示空状态，不展示任何商品信息（FR-044） -->
      <EmptyState
        v-if="state === 'NONE'"
        icon="📦"
        :text="message || '暂无商品在售'"
        hint="卖家上架后即可在此浏览并提交购买意向"
      />

      <template v-else>
        <!-- 冻结：额外提示条，文案取后端 message（FR-046） -->
        <div v-if="state === 'FROZEN'" class="product-view__banner">
          {{ message || '商品交易中' }}
        </div>

        <div class="card">
          <ProductImage :src="product.image_url" :alt="product.name" />

          <div class="card__body">
            <div class="card__head">
              <h2 class="card__name">{{ product.name }}</h2>
              <StatusTag :status="product.status" :label="product.status_label" />
            </div>

            <p class="price card__price">{{ formatPrice(product.price) }}</p>

            <p v-if="product.description" class="card__desc">{{ product.description }}</p>
          </div>
        </div>

        <!-- 吸底操作区 -->
        <div class="action-bar">
          <button
            class="action-bar__btn"
            type="button"
            :disabled="state === 'FROZEN'"
            @click="goBuy"
          >
            {{ state === 'FROZEN' ? '商品交易中' : '我 要 购 买' }}
          </button>
        </div>
      </template>
    </template>
  </div>
</template>

<style scoped>
.product-view {
  min-height: 200px;
}

.product-view__banner {
  margin-bottom: var(--sp-3);
  padding: var(--sp-3) var(--sp-4);
  font-size: var(--fs-sm);
  color: var(--c-warning);
  background: var(--c-warning-soft);
  border-radius: var(--r-btn);
}

.card {
  overflow: hidden;
  background: var(--c-surface);
  border-radius: var(--r-card);
  box-shadow: var(--shadow-sm);
}

.card__body {
  padding: var(--sp-4);
}

.card__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--sp-2);
}

.card__name {
  margin: 0;
  font-size: var(--fs-lg);
  font-weight: 600;
  line-height: 1.4;
}

.card__price {
  margin: var(--sp-2) 0 0;
  font-size: 24px;
}

.card__desc {
  margin: var(--sp-4) 0 0;
  padding-top: var(--sp-4);
  font-size: var(--fs-sm);
  color: var(--c-text-sub);
  white-space: pre-wrap;
  border-top: 1px solid var(--c-border);
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

.action-bar__btn:active:not(:disabled) {
  background: var(--c-primary-hover);
}

.action-bar__btn:disabled {
  color: var(--c-text-weak);
  background: var(--c-muted-soft);
  cursor: not-allowed;
}
</style>
