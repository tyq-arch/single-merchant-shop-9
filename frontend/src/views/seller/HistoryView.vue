<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

import { listHistory } from '@/api/seller'
import StatusTag from '@/components/StatusTag.vue'
import { formatPrice } from '@/utils/format'

/**
 * S6 历史商品 —— FR-012。
 * 包含已售罄下架与手动下架商品，并展示交易汇总。
 */
const router = useRouter()

const loading = ref(true)
const items = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)
const keyword = ref('')

async function load() {
  loading.value = true
  try {
    const result = await listHistory({
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

onMounted(load)
</script>

<template>
  <el-card shadow="never">
    <div class="history__toolbar">
      <el-input
        v-model="keyword"
        placeholder="搜索商品名称"
        clearable
        class="history__keyword"
        @keyup.enter="search"
        @clear="search"
      />
      <el-button type="primary" @click="search">查询</el-button>
    </div>

    <el-table v-loading="loading" :data="items" border>
      <el-table-column prop="name" label="商品名称" min-width="200" />

      <el-table-column label="价格" width="110">
        <template #default="{ row }">
          <span class="price">{{ formatPrice(row.price) }}</span>
        </template>
      </el-table-column>

      <el-table-column prop="stock" label="剩余库存" width="90" />

      <el-table-column label="状态" width="120">
        <template #default="{ row }">
          <StatusTag :status="row.status" :label="row.status_label" />
        </template>
      </el-table-column>

      <el-table-column label="交易汇总" min-width="240">
        <template #default="{ row }">
          <span v-if="row.transaction_summary">
            买家 {{ row.transaction_summary.buyer_count }} 人 ·
            失败 {{ row.transaction_summary.failed_rounds }} 次 ·
            {{ row.transaction_summary.final_result_label }}
          </span>
          <span v-else>—</span>
        </template>
      </el-table-column>

      <el-table-column prop="published_at" label="发布时间" width="180" />

      <el-table-column label="操作" width="100" fixed="right">
        <template #default="{ row }">
          <el-button
            link
            type="primary"
            @click="router.push({ name: 'seller-history-detail', params: { id: row.id } })"
          >
            查看详情
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-empty v-if="!items.length && !loading" description="暂无历史商品" :image-size="80" />

    <div class="history__pager">
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
.history__toolbar {
  display: flex;
  gap: var(--sp-2);
  margin-bottom: var(--sp-3);
}

.history__keyword {
  width: 240px;
}

.history__pager {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--sp-3);
}
</style>
