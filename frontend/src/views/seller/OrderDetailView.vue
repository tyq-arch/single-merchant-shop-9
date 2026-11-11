<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'

import { advanceOrder, cancelOrder, getOrder } from '@/api/seller'
import EmptyState from '@/components/EmptyState.vue'
import ProductImage from '@/components/ProductImage.vue'
import StatusTag from '@/components/StatusTag.vue'
import { formatPrice } from '@/utils/format'
import {
  ORDER_STATUS_LABELS,
  ORDER_STEPS,
  canSellerCancel,
  getNextOrderAction,
  normalizeOrder,
  orderItemCount,
  orderStepIndex,
} from '@/utils/shop'

/**
 * S5 订单详情 / 处理 —— 挑战 B。
 *
 * 商家按「确认 → 备货完成 → 开始发货 → 交易完成」单向推进；
 * 交易完成前可取消（AC-015）；状态推进与取消都由后端做状态机校验。
 */
const route = useRoute()
const router = useRouter()

const loading = ref(true)
const errorState = ref('')
const order = ref(null)
const acting = ref(false)

const stepIndex = computed(() => orderStepIndex(order.value?.status))

const activeStep = computed(() => {
  if (order.value?.status === 'COMPLETED') return ORDER_STEPS.length
  return Math.max(stepIndex.value, 0)
})

const nextAction = computed(() =>
  order.value ? getNextOrderAction(order.value.status) : null,
)

const canCancel = computed(
  () => order.value && canSellerCancel(order.value.status),
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

async function onAdvance() {
  if (!order.value || !nextAction.value || acting.value) return
  const targetLabel =
    ORDER_STATUS_LABELS[nextAction.value.targetStatus] || nextAction.value.label

  try {
    await ElMessageBox.confirm(
      `确认将订单推进到「${targetLabel}」？订单状态不可回退。`,
      nextAction.value.label,
      { confirmButtonText: '确认推进', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return
  }

  acting.value = true
  try {
    const data = await advanceOrder(order.value.id)
    ElMessage.success(data?.message || `已推进到${targetLabel}`)
    await load()
  } catch (error) {
    ElMessage.error(error.message)
    if (error.status === 409) await load()
  } finally {
    acting.value = false
  }
}

/** 商家取消（FR-029）：TBD-07 明确「即时生效、无需原因、无二次确认」 */
async function onCancel() {
  if (!order.value || acting.value) return

  acting.value = true
  try {
    const data = await cancelOrder(order.value.id)
    ElMessage.success(data?.message || '订单已取消')
    await load()
  } catch (error) {
    ElMessage.error(error.message)
    if (error.status === 409) await load()
  } finally {
    acting.value = false
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
      hint="订单可能不存在，或已被删除"
    >
      <el-button @click="router.push({ name: 'seller-orders' })">
        返回订单管理
      </el-button>
    </EmptyState>

    <template v-else-if="order">
      <div class="order-detail__bar">
        <el-button link type="primary" @click="router.push({ name: 'seller-orders' })">
          ← 返回订单管理
        </el-button>
      </div>

      <el-card shadow="never">
        <div class="order-head">
          <div>
            <h2 class="order-head__no">{{ order.order_no || order.id }}</h2>
            <p class="order-head__time">
              下单时间：{{ order.created_at || '—' }}
            </p>
          </div>
          <StatusTag
            :status="order.status"
            :label="order.statusLabel"
            size="large"
          />
        </div>

        <el-descriptions :column="2" border class="order-customer">
          <el-descriptions-item label="客户">
            {{ order.customer?.username || order.customer_name || '—' }}
          </el-descriptions-item>
          <el-descriptions-item label="电话">
            {{ order.customer?.phone || '—' }}
          </el-descriptions-item>
          <el-descriptions-item label="默认交易地点">
            {{ order.customer?.trade_location || '—' }}
          </el-descriptions-item>
          <el-descriptions-item label="订单总额">
            <span class="price">{{ formatPrice(order.total_amount) }}</span>
          </el-descriptions-item>
        </el-descriptions>
      </el-card>

      <el-alert
        v-if="order.status === 'CANCELLED'"
        class="order-detail__alert"
        type="info"
        :closable="false"
        show-icon
        title="订单已取消，占用库存已回补"
      />

      <el-card v-else shadow="never" class="order-detail__card">
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
          已开始发货：客户不能再取消，商家仍可在交易完成前取消。
        </p>
      </el-card>

      <el-card shadow="never" class="order-detail__card">
        <template #header>商品清单</template>
        <el-table :data="order.items" border>
          <el-table-column label="商品" min-width="220">
            <template #default="{ row }">
              <div class="item-cell">
                <div class="item-cell__thumb">
                  <ProductImage :src="row.image" :alt="row.name" ratio="1 / 1" />
                </div>
                <span class="item-cell__name">{{ row.name }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="单价" width="120">
            <template #default="{ row }">
              <span class="price">{{ formatPrice(row.price) }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="quantity" label="数量" width="90" />
          <el-table-column label="小计" width="120">
            <template #default="{ row }">
              <span class="price">{{ formatPrice(row.subtotal) }}</span>
            </template>
          </el-table-column>
        </el-table>

        <p class="order-total">
          共 {{ orderItemCount(order) }} 件，合计
          <span class="price">{{ formatPrice(order.total_amount) }}</span>
        </p>
      </el-card>

      <div class="order-actions">
        <el-button
          v-if="nextAction"
          type="primary"
          :loading="acting"
          @click="onAdvance"
        >
          {{ nextAction.label }}
        </el-button>
        <el-button
          v-if="canCancel"
          type="danger"
          plain
          :loading="acting"
          @click="onCancel"
        >
          取消订单
        </el-button>

        <p class="order-actions__hint">
          订单只能按「客户下单 → 商家确认 → 备货完成 → 开始发货 → 交易完成」
          逐步推进，不可跳序、不可回退；取消后占用库存回补。
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
}

.order-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--sp-3);
}

.order-head__no {
  margin: 0;
  font-size: var(--fs-lg);
  font-weight: 600;
  word-break: break-all;
}

.order-head__time {
  margin: var(--sp-1) 0 0;
  font-size: var(--fs-sm);
  color: var(--c-text-weak);
}

.order-customer {
  margin-top: var(--sp-4);
}

.order-detail__alert,
.order-detail__card {
  margin-top: var(--sp-4);
}

.order-steps__hint {
  margin: var(--sp-3) 0 0;
  font-size: var(--fs-sm);
  color: var(--c-text-weak);
  text-align: center;
}

.item-cell {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
}

.item-cell__thumb {
  width: 44px;
  flex: none;
}

.item-cell__name {
  word-break: break-all;
}

.order-total {
  margin: var(--sp-3) 0 0;
  font-size: var(--fs-sm);
  color: var(--c-text-sub);
  text-align: right;
}

.order-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--sp-2);
  margin-top: var(--sp-4);
}

.order-actions__hint {
  width: 100%;
  margin: var(--sp-1) 0 0;
  font-size: var(--fs-sm);
  color: var(--c-text-weak);
}
</style>
