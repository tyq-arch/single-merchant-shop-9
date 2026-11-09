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
import { useCustomerStore } from '@/stores/customer'
import * as guest from '@/utils/guestShop'
import { normalizeCartLine, normalizeFavorite } from '@/utils/shop'

/**
 * 收藏 / 购物车的统一入口。
 *
 * 未登录：读写 localStorage 的临时收藏与临时购物车；
 * 已登录：走后端接口；
 * 登录成功后由 mergeGuestData() 把临时数据合并进账号（TBD-12）。
 */
export const useShopStore = defineStore('shop', () => {
  const customerStore = useCustomerStore()

  const guestCart = ref(guest.readGuestCart())
  const guestFavorites = ref(guest.readGuestFavorites())
  const guestFavoriteIds = computed(() =>
    guestFavorites.value.map((item) => String(item.product_id)),
  )

  const serverCartCount = ref(0)
  const serverFavoriteCount = ref(0)
  const favoriteIds = ref([])

  const cartCount = computed(() => {
    if (!customerStore.isLoggedIn) {
      return guestCart.value.reduce((sum, item) => sum + (Number(item.quantity) || 0), 0)
    }
    return serverCartCount.value
  })

  const favoriteCount = computed(() =>
    customerStore.isLoggedIn ? serverFavoriteCount.value : guestFavorites.value.length,
  )

  function localProductId(record) {
    return String(record.product_id ?? record.product?.id ?? record.id ?? '')
  }

  function refreshLocal() {
    guestCart.value = guest.readGuestCart()
    guestFavorites.value = guest.readGuestFavorites()
  }

  /** 顶栏角标：登录态取后端，游客态取本地 */
  async function refreshCounts() {
    if (!customerStore.isLoggedIn) {
      refreshLocal()
      return
    }

    try {
      const [cart, favorites] = await Promise.all([
        listCart(),
        listFavorites({ page: 1, page_size: 1 }),
      ])
      serverCartCount.value = (cart.items || []).reduce(
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

  async function loadCart() {
    if (!customerStore.isLoggedIn) {
      return guestCart.value.map(normalizeCartLine)
    }
    const data = await listCart()
    return (data.items || []).map(normalizeCartLine)
  }

  async function loadFavorites() {
    if (!customerStore.isLoggedIn) {
      return guestFavorites.value.map(normalizeFavorite)
    }
    const data = await listFavorites({
      page: 1,
      page_size: guest.FAVORITES_MAX_ITEMS,
    })
    serverFavoriteCount.value = Number(
      data.total ?? (data.items || []).length,
    ) || 0
    return (data.items || []).map(normalizeFavorite)
  }

  /** 商品详情进入时调用，拿到当前客户已收藏的商品 ID */
  async function loadFavoriteIds() {
    if (!customerStore.isLoggedIn) {
      favoriteIds.value = [...guestFavoriteIds.value]
      return
    }
    try {
      const data = await listFavorites({
        page: 1,
        page_size: guest.FAVORITES_MAX_ITEMS,
      })
      favoriteIds.value = (data.items || []).map(localProductId)
      serverFavoriteCount.value = Number(
        data.total ?? favoriteIds.value.length,
      ) || 0
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

    if (!customerStore.isLoggedIn) {
      guestCart.value = guest.addGuestCartItem(product, quantity)
      return { guest: true }
    }

    await addCartItem(product.id, guest.clampQuantity(quantity))
    await refreshCounts()
    return { guest: false }
  }

  async function updateQuantity(productId, quantity) {
    const next = guest.clampQuantity(quantity)
    if (!customerStore.isLoggedIn) {
      guestCart.value = guest.updateGuestCartQuantity(productId, next)
      return
    }
    await updateCartItemQuantity(productId, next)
    await refreshCounts()
  }

  async function removeCartItems(productIds) {
    if (!productIds.length) return { failed: 0 }

    if (!customerStore.isLoggedIn) {
      guestCart.value = guest.removeGuestCartItems(productIds)
      return { failed: 0 }
    }

    const results = await Promise.allSettled(
      productIds.map((productId) => removeCartItem(productId)),
    )
    await refreshCounts()
    return { failed: results.filter((item) => item.status === 'rejected').length }
  }

  /** 购物车多选转收藏：转完即从购物车移除（TBD-06） */
  async function convertSelectedToFavorites(productIds) {
    if (!productIds.length) return { converted: 0 }

    if (!customerStore.isLoggedIn) {
      const idSet = new Set(productIds.map(String))
      let converted = 0
      let error = ''
      for (const item of guestCart.value) {
        if (!idSet.has(String(item.product_id))) continue
        try {
          // 购物车快照是 snake_case，收藏写入需要商品对象
          guest.addGuestFavorite({
            id: item.product_id,
            name: item.name,
            price: item.price,
            primary_image: item.primary_image,
            stock: item.stock,
            status: item.status,
            status_label: item.status_label,
          })
          converted += 1
        } catch (err) {
          error = err.message
          break
        }
      }
      const convertedIds = guestCart.value
        .filter((item) => idSet.has(String(item.product_id)))
        .slice(0, converted)
        .map((item) => item.product_id)
      guestCart.value = guest.removeGuestCartItems(convertedIds)
      guestFavorites.value = guest.readGuestFavorites()
      return { converted, error }
    }

    const data = await convertCartToFavorites(productIds)
    await refreshCounts()
    return { converted: data?.converted_count ?? productIds.length }
  }

  async function checkout(productIds) {
    const data = await checkoutCart(productIds)
    await refreshCounts()
    return data
  }

  /* ---------------- 收藏 ---------------- */

  async function toggleFavorite(product) {
    if (!customerStore.isLoggedIn) {
      const productId = String(product.id)
      const wasFavorite = guest.isGuestFavorite(productId)
      if (wasFavorite) {
        guestFavorites.value = guest.removeGuestFavorite(productId)
      } else {
        guestFavorites.value = guest.addGuestFavorite(product)
      }
      return { favorited: !wasFavorite, guest: true }
    }

    const productId = String(product.id)
    const wasFavorite = hasFavorite(productId)
    if (wasFavorite) {
      await removeFavorite(productId)
      favoriteIds.value = favoriteIds.value.filter((id) => id !== productId)
      serverFavoriteCount.value = Math.max(0, serverFavoriteCount.value - 1)
    } else {
      await addFavorite(productId)
      favoriteIds.value = [...favoriteIds.value, productId]
      serverFavoriteCount.value += 1
    }
    return { favorited: !wasFavorite, guest: false }
  }

  async function removeFavoriteItem(productId) {
    if (!customerStore.isLoggedIn) {
      guestFavorites.value = guest.removeGuestFavorite(productId)
      return
    }
    await removeFavorite(productId)
    favoriteIds.value = favoriteIds.value.filter(
      (id) => id !== String(productId),
    )
    serverFavoriteCount.value = Math.max(0, serverFavoriteCount.value - 1)
  }

  /* ---------------- 登录后合并临时数据 ---------------- */

  /**
   * 网络错误、5xx、404（接口尚未部署）时保留本地数据，下次登录再试；
   * 4xx（已下架、库存不足、超出上限）说明该商品确实无法合并，直接丢弃。
   */
  function isRetryableMergeError(error) {
    return !error.status || error.status >= 500 || error.status === 404
  }

  /**
   * 把游客临时收藏/购物车合并进账号。
   *
   * 单个商品失败时：4xx（已下架、库存不足等）直接丢弃；
   * 网络错误或 5xx 保留在本地，下次登录再试。
   */
  async function mergeGuestData() {
    if (!customerStore.isLoggedIn) return { favorites: 0, cart: 0 }

    const pendingFavorites = [...guestFavorites.value]
    const pendingCart = [...guestCart.value]
    if (!pendingFavorites.length && !pendingCart.length) {
      await refreshCounts()
      return { favorites: 0, cart: 0 }
    }

    const keepFavorites = []
    const keepCart = []
    let mergedFavorites = 0
    let mergedCart = 0

    for (const item of pendingFavorites) {
      try {
        await addFavorite(item.product_id)
        mergedFavorites += 1
      } catch (error) {
        if (isRetryableMergeError(error)) keepFavorites.push(item)
      }
    }

    for (const item of pendingCart) {
      try {
        await addCartItem(item.product_id, guest.clampQuantity(item.quantity))
        mergedCart += 1
      } catch (error) {
        if (isRetryableMergeError(error)) keepCart.push(item)
      }
    }

    guest.writeGuestFavorites(keepFavorites)
    guest.writeGuestCart(keepCart)
    guestFavorites.value = keepFavorites
    guestCart.value = keepCart

    await refreshCounts()
    return { favorites: mergedFavorites, cart: mergedCart }
  }

  return {
    guestCart,
    guestFavorites,
    favoriteIds,
    cartCount,
    favoriteCount,
    refreshLocal,
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
    mergeGuestData,
  }
})
