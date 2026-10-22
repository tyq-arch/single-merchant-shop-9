<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'

import { createIntent } from '@/api/customer'
import { getProduct } from '@/api/public'
import EmptyState from '@/components/EmptyState.vue'
import ProductImage from '@/components/ProductImage.vue'
import StatusTag from '@/components/StatusTag.vue'
import { useCustomerStore } from '@/stores/customer'
import { formatPrice } from '@/utils/format'
import { sanitizeHtml } from '@/utils/sanitize'

/**
 * B2 商品详情 —— FR-019 / FR-020。
 *
 * 浏览免登录；点「我要购买」时未登录先引导登录（AC-007）。
 */
const route = useRoute()
const router = useRouter()
const customerStore = useCustomerStore()

const loading = ref(true)
const submitting = ref(false)
const errorState = ref('')
const product = ref(null)
const queueCount = ref(0)
const canBuy = ref(false)
const activeImage = ref('')

const images = computed(() => product.value?.images || [])

const descriptionHtml = computed(() =>
  sanitizeHtml(product.value?.description || ''),
)

const buyLabel = computed(() => {
  if (canBuy.value) return '我 要 购 买'
  if (product.value?.status === 'FROZEN') return '商品交易中'
  if (product.value?.status === 'SOLD_OUT') return '商品已售罄'
  if (product.value?.status === 'OFF_SHELF') return '商品已下架'
  return '暂不可购买'
})

async function load() {
  loading.value = true
  errorState.value = ''
  try {
    const data = await getProduct(route.params.id)
    product.value = data.product
    queueCount.value = data.queue_count || 0
    canBuy.value = Boolean(data.can_buy)
    activeImage.value = data.product?.primary_image || ''
  } catch (error) {
    errorState.value = error.message
  } finally {
    loading.value = false
  }
}

async function onBuy() {
  if (!canBuy.value || submitting.value) return

  if (!customerStore.isLoggedIn) {
    try {
      await ElMessageBox.confirm('购买需要先登录，是否前往登录？', '请先登录', {
        confirmButtonText: '去登录',
        cancelButtonText: '再逛逛',
        type: 'info',
      })
    } catch {
      return
    }
    router.push({ name: 'customer-login', query: { redirect: route.fullPath } })
    return
  }

  submitting.value = true
  try {
    const data = await createIntent(product.value.id)
    ElMessage.success(data.message || '已进入该商品队列')
    router.replace({
      name: 'customer-intent-detail',
      params: { id: data.intent.id },
    })
  } catch (error) {
    ElMessage.error(error.message)
    // 409：已下架 / 售罄 / 交易中 / 重复提交 —— 刷新页面状态
    if (error.status === 409) load()
  } finally {
    submitting.value = false
  }
}

onMounted(load)
</script>

<template>
  <div v-loading="loading" class="product-detail">
    <EmptyState
      v-if="errorState && !loading"
      icon="⚠️"
      :text="errorState"
      hint="商品可能已被卖家手动下架"
    >
      <router-link class="product-detail__back" to="/">返回商品列表</router-link>
    </EmptyState>

    <template v-else-if="product">
      <div class="gallery">
        <ProductImage :src="activeImage" :alt="product.name" ratio="1 / 1" />
        <div v-if="images.length > 1" class="gallery__thumbs">
          <button
            v-for="image in images"
            :key="image.id"
            class="gallery__thumb"
            :class="{ 'gallery__thumb--active': image.url === activeImage }"
            type="button"
            @click="activeImage = image.url"
          >
            <img :src="image.url" :alt="product.name" />
          </button>
        </div>
      </div>

      <div class="summary">
        <div class="summary__head">
          <h1 class="summary__name">{{ product.name }}</h1>
          <StatusTag :status="product.status" :label="product.status_label" />
        </div>

        <p class="summary__price price">{{ formatPrice(product.price) }}</p>

        <div class="summary__meta">
          <span>库存 {{ product.stock }}</span>
          <span v-if="queueCount > 0">排队 {{ queueCount }} 人</span>
          <span v-if="product.category_name">
            {{ product.parent_category_name }} / {{ product.category_name }}
          </span>
        </div>
      </div>

      <section class="description">
        <h2 class="description__title">商品描述</h2>
        <div
          v-if="descriptionHtml"
          class="rich-content"
          v-html="descriptionHtml"
        />
        <p v-else class="description__empty">卖家暂未填写描述</p>
      </section>

      <div class="action-bar">
        <button
          class="action-bar__btn"
          type="button"
          :disabled="!canBuy || submitting"
          @click="onBuy"
        >
          {{ submitting ? '提交中…' : buyLabel }}
        </button>
      </div>
    </template>
  </div>
</template>

<style scoped>
.product-detail {
  min-height: 240px;
}

.product-detail__back {
  font-size: var(--fs-sm);
}

.gallery__thumbs {
  display: flex;
  gap: var(--sp-2);
  margin-top: var(--sp-2);
  overflow-x: auto;
}

.gallery__thumb {
  width: 64px;
  height: 64px;
  flex: none;
  padding: 0;
  overflow: hidden;
  background: var(--c-muted-soft);
  border: 2px solid transparent;
  border-radius: var(--r-btn);
  cursor: pointer;
}

.gallery__thumb--active {
  border-color: var(--c-primary);
}

.gallery__thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.summary {
  margin-top: var(--sp-3);
  padding: var(--sp-3);
  background: var(--c-surface);
  border-radius: var(--r-card);
  box-shadow: var(--shadow-sm);
}

.summary__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--sp-2);
}

.summary__name {
  margin: 0;
  font-size: var(--fs-lg);
  font-weight: 600;
  word-break: break-all;
}

.summary__price {
  margin: var(--sp-2) 0;
  font-size: var(--fs-xl);
}

.summary__meta {
  display: flex;
  flex-wrap: wrap;
  gap: var(--sp-3);
  font-size: var(--fs-sm);
  color: var(--c-text-weak);
}

.description {
  margin-top: var(--sp-3);
  padding: var(--sp-3);
  background: var(--c-surface);
  border-radius: var(--r-card);
  box-shadow: var(--shadow-sm);
}

.description__title {
  margin: 0 0 var(--sp-2);
  font-size: var(--fs-base);
  font-weight: 600;
}

.description__empty {
  margin: 0;
  font-size: var(--fs-sm);
  color: var(--c-text-weak);
}

.rich-content {
  font-size: var(--fs-base);
  line-height: var(--lh-base);
  word-break: break-word;
}

.rich-content :deep(img),
.rich-content :deep(video) {
  max-width: 100%;
  margin: var(--sp-2) 0;
}

.action-bar {
  position: fixed;
  right: 0;
  bottom: 0;
  left: 0;
  z-index: 10;
  max-width: var(--buyer-max-width);
  margin: 0 auto;
  padding: var(--sp-3) var(--sp-4) calc(var(--sp-3) + env(safe-area-inset-bottom));
  background: var(--c-surface);
  border-top: 1px solid var(--c-border);
}

.action-bar__btn {
  width: 100%;
  min-height: var(--touch-min);
  font-size: var(--fs-base);
  font-weight: 600;
  color: #fff;
  background: var(--c-primary);
  border: none;
  border-radius: var(--r-btn);
  cursor: pointer;
}

.action-bar__btn:disabled {
  background: var(--c-text-weak);
  cursor: not-allowed;
}
</style>
