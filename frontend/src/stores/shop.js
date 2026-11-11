import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

import {
  addCartItem,
  addFavorite,
  checkoutCart,
  convertCartToFavorites,
  listCart,
  listFavorites,
  removeCartItem,
  removeFavorite,
  updateCartItemQuantity,
} from '@/api/customer'
import * as guestApi from '@/api/guest'
import { useCustomerStore } from '@/stores/customer'
import { FAVORITES_MAX_ITEMS, FAVORITES_PAGE_SIZE, clampQuantity } from '@/utils/limits'
import { normalizeCartLine, normalizeFavorite } from '@/utils/shop'

/**
 * 收藏 / 购物车的统一入口。
 *
 * 未登录：走 /api/guest/**，数据挂在后端匿名会话（X-Guest-Id）下；
 * 已登录：走 /api/customer/**；
 * 登录时同一个 X-Guest-Id 随请求提交，由后端把临时收藏/购物车并入账号（TBD-12），
 * 前端只负责清点合并结果并刷新角标，不再逐条补录。
 */
export const useShopStore = defineStore('shop', () => {
  const customerStore = useCustomerStore()

  const serverCartCount = ref(0)
  const serverFavoriteCount = ref(0)
  const favoriteIds = ref([])

  const isGuest = computed(() => !customerStore.isLoggedIn)

  const cartCount = computed(() => serverCartCount.value)

  const favoriteCount = computed(() => serverFavoriteCount.value)

  function localProductId(record) {
    return String(record.product_id ?? record.product?.id ?? record.id ?? '')
  }

  /* ---------------- 角标 ---------------- */

  /** 顶栏角标：游客走 /guest，登录态走 /customer */
  async function refreshCounts() {
    try {
      const [cart, favorites] = isGuest.value
        ? await Promise.all([guestApi.listCart(), guestApi.listFavorites({ page: 1, page_size: 1 })])
        : await Promise.all([listCart(), listFavorites({ page: 1, page_size: 1 })])

      serverCartCount.value = Number(cart?.total_quantity) || (cart.items || []).reduce(
        (sum, item) => sum + (Number(item.quantity) || 0),
        0,
      )
      serverFavoriteCount.value = Number(
        favorites.total ?? (favorites.items || []).length,
      ) || 0
    } catch {
      /* 角标失败不阻塞页面 */
    }
  }

  /* ---------------- 列表 ---------------- */

  async function loadCart() {
    const data = isGuest.value ? await guestApi.listCart() : await listCart()
    return (data.items || []).map(normalizeCartLine)
  }

  async function loadFavorites() {
    const data = await fetchAllFavorites()

    serverFavoriteCount.value = data.total
    return data.items.map(normalizeFavorite)
  }

  /**
   * 拉取全部收藏。
   *
   * 后端分页接口的 page_size 上限是 AppConfig.maxPageSize（默认 100），
   * 而收藏本身最多 200 条，所以不能把 200 当成 page_size 直接传
   * （会触发 422「page_size 必须在 1 到 100 之间」），需要按页循环取。
   */
  async function fetchAllFavorites() {
    const fetchPage = isGuest.value ? guestApi.listFavorites : listFavorites
    const maxPages = Math.ceil(FAVORITES_MAX_ITEMS / FAVORITES_PAGE_SIZE)
    const items = []
    let total = 0

    for (let page = 1; page <= maxPages; page += 1) {
      const data = await fetchPage({ page, page_size: FAVORITES_PAGE_SIZE })
      const rows = data.items || []
      total = Number(data.total ?? rows.length) || 0
      items.push(...rows)
      // 本页不足一页，或已取满总数，说明没有更多
      if (rows.length < FAVORITES_PAGE_SIZE || items.length >= total) break
    }

    return { items, total: total || items.length }
  }

  /** 商品详情进入时调用，拿到当前已收藏的商品 ID */
  async function loadFavoriteIds() {
    try {
      const data = await fetchAllFavorites()
      favoriteIds.value = data.items.map(localProductId)
      serverFavoriteCount.value = data.total
    } catch {
      favoriteIds.value = []
    }
  }

  function hasFavorite(productId) {
    return favoriteIds.value.includes(String(productId))
  }

  /* ---------------- 加购 ---------------- */

  async function addToCart(product, quantity = 1) {
    if (product?.status && product.status !== 'ON_SALE') {
      throw new Error(product.status_label || '商品当前不可购买')
    }
    if (
      product?.stock !== undefined &&
      product?.stock !== null &&
      Number(product.stock) <= 0
    ) {
      throw new Error('商品库存不足')
    }

    const guest = isGuest.value
    if (guest) {
      await guestApi.addCartItem(product.id, clampQuantity(quantity))
    } else {
      await addCartItem(product.id, clampQuantity(quantity))
    }
    await refreshCounts()
    return { guest }
  }

  async function updateQuantity(productId, quantity) {
    const next = clampQuantity(quantity)
    if (isGuest.value) {
      await guestApi.updateCartItemQuantity(productId, next)
    } else {
      await updateCartItemQuantity(productId, next)
    }
    await refreshCounts()
  }

  async function removeCartItems(productIds) {
    if (!productIds.length) return { failed: 0 }

    const remove = isGuest.value ? guestApi.removeCartItem : removeCartItem
    const results = await Promise.allSettled(
      productIds.map((productId) => remove(productId)),
    )
    await refreshCounts()
    return { failed: results.filter((item) => item.status === 'rejected').length }
  }

  /** 购物车多选转收藏：转完即从购物车移除（TBD-06） */
  async function convertSelectedToFavorites(productIds) {
    if (!productIds.length) return { converted: 0 }

    if (isGuest.value) {
      const data = await guestApi.convertCartToFavorites(productIds)
      await refreshCounts()
      return { converted: data?.converted_count ?? productIds.length }
    }

    const data = await convertCartToFavorites(productIds)
    await refreshCounts()
    return { converted: data?.converted_count ?? productIds.length }
  }

  /** 下单：仅登录客户可用（TBD-14），游客在页面侧已被引导去登录 */
  async function checkout(productIds) {
    const data = await checkoutCart(productIds)
    await refreshCounts()
    return data
  }

  /* ---------------- 收藏 ---------------- */

  async function toggleFavorite(product) {
    const productId = String(product.id)
    const wasFavorite = hasFavorite(productId)
    const guest = isGuest.value

    if (wasFavorite) {
      await (guest ? guestApi.removeFavorite(productId) : removeFavorite(productId))
      favoriteIds.value = favoriteIds.value.filter((id) => id !== productId)
      serverFavoriteCount.value = Math.max(0, serverFavoriteCount.value - 1)
    } else {
      await (guest ? guestApi.addFavorite(productId) : addFavorite(productId))
      favoriteIds.value = [...favoriteIds.value, productId]
      serverFavoriteCount.value += 1
    }

    return { favorited: !wasFavorite, guest }
  }

  async function removeFavoriteItem(productId) {
    const id = String(productId)
    if (isGuest.value) {
      await guestApi.removeFavorite(id)
    } else {
      await removeFavorite(id)
    }
    favoriteIds.value = favoriteIds.value.filter((item) => item !== id)
    serverFavoriteCount.value = Math.max(0, serverFavoriteCount.value - 1)
  }

  /* ---------------- 登录后合并临时预选 ---------------- */

  /**
   * 登录接口已经把 X-Guest-Id 交给后端，回包里的 preselect_merged
   * 就是合并结果（favorites_added / cart_merged）。
   * 这里只清点数量并刷新角标，临时数据已由后端删除、并入账号。
   */
  async function mergeGuestPreselect(merged) {
    await refreshCounts()
    return {
      favorites: Number(merged?.favorites_added) || 0,
      cart: Number(merged?.cart_merged) || 0,
    }
  }

  return {
    favoriteIds,
    cartCount,
    favoriteCount,
    isGuest,
    refreshCounts,
    loadCart,
    loadFavorites,
    loadFavoriteIds,
    hasFavorite,
    addToCart,
    updateQuantity,
    removeCartItems,
    convertSelectedToFavorites,
    checkout,
    toggleFavorite,
    removeFavoriteItem,
    mergeGuestPreselect,
  }
})
