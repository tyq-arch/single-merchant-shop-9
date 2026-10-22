import { createRouter, createWebHistory } from 'vue-router'

import { useCustomerStore } from '@/stores/customer'
import { useSellerStore } from '@/stores/seller'

/**
 * 路由表 —— 对应《需求规格说明书-挑战A升级版》。
 *
 * 刻意不存在的路由：
 *   - 买家撤销 / 修改意向（FR-025 已作废该能力）
 *   - 口令码查询（C-10 口令码机制作废）
 *   - 卖家注册 / 退出登录（FR-036）
 *   - 已发布商品编辑（FR-007，仅库存可调）
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
        path: 'my/intents',
        name: 'customer-intents',
        component: () => import('@/views/buyer/MyIntentsView.vue'),
        meta: { title: '我的意向', requiresCustomer: true },
      },
      {
        path: 'my/intents/:id',
        name: 'customer-intent-detail',
        component: () => import('@/views/buyer/IntentDetailView.vue'),
        meta: { title: '意向详情', requiresCustomer: true },
      },
      {
        path: 'my/orders',
        name: 'customer-orders',
        component: () => import('@/views/buyer/OrdersView.vue'),
        meta: { title: '历史下单', requiresCustomer: true },
      },
      {
        path: 'my/profile',
        name: 'customer-profile',
        component: () => import('@/views/buyer/ProfileView.vue'),
        meta: { title: '个人中心', requiresCustomer: true },
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
        path: 'intents',
        name: 'seller-intents',
        component: () => import('@/views/seller/IntentsView.vue'),
        meta: { title: '意向队列' },
      },
      {
        path: 'history',
        name: 'seller-history',
        component: () => import('@/views/seller/HistoryView.vue'),
        meta: { title: '历史商品' },
      },
      {
        path: 'history/:id',
        name: 'seller-history-detail',
        component: () => import('@/views/seller/HistoryDetailView.vue'),
        meta: { title: '历史商品详情' },
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
