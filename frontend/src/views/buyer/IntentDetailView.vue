<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'

import { getIntent } from '@/api/customer'
import EmptyState from '@/components/EmptyState.vue'
import ProductImage from '@/components/ProductImage.vue'
import StatusTag from '@/components/StatusTag.vue'
import { formatPrice } from '@/utils/format'

/**
 * B6 意向详情 —— FR-025 / AC-016。
 *
 * 纯只读：客户提交意向后无主动撤销、无修改入口，
 * 状态只能由卖家处理或售罄触发。
 */
const route = useRoute()

const loading = ref(true)
const errorState = ref('')
const intent = ref(null)

const product = computed(() => intent.value?.product || null)
const transaction = computed(() => intent.value?.transaction || null)

const waiting = computed(() =>
  ['QUEUED', 'REQUEUED'].includes(intent.value?.status),
)

const statusNotice = computed(() => {
  switch (intent.value?.status) {
    case 'QUEUED':
      return '已进入商品队列，请耐心等待卖家处理。提交后不可撤销、不可修改。'
    case 'REQUEUED':
      return '交易失败后卖家已将你重新排到队尾，请继续等待。'
    case 'IN_TRANSACTION':
      return '卖家已与你进入交易，请按约定地点完成线下交易。交易中不可撤销。'
    case 'SUCCESS':
      return '交易已完成，感谢购买。'
    case 'FAILED':
      return '本次交易未完成，等待卖家确认后续处理。'
    case 'VOIDED':
      return '该意向已作废。'
    case 'SOLD_OUT':
      return '商品已售罄，意向自动失效。'
    default:
      return ''
  }
})

async function load() {
  loading.value = true
  errorState.value = ''
  try {
    intent.value = await getIntent(route.params.id)
  } catch (error) {
    errorState.value = error.message
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div v-loading="loading" class="intent-detail">
    <EmptyState
      v-if="errorState && !loading"
      icon="⚠️"
      :text="errorState"
      hint="意向不存在，或不属于当前账号"
    >
      <router-link class="intent-detail__link" to="/my/intents">返回我的意向</router-link>
    </EmptyState>

    <template v-else-if="intent">
      <!-- 状态卡 -->
      <div class="status-card" :class="{ 'status-card--wait': waiting }">
        <template v-if="waiting && intent.queue_position">
          <p class="status-card__label">您当前排在第</p>
          <p class="status-card__value">{{ intent.queue_position }}</p>
          <p class="status-card__unit">位</p>
        </template>
        <p v-else class="status-card__title">{{ intent.status_label }}</p>

        <StatusTag :status="intent.status" :label="intent.status_label" size="large" />
      </div>

      <p class="notice">{{ statusNotice }}</p>

      <!-- 商品快照 -->
      <div class="product-card">
        <div class="product-card__thumb">
          <ProductImage
            :src="product?.primary_image"
            :alt="product?.name"
            ratio="1 / 1"
          />
        </div>
        <div class="product-card__body">
          <p class="product-card__name">{{ product?.name || '商品信息不可用' }}</p>
          <p class="product-card__price price">{{ formatPrice(product?.price) }}</p>
          <p v-if="product?.category_name" class="product-card__category">
            {{ product.parent_category_name }} / {{ product.category_name }}
          </p>
        </div>
      </div>

      <!-- 意向信息 -->
      <div class="info-card">
        <div class="info-row">
          <span class="info-row__key">提交时间</span>
          <span class="info-row__value">{{ intent.created_at }}</span>
        </div>
        <div class="info-row">
          <span class="info-row__key">最后更新</span>
          <span class="info-row__value">{{ intent.updated_at }}</span>
        </div>
        <div v-if="intent.requeue_count > 0" class="info-row">
          <span class="info-row__key">重新排队</span>
          <span class="info-row__value">已重排 {{ intent.requeue_count }} 次</span>
        </div>
        <div v-if="transaction" class="info-row">
          <span class="info-row__key">交易结果</span>
          <span class="info-row__value">{{ transaction.result_label }}</span>
        </div>
        <div v-if="transaction?.note" class="info-row">
          <span class="info-row__key">卖家备注</span>
          <span class="info-row__value">{{ transaction.note }}</span>
        </div>
      </div>

      <div class="intent-detail__footer">
        <router-link class="intent-detail__link" to="/my/intents">← 返回我的意向</router-link>
        <span class="intent-detail__divider">·</span>
        <router-link class="intent-detail__link" to="/my/orders">历史下单</router-link>
      </div>
    </template>
  </div>
</template>

<style scoped>
.intent-detail {
  min-height: 240px;
}

.status-card {
  padding: var(--sp-5) var(--sp-4);
  text-align: center;
  background: var(--c-surface);
  border-radius: var(--r-card);
  box-shadow: var(--shadow-sm);
}

.status-card--wait {
  background: var(--c-primary-soft);
}

.status-card__label,
.status-card__unit {
  margin: 0;
  font-size: var(--fs-sm);
  color: var(--c-text-sub);
}

.status-card__value {
  margin: var(--sp-1) 0;
  font-size: 44px;
  font-weight: 700;
  line-height: 1.1;
  color: var(--c-primary);
}

.status-card__title {
  margin: 0 0 var(--sp-3);
  font-size: var(--fs-lg);
  font-weight: 600;
}

.notice {
  margin: var(--sp-3) 0 0;
  padding: var(--sp-3) var(--sp-4);
  font-size: var(--fs-sm);
  color: var(--c-text-sub);
  background: var(--c-muted-soft);
  border-radius: var(--r-btn);
}

.product-card {
  display: flex;
  gap: var(--sp-3);
  margin-top: var(--sp-3);
  padding: var(--sp-3);
  background: var(--c-surface);
  border-radius: var(--r-card);
  box-shadow: var(--shadow-sm);
}

.product-card__thumb {
  width: 80px;
  flex: none;
}

.product-card__body {
  display: flex;
  flex-direction: column;
  justify-content: center;
  min-width: 0;
}

.product-card__name {
  margin: 0;
  font-size: var(--fs-base);
  font-weight: 600;
}

.product-card__price {
  margin: var(--sp-1) 0 0;
}

.product-card__category {
  margin: var(--sp-1) 0 0;
  font-size: var(--fs-sm);
  color: var(--c-text-weak);
}

.info-card {
  margin-top: var(--sp-3);
  padding: var(--sp-2) var(--sp-4);
  background: var(--c-surface);
  border-radius: var(--r-card);
  box-shadow: var(--shadow-sm);
}

.info-row {
  display: flex;
  justify-content: space-between;
  gap: var(--sp-4);
  padding: var(--sp-3) 0;
  font-size: var(--fs-sm);
}

.info-row + .info-row {
  border-top: 1px solid var(--c-border);
}

.info-row__key {
  flex: none;
  color: var(--c-text-weak);
}

.info-row__value {
  text-align: right;
  word-break: break-all;
}

.intent-detail__footer {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--sp-3);
  margin-top: var(--sp-5);
}

.intent-detail__link {
  font-size: var(--fs-sm);
  color: var(--c-primary);
}

.intent-detail__divider {
  color: var(--c-text-weak);
}
</style>
