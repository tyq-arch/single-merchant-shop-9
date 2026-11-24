<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'

import {
  advanceOrder,
  cancelOrder,
  getOrder,
  shipOrder,
  updateShipmentStatus,
} from '@/api/seller'
import EmptyState from '@/components/EmptyState.vue'
import ProductImage from '@/components/ProductImage.vue'
import StatusTag from '@/components/StatusTag.vue'
import { formatPrice } from '@/utils/format'
import {
  CARRIER_OPTIONS,
  ORDER_STATUS_LABELS,
  ORDER_STEPS,
  SHIPMENT_STATUS_LABELS,
  canSellerCancel,
  getNextOrderAction,
  nextShipmentStatus,
  normalizeOrder,
  orderItemCount,
  orderStepIndex,
} from '@/utils/shop'

/**
 * S5 订单详情 / 处理 —— 挑战 C。
 *
 * 商家从「已支付」开始推进：确认 → 备货完成 → 发货（填物流）→ 交易完成；
 * 交易完成前可取消（FR-029）。发货后物流状态由商家手动更新（TBD-09）。
 */
const route = useRoute()
const router = useRouter()

const loading = ref(true)
const errorState = ref('')
const order = ref(null)
const acting = ref(false)

const shipVisible = ref(false)
const shipping = ref(false)
const shipFormRef = ref()
const shipForm = reactive({
  carrier: 'SF',
  trackingNo: '',
})

const shipmentUpdating = ref(false)

const shipRules = {
  carrier: [{ required: true, message: '请选择物流服务商', trigger: 'change' }],
  trackingNo: [
    { required: true, message: '请填写运单号', trigger: 'blur' },
    {
      pattern: /^[A-Za-z0-9-]{6,40}$/,
      message: '运单号应为 6–40 位字母、数字或连字符',
      trigger: 'blur',
    },
  ],
}

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

const nextLogisticsStatus = computed(() => {
  const current = order.value
  if (!current?.shipment || current.status === 'CANCELLED') return ''
  return nextShipmentStatus(current.shipment.status)
})

const nextLogisticsLabel = computed(
  () => SHIPMENT_STATUS_LABELS[nextLogisticsStatus.value] || '',
)

const hasPaymentInfo = computed(() => {
  const payment = order.value?.payment
  if (!payment) return false
  return Boolean(
    payment.methodLabel !== '—' ||
      payment.paidAt ||
      payment.transactionNo ||
      payment.refundStatus,
  )
})

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

/** 推进订单；「备货完成 → 开始发货」必须先填物流信息（FR-049） */
async function onAdvance() {
  if (!order.value || !nextAction.value || acting.value) return
  const action = nextAction.value

  if (action.requiresShipment) {
    shipForm.carrier = order.value.shipment?.carrier || 'SF'
    shipForm.trackingNo = order.value.shipment?.trackingNo || ''
    shipVisible.value = true
    return
  }

  const targetLabel =
    ORDER_STATUS_LABELS[action.targetStatus] || action.label

  try {
    await ElMessageBox.confirm(
      `确认将订单推进到「${targetLabel}」？订单状态不可回退。`,
      action.label,
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

async function onShip() {
  if (!order.value || shipping.value) return

  try {
    await shipFormRef.value.validate()
  } catch {
    return
  }

  shipping.value = true
  try {
    const data = await shipOrder(order.value.id, {
      carrier: shipForm.carrier,
      trackingNo: shipForm.trackingNo.trim(),
    })
    ElMessage.success(data?.message || '已发货')
    shipVisible.value = false
    await load()
  } catch (error) {
    ElMessage.error(error.message)
    if (error.status === 409) {
      shipVisible.value = false
      await load()
    }
  } finally {
    shipping.value = false
  }
}

/** 物流状态由商家手动更新：运输中 → 派送中 → 已签收（TBD-09） */
async function onUpdateShipment() {
  if (!order.value || !nextLogisticsStatus.value || shipmentUpdating.value) return

  try {
    await ElMessageBox.confirm(
      `确认将物流状态更新为「${nextLogisticsLabel.value}」？`,
      '更新物流状态',
      { confirmButtonText: '确认更新', cancelButtonText: '取消', type: 'info' },
    )
  } catch {
    return
  }

  shipmentUpdating.value = true
  try {
    const data = await updateShipmentStatus(
      order.value.id,
      nextLogisticsStatus.value,
    )
    ElMessage.success(data?.message || '物流状态已更新')
    await load()
  } catch (error) {
    ElMessage.error(error.message)
    if (error.status === 409) await load()
  } finally {
    shipmentUpdating.value = false
  }
}

/** 商家取消（FR-029）：交易完成前可用，已支付订单自动退款 */
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
            {{ order.customer?.phone || order.address?.phone || '—' }}
          </el-descriptions-item>
          <el-descriptions-item label="订单总额">
            <span class="price">{{ formatPrice(order.total_amount) }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="支付状态">
            <StatusTag
              v-if="order.payment"
              :status="order.payment.status"
              :label="order.payment.statusLabel"
            />
            <span v-else>—</span>
          </el-descriptions-item>
        </el-descriptions>
      </el-card>

      <el-card v-if="hasPaymentInfo" shadow="never" class="order-detail__card">
        <template #header>支付信息</template>
        <el-descriptions :column="2" border>
          <el-descriptions-item label="支付方式">
            {{ order.payment.methodLabel }}
          </el-descriptions-item>
          <el-descriptions-item label="支付状态">
            <StatusTag
              :status="order.payment.status"
              :label="order.payment.statusLabel"
            />
          </el-descriptions-item>
          <el-descriptions-item label="支付时间">
            {{ order.payment.paidAt || '—' }}
          </el-descriptions-item>
          <el-descriptions-item label="交易号">
            {{ order.payment.transactionNo || '—' }}
          </el-descriptions-item>
          <el-descriptions-item v-if="order.payment.refundStatus" label="退款状态">
            {{ order.payment.refundStatusLabel }}
          </el-descriptions-item>
          <el-descriptions-item
            v-if="order.payment.refundAmount > 0"
            label="退款金额"
          >
            <span class="price">{{ formatPrice(order.payment.refundAmount) }}</span>
          </el-descriptions-item>
        </el-descriptions>
      </el-card>

      <el-card v-if="order.address" shadow="never" class="order-detail__card">
        <template #header>收货地址（下单时固化）</template>
        <p class="address-line">
          {{ order.address.receiver }} {{ order.address.phone }}
        </p>
        <p class="address-line address-line--sub">
          {{ order.address.fullAddress || '—' }}
        </p>
      </el-card>

      <el-alert
        v-if="order.status === 'CANCELLED'"
        class="order-detail__alert"
        type="info"
        :closable="false"
        show-icon
        title="订单已取消，占用库存已回补；已支付订单已触发退款"
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
        <p v-if="order.status === 'PAYMENT_PENDING'" class="order-steps__hint">
          等待客户完成线上支付；24 小时未支付系统将自动取消并回补库存。
        </p>
        <p v-else-if="order.status === 'SHIPPING'" class="order-steps__hint">
          已发货：客户不能再取消，商家仍可在交易完成前取消。
        </p>
      </el-card>

      <el-card v-if="order.shipment" shadow="never" class="order-detail__card">
        <template #header>物流信息</template>
        <el-descriptions :column="2" border>
          <el-descriptions-item label="物流服务商">
            {{ order.shipment.carrierName }}
          </el-descriptions-item>
          <el-descriptions-item label="运单号">
            {{ order.shipment.trackingNo || '—' }}
          </el-descriptions-item>
          <el-descriptions-item label="物流状态">
            <StatusTag
              :status="order.shipment.status"
              :label="order.shipment.statusLabel"
            />
          </el-descriptions-item>
          <el-descriptions-item label="发货时间">
            {{ order.shipment.shippedAt || '—' }}
          </el-descriptions-item>
        </el-descriptions>

        <div v-if="order.shipment.traces.length" class="logistics-traces">
          <p
            v-for="(trace, index) in order.shipment.traces"
            :key="index"
            class="logistics-trace"
          >
            <span class="logistics-trace__time">{{ trace.time || '—' }}</span>
            <span>{{ trace.text || trace.status }}</span>
          </p>
        </div>

        <div v-if="nextLogisticsStatus" class="logistics-update">
          <el-button
            type="primary"
            plain
            :loading="shipmentUpdating"
            @click="onUpdateShipment"
          >
            更新物流状态为「{{ nextLogisticsLabel }}」
          </el-button>
          <span class="logistics-update__hint">
            物流状态由商家手动更新，按 运输中 → 派送中 → 已签收 顺序推进
          </span>
        </div>
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
                <div>
                  <span class="item-cell__name">{{ row.name }}</span>
                  <div v-if="row.aftersale" class="item-cell__aftersale">
                    <StatusTag
                      :status="row.aftersale.status"
                      :label="`售后：${row.aftersale.statusLabel}`"
                    />
                  </div>
                </div>
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
          订单只能按「待支付 → 已支付 → 商家确认 → 备货完成 → 开始发货 → 交易完成」
          逐步推进，不可跳序、不可回退；发货需填写物流服务商与运单号；
          取消后占用库存回补，已支付订单自动退款。
        </p>
      </div>
    </template>

    <el-dialog
      v-model="shipVisible"
      title="填写发货物流"
      width="440px"
      append-to-body
      :close-on-click-modal="!shipping"
    >
      <el-form
        ref="shipFormRef"
        :model="shipForm"
        :rules="shipRules"
        label-position="top"
        @submit.prevent="onShip"
      >
        <el-form-item label="物流服务商" prop="carrier">
          <el-select v-model="shipForm.carrier" class="ship-form__select">
            <el-option
              v-for="carrier in CARRIER_OPTIONS"
              :key="carrier.value"
              :label="carrier.label"
              :value="carrier.value"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="运单号" prop="trackingNo">
          <el-input
            v-model="shipForm.trackingNo"
            maxlength="40"
            placeholder="请输入快递运单号"
          />
        </el-form-item>

        <el-alert
          type="info"
          :closable="false"
          show-icon
          title="确认后订单进入「开始发货」，客户将不能取消订单"
        />
      </el-form>

      <template #footer>
        <el-button :disabled="shipping" @click="shipVisible = false">取消</el-button>
        <el-button type="primary" :loading="shipping" @click="onShip">
          确认发货
        </el-button>
      </template>
    </el-dialog>
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

.address-line {
  margin: 0;
  font-size: var(--fs-sm);
  font-weight: 600;
}

.address-line--sub {
  margin-top: var(--sp-1);
  font-weight: 400;
  color: var(--c-text-sub);
  word-break: break-all;
}

.logistics-traces {
  margin-top: var(--sp-3);
  padding-top: var(--sp-2);
  border-top: 1px solid var(--c-border);
}

.logistics-trace {
  display: flex;
  gap: var(--sp-3);
  padding: var(--sp-1) 0;
  font-size: var(--fs-sm);
  color: var(--c-text-sub);
}

.logistics-trace__time {
  width: 160px;
  flex: none;
  color: var(--c-text-weak);
}

.logistics-update {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--sp-2);
  margin-top: var(--sp-3);
}

.logistics-update__hint {
  font-size: 12px;
  color: var(--c-text-weak);
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

.item-cell__aftersale {
  margin-top: var(--sp-1);
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

.ship-form__select {
  width: 100%;
}
</style>
