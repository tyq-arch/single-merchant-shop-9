<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

import { cancelOrder, getOrder } from '@/api/customer'
import AfterSaleApplyDialog from '@/components/AfterSaleApplyDialog.vue'
import EmptyState from '@/components/EmptyState.vue'
import PaymentDialog from '@/components/PaymentDialog.vue'
import ProductImage from '@/components/ProductImage.vue'
import StatusTag from '@/components/StatusTag.vue'
import { formatPrice } from '@/utils/format'
import {
  ORDER_STEPS,
  canApplyAfterSale,
  canCustomerCancel,
  canPayOrder,
  cancelReasonText,
  formatCountdown,
  normalizeOrder,
  orderItemCount,
  orderStepIndex,
  paymentDeadline,
} from '@/utils/shop'

/**
 * C 订单详情（客户侧）—— 挑战 C。
 *
 * 待支付：显示 24 小时支付倒计时与支付入口；
 * 物流：商家发货后可查物流服务商 / 运单号 / 轨迹；
 * 售后：交易完成后可对订单内某件商品发起售后申请。
 * 客户侧只读，取消入口在「开始发货」之前可用。
 */
const route = useRoute()
const router = useRouter()

const loading = ref(true)
const errorState = ref('')
const order = ref(null)
const cancelling = ref(false)

const payVisible = ref(false)
const afterSaleVisible = ref(false)
const afterSaleItem = ref(null)

/** 每秒刷新支付倒计时 */
const now = ref(Date.now())
let timer = null

const stepIndex = computed(() => orderStepIndex(order.value?.status))

const activeStep = computed(() => {
  if (order.value?.status === 'COMPLETED') return ORDER_STEPS.length
  return Math.max(stepIndex.value, 0)
})

const canCancel = computed(
  () => order.value && canCustomerCancel(order.value.status),
)

const canPay = computed(
  () => order.value && canPayOrder(order.value.status),
)

const canAfterSale = computed(
  () => order.value && canApplyAfterSale(order.value.status),
)

const deadline = computed(() => paymentDeadline(order.value))
const remainingMs = computed(() => {
  if (!deadline.value) return 0
  return deadline.value.getTime() - now.value
})
const payExpired = computed(() => Boolean(deadline.value) && remainingMs.value <= 0)
const remainingText = computed(() => formatCountdown(remainingMs.value))

/** 超时后只主动刷新一次，避免接口仍返回待支付时反复请求 */
const expireRefreshed = ref(false)
watch(payExpired, (expired) => {
  if (!expired || expireRefreshed.value) return
  if (order.value?.status !== 'PAYMENT_PENDING') return
  expireRefreshed.value = true
  load()
})
// 切换订单时重置，保证新订单超时也能主动刷新
watch(() => route.params.id, () => {
  expireRefreshed.value = false
})

const hasPaymentInfo = computed(() => {
  const payment = order.value?.payment
  if (!payment) return false
  return Boolean(
    payment.methodLabel !== '—' ||
      payment.paidAt ||
      payment.transactionNo ||
      payment.refundStatus ||
      payment.refundTransactionNo ||
      payment.refundedAt ||
      payment.status === 'FAILED',
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

/** 客户取消（FR-028）：开始发货前可用，取消后占用库存回补，已支付则退款 */
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

async function onPaySuccess() {
  await load()
}

function openAfterSale(item) {
  afterSaleItem.value = item
  afterSaleVisible.value = true
}

async function onAfterSaleSuccess() {
  await load()
}

function openAfterSaleDetail(item) {
  if (!item.aftersale?.id) return
  router.push({
    name: 'customer-aftersale-detail',
    params: { id: item.aftersale.id },
  })
}

onMounted(() => {
  load()
  timer = setInterval(() => {
    now.value = Date.now()
  }, 1000)
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
})
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

      <!-- 待支付：倒计时 + 支付入口 -->
      <section v-if="canPay" class="order-pay">
        <div>
          <p class="order-pay__label">待支付</p>
          <p class="order-pay__time">
            <template v-if="deadline && !payExpired">
              剩余支付时间 <strong>{{ remainingText }}</strong>
            </template>
            <template v-else-if="payExpired">
              已超过 24 小时支付时限，系统将自动取消订单
            </template>
            <template v-else>请在 24 小时内完成支付</template>
          </p>
          <p v-if="order.payment?.status === 'FAILED'" class="order-pay__failed">
            上次支付未成功，订单仍为待支付，可重新发起支付
          </p>
        </div>
        <el-button type="primary" :disabled="payExpired" @click="payVisible = true">
          去支付
        </el-button>
      </section>

      <!-- 支付信息（已支付 / 退款） -->
      <section v-if="hasPaymentInfo" class="order-info">
        <h2 class="order-info__title">支付信息</h2>
        <div class="order-info__row">
          <span>支付方式</span>
          <span>{{ order.payment.methodLabel }}</span>
        </div>
        <div class="order-info__row">
          <span>支付状态</span>
          <StatusTag
            :status="order.payment.status"
            :label="order.payment.statusLabel"
          />
        </div>
        <div v-if="order.payment.paidAt" class="order-info__row">
          <span>支付时间</span>
          <span>{{ order.payment.paidAt }}</span>
        </div>
        <div v-if="order.payment.transactionNo" class="order-info__row">
          <span>交易号</span>
          <span class="order-info__mono">{{ order.payment.transactionNo }}</span>
        </div>
        <div v-if="order.payment.refundStatus" class="order-info__row">
          <span>退款状态</span>
          <span>{{ order.payment.refundStatusLabel }}</span>
        </div>
        <div v-if="order.payment.refundAmount > 0" class="order-info__row">
          <span>退款金额</span>
          <span class="price">{{ formatPrice(order.payment.refundAmount) }}</span>
        </div>
        <div v-if="order.payment.refundTransactionNo" class="order-info__row">
          <span>退款流水号</span>
          <span class="order-info__mono">{{ order.payment.refundTransactionNo }}</span>
        </div>
        <div v-if="order.payment.refundedAt" class="order-info__row">
          <span>退款时间</span>
          <span>{{ order.payment.refundedAt }}</span>
        </div>
      </section>

      <!-- 收货地址快照 -->
      <section v-if="order.address" class="order-info">
        <h2 class="order-info__title">收货地址</h2>
        <p class="order-address__head">
          {{ order.address.receiver }} {{ order.address.phone }}
        </p>
        <p class="order-address__text">{{ order.address.fullAddress || '—' }}</p>
        <p class="order-address__hint">下单时固化的地址，后续修改地址不影响本订单</p>
      </section>

      <el-alert
        v-if="order.status === 'CANCELLED'"
        class="order-detail__alert"
        type="info"
        :closable="false"
        show-icon
        :title="`${cancelReasonText(order)}，占用库存已回补；已支付订单的款项将原路退回`"
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

      <!-- 物流信息 -->
      <section v-if="order.shipment" class="order-info">
        <h2 class="order-info__title">物流信息</h2>
        <div class="order-info__row">
          <span>物流服务商</span>
          <span>{{ order.shipment.carrierName }}</span>
        </div>
        <div class="order-info__row">
          <span>运单号</span>
          <span class="order-info__mono">{{ order.shipment.trackingNo || '—' }}</span>
        </div>
        <div class="order-info__row">
          <span>物流状态</span>
          <StatusTag
            :status="order.shipment.status"
            :label="order.shipment.statusLabel"
          />
        </div>
        <div v-if="order.shipment.shippedAt" class="order-info__row">
          <span>发货时间</span>
          <span>{{ order.shipment.shippedAt }}</span>
        </div>

        <div v-if="order.shipment.traces.length" class="order-traces">
          <p class="order-traces__title">物流轨迹</p>
          <div
            v-for="(trace, index) in order.shipment.traces"
            :key="index"
            class="order-trace"
          >
            <span class="order-trace__dot" />
            <div>
              <p class="order-trace__text">{{ trace.text || trace.status }}</p>
              <p class="order-trace__time">{{ trace.time || '—' }}</p>
            </div>
          </div>
        </div>
        <p v-else class="order-info__hint">
          物流状态由商家手动更新，暂无更多轨迹。
        </p>
      </section>

      <section class="order-items">
        <h2 class="order-items__title">商品清单</h2>
        <div
          v-for="item in order.items"
          :key="item.id || item.itemId || item.productId"
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

            <div v-if="item.aftersale" class="order-item__aftersale">
              <StatusTag
                :status="item.aftersale.status"
                :label="`售后：${item.aftersale.statusLabel}`"
              />
              <el-button link type="primary" @click="openAfterSaleDetail(item)">
                查看售后
              </el-button>
            </div>
            <el-button
              v-else-if="canAfterSale"
              class="order-item__aftersale-btn"
              link
              type="primary"
              @click="openAfterSale(item)"
            >
              申请售后
            </el-button>
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
          订单进入「开始发货」前可取消，取消后占用库存会回补；已支付订单会自动退款。
        </p>
      </div>
    </template>

    <PaymentDialog
      v-model="payVisible"
      :order="order"
      @success="onPaySuccess"
    />

    <AfterSaleApplyDialog
      v-model="afterSaleVisible"
      :order="order"
      :item="afterSaleItem"
      @success="onAfterSaleSuccess"
    />
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
.order-pay,
.order-info,
.order-items,
.order-steps {
  padding: var(--sp-3);
  background: var(--c-surface);
  border-radius: var(--r-card);
  box-shadow: var(--shadow-sm);
}

.order-card + .order-pay,
.order-card + .order-info,
.order-card + .order-steps,
.order-pay + .order-info,
.order-info + .order-info,
.order-info + .order-steps,
.order-steps + .order-info,
.order-info + .order-items,
.order-steps + .order-items {
  margin-top: var(--sp-3);
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

.order-pay {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-3);
  margin-top: var(--sp-3);
  background: var(--c-warning-soft);
}

.order-pay__label {
  margin: 0;
  font-size: var(--fs-base);
  font-weight: 600;
  color: var(--c-warning);
}

.order-pay__time {
  margin: var(--sp-1) 0 0;
  font-size: var(--fs-sm);
  color: var(--c-text-sub);
}

.order-pay__time strong {
  font-variant-numeric: tabular-nums;
}

.order-pay__failed {
  margin: var(--sp-1) 0 0;
  font-size: 12px;
  color: var(--c-danger);
}

.order-info {
  margin-top: var(--sp-3);
}

.order-info__title {
  margin: 0 0 var(--sp-2);
  font-size: var(--fs-base);
  font-weight: 600;
}

.order-info__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-3);
  padding: var(--sp-2) 0;
  font-size: var(--fs-sm);
  color: var(--c-text-sub);
}

.order-info__row + .order-info__row {
  border-top: 1px solid var(--c-border);
}

.order-info__mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  word-break: break-all;
  text-align: right;
}

.order-info__hint {
  margin: var(--sp-2) 0 0;
  font-size: 12px;
  color: var(--c-text-weak);
}

.order-address__head {
  margin: 0;
  font-size: var(--fs-sm);
  font-weight: 600;
}

.order-address__text {
  margin: var(--sp-1) 0 0;
  font-size: var(--fs-sm);
  color: var(--c-text-sub);
  word-break: break-all;
}

.order-address__hint {
  margin: var(--sp-2) 0 0;
  font-size: 12px;
  color: var(--c-text-weak);
}

.order-detail__alert {
  margin-top: var(--sp-3);
}

.order-steps {
  margin-top: var(--sp-3);
  padding-top: var(--sp-4);
  padding-bottom: var(--sp-4);
}

.order-steps__hint {
  margin: var(--sp-3) 0 0;
  font-size: var(--fs-sm);
  color: var(--c-text-weak);
  text-align: center;
}

.order-traces {
  margin-top: var(--sp-3);
  padding-top: var(--sp-2);
  border-top: 1px solid var(--c-border);
}

.order-traces__title {
  margin: 0 0 var(--sp-2);
  font-size: var(--fs-sm);
  font-weight: 600;
  color: var(--c-text-sub);
}

.order-trace {
  display: flex;
  gap: var(--sp-2);
  padding: var(--sp-1) 0;
}

.order-trace__dot {
  width: 6px;
  height: 6px;
  flex: none;
  margin-top: 7px;
  background: var(--c-primary);
  border-radius: 50%;
}

.order-trace__text {
  margin: 0;
  font-size: var(--fs-sm);
  color: var(--c-text-sub);
}

.order-trace__time {
  margin: 1px 0 0;
  font-size: 12px;
  color: var(--c-text-weak);
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
  align-items: flex-start;
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

.order-item__aftersale {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  margin-top: var(--sp-1);
}

.order-item__aftersale-btn {
  margin-top: var(--sp-1);
  padding: 0;
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
