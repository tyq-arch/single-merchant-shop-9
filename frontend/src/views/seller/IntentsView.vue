<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'

import { exportIntents, getCurrentProduct, listIntents } from '@/api/seller'
import EmptyState from '@/components/EmptyState.vue'
import StatusTag from '@/components/StatusTag.vue'
import { downloadBlob } from '@/utils/format'

/**
 * S4 意向购买人 —— FR-017 / FR-018 / FR-019 / FR-039。
 *
 * 刻意不提供：拖拽排序、上移下移、勾选买家等任何队列调整控件
 * （FR-020 / AC-012：卖家不能调整顺序、不能挑人）。
 *
 * 手机号脱敏由后端完成（终结或商品已下架后返回 前3位****后4位），前端不做二次处理。
 */
/** 「全部」用非空哨兵值：Element Plus 会把空字符串视为未选中并显示 placeholder */
const ALL_STATUS = 'ALL'

const STATUS_OPTIONS = [
  { value: ALL_STATUS, label: '全部' },
  // 以下文案镜像后端 OrderStatus.LABELS，用于筛选下拉，不是数据展示
  { value: 'QUEUED', label: '排队中' },
  { value: 'IN_TRANSACTION', label: '已进入交易' },
  { value: 'REQUEUED', label: '重新排队中' },
  { value: 'SUCCESS', label: '交易成功' },
  { value: 'FAILED', label: '交易失败' },
  { value: 'VOIDED', label: '已作废' },
  { value: 'CANCELLED', label: '已撤销' },
]

const product = ref(null)
const items = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)
const statusFilter = ref(ALL_STATUS)
const loading = ref(true)
const exporting = ref(false)

async function loadProduct() {
  const data = await getCurrentProduct()
  product.value = data.product || null
}

async function loadList() {
  if (!product.value) {
    loading.value = false
    return
  }

  loading.value = true
  try {
    const params = { page: page.value, page_size: pageSize.value }
    if (statusFilter.value !== ALL_STATUS) params.status = statusFilter.value

    const result = await listIntents(product.value.id, params)
    items.value = result.items || []
    total.value = result.total || 0
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    loading.value = false
  }
}

async function refresh() {
  loading.value = true
  try {
    await loadProduct()
    await loadList()
  } catch (error) {
    ElMessage.error(error.message)
    loading.value = false
  }
}

function onFilterChange() {
  page.value = 1
  loadList()
}

function onPageChange(nextPage) {
  page.value = nextPage
  loadList()
}

function onPageSizeChange(nextSize) {
  pageSize.value = nextSize
  page.value = 1
  loadList()
}

async function onExport() {
  if (!product.value || exporting.value) return

  exporting.value = true
  try {
    const blob = await exportIntents(product.value.id)
    downloadBlob(blob, `意向购买人_${product.value.name}.csv`)
    ElMessage.success('导出成功')
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    exporting.value = false
  }
}

onMounted(refresh)
</script>

<template>
  <el-card v-loading="loading" shadow="never">
    <template #header>
      <div class="head">
        <!-- 页面标题已在 SellerLayout 顶栏显示，此处只呈现当前商品上下文 -->
        <span class="head__title head__product">
          {{ product ? `当前商品：${product.name}` : '—' }}
        </span>
        <el-button
          type="primary"
          plain
          :disabled="!product || exporting"
          :loading="exporting"
          @click="onExport"
        >
          导出 CSV
        </el-button>
      </div>
    </template>

    <EmptyState
      v-if="!loading && !product"
      icon="📦"
      text="当前无在售 / 交易中的商品"
      hint="商品发布后，买家的购买意向会显示在这里。"
    />

    <template v-else-if="!loading">
      <div class="toolbar">
        <span class="toolbar__label">状态筛选</span>
        <el-select v-model="statusFilter" class="toolbar__select" @change="onFilterChange">
          <el-option
            v-for="option in STATUS_OPTIONS"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
      </div>

      <el-table :data="items" border>
        <el-table-column prop="queue_position" label="排队序号" width="100" />
        <el-table-column prop="buyer_name" label="姓名" width="140" />
        <el-table-column prop="buyer_phone" label="联系电话" width="180" />
        <el-table-column prop="submitted_at" label="提交时间" min-width="180" />
        <el-table-column label="状态" width="130">
          <template #default="{ row }">
            <StatusTag :status="row.status" :label="row.status_label" />
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!items.length" description="暂无符合条件的意向记录" :image-size="80" />

      <div class="pager">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next"
          background
          @current-change="onPageChange"
          @size-change="onPageSizeChange"
        />
      </div>

      <p class="note">
        排队顺序严格按提交时间先到先得。系统不提供调整顺序或挑选买家的入口（需求 FR-020）。
        手机号在交易终结或商品下架后按隐私策略脱敏展示。
      </p>
    </template>
  </el-card>
</template>

<style scoped>
.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-4);
}

.head__title {
  font-weight: 600;
}

.head__product {
  color: var(--c-text-sub);
  font-weight: 400;
}

.toolbar {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  margin-bottom: var(--sp-4);
}

.toolbar__label {
  font-size: var(--fs-sm);
  color: var(--c-text-sub);
}

.toolbar__select {
  width: 180px;
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--sp-4);
}

.note {
  margin: var(--sp-4) 0 0;
  font-size: var(--fs-sm);
  color: var(--c-text-weak);
}
</style>
