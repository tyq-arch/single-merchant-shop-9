<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

import { listHistory } from '@/api/seller'
import ProductImage from '@/components/ProductImage.vue'
import StatusTag from '@/components/StatusTag.vue'
import { formatPrice } from '@/utils/format'

/**
 * S5 历史商品列表 —— FR-004 / FR-037 / AC-028。
 * 按发布时间倒序分页，展示商品信息与交易结果摘要。
 */
const router = useRouter()

const items = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)
const keyword = ref('')
const loading = ref(true)

async function load() {
  loading.value = true
  try {
    const params = { page: page.value, page_size: pageSize.value }
    const trimmed = keyword.value.trim()
    if (trimmed) params.keyword = trimmed

    const result = await listHistory(params)
    items.value = result.items || []
    total.value = result.total || 0
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    loading.value = false
  }
}

function onSearch() {
  page.value = 1
  load()
}

function onReset() {
  keyword.value = ''
  page.value = 1
  load()
}

function onPageChange(nextPage) {
  page.value = nextPage
  load()
}

function onPageSizeChange(nextSize) {
  pageSize.value = nextSize
  page.value = 1
  load()
}

function goDetail(row) {
  router.push({ name: 'seller-history-detail', params: { id: row.id } })
}

onMounted(load)
</script>

<template>
  <el-card v-loading="loading" shadow="never">
    <!-- 页面标题已在 SellerLayout 顶栏显示，此处只放搜索工具条 -->
    <div class="toolbar">
      <el-input
        v-model="keyword"
        placeholder="按商品名称搜索"
        clearable
        class="toolbar__input"
        @keyup.enter="onSearch"
        @clear="onReset"
      />
      <el-button type="primary" @click="onSearch">查询</el-button>
    </div>

    <el-table :data="items" border>
      <el-table-column label="主图" width="100">
        <template #default="{ row }">
          <ProductImage :src="row.image_url" :alt="row.name" ratio="1 / 1" />
        </template>
      </el-table-column>

      <el-table-column label="商品信息" min-width="240">
        <template #default="{ row }">
          <p class="cell__name">{{ row.name }}</p>
          <p class="price cell__price">{{ formatPrice(row.price) }}</p>
          <p class="cell__meta">发布：{{ row.published_at }}</p>
          <p v-if="row.description" class="cell__desc">{{ row.description }}</p>
        </template>
      </el-table-column>

      <el-table-column label="交易结果" min-width="200">
        <template #default="{ row }">
          <p class="cell__result">
            {{ row.transaction_summary?.final_result_label || '未成交' }}
          </p>
          <p
            v-if="row.transaction_summary?.failed_rounds > 0"
            class="cell__failed"
          >
            失败 {{ row.transaction_summary.failed_rounds }} 轮
          </p>
          <p class="cell__meta">
            意向买家 {{ row.transaction_summary?.buyer_count ?? 0 }} 人
          </p>
          <p v-if="row.transaction_summary?.last_transaction_at" class="cell__meta">
            最近交易：{{ row.transaction_summary.last_transaction_at }}
          </p>
        </template>
      </el-table-column>

      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <StatusTag :status="row.status" :label="row.status_label" />
        </template>
      </el-table-column>

      <el-table-column label="操作" width="120" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="goDetail(row)">查看详情</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-empty v-if="!items.length" description="暂无历史商品" :image-size="80" />

    <div class="pager">
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        background
        @current-change="onPageChange"
        @size-change="onPageSizeChange"
      />
    </div>
  </el-card>
</template>

<style scoped>
.toolbar {
  display: flex;
  gap: var(--sp-2);
  margin-bottom: var(--sp-4);
}

.toolbar__input {
  width: 240px;
}

.cell__name {
  margin: 0;
  font-weight: 600;
}

.cell__price {
  margin: var(--sp-1) 0 0;
}

.cell__result {
  margin: 0;
  font-weight: 600;
}

.cell__failed {
  margin: var(--sp-1) 0 0;
  font-size: var(--fs-sm);
  color: var(--c-danger);
}

.cell__meta {
  margin: var(--sp-1) 0 0;
  font-size: var(--fs-sm);
  color: var(--c-text-weak);
}

.cell__desc {
  margin: var(--sp-2) 0 0;
  font-size: var(--fs-sm);
  color: var(--c-text-sub);
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--sp-4);
}
</style>
