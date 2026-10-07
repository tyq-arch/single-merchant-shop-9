<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'

import { useSellerStore } from '@/stores/seller'

const route = useRoute()
const sellerStore = useSellerStore()

/**
 * 后台导航 —— 依据需求 3.9 节「卖家后台功能入口」。
 * 刻意不含：编辑商品（FR-002）、退出登录（FR-034）、注册（FR-033）。
 */
const navItems = [
  { name: 'seller-dashboard', label: '工作台', icon: '🏠' },
  { name: 'seller-publish', label: '发布商品', icon: '📦' },
  { name: 'seller-intents', label: '意向购买人', icon: '👥' },
  { name: 'seller-history', label: '历史商品', icon: '🗂️' },
  { name: 'seller-password', label: '修改密码', icon: '🔑' },
  { name: 'seller-logs', label: '操作日志', icon: '📋' },
]

const activeName = computed(() => {
  // 历史详情页归属「历史商品」高亮
  if (route.name === 'seller-history-detail') return 'seller-history'
  return route.name
})
</script>

<template>
  <el-container class="seller-layout">
    <el-aside class="seller-aside" width="200px">
      <div class="seller-aside__brand">单卖家小店</div>
      <div class="seller-aside__caption">卖家后台</div>

      <el-menu :default-active="activeName" router class="seller-menu">
        <el-menu-item v-for="item in navItems" :key="item.name" :index="item.name">
          <span class="seller-menu__icon">{{ item.icon }}</span>
          <span>{{ item.label }}</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="seller-header">
        <h1 class="seller-header__title">{{ route.meta.title || '卖家后台' }}</h1>
        <div class="seller-header__user">
          当前账号：<strong>{{ sellerStore.username || '—' }}</strong>
        </div>
      </el-header>

      <el-main class="seller-main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.seller-layout {
  min-height: 100vh;
}

.seller-aside {
  background: var(--c-surface);
  border-right: 1px solid var(--c-border);
}

.seller-aside__brand {
  padding: var(--sp-5) var(--sp-5) 0;
  font-size: var(--fs-lg);
  font-weight: 600;
}

.seller-aside__caption {
  padding: 2px var(--sp-5) var(--sp-5);
  font-size: var(--fs-sm);
  color: var(--c-text-weak);
  border-bottom: 1px solid var(--c-border);
}

.seller-menu {
  border-right: none;
}

.seller-menu__icon {
  margin-right: var(--sp-2);
}

.seller-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-4);
  background: var(--c-surface);
  border-bottom: 1px solid var(--c-border);
}

.seller-header__title {
  margin: 0;
  font-size: var(--fs-lg);
  font-weight: 600;
}

.seller-header__user {
  font-size: var(--fs-sm);
  color: var(--c-text-sub);
}

.seller-main {
  padding: var(--sp-5);
  background: var(--c-bg);
}

/* 窄屏：侧边栏收窄，仅保留图标 */
@media (max-width: 1024px) {
  .seller-aside {
    width: 64px !important;
  }

  .seller-aside__brand,
  .seller-aside__caption,
  .seller-menu__icon + span {
    display: none;
  }

  .seller-menu__icon {
    margin-right: 0;
  }
}
</style>
