<script setup>
import { computed } from 'vue'

import ProductImage from '@/components/ProductImage.vue'
import StatusTag from '@/components/StatusTag.vue'
import { formatPrice } from '@/utils/format'

/**
 * 客户侧意向/订单卡片。只读展示，不提供任何撤销、修改入口（FR-025）。
 */
const props = defineProps({
  intent: { type: Object, required: true },
  to: { type: [String, Object], default: null },
})

const product = computed(() => props.intent.product || {})
const transaction = computed(() => props.intent.transaction || null)

const waiting = computed(() =>
  ['QUEUED', 'REQUEUED'].includes(props.intent.status),
)
</script>

<template>
  <component
    :is="to ? 'router-link' : 'div'"
    :to="to || undefined"
    class="intent-card"
  >
    <div class="intent-card__thumb">
      <ProductImage :src="product.primary_image" :alt="product.name" ratio="1 / 1" />
    </div>

    <div class="intent-card__body">
      <div class="intent-card__head">
        <p class="intent-card__name">{{ product.name || '商品已删除' }}</p>
        <StatusTag :status="intent.status" :label="intent.status_label" />
      </div>

      <p class="intent-card__meta">
        <span class="price">{{ formatPrice(product.price) }}</span>
        <span v-if="waiting && intent.queue_position">第 {{ intent.queue_position }} 位</span>
        <span v-if="intent.requeue_count > 0">已重排 {{ intent.requeue_count }} 次</span>
      </p>

      <p v-if="transaction" class="intent-card__result">
        交易结果：{{ transaction.result_label }}
      </p>

      <p class="intent-card__time">{{ intent.updated_at || intent.created_at }}</p>
    </div>
  </component>
</template>

<style scoped>
.intent-card {
  display: flex;
  gap: var(--sp-3);
  padding: var(--sp-3);
  color: inherit;
  background: var(--c-surface);
  border-radius: var(--r-card);
  box-shadow: var(--shadow-sm);
}

.intent-card + .intent-card {
  margin-top: var(--sp-3);
}

.intent-card__thumb {
  width: 80px;
  flex: none;
}

.intent-card__body {
  min-width: 0;
  flex: 1;
}

.intent-card__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--sp-2);
}

.intent-card__name {
  margin: 0;
  font-size: var(--fs-base);
  font-weight: 600;
  word-break: break-all;
}

.intent-card__meta {
  display: flex;
  flex-wrap: wrap;
  gap: var(--sp-3);
  margin: var(--sp-2) 0 0;
  font-size: var(--fs-sm);
  color: var(--c-text-sub);
}

.intent-card__result {
  margin: var(--sp-1) 0 0;
  font-size: var(--fs-sm);
  color: var(--c-text-sub);
}

.intent-card__time {
  margin: var(--sp-1) 0 0;
  font-size: var(--fs-sm);
  color: var(--c-text-weak);
}
</style>
