<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'

import EmptyState from '@/components/EmptyState.vue'
import ProductImage from '@/components/ProductImage.vue'
import StatusTag from '@/components/StatusTag.vue'
import { useCustomerStore } from '@/stores/customer'
import { useShopStore } from '@/stores/shop'
import { formatPrice } from '@/utils/format'
import { FAVORITES_MAX_ITEMS } from '@/utils/limits'
import { favoriteUnavailableText, isLinePurchasable } from '@/utils/shop'

/**
 * B5 我的收藏 —— 挑战 B。
 *
 * 收藏不锁库存（AC-008）；商品下架后收藏项保留并标注「已下架 / 无货」（AC-009）。
 * 游客也能收藏，数据存于后端匿名会话，登录后自动合并（TBD-12）。
 */
const customerStore = useCustomerStore()
const shopStore = useShopStore()

const loading = ref(true)
const items = ref([])
const addingId = ref('')
const removingId = ref('')

/** 是否已达收藏上限（200 条）—— 只影响提示文案，拦截以后端为准 */
const favoriteLimitReached = computed(
  () => items.value.length >= FAVORITES_MAX_ITEMS,
)

async function load() {
  loading.value = true
  try {
    items.value = await shopStore.loadFavorites()
    await shopStore.refreshCounts()
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    loading.value = false
  }
}

async function onAddToCart(item) {
  if (!isLinePurchasable(item) || addingId.value) return
  addingId.value = item.productId
  try {
    const result = await shopStore.addToCart(
      {
        id: item.productId,
        name: item.name,
        price: item.price,
        primary_image: item.image,
        stock: item.stock,
        status: item.status,
        status_label: item.statusLabel,
      },
      1,
    )
    ElMessage.success(
      result.guest
        ? '已加入临时购物车，登录后自动合并'
        : '已加入购物车',
    )
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    addingId.value = ''
  }
}

async function onRemove(item) {
  if (removingId.value) return
  removingId.value = item.productId
  try {
    await shopStore.removeFavoriteItem(item.productId)
    items.value = items.value.filter(
      (row) => row.productId !== item.productId,
    )
    ElMessage.success('已取消收藏')
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    removingId.value = ''
  }
}

onMounted(load)
</script>

<template>
  <div class="favorites">
    <div class="favorites__bar">
      <span class="favorites__hint">
        共 {{ items.length }} 件
      </span>
      <div class="favorites__bar-actions">
        <router-link class="favorites__go" to="/">← 继续购物</router-link>
        <router-link class="favorites__link" to="/cart">去购物车 →</router-link>
      </div>
    </div>

    <el-alert
      v-if="!customerStore.isLoggedIn"
      class="favorites__alert"
      type="info"
      :closable="false"
      show-icon
      title="未登录时的收藏临时保存在游客会话中，登录后会自动合并进账号"
    />

    <div v-loading="loading" class="favorites__body">
      <p
        v-if="items.length"
        class="favorites__limit"
        :class="{ 'favorites__limit--full': favoriteLimitReached }"
      >
        收藏最多 {{ FAVORITES_MAX_ITEMS }} 件，当前
        {{ items.length }}/{{ FAVORITES_MAX_ITEMS
        }}<template v-if="favoriteLimitReached">，已达上限，需先取消部分收藏</template>
      </p>

      <div v-if="items.length" class="favorite-list">
        <div v-for="item in items" :key="item.productId" class="favorite-card">
          <router-link
            :to="{ name: 'buyer-product-detail', params: { id: item.productId } }"
            class="favorite-card__thumb"
          >
            <ProductImage :src="item.image" :alt="item.name" ratio="1 / 1" />
          </router-link>

          <div class="favorite-card__body">
            <div class="favorite-card__head">
              <router-link
                :to="{ name: 'buyer-product-detail', params: { id: item.productId } }"
                class="favorite-card__name"
              >
                {{ item.name }}
              </router-link>
              <StatusTag
                v-if="item.status && item.status !== 'ON_SALE'"
                :status="item.status"
                :label="item.statusLabel"
              />
            </div>

            <p class="favorite-card__price price">{{ formatPrice(item.price) }}</p>
            <p v-if="favoriteUnavailableText(item)" class="favorite-card__unavailable">
              {{ favoriteUnavailableText(item) }}
            </p>

            <div class="favorite-card__actions">
              <button
                class="favorite-card__btn favorite-card__btn--primary"
                type="button"
                :disabled="!isLinePurchasable(item) || addingId === item.productId"
                @click="onAddToCart(item)"
              >
                {{ isLinePurchasable(item) ? '加入购物车' : '暂不可购买' }}
              </button>
              <button
                class="favorite-card__btn"
                type="button"
                :disabled="removingId === item.productId"
                @click="onRemove(item)"
              >
                取消收藏
              </button>
            </div>
          </div>
        </div>
      </div>

      <EmptyState
        v-else-if="!loading"
        icon="💛"
        text="还没有收藏商品"
        hint="在商品详情页点「收藏」，心仪的商品就会出现在这里"
      >
        <router-link class="favorites__link" to="/">去逛逛</router-link>
      </EmptyState>
    </div>
  </div>
</template>

<style scoped>
.favorites__bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--sp-3);
  font-size: var(--fs-sm);
}

.favorites__hint {
  color: var(--c-text-weak);
}

.favorites__link {
  color: var(--c-primary);
}

.favorites__bar-actions {
  display: inline-flex;
  align-items: center;
  gap: var(--sp-4);
}

/* 返回商品列表：收藏看完能一键回首页继续挑 */
.favorites__go {
  padding: 4px 12px;
  color: var(--c-primary);
  text-decoration: none;
  background: var(--c-primary-soft);
  border-radius: var(--r-btn);
  white-space: nowrap;
}

.favorites__go:hover {
  color: var(--c-primary-hover);
}

.favorites__alert {
  margin-bottom: var(--sp-3);
}

.favorites__body {
  min-height: 200px;
}

/* TBD-11 收藏上限提示：商品卡片上方的灰色小字 */
.favorites__limit {
  margin: 0 0 var(--sp-2);
  font-size: 12px;
  line-height: 1.5;
  color: var(--c-text-weak);
}

.favorites__limit--full {
  color: var(--c-warning);
}

.favorite-list {
  display: flex;
  flex-direction: column;
  gap: var(--sp-3);
}

.favorite-card {
  display: flex;
  gap: var(--sp-3);
  padding: var(--sp-3);
  background: var(--c-surface);
  border-radius: var(--r-card);
  box-shadow: var(--shadow-sm);
}

.favorite-card__thumb {
  width: 88px;
  flex: none;
}

.favorite-card__body {
  min-width: 0;
  flex: 1;
}

.favorite-card__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--sp-2);
}

.favorite-card__name {
  font-size: var(--fs-base);
  font-weight: 600;
  color: var(--c-text);
  word-break: break-all;
}

.favorite-card__price {
  margin: var(--sp-1) 0;
}

.favorite-card__unavailable {
  margin: 0;
  font-size: var(--fs-sm);
  color: var(--c-danger);
}

.favorite-card__actions {
  display: flex;
  gap: var(--sp-2);
  margin-top: var(--sp-2);
}

.favorite-card__btn {
  min-height: 34px;
  padding: 0 var(--sp-3);
  font-size: var(--fs-sm);
  color: var(--c-text-sub);
  background: var(--c-surface);
  border: 1px solid var(--c-border);
  border-radius: var(--r-btn);
  cursor: pointer;
}

.favorite-card__btn--primary {
  color: #fff;
  background: var(--c-primary);
  border-color: var(--c-primary);
}

.favorite-card__btn:disabled {
  color: var(--c-text-weak);
  background: var(--c-muted-soft);
  border-color: var(--c-border);
  cursor: not-allowed;
}
</style>
