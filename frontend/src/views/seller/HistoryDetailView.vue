<script setup>
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

import { getHistoryDetail } from '@/api/seller'
import EmptyState from '@/components/EmptyState.vue'
import ProductImage from '@/components/ProductImage.vue'
import StatusTag from '@/components/StatusTag.vue'
import { formatPrice } from '@/utils/format'

/**
 * S6 历史商品详情 —— FR-012 / FR-033。
 * 保留该商品全部意向客户与每一次交易记录（含失败轮次）。
 */
const route = useRoute()
const router = useRouter()

const loading = ref(true)
const errorState = ref('')
const detail = ref(null)
const activeImage = ref('')

async function load() {
  loading.value = true
  errorState.value = ''
  try {
    const data = await getHistoryDetail(route.params.id)
    detail.value = data
    activeImage.value = data.primary_image || ''
  } catch (error) {
    errorState.value = error.message
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div v-loading="loading" class="history-detail">
    <EmptyState
      v-if="errorState && !loading"
      icon="⚠️"
      :text="errorState"
      hint="只有已售罄下架或手动下架的商品才进入历史记录"
    >
      <el-button @click="router.push({ name: 'seller-history' })">返回历史商品</el-button>
    </EmptyState>

    <template v-else-if="detail">
      <el-card shadow="never">
        <template #header>
          <div class="history-detail__head">
            <span>商品信息</span>
            <el-button link type="primary" @click="router.push({ name: 'seller-history' })">
              ← 返回历史商品
            </el-button>
          </div>
        </template>

        <div class="history-detail__product">
          <div class="history-detail__gallery">
            <ProductImage :src="activeImage" :alt="detail.name" ratio="1 / 1" />
            <div v-if="(detail.images || []).length > 1" class="history-detail__thumbs">
              <button
                v-for="image in detail.images"
                :key="image.id"
                class="history-detail__thumb"
                :class="{ 'history-detail__thumb--active': image.url === activeImage }"
                type="button"
                @click="activeImage = image.url"
              >
                <img :src="image.url" :alt="detail.name" />
              </button>
            </div>
          </div>

          <div class="history-detail__info">
            <div class="history-detail__title-row">
              <h2 class="history-detail__name">{{ detail.name }}</h2>
              <StatusTag :status="detail.status" :label="detail.status_label" size="large" />
            </div>
            <p class="history-detail__price price">{{ formatPrice(detail.price) }}</p>
            <p class="history-detail__meta">剩余库存：{{ detail.stock }}</p>
            <p v-if="detail.category_name" class="history-detail__meta">
              类别：{{ detail.parent_category_name }} / {{ detail.category_name }}
            </p>
            <p class="history-detail__meta">发布时间：{{ detail.published_at }}</p>
            <p class="history-detail__meta">图片数量：{{ detail.image_count }} 张</p>
          </div>
        </div>
      </el-card>

      <el-card v-if="detail.transaction_summary" shadow="never" class="history-detail__card">
        <template #header>交易汇总</template>
        <div class="summary-grid">
          <div class="summary-item">
            <p class="summary-item__value">{{ detail.transaction_summary.buyer_count }}</p>
            <p class="summary-item__label">意向客户数</p>
          </div>
          <div class="summary-item">
            <p class="summary-item__value">{{ detail.transaction_summary.failed_rounds }}</p>
            <p class="summary-item__label">交易失败次数</p>
          </div>
          <div class="summary-item">
            <p class="summary-item__value">
              {{ detail.transaction_summary.final_result_label }}
            </p>
            <p class="summary-item__label">最终结果</p>
          </div>
          <div class="summary-item">
            <p class="summary-item__value">
              {{ detail.transaction_summary.last_transaction_at || '—' }}
            </p>
            <p class="summary-item__label">最近交易时间</p>
          </div>
        </div>
      </el-card>

      <el-card shadow="never" class="history-detail__card">
        <template #header>买家与意向记录</template>
        <el-table :data="detail.buyers || []" border>
          <el-table-column prop="queue_position" label="位次" width="70" />
          <el-table-column label="客户" width="150">
            <template #default="{ row }">{{ row.customer?.username || '—' }}</template>
          </el-table-column>
          <el-table-column label="电话" width="140">
            <template #default="{ row }">{{ row.customer?.phone || '—' }}</template>
          </el-table-column>
          <el-table-column label="交易地点" min-width="160">
            <template #default="{ row }">{{ row.customer?.trade_location || '—' }}</template>
          </el-table-column>
          <el-table-column label="状态" width="130">
            <template #default="{ row }">
              <StatusTag :status="row.status" :label="row.status_label" />
            </template>
          </el-table-column>
          <el-table-column prop="requeue_count" label="重排次数" width="100" />
          <el-table-column prop="created_at" label="提交时间" width="180" />
        </el-table>
        <el-empty v-if="!(detail.buyers || []).length" description="没有意向记录" :image-size="70" />
      </el-card>

      <el-card shadow="never" class="history-detail__card">
        <template #header>交易记录</template>
        <el-table :data="detail.transactions || []" border>
          <el-table-column prop="created_at" label="时间" width="190" />
          <el-table-column label="结果" width="120">
            <template #default="{ row }">
              <el-tag :type="row.result === 'SUCCESS' ? 'success' : 'danger'" size="small">
                {{ row.result_label }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="intent_id" label="意向 ID" min-width="240" />
          <el-table-column prop="note" label="备注" min-width="180">
            <template #default="{ row }">{{ row.note || '—' }}</template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!(detail.transactions || []).length" description="没有交易记录" :image-size="70" />
      </el-card>
    </template>
  </div>
</template>

<style scoped>
.history-detail {
  min-height: 240px;
}

.history-detail__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.history-detail__product {
  display: flex;
  gap: var(--sp-5);
}

.history-detail__gallery {
  width: 280px;
  flex: none;
}

.history-detail__thumbs {
  display: flex;
  gap: var(--sp-2);
  margin-top: var(--sp-2);
  overflow-x: auto;
}

.history-detail__thumb {
  width: 56px;
  height: 56px;
  flex: none;
  padding: 0;
  overflow: hidden;
  background: var(--c-muted-soft);
  border: 2px solid transparent;
  border-radius: 6px;
  cursor: pointer;
}

.history-detail__thumb--active {
  border-color: var(--c-primary);
}

.history-detail__thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.history-detail__info {
  min-width: 0;
}

.history-detail__title-row {
  display: flex;
  align-items: flex-start;
  gap: var(--sp-3);
}

.history-detail__name {
  margin: 0;
  font-size: var(--fs-lg);
  font-weight: 600;
  word-break: break-all;
}

.history-detail__price {
  margin: var(--sp-2) 0;
  font-size: var(--fs-xl);
}

.history-detail__meta {
  margin: var(--sp-1) 0;
  font-size: var(--fs-sm);
  color: var(--c-text-sub);
}

.history-detail__card {
  margin-top: var(--sp-4);
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  gap: var(--sp-4);
}

.summary-item__value {
  margin: 0;
  font-size: var(--fs-lg);
  font-weight: 600;
}

.summary-item__label {
  margin: var(--sp-1) 0 0;
  font-size: var(--fs-sm);
  color: var(--c-text-weak);
}

@media (max-width: 900px) {
  .history-detail__product {
    flex-direction: column;
  }

  .history-detail__gallery {
    width: 100%;
    max-width: 320px;
  }
}
</style>
