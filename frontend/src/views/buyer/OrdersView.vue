<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'

import { listOrders } from '@/api/customer'
import EmptyState from '@/components/EmptyState.vue'
import StatusTag from '@/components/StatusTag.vue'
import { formatPrice } from '@/utils/format'
import {
  ORDER_STATUS_OPTIONS,
  normalizeOrder,
  orderItemCount,
  orderItemsSummary,
} from '@/utils/shop'

/**
 * B7 我的订单 —— 挑战 B。
 *
 * 订单 5 态 + 已取消；记录含订单号、商品清单、总金额、下单时间、状态（TBD-09）。
 * 客户侧只读，取消入口在订单详情页（开始发货前可取消）。
 */
const loading = ref(true)
const items = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)
const status = ref('')

async function load() {
  loading.value = true
  try {
    const result = await listOrders({
      page: page.value,
      page_size: pageSize.value,
      status: status.value || undefined,
    })
    items.value = (result.items || []).map(normalizeOrder)
    total.value = result.total || 0
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    loading.value = false
  }
}

function onFilterChange() {
  page.value = 1
  load()
}

function onPageChange(nextPage) {
  page.value = nextPage
  load()
}

onMounted(load)
</script>

<template>
  <div class="orders">
    <div class="orders__bar">
      <el-select
        v-model="status"
        class="orders__filter"
        placeholder="全部状态"
        @change="onFilterChange"
      >
        <el-option
          v-for="option in ORDER_STATUS_OPTIONS"
          :key="option.value"
          :label="option.label"
          :value="option.value"
        />
      </el-select>
      <span class="orders__hint">共 {{ total }} 笔订单</span>
    </div>

    <div v-loading="loading" class="orders__body">
      <template v-if="items.length">
        <router-link
          v-for="order in items"
          :key="order.id"
          :to="{ name: 'customer-order-detail', params: { id: order.id } }"
          class="order-card"
        >
          <div class="order-card__head">
            <span class="order-card__no">
              订单号：{{ order.order_no || order.id }}
            </span>
            <StatusTag :status="order.status" :label="order.statusLabel" />
          </div>

          <p class="order-card__items">{{ orderItemsSummary(order) }}</p>

          <div class="order-card__foot">
            <span class="order-card__time">{{ order.created_at || '—' }}</span>
            <span class="order-card__amount">
              共 {{ orderItemCount(order) }} 件 ·
              <span class="price">{{ formatPrice(order.total_amount) }}</span>
            </span>
          </div>
        </router-link>
      </template>

      <EmptyState
        v-else-if="!loading"
        icon="🧾"
        text="还没有订单"
        hint="从购物车选件下单后，订单会出现在这里"
      >
        <router-link class="orders__link" to="/">去逛逛</router-link>
      </EmptyState>
    </div>

    <div v-if="total > pageSize" class="orders__pager">
      <el-pagination
        v-model:current-page="page"
        :page-size="pageSize"
        :total="total"
        layout="prev, pager, next"
        small
        background
        @current-change="onPageChange"
      />
    </div>
  </div>
</template>

<style scoped>
.orders__bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-3);
  margin-bottom: var(--sp-3);
}

.orders__filter {
  width: 150px;
}

.orders__hint {
  font-size: var(--fs-sm);
  color: var(--c-text-weak);
}

.orders__body {
  min-height: 200px;
}

.order-card {
  display: block;
  padding: var(--sp-3);
  color: inherit;
  background: var(--c-surface);
  border-radius: var(--r-card);
  box-shadow: var(--shadow-sm);
}

.order-card + .order-card {
  margin-top: var(--sp-3);
}

.order-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-2);
}

.order-card__no {
  min-width: 0;
  font-size: var(--fs-sm);
  color: var(--c-text-sub);
  word-break: break-all;
}

.order-card__items {
  margin: var(--sp-2) 0;
  font-size: var(--fs-base);
  font-weight: 600;
  word-break: break-all;
}

.order-card__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-2);
  font-size: var(--fs-sm);
}

.order-card__time {
  color: var(--c-text-weak);
}

.order-card__amount {
  color: var(--c-text-sub);
}

.orders__pager {
  display: flex;
  justify-content: center;
  margin-top: var(--sp-4);
}

.orders__link {
  color: var(--c-primary);
}
</style>
