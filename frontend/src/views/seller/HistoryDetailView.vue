<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

import { getHistoryDetail } from '@/api/seller'
import ProductImage from '@/components/ProductImage.vue'
import StatusTag from '@/components/StatusTag.vue'
import { formatPrice } from '@/utils/format'

/**
 * S6 历史商品详情 —— FR-005 / FR-006 / FR-038 / AC-029 / AC-031 / NFR-015。
 *
 * 必须完整展示每一轮交易，不可只显示最终结果。
 * 买家手机号由后端统一脱敏（历史商品必然已下架）。
 * 注意：后端 buyers 项用的是 created_at（非列表接口的 submitted_at）。
 */
const route = useRoute()
const router = useRouter()

const loading = ref(true)
const detail = ref(null)
const errorText = ref('')

const summary = computed(() => detail.value?.transaction_summary || {})

/** 后端按时间正序返回，展示改为倒序（最近的在最上） */
const transactions = computed(() => [...(detail.value?.transactions || [])].reverse())

async function load() {
  loading.value = true
  errorText.value = ''
  try {
    detail.value = await getHistoryDetail(route.params.id)
  } catch (error) {
    errorText.value = error.message
    ElMessage.error(error.message)
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div v-loading="loading" class="history-detail">
    <template v-if="!loading">
      <el-card v-if="errorText" shadow="never">
        <div class="empty-state">
          <div class="empty-state__icon">⚠️</div>
          <p class="empty-state__text">{{ errorText }}</p>
          <el-button type="primary" @click="router.push({ name: 'seller-history' })">
            返回历史列表
          </el-button>
        </div>
      </el-card>

      <template v-else-if="detail">
        <el-button link class="history-detail__back" @click="router.push({ name: 'seller-history' })">
          ← 返回历史列表
        </el-button>

        <!-- 商品快照 -->
        <el-card shadow="never">
          <div class="product">
            <div class="product__thumb">
              <ProductImage :src="detail.image_url" :alt="detail.name" ratio="1 / 1" />
            </div>

            <div class="product__info">
              <div class="product__head">
                <h2 class="product__name">{{ detail.name }}</h2>
                <StatusTag :status="detail.status" :label="detail.status_label" />
              </div>

              <p class="price product__price">{{ formatPrice(detail.price) }}</p>

              <p class="product__meta">
                发布时间：{{ detail.published_at }} · 下架时间：{{ detail.updated_at }}
              </p>

              <p v-if="detail.description" class="product__desc">{{ detail.description }}</p>
            </div>
          </div>
        </el-card>

        <!-- 交易概览 -->
        <el-card shadow="never" class="block">
          <template #header>
            <span class="block__title">交易概览</span>
          </template>

          <div class="stats">
            <div class="stats__item">
              <p class="stats__value">{{ summary.failed_rounds ?? 0 }}</p>
              <p class="stats__label">失败轮次</p>
            </div>
            <div class="stats__item">
              <p class="stats__value">{{ summary.buyer_count ?? 0 }}</p>
              <p class="stats__label">意向买家</p>
            </div>
            <div class="stats__item">
              <p class="stats__value stats__value--text">
                {{ summary.final_result_label || '未成交' }}
              </p>
              <p class="stats__label">最终结果</p>
            </div>
          </div>
        </el-card>

        <!-- 交易过程：完整保留每一轮 -->
        <el-card shadow="never" class="block">
          <template #header>
            <span class="block__title">交易过程（共 {{ transactions.length }} 轮）</span>
          </template>

          <el-timeline v-if="transactions.length">
            <el-timeline-item
              v-for="(tx, index) in transactions"
              :key="tx.id || index"
              :timestamp="tx.created_at"
              placement="top"
              :type="tx.result === 'SUCCESS' ? 'success' : 'danger'"
            >
              <p class="tx__result" :class="tx.result === 'SUCCESS' ? 'tx--success' : 'tx--danger'">
                {{ tx.result_label }}
              </p>
              <p v-if="tx.note" class="tx__note">备注：{{ tx.note }}</p>
            </el-timeline-item>
          </el-timeline>

          <el-empty v-else description="该商品没有产生交易记录" :image-size="80" />
        </el-card>

        <!-- 意向购买人：每人带自己那次的交易结果 -->
        <el-card shadow="never" class="block">
          <template #header>
            <span class="block__title">意向购买人（{{ detail.buyers?.length ?? 0 }} 人）</span>
          </template>

          <el-table :data="detail.buyers || []" border>
            <el-table-column prop="buyer_name" label="姓名" width="140" />
            <el-table-column prop="buyer_phone" label="联系电话" width="180" />
            <el-table-column prop="created_at" label="提交时间" min-width="180" />
            <el-table-column label="排队序号" width="110">
              <template #default="{ row }">
                {{ row.queue_position ?? '—' }}
              </template>
            </el-table-column>
            <el-table-column label="交易结果" width="130">
              <template #default="{ row }">
                <StatusTag :status="row.status" :label="row.status_label" />
              </template>
            </el-table-column>
          </el-table>

          <p class="block__note">
            手机号在商品下架后统一脱敏展示（前 3 位 + **** + 后 4 位）。
          </p>
        </el-card>
      </template>
    </template>
  </div>
</template>

<style scoped>
.history-detail {
  display: flex;
  flex-direction: column;
  gap: var(--sp-4);
  min-height: 200px;
}

.history-detail__back {
  align-self: flex-start;
}

.product {
  display: flex;
  gap: var(--sp-4);
}

.product__thumb {
  width: 140px;
  flex: none;
}

.product__head {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
}

.product__name {
  margin: 0;
  font-size: var(--fs-lg);
  font-weight: 600;
}

.product__price {
  margin: var(--sp-2) 0 0;
  font-size: 22px;
}

.product__meta {
  margin: var(--sp-1) 0 0;
  font-size: var(--fs-sm);
  color: var(--c-text-weak);
}

.product__desc {
  margin: var(--sp-3) 0 0;
  font-size: var(--fs-sm);
  color: var(--c-text-sub);
  white-space: pre-wrap;
}

.block {
  margin: 0;
}

.block__title {
  font-weight: 600;
}

.block__note {
  margin: var(--sp-3) 0 0;
  font-size: var(--fs-sm);
  color: var(--c-text-weak);
}

.stats {
  display: flex;
  flex-wrap: wrap;
  gap: var(--sp-8);
}

.stats__value {
  margin: 0;
  font-size: 28px;
  font-weight: 700;
  color: var(--c-primary);
}

.stats__value--text {
  font-size: 22px;
}

.stats__label {
  margin: var(--sp-1) 0 0;
  font-size: var(--fs-sm);
  color: var(--c-text-weak);
}

.tx__result {
  margin: 0;
  font-weight: 600;
}

.tx--success {
  color: var(--c-success);
}

.tx--danger {
  color: var(--c-danger);
}

.tx__note {
  margin: var(--sp-1) 0 0;
  font-size: var(--fs-sm);
  color: var(--c-text-sub);
}

@media (max-width: 767px) {
  .product {
    flex-direction: column;
  }

  .product__thumb {
    width: 100%;
    max-width: 200px;
  }
}
</style>
