<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'

import { listIntents } from '@/api/customer'
import EmptyState from '@/components/EmptyState.vue'
import IntentCard from '@/components/IntentCard.vue'

/**
 * B5 我的意向 —— FR-025。
 * 只读列表：可查看位次与状态，不提供撤销、修改入口。
 */
const loading = ref(true)
const items = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)
const status = ref('')

const STATUS_OPTIONS = [
  { value: '', label: '全部' },
  { value: 'QUEUED', label: '排队中' },
  { value: 'REQUEUED', label: '重新排队中' },
  { value: 'IN_TRANSACTION', label: '已进入交易' },
  { value: 'SUCCESS', label: '交易成功' },
  { value: 'FAILED', label: '交易失败' },
  { value: 'VOIDED', label: '已作废' },
  { value: 'SOLD_OUT', label: '售罄失效' },
]

async function load() {
  loading.value = true
  try {
    const result = await listIntents({
      page: page.value,
      page_size: pageSize.value,
      status: status.value || undefined,
    })
    items.value = result.items || []
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
