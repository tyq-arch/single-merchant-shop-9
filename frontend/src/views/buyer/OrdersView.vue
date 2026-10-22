<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'

import { listOrders } from '@/api/customer'
import EmptyState from '@/components/EmptyState.vue'
import IntentCard from '@/components/IntentCard.vue'

/**
 * B7 历史下单记录 —— FR-031 / AC-008。
 * 包含该客户全部意向（成功、失败、作废、售罄）。
 */
const loading = ref(true)
const items = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)

async function load() {
  loading.value = true
  try {
    const result = await listOrders({ page: page.value, page_size: pageSize.value })
    items.value = result.items || []
    total.value = result.total || 0
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    loading.value = false
  }
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
