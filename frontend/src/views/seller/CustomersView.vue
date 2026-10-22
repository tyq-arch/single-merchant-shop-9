<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'

import { getCustomerDetail, listCustomers } from '@/api/seller'
import StatusTag from '@/components/StatusTag.vue'
import { formatPrice } from '@/utils/format'

/**
 * S7 客户台账 —— FR-032 / FR-033 / AC-009。
 *
 * 列表仅展示固定字段：用户名、电话、默认交易地点、注册时间；
 * 详情抽屉中展示该客户的购买历史。
 */
const loading = ref(true)
const items = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)
const keyword = ref('')

const drawer = ref(false)
const detailLoading = ref(false)
const detail = ref(null)

async function load() {
  loading.value = true
  try {
    const result = await listCustomers({
      page: page.value,
      page_size: pageSize.value,
      keyword: keyword.value.trim() || undefined,
    })
    items.value = result.items || []
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

async function openDetail(row) {
  drawer.value = true
  detailLoading.value = true
  detail.value = null
  try {
    detail.value = await getCustomerDetail(row.id)
  } catch (error) {
    ElMessage.error(error.message)
    drawer.value = false
  } finally {
    detailLoading.value = false
  }
}

onMounted(load)
</script>

<template>
  <el-card shadow="never">
    <div class="customers__toolbar">
      <el-input
        v-model="keyword"
        placeholder="搜索用户名或电话"
        clearable
        class="customers__keyword"
        @keyup.enter="search"
        @clear="search"
      />
      <el-button type="primary" @click="search">查询</el-button>
    </div>

    <el-table v-loading="loading" :data="items" border>
      <el-table-column prop="username" label="用户名" min-width="150" />
      <el-table-column prop="phone" label="电话" width="150" />
      <el-table-column prop="trade_location" label="默认交易地点" min-width="180" />
      <el-table-column prop="registered_at" label="注册时间" width="180" />
      <el-table-column prop="purchase_count" label="成交次数" width="100" />
      <el-table-column label="操作" width="110" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row)">购买历史</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-empty v-if="!items.length && !loading" description="暂无注册客户" :image-size="80" />

    <div class="customers__pager">
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

  <el-drawer v-model="drawer" title="客户详情" size="680px">
    <div v-loading="detailLoading" class="customer-detail">
      <template v-if="detail">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="用户名">{{ detail.username }}</el-descriptions-item>
          <el-descriptions-item label="电话">{{ detail.phone }}</el-descriptions-item>
          <el-descriptions-item label="默认交易地点">
            {{ detail.trade_location }}
          </el-descriptions-item>
          <el-descriptions-item label="注册时间">
            {{ detail.registered_at }}
          </el-descriptions-item>
          <el-descriptions-item label="成交次数">
            {{ detail.purchase_count }}
          </el-descriptions-item>
        </el-descriptions>

        <h3 class="customer-detail__title">购买历史</h3>

        <el-table :data="detail.orders || []" border>
          <el-table-column label="商品" min-width="160">
            <template #default="{ row }">{{ row.product?.name || '—' }}</template>
          </el-table-column>
          <el-table-column label="金额" width="100">
            <template #default="{ row }">
              <span class="price">{{ formatPrice(row.product?.price) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="120">
            <template #default="{ row }">
              <StatusTag :status="row.status" :label="row.status_label" />
            </template>
          </el-table-column>
          <el-table-column prop="queue_position" label="位次" width="70" />
          <el-table-column prop="created_at" label="提交时间" width="170" />
          <el-table-column label="交易结果" width="110">
            <template #default="{ row }">
              {{ row.transaction?.result_label || '—' }}
            </template>
          </el-table-column>
        </el-table>

        <el-empty
          v-if="!(detail.orders || []).length"
          description="该客户暂无购买记录"
          :image-size="70"
        />
      </template>
    </div>
  </el-drawer>
</template>

<style scoped>
.customers__toolbar {
  display: flex;
  gap: var(--sp-2);
  margin-bottom: var(--sp-3);
}

.customers__keyword {
  width: 260px;
}

.customers__pager {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--sp-3);
}

.customer-detail__title {
  margin: var(--sp-5) 0 var(--sp-3);
  font-size: var(--fs-base);
  font-weight: 600;
}
</style>
