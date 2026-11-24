<script setup>
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { getAfterSale } from '@/api/customer'
import EmptyState from '@/components/EmptyState.vue'
import ProductImage from '@/components/ProductImage.vue'
import StatusTag from '@/components/StatusTag.vue'
import { formatPrice } from '@/utils/format'
import { normalizeAfterSale } from '@/utils/shop'

/**
 * C3 售后详情 —— 客户只读。
 *
 * 展示申请单（标题 / 描述 / 图片）与商家记录的结果；
 * 退货成功会回补库存，纯退款不回补（TBD-12）。
 */
const route = useRoute()
const router = useRouter()

const loading = ref(true)
const errorState = ref('')
const aftersale = ref(null)

async function load() {
  loading.value = true
  errorState.value = ''
  try {
    const data = await getAfterSale(route.params.id)
    aftersale.value = normalizeAfterSale(data.aftersale || data)
  } catch (error) {
    errorState.value = error.message
  } finally {
    loading.value = false
  }
}

function openOrder() {
  if (!aftersale.value?.orderId) return
  router.push({
    name: 'customer-order-detail',
    params: { id: aftersale.value.orderId },
  })
}

onMounted(load)
</script>

<template>
  <div v-loading="loading" class="aftersale-detail">
    <EmptyState
      v-if="errorState && !loading"
      icon="⚠️"
      :text="errorState"
      hint="售后申请可能不存在，或不属于当前账号"
    >
      <el-button @click="router.push({ name: 'customer-aftersales' })">
        返回售后服务
      </el-button>
    </EmptyState>

    <template v-else-if="aftersale">
      <div class="aftersale-detail__bar">
        <router-link class="aftersale-detail__link" :to="{ name: 'customer-aftersales' }">
          ← 全部售后
        </router-link>
      </div>

      <section class="aftersale-card">
        <div class="aftersale-card__head">
          <h1 class="aftersale-card__title">{{ aftersale.title || '售后申请' }}</h1>
          <StatusTag
            :status="aftersale.status"
            :label="aftersale.statusLabel"
            size="large"
          />
        </div>
        <p class="aftersale-card__meta">
          申请时间：{{ aftersale.createdAt || '—' }}
          <template v-if="aftersale.orderNo">
            · 订单号：{{ aftersale.orderNo }}
          </template>
        </p>
        <el-button
          v-if="aftersale.orderId"
          class="aftersale-card__order"
          link
          type="primary"
          @click="openOrder"
        >
          查看订单 →
        </el-button>
      </section>

      <section class="aftersale-product">
        <div class="aftersale-product__thumb">
          <ProductImage
            :src="aftersale.productImage"
            :alt="aftersale.productName"
            ratio="1 / 1"
          />
        </div>
        <div>
          <p class="aftersale-product__name">{{ aftersale.productName }}</p>
          <p class="aftersale-product__hint">售后针对订单内的该件商品</p>
        </div>
      </section>

      <section class="aftersale-body">
        <h2 class="aftersale-body__title">问题描述</h2>
        <p class="aftersale-body__text">{{ aftersale.description || '—' }}</p>

        <template v-if="aftersale.images.length">
          <h2 class="aftersale-body__title">图片</h2>
          <div class="aftersale-images">
            <el-image
              v-for="(image, index) in aftersale.images"
              :key="image"
              :src="image"
              :preview-src-list="aftersale.images"
              :initial-index="index"
              fit="cover"
              class="aftersale-images__item"
            />
          </div>
        </template>
      </section>

      <section class="aftersale-result">
        <h2 class="aftersale-result__title">处理结果</h2>

        <template v-if="aftersale.resultLabel">
          <div class="aftersale-result__row">
            <span>结果</span>
            <strong>{{ aftersale.resultLabel }}</strong>
          </div>
          <div v-if="aftersale.refundAmount > 0" class="aftersale-result__row">
            <span>退款金额</span>
            <span class="price">{{ formatPrice(aftersale.refundAmount) }}</span>
          </div>
          <div v-if="aftersale.resultNote" class="aftersale-result__row">
            <span>说明</span>
            <span class="aftersale-result__note">{{ aftersale.resultNote }}</span>
          </div>
          <div v-if="aftersale.handledAt" class="aftersale-result__row">
            <span>处理时间</span>
            <span>{{ aftersale.handledAt }}</span>
          </div>
        </template>

        <p v-else class="aftersale-result__empty">
          商家尚未记录结果，请与商家线下协商。
        </p>
      </section>
    </template>
  </div>
</template>

<style scoped>
.aftersale-detail {
  min-height: 240px;
}

.aftersale-detail__bar {
  margin-bottom: var(--sp-3);
  font-size: var(--fs-sm);
}

.aftersale-detail__link {
  color: var(--c-primary);
}

.aftersale-card,
.aftersale-product,
.aftersale-body,
.aftersale-result {
  padding: var(--sp-3);
  background: var(--c-surface);
  border-radius: var(--r-card);
  box-shadow: var(--shadow-sm);
}

.aftersale-card__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--sp-2);
}

.aftersale-card__title {
  margin: 0;
  font-size: var(--fs-lg);
  font-weight: 600;
  word-break: break-all;
}

.aftersale-card__meta {
  margin: var(--sp-2) 0 0;
  font-size: var(--fs-sm);
  color: var(--c-text-weak);
  word-break: break-all;
}

.aftersale-card__order {
  margin-top: var(--sp-1);
  padding: 0;
}

.aftersale-product {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  margin-top: var(--sp-3);
}

.aftersale-product__thumb {
  width: 64px;
  flex: none;
}

.aftersale-product__name {
  margin: 0;
  font-size: var(--fs-base);
  font-weight: 600;
  word-break: break-all;
}

.aftersale-product__hint {
  margin: var(--sp-1) 0 0;
  font-size: 12px;
  color: var(--c-text-weak);
}

.aftersale-body,
.aftersale-result {
  margin-top: var(--sp-3);
}

.aftersale-body__title,
.aftersale-result__title {
  margin: 0 0 var(--sp-2);
  font-size: var(--fs-base);
  font-weight: 600;
}

.aftersale-body__title + .aftersale-body__text {
  margin-top: 0;
}

.aftersale-body__text {
  margin: var(--sp-2) 0 0;
  font-size: var(--fs-sm);
  color: var(--c-text-sub);
  white-space: pre-wrap;
  word-break: break-word;
}

.aftersale-images {
  display: flex;
  flex-wrap: wrap;
  gap: var(--sp-2);
}

.aftersale-images__item {
  width: 88px;
  height: 88px;
  border-radius: var(--r-btn);
}

.aftersale-result__row {
  display: flex;
  justify-content: space-between;
  gap: var(--sp-3);
  padding: var(--sp-2) 0;
  font-size: var(--fs-sm);
  color: var(--c-text-sub);
}

.aftersale-result__row + .aftersale-result__row {
  border-top: 1px solid var(--c-border);
}

.aftersale-result__note {
  text-align: right;
  word-break: break-all;
}

.aftersale-result__empty {
  margin: 0;
  font-size: var(--fs-sm);
  color: var(--c-text-weak);
}
</style>
