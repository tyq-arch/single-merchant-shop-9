<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

import { listAfterSales } from '@/api/customer'
import EmptyState from '@/components/EmptyState.vue'
import StatusTag from '@/components/StatusTag.vue'
import {
  AFTER_SALE_STATUS_OPTIONS,
  normalizeAfterSale,
} from '@/utils/shop'

/**
 * C3 售后服务列表 —— FR-054 / FR-055。
 *
 * 客户只能查看自己发起的售后申请及商家记录的结果。
 */
const router = useRouter()

const loading = ref(true)
const items = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)
const status = ref('')

async function load() {
  loading.value = true
  try {
    const data = await listAfterSales({
      page: page.value,
      page_size: pageSize.value,
      status: status.value || undefined,
    })
    items.value = (data.items || []).map(normalizeAfterSale)
    total.value = Number(data.total ?? items.value.length) || 0
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

function openDetail(aftersale) {
  router.push({
    name: 'customer-aftersale-detail',
    params: { id: aftersale.id },
  })
}

onMounted(load)
</script>

<template>
  <div class="aftersales">
    <div class="aftersales__bar">
      <div>
        <h1 class="aftersales__title">售后服务</h1>
        <p class="aftersales__hint">
          交易完成后可对订单内某件商品发起售后，商家线下协商后记录结果
        </p>
      </div>
      <el-select
        v-model="status"
        class="aftersales__filter"
        placeholder="全部状态"
        @change="onFilterChange"
      >
        <el-option
          v-for="option in AFTER_SALE_STATUS_OPTIONS"
          :key="option.value"
          :label="option.label"
          :value="option.value"
        />
      </el-select>
    </div>

    <div v-loading="loading" class="aftersales__body">
      <template v-if="items.length">
        <button
          v-for="aftersale in items"
          :key="aftersale.id"
          type="button"
          class="aftersale-card"
          @click="openDetail(aftersale)"
        >
          <div class="aftersale-card__head">
            <span class="aftersale-card__title">{{ aftersale.title || '售后申请' }}</span>
            <StatusTag
              :status="aftersale.status"
              :label="aftersale.statusLabel"
            />
          </div>

          <p class="aftersale-card__product">
            {{ aftersale.productName }}
            <template v-if="aftersale.orderNo">
              · 订单 {{ aftersale.orderNo }}
            </template>
          </p>

          <div class="aftersale-card__foot">
            <span class="aftersale-card__time">{{ aftersale.createdAt || '—' }}</span>
            <span v-if="aftersale.resultLabel" class="aftersale-card__result">
              结果：{{ aftersale.resultLabel }}
            </span>
          </div>
        </button>
      </template>

      <EmptyState
        v-else-if="!loading"
        icon="🛠️"
        text="暂无售后申请"
        hint="订单交易完成后，可在订单详情对某件商品发起售后"
      />
    </div>

    <div v-if="total > pageSize" class="aftersales__pager">
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
.aftersales__bar {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--sp-3);
  margin-bottom: var(--sp-3);
}

.aftersales__title {
  margin: 0;
  font-size: var(--fs-lg);
  font-weight: 600;
}

.aftersales__hint {
  margin: var(--sp-1) 0 0;
  font-size: 12px;
  color: var(--c-text-weak);
}

.aftersales__filter {
  width: 130px;
  flex: none;
}

.aftersales__body {
  min-height: 180px;
}

.aftersale-card {
  display: block;
  width: 100%;
  padding: var(--sp-3);
  font: inherit;
  color: inherit;
  text-align: left;
  background: var(--c-surface);
  border: none;
  border-radius: var(--r-card);
  box-shadow: var(--shadow-sm);
  cursor: pointer;
}

.aftersale-card + .aftersale-card {
  margin-top: var(--sp-3);
}

.aftersale-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-2);
}

.aftersale-card__title {
  font-size: var(--fs-base);
  font-weight: 600;
  word-break: break-all;
}

.aftersale-card__product {
  margin: var(--sp-2) 0 0;
  font-size: var(--fs-sm);
  color: var(--c-text-sub);
  word-break: break-all;
}

.aftersale-card__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-2);
  margin-top: var(--sp-2);
  font-size: var(--fs-sm);
  color: var(--c-text-weak);
}

.aftersale-card__result {
  color: var(--c-text-sub);
}

.aftersales__pager {
  display: flex;
  justify-content: center;
  margin-top: var(--sp-4);
}
</style>
