<script setup>
import { computed } from 'vue'

/**
 * 状态徽标。
 *
 * 文案一律由使用方传入后端下发的 status_label / result_label，
 * 本组件只负责依据状态码附加配色，不维护中文映射表
 * （见 docs/05-页面原型设计.md 第 3.1 节）。
 */
const props = defineProps({
  status: { type: String, default: '' },
  label: { type: String, default: '' },
  size: { type: String, default: 'default' }, // default | large
})

const VARIANT_BY_STATUS = {
  // 商品状态
  ON_SALE: 'success',
  FROZEN: 'warning',
  OFF_SHELF: 'muted',
  // 意向状态
  QUEUED: 'primary',
  IN_TRANSACTION: 'warning',
  REQUEUED: 'primary',
  SUCCESS: 'success',
  FAILED: 'danger',
  CANCELLED: 'muted',
  VOIDED: 'muted',
}

const variant = computed(() => VARIANT_BY_STATUS[props.status] || 'muted')
</script>

<template>
  <span class="status-tag" :class="[`status-tag--${variant}`, { 'status-tag--large': size === 'large' }]">
    {{ label || status }}
  </span>
</template>

<style scoped>
.status-tag {
  display: inline-block;
  padding: 2px 10px;
  font-size: var(--fs-sm);
  line-height: 1.6;
  border-radius: 999px;
  white-space: nowrap;
}

.status-tag--large {
  padding: 4px 14px;
  font-size: var(--fs-base);
}

.status-tag--primary {
  color: var(--c-primary);
  background: var(--c-primary-soft);
}

.status-tag--success {
  color: var(--c-success);
  background: var(--c-success-soft);
}

.status-tag--warning {
  color: var(--c-warning);
  background: var(--c-warning-soft);
}

.status-tag--danger {
  color: var(--c-danger);
  background: var(--c-danger-soft);
}

.status-tag--muted {
  color: var(--c-muted);
  background: var(--c-muted-soft);
}
</style>
