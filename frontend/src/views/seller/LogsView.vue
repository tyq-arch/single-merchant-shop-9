<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'

import { listLogs } from '@/api/seller'

/**
 * S8 操作日志 —— FR-042 / NFR-010。
 * 卖家关键操作可追溯；操作内容由后端生成中文描述（detail 字段）。
 */
const items = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
const loading = ref(true)

async function load() {
  loading.value = true
  try {
    const result = await listLogs({ page: page.value, page_size: pageSize.value })
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

function onPageSizeChange(nextSize) {
  pageSize.value = nextSize
  page.value = 1
  load()
}

onMounted(load)
</script>

<template>
  <el-card v-loading="loading" shadow="never">
    <el-table :data="items" border>
      <el-table-column prop="created_at" label="时间" width="200" />
      <el-table-column prop="action" label="动作" width="200">
        <template #default="{ row }">
          <code class="cell__action">{{ row.action }}</code>
        </template>
      </el-table-column>
      <el-table-column prop="detail" label="操作内容" min-width="280" />
    </el-table>

    <el-empty v-if="!items.length" description="暂无操作记录" :image-size="80" />

    <div class="pager">
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[20, 50, 100]"
        layout="total, sizes, prev, pager, next"
        background
        @current-change="onPageChange"
        @size-change="onPageSizeChange"
      />
    </div>
  </el-card>
</template>

<style scoped>
.cell__action {
  font-family: ui-monospace, 'SFMono-Regular', Consolas, monospace;
  font-size: var(--fs-sm);
  color: var(--c-text-sub);
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--sp-4);
}
</style>
