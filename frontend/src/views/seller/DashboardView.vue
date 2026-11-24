<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'

import {
  listAfterSales,
  listCustomers,
  listOrders,
  listProducts,
} from '@/api/seller'
import StatusTag from '@/components/StatusTag.vue'
import { formatPrice } from '@/utils/format'
import { normalizeOrder, orderItemCount } from '@/utils/shop'

/**
 * S1 工作台 —— 挑战 C。
 *
 * 关注点：待支付 / 待确认 / 待发货 / 待处理售后，以及最近订单。
 */
const loading = ref(true)
const counts = ref({
  ON_SALE: 0,
  PAYMENT_PENDING: 0,
  PAID: 0,
  STOCK_READY: 0,
  AFTER_SALE_PENDING: 0,
})
const customerTotal = ref(0)
const recentOrders = ref([])

async function load() {
  loading.value = true
  try {
    const [
      onSale,
      paymentPending,
      paid,
      stockReady,
      aftersales,
      customers,
      latest,
    ] = await Promise.all([
      listProducts({ page: 1, page_size: 1, status: 'ON_SALE' }),
      listOrders({ page: 1, page_size: 1, status: 'PAYMENT_PENDING' }),
      listOrders({ page: 1, page_size: 1, status: 'PAID' }),
      listOrders({ page: 1, page_size: 1, status: 'STOCK_READY' }),
      listAfterSales({ page: 1, page_size: 1, status: 'PENDING' }),
      listCustomers({ page: 1, page_size: 1 }),
      listOrders({ page: 1, page_size: 5 }),
    ])

    counts.value = {
      ON_SALE: onSale.total || 0,
      PAYMENT_PENDING: paymentPending.total || 0,
      PAID: paid.total || 0,
      STOCK_READY: stockReady.total || 0,
      AFTER_SALE_PENDING: aftersales.total || 0,
    }
    customerTotal.value = customers.total || 0
    recentOrders.value = (latest.items || []).map(normalizeOrder)
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div v-loading="loading" class="dashboard">
    <div class="stat-grid">
      <router-link
        :to="{ name: 'seller-products', query: { status: 'ON_SALE' } }"
        class="stat-card stat-card--success"
      >
        <span class="stat-card__icon">🟢</span>
        <div>
          <p class="stat-card__value">{{ counts.ON_SALE }}</p>
          <p class="stat-card__label">在售商品</p>
        </div>
      </router-link>

      <router-link
        :to="{ name: 'seller-orders', query: { status: 'PAYMENT_PENDING' } }"
        class="stat-card stat-card--muted"
      >
        <span class="stat-card__icon">⏰</span>
        <div>
          <p class="stat-card__value">{{ counts.PAYMENT_PENDING }}</p>
          <p class="stat-card__label">待支付订单</p>
        </div>
      </router-link>

      <router-link
        :to="{ name: 'seller-orders', query: { status: 'PAID' } }"
        class="stat-card stat-card--warning"
      >
        <span class="stat-card__icon">📥</span>
        <div>
          <p class="stat-card__value">{{ counts.PAID }}</p>
          <p class="stat-card__label">待确认订单</p>
        </div>
      </router-link>

      <router-link
        :to="{ name: 'seller-orders', query: { status: 'STOCK_READY' } }"
        class="stat-card stat-card--danger"
      >
        <span class="stat-card__icon">📦</span>
        <div>
          <p class="stat-card__value">{{ counts.STOCK_READY }}</p>
          <p class="stat-card__label">待发货订单</p>
        </div>
      </router-link>

      <router-link
        :to="{ name: 'seller-aftersales', query: { status: 'PENDING' } }"
        class="stat-card stat-card--primary"
      >
        <span class="stat-card__icon">🛠️</span>
        <div>
          <p class="stat-card__value">{{ counts.AFTER_SALE_PENDING }}</p>
          <p class="stat-card__label">待处理售后</p>
        </div>
      </router-link>

      <router-link
        :to="{ name: 'seller-customers' }"
        class="stat-card stat-card--muted"
      >
        <span class="stat-card__icon">👥</span>
        <div>
          <p class="stat-card__value">{{ customerTotal }}</p>
          <p class="stat-card__label">注册客户</p>
        </div>
      </router-link>
    </div>

    <el-card class="dashboard__card" shadow="never">
      <template #header>
        <div class="dashboard__card-head">
          <span>最近订单</span>
          <router-link :to="{ name: 'seller-orders' }">全部订单 →</router-link>
        </div>
      </template>

      <el-table :data="recentOrders" border>
        <el-table-column label="订单号" min-width="180">
          <template #default="{ row }">{{ row.order_no || row.id }}</template>
        </el-table-column>
        <el-table-column label="客户" width="130">
          <template #default="{ row }">
            {{ row.customer?.username || row.customer_name || '—' }}
          </template>
        </el-table-column>
        <el-table-column label="商品件数" width="100">
          <template #default="{ row }">{{ orderItemCount(row) }}</template>
        </el-table-column>
        <el-table-column label="金额" width="120">
          <template #default="{ row }">
            <span class="price">{{ formatPrice(row.total_amount) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="支付" width="100">
          <template #default="{ row }">
            <StatusTag
              v-if="row.payment"
              :status="row.payment.status"
              :label="row.payment.statusLabel"
            />
            <span v-else>—</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <StatusTag :status="row.status" :label="row.statusLabel" />
          </template>
        </el-table-column>
        <el-table-column prop="created_at" label="下单时间" width="180" />
      </el-table>

      <el-empty
        v-if="!recentOrders.length"
        description="还没有订单"
        :image-size="80"
      />
    </el-card>

    <el-card class="dashboard__card" shadow="never">
      <template #header>快捷入口</template>
      <div class="quick-links">
        <router-link :to="{ name: 'seller-publish' }">➕ 发布商品</router-link>
        <router-link :to="{ name: 'seller-categories' }">🗂️ 类别管理</router-link>
        <router-link :to="{ name: 'seller-orders' }">🧾 订单管理</router-link>
        <router-link :to="{ name: 'seller-aftersales' }">🛠️ 售后管理</router-link>
        <router-link :to="{ name: 'seller-customers' }">👥 客户台账</router-link>
        <router-link :to="{ name: 'seller-logs' }">📋 操作日志</router-link>
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.stat-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: var(--sp-4);
}

.stat-card {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  padding: var(--sp-4);
  color: inherit;
  background: var(--c-surface);
  border-left: 4px solid var(--c-muted);
  border-radius: var(--r-card);
  box-shadow: var(--shadow-sm);
}

.stat-card--success {
  border-left-color: var(--c-success);
}

.stat-card--warning {
  border-left-color: var(--c-warning);
}

.stat-card--danger {
  border-left-color: var(--c-danger);
}

.stat-card--muted {
  border-left-color: var(--c-muted);
}

.stat-card--primary {
  border-left-color: var(--c-primary);
}

.stat-card__icon {
  font-size: 26px;
}

.stat-card__value {
  margin: 0;
  font-size: var(--fs-xl);
  font-weight: 700;
  line-height: 1.1;
}

.stat-card__label {
  margin: 2px 0 0;
  font-size: var(--fs-sm);
  color: var(--c-text-weak);
}

.dashboard__card {
  margin-top: var(--sp-4);
}

.dashboard__card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: var(--fs-sm);
}

.quick-links {
  display: flex;
  flex-wrap: wrap;
  gap: var(--sp-3);
}

.quick-links a {
  padding: var(--sp-2) var(--sp-4);
  font-size: var(--fs-sm);
  background: var(--c-primary-soft);
  border-radius: var(--r-btn);
}
</style>
