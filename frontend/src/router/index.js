import { createRouter, createWebHistory } from 'vue-router'

import { useSellerStore } from '@/stores/seller'

/**
 * 路由表 —— 见 docs/05-页面原型设计.md 第 2 节。
 *
 * 刻意不存在的路由：注册、退出登录、商品编辑（需求 FR-002 / FR-033 / FR-034）。
 */
const routes = [
  {
    path: '/',
    component: () => import('@/layouts/BuyerLayout.vue'),
    children: [
      {
        path: '',
        name: 'buyer-product',
        component: () => import('@/views/buyer/ProductView.vue'),
        meta: { title: '商品' },
      },
      {
        path: 'buy',
        name: 'buyer-buy',
        component: () => import('@/views/buyer/IntentFormView.vue'),
        meta: { title: '填写购买信息' },
      },
      {
        path: 'success',
        name: 'buyer-success',
        component: () => import('@/views/buyer/IntentSuccessView.vue'),
        meta: { title: '提交成功' },
      },
      {
        path: 'query',
        name: 'buyer-query',
        component: () => import('@/views/buyer/CodeQueryView.vue'),
        meta: { title: '查询我的意向' },
      },
      {
        path: 'intent',
        name: 'buyer-intent',
        component: () => import('@/views/buyer/IntentDetailView.vue'),
        meta: { title: '我的排队状态' },
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
    meta: { requiresAuth: true },
    children: [
      {
        path: '',
        name: 'seller-dashboard',
        component: () => import('@/views/seller/DashboardView.vue'),
        meta: { title: '工作台' },
      },
      {
        path: 'publish',
        name: 'seller-publish',
        component: () => import('@/views/seller/PublishView.vue'),
        meta: { title: '发布商品' },
      },
      {
        path: 'intents',
        name: 'seller-intents',
        component: () => import('@/views/seller/IntentsView.vue'),
        meta: { title: '意向购买人' },
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

  if (!to.meta.requiresAuth) return true

  const sellerStore = useSellerStore()
  if (sellerStore.isLoggedIn) return true

  // 令牌缺失或本地已过期（后端仍会独立校验，此处仅避免无谓的跳转闪烁）
  sellerStore.clear()
  return { name: 'seller-login', query: { redirect: to.fullPath } }
})

export default router
