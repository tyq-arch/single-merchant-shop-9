<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()

/** 首页不显示返回箭头 */
const showBack = computed(() => route.name !== 'buyer-product')

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
        ← 返回
      </button>
      <router-link to="/" class="buyer-header__brand">单卖家小店</router-link>
      <router-link to="/query" class="buyer-header__action">查询意向</router-link>
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
  gap: var(--sp-3);
  height: 52px;
  padding: 0 var(--sp-4);
  background: var(--c-surface);
  border-bottom: 1px solid var(--c-border);
}

.buyer-header__back,
.buyer-header__action {
  font-size: var(--fs-sm);
  color: var(--c-text-sub);
  background: none;
  border: none;
  padding: var(--sp-1) 0;
  cursor: pointer;
  white-space: nowrap;
}

.buyer-header__action {
  margin-left: auto;
  color: var(--c-primary);
}

.buyer-header__brand {
  font-size: var(--fs-base);
  font-weight: 600;
  color: var(--c-text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.buyer-main {
  flex: 1;
  padding: var(--sp-4);
  /* 为吸底按钮预留空间 */
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
