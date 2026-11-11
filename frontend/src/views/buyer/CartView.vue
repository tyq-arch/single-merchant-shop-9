<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'

import EmptyState from '@/components/EmptyState.vue'
import ProductImage from '@/components/ProductImage.vue'
import StatusTag from '@/components/StatusTag.vue'
import { useCustomerStore } from '@/stores/customer'
import { useShopStore } from '@/stores/shop'
import { formatPrice } from '@/utils/format'
import { isLinePurchasable } from '@/utils/shop'
import {
  CART_MAX_ITEMS,
  ITEM_MAX_QUANTITY,
  ITEM_MIN_QUANTITY,
  clampQuantity,
} from '@/utils/limits'

/**
 * B4 购物车 —— 挑战 B。
 *
 * 展示名称、单价、数量、小计；支持调数量、移除、多选下单、多选转收藏。
 * 购物车不锁库存（AC-010）；下单时才占用库存（TBD-03）。
 * 游客可用临时购物车（存于后端匿名会话），下单时才要求登录（TBD-12）。
 */
const router = useRouter()
const customerStore = useCustomerStore()
const shopStore = useShopStore()

const loading = ref(true)
const items = ref([])
const selectedIds = ref([])
const shortageIds = ref([])
const acting = ref(false)
/** 正在保存数量的商品 ID —— 用于禁用输入框并显示「保存中」 */
const savingId = ref('')

const selectedItems = computed(() =>
  items.value.filter((item) => selectedIds.value.includes(item.productId)),
)

/** 是否已达购物车种类上限（50 种）—— 只影响提示文案，拦截以后端为准 */
const cartLimitReached = computed(() => items.value.length >= CART_MAX_ITEMS)

const selectedQuantity = computed(() =>
  selectedItems.value.reduce((sum, item) => sum + Number(item.quantity || 0), 0),
)

const selectedAmount = computed(() =>
  selectedItems.value.reduce(
    (sum, item) => sum + item.price * Number(item.quantity || 0),
    0,
  ),
)

const purchasableIds = computed(() =>
  items.value.filter((item) => isLinePurchasable(item)).map((item) => item.productId),
)

const allSelected = computed(
  () =>
    purchasableIds.value.length > 0 &&
    purchasableIds.value.every((id) => selectedIds.value.includes(id)),
)

const someSelected = computed(
  () =>
    !allSelected.value &&
    purchasableIds.value.some((id) => selectedIds.value.includes(id)),
)

/**
 * 数量超过当前可售件数（用于行内提醒，不阻断编辑）。
 *
 * 购物车不锁库存（AC-010），数量上限不跟库存挂钩，否则库存少于期望数量时
 * 数量控件会被封顶、看起来"改不动"。可售不足只在行内提示，
 * 结算时由后端返回缺货明细（TBD-04）再逐项标红。
 */
function overStock(item) {
  const stock = Number(item.stock)
  if (!Number.isFinite(stock) || stock < 0) return false
  return Number(item.quantity) > stock
}

async function load({ keepSelection = true } = {}) {
  loading.value = true
  try {
    const rows = await shopStore.loadCart()
    items.value = rows
    const existing = new Set(rows.map((item) => item.productId))
    if (keepSelection && selectedIds.value.length) {
      selectedIds.value = selectedIds.value.filter((id) => existing.has(id))
    } else {
      selectedIds.value = rows
        .filter((item) => isLinePurchasable(item))
        .map((item) => item.productId)
    }
    shortageIds.value = shortageIds.value.filter((id) => existing.has(id))
    await shopStore.refreshCounts()
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    loading.value = false
  }
}

function isSelected(productId) {
  return selectedIds.value.includes(productId)
}

function toggleSelect(productId, checked) {
  if (checked) {
    if (!selectedIds.value.includes(productId)) {
      selectedIds.value = [...selectedIds.value, productId]
    }
  } else {
    selectedIds.value = selectedIds.value.filter((id) => id !== productId)
  }
}

function toggleAll(checked) {
  selectedIds.value = checked ? [...purchasableIds.value] : []
}

async function onQuantityChange(item, value) {
  const next = Number(value)
  if (!Number.isFinite(next) || next < ITEM_MIN_QUANTITY) {
    item.quantity = ITEM_MIN_QUANTITY
    return
  }
  const target = clampQuantity(next)
  savingId.value = item.productId
  try {
    await shopStore.updateQuantity(item.productId, target)
    item.quantity = target
    shortageIds.value = shortageIds.value.filter((id) => id !== item.productId)
  } catch (error) {
    ElMessage.error(error.message)
    await load()
  } finally {
    savingId.value = ''
  }
}

async function onRemove(item) {
  if (acting.value) return
  acting.value = true
  try {
    const result = await shopStore.removeCartItems([item.productId])
    if (result.failed) {
      ElMessage.error('部分商品移除失败，请重试')
    } else {
      items.value = items.value.filter((row) => row.productId !== item.productId)
      selectedIds.value = selectedIds.value.filter((id) => id !== item.productId)
      ElMessage.success('已移除')
    }
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    acting.value = false
  }
}

async function onRemoveSelected() {
  if (!selectedIds.value.length) {
    ElMessage.warning('请先选择商品')
    return
  }
  try {
    await ElMessageBox.confirm(
      `确认移除选中的 ${selectedIds.value.length} 种商品？`,
      '移除商品',
      { confirmButtonText: '移除', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return
  }

  acting.value = true
  try {
    const ids = [...selectedIds.value]
    const result = await shopStore.removeCartItems(ids)
    if (result.failed) {
      ElMessage.error('部分商品移除失败，请重试')
    }
    await load({ keepSelection: false })
    if (!result.failed) ElMessage.success('已移除所选商品')
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    acting.value = false
  }
}

async function onConvertSelected() {
  if (!selectedIds.value.length) {
    ElMessage.warning('请先选择商品')
    return
  }

  acting.value = true
  try {
    const result = await shopStore.convertSelectedToFavorites([
      ...selectedIds.value,
    ])
    if (result.error) {
      ElMessage.warning(result.error)
    } else {
      ElMessage.success(`已转收藏 ${result.converted} 件`)
    }
    await load({ keepSelection: false })
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    acting.value = false
  }
}

function extractShortages(details) {
  const list = Array.isArray(details)
    ? details
    : details?.shortages || details?.items || []
  return list.map((entry) => ({
    productId: String(entry.product_id ?? entry.productId ?? entry.id ?? ''),
    name: entry.name || entry.product_name || '商品',
    available: entry.available ?? entry.stock,
  }))
}

async function onCheckout() {
  if (!selectedIds.value.length) {
    ElMessage.warning('请先选择要下单的商品')
    return
  }
  if (!customerStore.isLoggedIn) {
    try {
      await ElMessageBox.confirm(
        '下单需要先登录，当前购物车会在登录后自动合并进账号。是否前往登录？',
        '请先登录',
        { confirmButtonText: '去登录', cancelButtonText: '继续逛逛', type: 'info' },
      )
    } catch {
      return
    }
    router.push({
      name: 'customer-login',
      query: { redirect: '/cart' },
    })
    return
  }

  acting.value = true
  try {
    const data = await shopStore.checkout([...selectedIds.value])
    const order = data?.order || data
    ElMessage.success(data?.message || '下单成功')
    selectedIds.value = []
    if (order?.id) {
      router.push({
        name: 'customer-order-detail',
        params: { id: order.id },
      })
    } else {
      router.push({ name: 'customer-orders' })
    }
  } catch (error) {
    const shortages = extractShortages(error.details)
    if (error.status === 409 && shortages.length) {
      const ids = shortages.map((entry) => entry.productId).filter(Boolean)
      shortageIds.value = Array.from(new Set([...shortageIds.value, ...ids]))
      const names = shortages
        .map((entry) => entry.name)
        .filter(Boolean)
        .join('、')
      selectedIds.value = selectedIds.value.filter((id) => !ids.includes(id))
      ElMessageBox.alert(
        `以下商品库存不足：${names || '部分商品'}。已从本次选择中剔除，请调整数量或移除后再下单；其他商品可正常下单。`,
        '部分商品缺货',
        { confirmButtonText: '知道了', type: 'warning' },
      )
      await load()
    } else if (error.status === 409) {
      ElMessage.error(error.message || '部分商品库存不足，请调整后再试')
      await load()
    } else {
      ElMessage.error(error.message)
    }
  } finally {
    acting.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="cart">
    <el-alert
      v-if="!customerStore.isLoggedIn"
      class="cart__alert"
      type="info"
      :closable="false"
      show-icon
      title="未登录：购物车临时保存在游客会话中，登录后自动合并进账号；下单需登录"
    />

    <div v-loading="loading" class="cart__body">
      <template v-if="items.length">
        <div class="cart__bar">
          <el-checkbox
            :model-value="allSelected"
            :indeterminate="someSelected"
            @change="toggleAll"
          >
            全选（{{ selectedIds.length }}/{{ items.length }}）
          </el-checkbox>
          <div class="cart__bar-actions">
            <router-link class="cart__go" to="/">← 继续购物</router-link>
            <button class="cart__text-btn" type="button" @click="onRemoveSelected">
              删除所选
            </button>
          </div>
        </div>

        <p
          class="cart__limit"
          :class="{ 'cart__limit--full': cartLimitReached }"
        >
          购物车最多放 {{ CART_MAX_ITEMS }} 种商品，当前
          {{ items.length }}/{{ CART_MAX_ITEMS
          }}<template v-if="cartLimitReached">，已达上限，再加需先移除</template>
        </p>

        <div class="cart-list">
          <div
            v-for="item in items"
            :key="item.productId"
            class="cart-card"
            :class="{ 'cart-card--shortage': shortageIds.includes(item.productId) }"
          >
            <el-checkbox
              class="cart-card__check"
              :model-value="isSelected(item.productId)"
              :disabled="!isLinePurchasable(item)"
              @change="(checked) => toggleSelect(item.productId, checked)"
            />

            <router-link
              :to="{ name: 'buyer-product-detail', params: { id: item.productId } }"
              class="cart-card__thumb"
            >
              <ProductImage :src="item.image" :alt="item.name" ratio="1 / 1" />
            </router-link>

            <div class="cart-card__body">
              <div class="cart-card__head">
                <router-link
                  :to="{ name: 'buyer-product-detail', params: { id: item.productId } }"
                  class="cart-card__name"
                >
                  {{ item.name }}
                </router-link>
                <StatusTag
                  v-if="item.status && item.status !== 'ON_SALE'"
                  :status="item.status"
                  :label="item.statusLabel"
                />
              </div>

              <p class="cart-card__price price">{{ formatPrice(item.price) }}</p>
              <p v-if="!isLinePurchasable(item)" class="cart-card__unavailable">
                商品已下架或无货，无法下单
              </p>
              <p
                v-else-if="shortageIds.includes(item.productId)"
                class="cart-card__unavailable"
              >
                库存不足，请调整数量或移除
              </p>

              <div class="cart-card__foot">
                <div class="cart-card__qty-wrap">
                  <el-input-number
                    v-model="item.quantity"
                    :min="ITEM_MIN_QUANTITY"
                    :max="ITEM_MAX_QUANTITY"
                    :precision="0"
                    :disabled="savingId === item.productId"
                    size="small"
                    controls-position="right"
                    class="cart-card__qty"
                    @change="(value) => onQuantityChange(item, value)"
                  />
                  <span v-if="savingId === item.productId" class="cart-card__qty-tip">
                    保存中…
                  </span>
                  <span
                    v-else-if="isLinePurchasable(item) && overStock(item)"
                    class="cart-card__qty-tip cart-card__qty-tip--warn"
                  >
                    可售仅 {{ item.stock }} 件，下单时会提示调整
                  </span>
                </div>
                <span class="cart-card__subtotal">
                  小计 <span class="price">{{ formatPrice(item.price * item.quantity) }}</span>
                </span>
              </div>
            </div>

            <button
              class="cart-card__remove"
              type="button"
              :disabled="acting"
              @click="onRemove(item)"
            >
              移除
            </button>
          </div>
        </div>
      </template>

      <EmptyState
        v-else-if="!loading"
        icon="🛒"
        text="购物车还是空的"
        hint="去商品详情页把喜欢的商品加进来吧"
      >
        <router-link class="cart__link" to="/">去逛逛</router-link>
      </EmptyState>
    </div>

    <div v-if="items.length" class="cart-bar">
      <div class="cart-bar__summary">
        <span>已选 {{ selectedQuantity }} 件</span>
        <span class="cart-bar__amount price">{{ formatPrice(selectedAmount) }}</span>
      </div>
      <div class="cart-bar__actions">
        <button
          class="cart-bar__btn"
          type="button"
          :disabled="acting || !selectedIds.length"
          @click="onConvertSelected"
        >
          转收藏
        </button>
        <button
          class="cart-bar__btn cart-bar__btn--primary"
          type="button"
          :disabled="acting || !selectedIds.length"
          @click="onCheckout"
        >
          {{ customerStore.isLoggedIn ? '去结算' : '登录后下单' }}
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.cart {
  padding-bottom: 84px;
}

.cart__alert {
  margin-bottom: var(--sp-3);
}

.cart__body {
  min-height: 200px;
}

.cart__bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--sp-3);
  font-size: var(--fs-sm);
}

.cart__bar-actions {
  display: inline-flex;
  align-items: center;
  gap: var(--sp-4);
}

/* TBD-11 购物车种类上限提示：商品列表上方的灰色小字 */
.cart__limit {
  margin: 0 0 var(--sp-2);
  font-size: 12px;
  line-height: 1.5;
  color: var(--c-text-weak);
}

.cart__limit--full {
  color: var(--c-warning);
}

/* 返回商品列表：与「删除所选」并列，但更醒目，方便随时继续逛 */
.cart__go {
  padding: 4px 12px;
  color: var(--c-primary);
  text-decoration: none;
  background: var(--c-primary-soft);
  border-radius: var(--r-btn);
  white-space: nowrap;
}

.cart__go:hover {
  color: var(--c-primary-hover);
}

.cart__text-btn,
.cart-card__remove {
  padding: 0;
  font-size: var(--fs-sm);
  color: var(--c-text-weak);
  background: none;
  border: none;
  cursor: pointer;
}

.cart-card {
  display: flex;
  align-items: flex-start;
  gap: var(--sp-2);
  padding: var(--sp-3);
  background: var(--c-surface);
  border-radius: var(--r-card);
  box-shadow: var(--shadow-sm);
}

.cart-card + .cart-card {
  margin-top: var(--sp-3);
}

.cart-card--shortage {
  box-shadow: 0 0 0 1px var(--c-danger);
}

.cart-card__check {
  flex: none;
  margin-top: 30px;
}

.cart-card__thumb {
  width: 84px;
  flex: none;
}

.cart-card__body {
  min-width: 0;
  flex: 1;
}

.cart-card__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--sp-2);
}

.cart-card__name {
  font-size: var(--fs-base);
  font-weight: 600;
  color: var(--c-text);
  word-break: break-all;
}

.cart-card__price {
  margin: var(--sp-1) 0;
}

.cart-card__unavailable {
  margin: 0;
  font-size: var(--fs-sm);
  color: var(--c-danger);
}

.cart-card__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-2);
  margin-top: var(--sp-2);
}

.cart-card__qty-wrap {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.cart-card__qty {
  width: 108px;
  flex: none;
}

.cart-card__qty-tip {
  font-size: 12px;
  color: var(--c-text-weak);
}

.cart-card__qty-tip--warn {
  color: var(--c-warning);
}

.cart-card__subtotal {
  font-size: var(--fs-sm);
  color: var(--c-text-sub);
}

.cart-card__remove {
  flex: none;
  margin-top: 2px;
}

.cart-bar {
  position: fixed;
  right: 0;
  bottom: 0;
  left: 0;
  z-index: 10;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-3);
  max-width: var(--buyer-max-width);
  margin: 0 auto;
  padding: var(--sp-3) var(--sp-4) calc(var(--sp-3) + env(safe-area-inset-bottom));
  background: var(--c-surface);
  border-top: 1px solid var(--c-border);
}

.cart-bar__summary {
  display: flex;
  flex-direction: column;
  gap: 2px;
  font-size: var(--fs-sm);
  color: var(--c-text-sub);
}

.cart-bar__amount {
  font-size: var(--fs-lg);
  font-weight: 700;
}

.cart-bar__actions {
  display: flex;
  gap: var(--sp-2);
}

.cart-bar__btn {
  min-height: var(--touch-min);
  padding: 0 var(--sp-4);
  font-size: var(--fs-base);
  color: var(--c-text-sub);
  background: var(--c-surface);
  border: 1px solid var(--c-border);
  border-radius: var(--r-btn);
  cursor: pointer;
}

.cart-bar__btn--primary {
  color: #fff;
  background: var(--c-primary);
  border-color: var(--c-primary);
}

.cart-bar__btn:disabled {
  color: var(--c-text-weak);
  background: var(--c-muted-soft);
  border-color: var(--c-border);
  cursor: not-allowed;
}

.cart__link {
  color: var(--c-primary);
}
</style>
