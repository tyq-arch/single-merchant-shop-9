<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

import { cancelOrder, getOrder } from '@/api/customer'
import EmptyState from '@/components/EmptyState.vue'
import ProductImage from '@/components/ProductImage.vue'
import StatusTag from '@/components/StatusTag.vue'
import { formatPrice } from '@/utils/format'
import {
  ORDER_STEPS,
  canCustomerCancel,
  normalizeOrder,
  orderItemCount,
  orderStepIndex,
} from '@/utils/shop'

/**
 * B8 订单详情（客户侧，只读 + 取消）—— 挑战 B。
 *
 * 客户可在「开始发货」前取消；进入「开始发货」后不可取消（AC-014）。
 * 取消即时生效，无需原因、无需二次确认（TBD-07）。
 */
const route = useRoute()
const router = useRouter()

const loading = ref(true)
const errorState = ref('')
const order = ref(null)
const cancelling = ref(false)

const stepIndex = computed(() => orderStepIndex(order.value?.status))

const activeStep = computed(() => {
  if (order.value?.status === 'COMPLETED') return ORDER_STEPS.length
  return Math.max(stepIndex.value, 0)
})

const canCancel = computed(
  () => order.value && canCustomerCancel(order.value.status),
)

async function load() {
  loading.value = true
  errorState.value = ''
  try {
    const data = await getOrder(route.params.id)
    order.value = normalizeOrder(data.order || data)
  } catch (error) {
    errorState.value = error.message
  } finally {
    loading.value = false
  }
}

/** 客户取消（FR-028）：TBD-07 明确「即时生效、无需原因、无二次确认」 */
async function onCancel() {
  if (!order.value || cancelling.value) return

  cancelling.value = true
  try {
    const data = await cancelOrder(order.value.id)
    ElMessage.success(data?.message || '订单已取消')
    await load()
  } catch (error) {
    ElMessage.error(error.message)
    if (error.status === 409) await load()
  } finally {
    cancelling.value = false
  }
}

onMounted(load)
</script>

<template>
  <div v-loading="loading" class="order-detail">
    <EmptyState
      v-if="errorState && !loading"
      icon="⚠️"
      :text="errorState"
      hint="订单可能不存在，或不属于当前账号"
    >
      <el-button @click="router.push({ name: 'customer-orders' })">
        返回我的订单
      </el-button>
    </EmptyState>

    <template v-else-if="order">
      <div class="order-detail__bar">
        <router-link class="order-detail__link" :to="{ name: 'customer-orders' }">
          ← 全部订单
        </router-link>
      </div>

      <section class="order-card">
        <div class="order-card__head">
          <h1 class="order-card__no">{{ order.order_no || order.id }}</h1>
          <StatusTag
            :status="order.status"
            :label="order.statusLabel"
            size="large"
          />
        </div>
        <p class="order-card__time">下单时间：{{ order.created_at || '—' }}</p>
        <p class="order-card__total">
          订单总额
          <span class="price">{{ formatPrice(order.total_amount) }}</span>
        </p>
      </section>

      <el-alert
        v-if="order.status === 'CANCELLED'"
        class="order-detail__alert"
        type="info"
        :closable="false"
        show-icon
        title="订单已取消，占用库存已回补"
      />

      <section v-else class="order-steps">
        <el-steps
          :active="activeStep"
          finish-status="success"
          process-status="process"
          align-center
        >
          <el-step
            v-for="step in ORDER_STEPS"
            :key="step.status"
            :title="step.label"
          />
        </el-steps>
        <p v-if="order.status === 'SHIPPING'" class="order-steps__hint">
          商家已开始发货，客户不能再取消订单。
        </p>
      </section>

      <section class="order-items">
        <h2 class="order-items__title">商品清单</h2>
        <div
          v-for="item in order.items"
          :key="item.id || item.productId"
          class="order-item"
        >
          <div class="order-item__thumb">
            <ProductImage :src="item.image" :alt="item.name" ratio="1 / 1" />
          </div>
          <div class="order-item__body">
            <p class="order-item__name">{{ item.name }}</p>
            <p class="order-item__meta">
              <span class="price">{{ formatPrice(item.price) }}</span>
              <span>× {{ item.quantity }}</span>
            </p>
          </div>
          <span class="order-item__subtotal price">
            {{ formatPrice(item.subtotal) }}
          </span>
        </div>

        <div class="order-items__total">
          共 {{ orderItemCount(order) }} 件，合计
          <span class="price">{{ formatPrice(order.total_amount) }}</span>
        </div>
      </section>

      <div v-if="canCancel" class="order-detail__actions">
        <el-button
          type="danger"
          plain
          :loading="cancelling"
          @click="onCancel"
        >
          取消订单
        </el-button>
        <p class="order-detail__hint">
          订单进入「开始发货」前可取消，取消后占用库存会回补。
        </p>
      </div>
    </template>
  </div>
</template>

<style scoped>
.order-detail {
  min-height: 240px;
}

.order-detail__bar {
  margin-bottom: var(--sp-3);
  font-size: var(--fs-sm);
}

.order-detail__link {
  color: var(--c-primary);
}

.order-card,
.order-items {
  padding: var(--sp-3);
  background: var(--c-surface);
  border-radius: var(--r-card);
  box-shadow: var(--shadow-sm);
}

.order-card__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--sp-2);
}

.order-card__no {
  margin: 0;
  font-size: var(--fs-lg);
  font-weight: 600;
  word-break: break-all;
}

.order-card__time {
  margin: var(--sp-2) 0 0;
  font-size: var(--fs-sm);
  color: var(--c-text-weak);
}

.order-card__total {
  margin: var(--sp-2) 0 0;
  font-size: var(--fs-sm);
  color: var(--c-text-sub);
}

.order-detail__alert {
  margin-top: var(--sp-3);
}

.order-steps {
  margin-top: var(--sp-3);
  padding: var(--sp-4) var(--sp-2);
  background: var(--c-surface);
  border-radius: var(--r-card);
  box-shadow: var(--shadow-sm);
}

.order-steps__hint {
  margin: var(--sp-3) 0 0;
  font-size: var(--fs-sm);
  color: var(--c-text-weak);
  text-align: center;
}

.order-items {
  margin-top: var(--sp-3);
}

.order-items__title {
  margin: 0 0 var(--sp-2);
  font-size: var(--fs-base);
  font-weight: 600;
}

.order-item {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  padding: var(--sp-2) 0;
}

.order-item + .order-item {
  border-top: 1px solid var(--c-border);
}

.order-item__thumb {
  width: 56px;
  flex: none;
}

.order-item__body {
  min-width: 0;
  flex: 1;
}

.order-item__name {
  margin: 0;
  font-size: var(--fs-sm);
  font-weight: 600;
  word-break: break-all;
}

.order-item__meta {
  display: flex;
  gap: var(--sp-2);
  margin: 2px 0 0;
  font-size: var(--fs-sm);
  color: var(--c-text-weak);
}

.order-item__subtotal {
  flex: none;
  font-size: var(--fs-sm);
}

.order-items__total {
  margin-top: var(--sp-2);
  padding-top: var(--sp-2);
  font-size: var(--fs-sm);
  color: var(--c-text-sub);
  text-align: right;
  border-top: 1px solid var(--c-border);
}

.order-detail__actions {
  margin-top: var(--sp-4);
}

.order-detail__actions .el-button {
  width: 100%;
}

.order-detail__hint {
  margin: var(--sp-2) 0 0;
  font-size: var(--fs-sm);
  color: var(--c-text-weak);
  text-align: center;
}
</style>
