<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'

import { listIntents } from '@/api/customer'
import EmptyState from '@/components/EmptyState.vue'
import IntentCard from '@/components/IntentCard.vue'

/**
 * B5 我的意向 —— FR-025。
 * 只展示进行中意向（QUEUED / REQUEUED / IN_TRANSACTION）。
 * 只读列表：可查看位次与状态，不提供撤销、修改入口。
 */
const loading = ref(true)
const items = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)
const status = ref('')

const FETCH_PAGE_SIZE = 100
const ACTIVE_STATUSES = ['QUEUED', 'REQUEUED', 'IN_TRANSACTION']
const ACTIVE_STATUS_SET = new Set(ACTIVE_STATUSES)
const allItems = ref([])

const STATUS_OPTIONS = [
  { value: '', label: '全部' },
  { value: 'QUEUED', label: '排队中' },
  { value: 'REQUEUED', label: '重新排队中' },
  { value: 'IN_TRANSACTION', label: '已进入交易' },
]

/** 后端不支持多状态过滤，先拉取全部分页，再在前端筛选。 */
async function fetchAllIntents() {
  const rows = []
  let currentPage = 1
  let expectedTotal = 0

  while (true) {
    const result = await listIntents({
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
  const filtered = allItems.value.filter((item) => {
    if (!ACTIVE_STATUS_SET.has(item.status)) return false
    return !status.value || item.status === status.value
  })

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
    allItems.value = await fetchAllIntents()
    applyPage()
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
  applyPage()
}

onMounted(load)
</script>

<template>
  <div class="my-intents">
    <div class="my-intents__bar">
      <el-select v-model="status" size="default" @change="onFilterChange">
        <el-option
          v-for="option in STATUS_OPTIONS"
          :key="option.value"
          :label="option.label"
          :value="option.value"
        />
      </el-select>

      <router-link class="my-intents__orders" to="/my/orders">历史下单 →</router-link>
    </div>

    <div v-loading="loading" class="my-intents__body">
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
        icon="📋"
        text="还没有购买意向"
        hint="去商品列表看看，点击「我要购买」即可进入排队"
      >
        <router-link class="my-intents__link" to="/">去逛逛</router-link>
      </EmptyState>
    </div>

    <div v-if="total > pageSize" class="my-intents__pager">
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
.my-intents__bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-3);
  margin-bottom: var(--sp-3);
}

.my-intents__orders {
  font-size: var(--fs-sm);
}

.my-intents__body {
  min-height: 200px;
}

.my-intents__link {
  font-size: var(--fs-sm);
}

.my-intents__pager {
  display: flex;
  justify-content: center;
  margin-top: var(--sp-4);
}
</style>
