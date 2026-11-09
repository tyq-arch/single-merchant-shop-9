<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'

import { getProduct } from '@/api/public'
import EmptyState from '@/components/EmptyState.vue'
import ProductImage from '@/components/ProductImage.vue'
import StatusTag from '@/components/StatusTag.vue'
import { useShopStore } from '@/stores/shop'
import { formatPrice } from '@/utils/format'
import { sanitizeHtml } from '@/utils/sanitize'

/**
 * B2 商品详情 —— 挑战 B。
 *
 * 浏览免登录；游客也能临时收藏 / 加购（登录后自动合并，TBD-12）。
 * 挑战 A 的「提交购买意向」入口已移除：下单必经购物车（TBD-14）。
 */
const route = useRoute()
const shopStore = useShopStore()

const loading = ref(true)
const errorState = ref('')
const product = ref(null)
const activeImage = ref('')

const isFavorite = ref(false)
const favoriteLoading = ref(false)
const cartLoading = ref(false)

const images = computed(() => product.value?.images || [])

const descriptionHtml = computed(() =>
  sanitizeHtml(product.value?.description || ''),
)

const purchasable = computed(
  () =>
    product.value?.status === 'ON_SALE' &&
    Number(product.value?.stock) > 0,
)

const unavailableLabel = computed(() => {
  if (purchasable.value) return ''
  if (product.value?.status === 'SOLD_OUT') return '商品已售罄'
  if (product.value?.status === 'OFF_SHELF') return '商品已下架'
  if (product.value?.status && product.value.status !== 'ON_SALE') {
    return product.value.status_label || '暂不可购买'
  }
  return '商品库存不足'
})

async function load() {
  loading.value = true
  errorState.value = ''
  try {
    const data = await getProduct(route.params.id)
    product.value = data.product || data
    activeImage.value = product.value?.primary_image || ''

    // 游客态也要同步本机临时收藏，否则已收藏的商品会显示成未收藏
    await shopStore.loadFavoriteIds()
    isFavorite.value = shopStore.hasFavorite(product.value.id)
  } catch (error) {
    errorState.value = error.message
  } finally {
    loading.value = false
  }
}

async function onToggleFavorite() {
  if (!product.value || favoriteLoading.value) return
  // 只有「在售」商品可以被新收藏；已收藏的仍允许取消
  if (!isFavorite.value && product.value.status !== 'ON_SALE') {
    ElMessage.warning('商品已下架，无法收藏')
    return
  }
  favoriteLoading.value = true
  try {
    const result = await shopStore.toggleFavorite(product.value)
    isFavorite.value = result.favorited
    if (result.favorited) {
      ElMessage.success(
        result.guest ? '已加入临时收藏，登录后自动合并' : '已加入收藏',
      )
    } else {
      ElMessage.success('已取消收藏')
    }
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    favoriteLoading.value = false
  }
}

async function onAddToCart() {
  if (!purchasable.value || cartLoading.value) return
  cartLoading.value = true
  try {
    const result = await shopStore.addToCart(product.value, 1)
    ElMessage.success(
      result.guest
        ? '已加入临时购物车，登录后自动合并'
        : '已加入购物车',
    )
  } catch (error) {
    ElMessage.error(error.message)
    if (error.status === 409) load()
  } finally {
    cartLoading.value = false
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
          <span>剩余可售 {{ product.stock }}</span>
          <span v-if="product.category_name">
            {{ product.parent_category_name }} / {{ product.category_name }}
          </span>
        </div>

        <p v-if="!purchasable" class="summary__unavailable">
          {{ unavailableLabel }}
        </p>
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
          class="action-bar__btn action-bar__btn--outline"
          type="button"
          :disabled="favoriteLoading || (!isFavorite && product.status !== 'ON_SALE')"
          @click="onToggleFavorite"
        >
          {{ isFavorite ? '★ 已收藏' : '☆ 收藏' }}
        </button>
        <button
          class="action-bar__btn"
          type="button"
          :disabled="!purchasable || cartLoading"
          @click="onAddToCart"
        >
          {{ cartLoading ? '加入中…' : purchasable ? '加入购物车' : unavailableLabel }}
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

.summary__unavailable {
  margin: var(--sp-2) 0 0;
  font-size: var(--fs-sm);
  color: var(--c-danger);
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
  display: flex;
  gap: var(--sp-2);
  max-width: var(--buyer-max-width);
  margin: 0 auto;
  padding: var(--sp-3) var(--sp-4) calc(var(--sp-3) + env(safe-area-inset-bottom));
  background: var(--c-surface);
  border-top: 1px solid var(--c-border);
}

.action-bar__btn {
  flex: 1;
  min-height: var(--touch-min);
  font-size: var(--fs-base);
  font-weight: 600;
  color: #fff;
  background: var(--c-primary);
  border: 1px solid var(--c-primary);
  border-radius: var(--r-btn);
  cursor: pointer;
}

.action-bar__btn--outline {
  flex: none;
  width: 108px;
  color: var(--c-primary);
  background: var(--c-surface);
}

.action-bar__btn:disabled {
  color: #fff;
  background: var(--c-text-weak);
  border-color: var(--c-text-weak);
  cursor: not-allowed;
}

.action-bar__btn--outline:disabled {
  color: var(--c-text-weak);
  background: var(--c-surface);
  border-color: var(--c-border);
}
</style>
