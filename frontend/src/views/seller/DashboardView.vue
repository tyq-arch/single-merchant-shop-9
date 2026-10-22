<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'

import { listCustomers, listProducts } from '@/api/seller'
import StatusTag from '@/components/StatusTag.vue'
import { formatPrice } from '@/utils/format'

/**
 * S1 工作台 —— 多商品概览与常用入口。
 */
const loading = ref(true)
const counts = ref({
  ON_SALE: 0,
  FROZEN: 0,
  SOLD_OUT: 0,
  OFF_SHELF: 0,
})
const customerTotal = ref(0)
const recent = ref([])

const cards = [
  { key: 'ON_SALE', label: '在售商品', icon: '🟢', accent: 'success' },
  { key: 'FROZEN', label: '交易中 / 冻结', icon: '🟡', accent: 'warning' },
  { key: 'SOLD_OUT', label: '售罄下架', icon: '🔴', accent: 'danger' },
  { key: 'OFF_SHELF', label: '手动下架', icon: '⚪', accent: 'muted' },
]

async function load() {
  loading.value = true
  try {
    const [onSale, frozen, soldOut, offShelf, customers, latest] = await Promise.all([
      listProducts({ page: 1, page_size: 1, status: 'ON_SALE' }),
      listProducts({ page: 1, page_size: 1, status: 'FROZEN' }),
      listProducts({ page: 1, page_size: 1, status: 'SOLD_OUT' }),
      listProducts({ page: 1, page_size: 1, status: 'OFF_SHELF' }),
      listCustomers({ page: 1, page_size: 1 }),
      listProducts({ page: 1, page_size: 5 }),
    ])

    counts.value = {
      ON_SALE: onSale.total || 0,
      FROZEN: frozen.total || 0,
      SOLD_OUT: soldOut.total || 0,
      OFF_SHELF: offShelf.total || 0,
    }
    customerTotal.value = customers.total || 0
    recent.value = latest.items || []
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
        v-for="card in cards"
        :key="card.key"
        :to="{ name: 'seller-products', query: { status: card.key } }"
        class="stat-card"
        :class="`stat-card--${card.accent}`"
      >
        <span class="stat-card__icon">{{ card.icon }}</span>
        <div>
          <p class="stat-card__value">{{ counts[card.key] }}</p>
          <p class="stat-card__label">{{ card.label }}</p>
        </div>
      </router-link>

      <router-link
        :to="{ name: 'seller-customers' }"
        class="stat-card stat-card--primary"
      >
        <span class="stat-card__icon">🧾</span>
        <div>
          <p class="stat-card__value">{{ customerTotal }}</p>
          <p class="stat-card__label">注册客户</p>
        </div>
      </router-link>
    </div>

    <el-card class="dashboard__card" shadow="never">
      <template #header>
        <div class="dashboard__card-head">
          <span>最近发布的商品</span>
          <router-link :to="{ name: 'seller-products' }">全部商品 →</router-link>
        </div>
      </template>

      <el-table :data="recent" border>
        <el-table-column prop="name" label="商品名称" min-width="200" />
        <el-table-column label="价格" width="120">
          <template #default="{ row }">
            <span class="price">{{ formatPrice(row.price) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="stock" label="库存" width="90" />
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <StatusTag :status="row.status" :label="row.status_label" />
          </template>
        </el-table-column>
        <el-table-column prop="published_at" label="发布时间" width="180" />
      </el-table>

      <el-empty v-if="!recent.length" description="还没有商品，先去发布一件吧" :image-size="80" />
    </el-card>

    <el-card class="dashboard__card" shadow="never">
      <template #header>快捷入口</template>
      <div class="quick-links">
        <router-link :to="{ name: 'seller-publish' }">➕ 发布商品</router-link>
        <router-link :to="{ name: 'seller-categories' }">🗂️ 类别管理</router-link>
        <router-link :to="{ name: 'seller-intents' }">👥 意向队列</router-link>
        <router-link :to="{ name: 'seller-history' }">📚 历史商品</router-link>
        <router-link :to="{ name: 'seller-customers' }">🧾 客户台账</router-link>
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
