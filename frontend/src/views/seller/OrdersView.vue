<script setup>
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

import { listOrders } from '@/api/seller'
import StatusTag from '@/components/StatusTag.vue'
import { formatPrice } from '@/utils/format'
import {
  ORDER_STATUS_OPTIONS,
  normalizeOrder,
  orderItemCount,
} from '@/utils/shop'

/**
 * S5 订单管理 —— 挑战 B。
 *
 * 订单 5 态由商家后台按序推进；商家可在交易完成前取消。
 * 列表支持按状态筛选、按订单号 / 客户搜索。
 */
const route = useRoute()
const router = useRouter()

const loading = ref(true)
const items = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)
const status = ref(
  typeof route.query.status === 'string' ? route.query.status : '',
)
const keyword = ref('')

async function load() {
  loading.value = true
  try {
    const result = await listOrders({
      page: page.value,
      page_size: pageSize.value,
      status: status.value || undefined,
      keyword: keyword.value.trim() || undefined,
    })
    items.value = (result.items || []).map(normalizeOrder)
    total.value = result.total || 0
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    loading.value = false
  }
}

function search() {
  page.value = 1
  load()
}

function onPageChange(nextPage) {
  page.value = nextPage
  load()
}

function openDetail(row) {
  router.push({ name: 'seller-order-detail', params: { id: row.id } })
}

onMounted(load)
</script>

<template>
  <el-card shadow="never">
    <div class="orders__toolbar">
      <el-select
        v-model="status"
        class="orders__status"
        placeholder="全部状态"
        @change="search"
      >
        <el-option
          v-for="option in ORDER_STATUS_OPTIONS"
          :key="option.value"
          :label="option.label"
          :value="option.value"
        />
      </el-select>

      <el-input
        v-model="keyword"
        placeholder="搜索订单号或客户"
        clearable
        class="orders__keyword"
        @keyup.enter="search"
        @clear="search"
      />
      <el-button type="primary" @click="search">查询</el-button>
    </div>

    <el-table v-loading="loading" :data="items" border>
      <el-table-column label="订单号" min-width="190">
        <template #default="{ row }">{{ row.order_no || row.id }}</template>
      </el-table-column>

      <el-table-column label="客户" width="130">
        <template #default="{ row }">
          {{ row.customer?.username || row.customer_name || '—' }}
        </template>
      </el-table-column>

      <el-table-column label="电话" width="140">
        <template #default="{ row }">
          {{ row.customer?.phone || '—' }}
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

      <el-table-column label="状态" width="120">
        <template #default="{ row }">
          <StatusTag :status="row.status" :label="row.statusLabel" />
        </template>
      </el-table-column>

      <el-table-column prop="created_at" label="下单时间" width="180" />

      <el-table-column label="操作" width="110" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row)">
            查看 / 处理
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-empty
      v-if="!items.length && !loading"
      description="暂无符合条件的订单"
      :image-size="80"
    />

    <div class="orders__pager">
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        background
        @current-change="onPageChange"
        @size-change="search"
      />
    </div>
  </el-card>
</template>

<style scoped>
.orders__toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: var(--sp-2);
  margin-bottom: var(--sp-3);
}

.orders__status {
  width: 160px;
}

.orders__keyword {
  width: 240px;
}

.orders__pager {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--sp-3);
}
</style>
