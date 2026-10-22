<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { useCustomerStore } from '@/stores/customer'

const route = useRoute()
const router = useRouter()
const customerStore = useCustomerStore()

/** 首页不显示返回箭头 */
const showBack = computed(() => route.name !== 'buyer-products')

const loggedIn = computed(() => customerStore.isLoggedIn)

function goBack() {
  if (window.history.length > 1) {
    router.back()
  } else {
    router.push('/')
  }
}
</script>

<template>
  <div class="buyer-layout">
    <header class="buyer-header">
      <button v-if="showBack" class="buyer-header__back" type="button" @click="goBack">
        ←
      </button>
      <router-link to="/" class="buyer-header__brand">单卖家小店</router-link>

      <nav class="buyer-header__nav">
        <template v-if="loggedIn">
          <router-link to="/my/intents" class="buyer-header__link">我的意向</router-link>
          <router-link to="/my/orders" class="buyer-header__link">历史</router-link>
          <router-link to="/my/profile" class="buyer-header__link buyer-header__link--strong">
            {{ customerStore.username || '我的' }}
          </router-link>
        </template>
        <template v-else>
          <router-link to="/login" class="buyer-header__link">登录</router-link>
          <router-link to="/register" class="buyer-header__link buyer-header__link--strong">
            注册
          </router-link>
        </template>
      </nav>
    </header>

    <main class="buyer-main">
      <router-view />
    </main>

    <footer class="buyer-footer">
      本店为单卖家线下交易，一手交钱一手交货
    </footer>
  </div>
</template>

<style scoped>
.buyer-layout {
  display: flex;
  flex-direction: column;
  min-height: 100%;
  max-width: var(--buyer-max-width);
  margin: 0 auto;
  background: var(--c-bg);
}

.buyer-header {
  position: sticky;
  top: 0;
  z-index: 10;
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  height: 52px;
  padding: 0 var(--sp-4);
  background: var(--c-surface);
  border-bottom: 1px solid var(--c-border);
}

.buyer-header__back {
  flex: none;
  padding: 0 4px;
  font-size: var(--fs-base);
  color: var(--c-text-sub);
  background: none;
  border: none;
  cursor: pointer;
}

.buyer-header__brand {
  flex: 1;
  min-width: 0;
  font-size: var(--fs-base);
  font-weight: 600;
  color: var(--c-text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.buyer-header__nav {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  flex: none;
}

.buyer-header__link {
  font-size: var(--fs-sm);
  color: var(--c-text-sub);
  white-space: nowrap;
}

.buyer-header__link--strong {
  color: var(--c-primary);
  max-width: 88px;
  overflow: hidden;
  text-overflow: ellipsis;
}

.buyer-main {
  flex: 1;
  padding: var(--sp-4);
  padding-bottom: 96px;
}

.buyer-footer {
  padding: var(--sp-4);
  font-size: var(--fs-sm);
  color: var(--c-text-weak);
  text-align: center;
}

@media (min-width: 768px) {
  .buyer-layout {
    box-shadow: var(--shadow-md);
  }
}
</style>
