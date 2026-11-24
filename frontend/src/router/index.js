import { createRouter, createWebHistory } from 'vue-router'

import { useCustomerStore } from '@/stores/customer'
import { useSellerStore } from '@/stores/seller'

/**
 * 路由表 —— 对应《需求规格说明书-挑战C升级版》。
 *
 * 挑战 C 在 B 的「收藏 / 购物车 / 订单 5 态」之上新增：
 *   - 线上支付（待支付、已支付 2 态）
 *   - 收货地址管理 + 物流查询
 *   - 按订单项发起售后服务
 *
 * 刻意不存在的路由：
 *   - 商品详情页「立即购买」旁路（下单必经购物车）
 *   - 卖家注册 / 退出登录（FR-035）
 *   - 已发布商品编辑（FR-007，仅库存可调）
 *   - 下单时新建/编辑收货地址（TBD-07：只能选已有地址）
 */
const routes = [
  {
    path: '/',
    component: () => import('@/layouts/BuyerLayout.vue'),
    children: [
      {
        path: '',
        name: 'buyer-products',
        component: () => import('@/views/buyer/ProductListView.vue'),
        meta: { title: '逛商品' },
      },
      {
        path: 'products/:id',
        name: 'buyer-product-detail',
        component: () => import('@/views/buyer/ProductDetailView.vue'),
        meta: { title: '商品详情' },
      },
      {
        path: 'login',
        name: 'customer-login',
        component: () => import('@/views/buyer/CustomerLoginView.vue'),
        meta: { title: '客户登录', guestOnly: true },
      },
      {
        path: 'register',
        name: 'customer-register',
        component: () => import('@/views/buyer/CustomerRegisterView.vue'),
        meta: { title: '注册客户', guestOnly: true },
      },
      {
        // 游客也能查看自己临时的收藏（登录后自动合并）
        path: 'favorites',
        name: 'buyer-favorites',
        component: () => import('@/views/buyer/FavoritesView.vue'),
        meta: { title: '我的收藏' },
      },
      {
        // 游客也能使用临时购物车；下单时才要求登录
        path: 'cart',
        name: 'buyer-cart',
        component: () => import('@/views/buyer/CartView.vue'),
        meta: { title: '购物车' },
      },
      {
        path: 'my/orders',
        name: 'customer-orders',
        component: () => import('@/views/buyer/OrdersView.vue'),
        meta: { title: '我的订单', requiresCustomer: true },
      },
      {
        path: 'my/orders/:id',
        name: 'customer-order-detail',
        component: () => import('@/views/buyer/OrderDetailView.vue'),
        meta: { title: '订单详情', requiresCustomer: true },
      },
      {
        path: 'my/profile',
        name: 'customer-profile',
        component: () => import('@/views/buyer/ProfileView.vue'),
        meta: { title: '个人中心', requiresCustomer: true },
      },
      {
        path: 'my/addresses',
        name: 'customer-addresses',
        component: () => import('@/views/buyer/AddressesView.vue'),
        meta: { title: '收货地址', requiresCustomer: true },
      },
      {
        path: 'my/aftersales',
        name: 'customer-aftersales',
        component: () => import('@/views/buyer/AfterSalesView.vue'),
        meta: { title: '售后服务', requiresCustomer: true },
      },
      {
        path: 'my/aftersales/:id',
        name: 'customer-aftersale-detail',
        component: () => import('@/views/buyer/AfterSaleDetailView.vue'),
        meta: { title: '售后详情', requiresCustomer: true },
      },
    ],
  },
  {
    path: '/seller/login',
    name: 'seller-login',
    component: () => import('@/views/seller/LoginView.vue'),
    meta: { title: '卖家登录' },
  },
  {
    path: '/seller',
    component: () => import('@/layouts/SellerLayout.vue'),
    meta: { requiresSeller: true },
    children: [
      {
        path: '',
        name: 'seller-dashboard',
        component: () => import('@/views/seller/DashboardView.vue'),
        meta: { title: '工作台' },
      },
      {
        path: 'products',
        name: 'seller-products',
        component: () => import('@/views/seller/ProductsView.vue'),
        meta: { title: '商品管理' },
      },
      {
        path: 'publish',
        name: 'seller-publish',
        component: () => import('@/views/seller/PublishView.vue'),
        meta: { title: '发布商品' },
      },
      {
        path: 'categories',
        name: 'seller-categories',
        component: () => import('@/views/seller/CategoriesView.vue'),
        meta: { title: '类别管理' },
      },
      {
        path: 'orders',
        name: 'seller-orders',
        component: () => import('@/views/seller/OrdersView.vue'),
        meta: { title: '订单管理' },
      },
      {
        path: 'orders/:id',
        name: 'seller-order-detail',
        component: () => import('@/views/seller/OrderDetailView.vue'),
        meta: { title: '订单详情' },
      },
      {
        path: 'aftersales',
        name: 'seller-aftersales',
        component: () => import('@/views/seller/AfterSalesView.vue'),
        meta: { title: '售后管理' },
      },
      {
        path: 'customers',
        name: 'seller-customers',
        component: () => import('@/views/seller/CustomersView.vue'),
        meta: { title: '客户台账' },
      },
      {
        path: 'password',
        name: 'seller-password',
        component: () => import('@/views/seller/PasswordView.vue'),
        meta: { title: '修改密码' },
      },
      {
        path: 'logs',
        name: 'seller-logs',
        component: () => import('@/views/seller/LogsView.vue'),
        meta: { title: '操作日志' },
      },
    ],
  },
  { path: '/:pathMatch(.*)*', redirect: '/' },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 }),
})

router.beforeEach((to) => {
  if (to.meta.title) {
    document.title = `${to.meta.title} · 单卖家小店`
  }

  if (to.meta.requiresSeller) {
    const sellerStore = useSellerStore()
    if (sellerStore.isLoggedIn) return true
    sellerStore.clear()
    return { name: 'seller-login', query: { redirect: to.fullPath } }
  }

  if (to.meta.requiresCustomer) {
    const customerStore = useCustomerStore()
    if (customerStore.isLoggedIn) return true
    customerStore.clear()
    return { name: 'customer-login', query: { redirect: to.fullPath } }
  }

  if (to.meta.guestOnly) {
    const customerStore = useCustomerStore()
    if (customerStore.isLoggedIn) return { name: 'buyer-products' }
  }

  return true
})

export default router
