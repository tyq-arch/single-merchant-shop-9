<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'

import { payOrder } from '@/api/customer'
import { formatPrice } from '@/utils/format'
import { PAYMENT_METHODS } from '@/utils/shop'

/**
 * 线上支付弹窗（FR-039 / FR-040）。
 *
 * 本期支付为模拟/预留接口（TBD-03）：确认后直接调用后端，
 * 由后端把订单置为「已支付」并记录支付流水。
 * 支付失败订单保持待支付，允许重新发起（FR-042）。
 */
const props = defineProps({
  modelValue: { type: Boolean, default: false },
  order: { type: Object, default: null },
  methods: { type: Array, default: () => PAYMENT_METHODS },
})

const emit = defineEmits(['update:modelValue', 'success'])

const method = ref('ALIPAY')
const submitting = ref(false)

const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value),
})

const amount = computed(() => Number(props.order?.total_amount) || 0)

watch(
  () => props.modelValue,
  (open) => {
    if (open) {
      method.value = 'ALIPAY'
      submitting.value = false
    }
  },
)

async function onSubmit() {
  if (!props.order?.id || submitting.value) return
  if (!method.value) {
    ElMessage.warning('请选择支付方式')
    return
  }

  submitting.value = true
  try {
    const data = await payOrder(props.order.id, method.value)
    ElMessage.success(data?.message || '支付成功')
    visible.value = false
    emit('success', data)
  } catch (error) {
    ElMessage.error(error.message || '支付失败，请重试')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <el-dialog
    v-model="visible"
    title="订单支付"
    width="420px"
    append-to-body
    :close-on-click-modal="!submitting"
  >
    <div class="pay">
      <div class="pay__amount">
        <span class="pay__amount-label">应付金额</span>
        <span class="pay__amount-value price">{{ formatPrice(amount) }}</span>
      </div>

      <p class="pay__label">选择支付方式</p>
      <el-radio-group v-model="method" class="pay__methods">
        <el-radio
          v-for="item in methods"
          :key="item.value"
          :value="item.value"
          border
          class="pay__method"
        >
          {{ item.label }}
        </el-radio>
      </el-radio-group>

      <el-alert
        class="pay__note"
        type="info"
        :closable="false"
        show-icon
        title="本期为模拟支付：确认后订单直接进入「已支付」，不会产生真实扣款"
      />
    </div>

    <template #footer>
      <el-button :disabled="submitting" @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="onSubmit">
        确认支付
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.pay__amount {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  padding: var(--sp-3) var(--sp-4);
  background: var(--c-muted-soft);
  border-radius: var(--r-card);
}

.pay__amount-label {
  font-size: var(--fs-sm);
  color: var(--c-text-sub);
}

.pay__amount-value {
  font-size: var(--fs-xl);
  font-weight: 700;
}

.pay__label {
  margin: var(--sp-4) 0 var(--sp-2);
  font-size: var(--fs-sm);
  color: var(--c-text-sub);
}

.pay__methods {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--sp-2);
}

.pay__method {
  width: 100%;
  margin: 0;
}

.pay__note {
  margin-top: var(--sp-4);
}
</style>
