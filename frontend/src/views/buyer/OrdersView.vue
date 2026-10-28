<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'

import { listOrders } from '@/api/customer'
import EmptyState from '@/components/EmptyState.vue'
import IntentCard from '@/components/IntentCard.vue'

/**
 * B7 历史下单记录 —— FR-031 / AC-008。
 * 只展示已完结记录（SUCCESS / FAILED / VOIDED / SOLD_OUT）。
 */
const loading = ref(true)
const items = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)

const FETCH_PAGE_SIZE = 100
const FINISHED_STATUSES = ['SUCCESS', 'FAILED', 'VOIDED', 'SOLD_OUT']
const FINISHED_STATUS_SET = new Set(FINISHED_STATUSES)
const allItems = ref([])

/** 后端不支持状态过滤，先拉取全部分页，再在前端筛选。 */
async function fetchAllOrders() {
  const rows = []
  let currentPage = 1
  let expectedTotal = 0

  while (true) {
    const result = await listOrders({
      page: currentPage,
      page_size: FETCH_PAGE_SIZE,
    })
    const batch = result.items || []
    rows.push(...batch)
    expectedTotal = Number(result.total) || 0

    const batchSize = Number(result.page_size) || FETCH_PAGE_SIZE
    if (batch.length < batchSize || rows.length >= expectedTotal) {
      break
    }
    currentPage += 1
  }

  return rows
}

function applyPage() {
  const filtered = allItems.value.filter((item) => FINISHED_STATUS_SET.has(item.status))

  total.value = filtered.length

  const maxPage = Math.max(1, Math.ceil(filtered.length / pageSize.value))
  if (page.value > maxPage) {
    page.value = maxPage
  }

  const start = (page.value - 1) * pageSize.value
  items.value = filtered.slice(start, start + pageSize.value)
}

async function load() {
  loading.value = true
  try {
    allItems.value = await fetchAllOrders()
    applyPage()
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    loading.value = false
  }
}

function onPageChange(nextPage) {
  page.value = nextPage
  applyPage()
}

onMounted(load)
</script>

<template>
  <div class="orders">
    <div class="orders__bar">
      <span class="orders__hint">按最近更新时间排序</span>
      <router-link class="orders__link" to="/my/intents">我的意向 →</router-link>
    </div>

    <div v-loading="loading" class="orders__body">
      <template v-if="items.length">
        <IntentCard
          v-for="item in items"
          :key="item.id"
          :intent="item"
          :to="{ name: 'customer-intent-detail', params: { id: item.id } }"
        />
      </template>

      <EmptyState
        v-else-if="!loading"
        icon="🧾"
        text="暂无历史下单记录"
        hint="提交购买意向后，这里会保留完整记录"
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
  margin-bottom: var(--sp-3);
  font-size: var(--fs-sm);
}

.orders__hint {
  color: var(--c-text-weak);
}

.orders__body {
  min-height: 200px;
}

.orders__pager {
  display: flex;
  justify-content: center;
  margin-top: var(--sp-4);
}
</style>
