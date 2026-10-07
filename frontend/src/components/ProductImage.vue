<script setup>
/**
 * 商品图。固定宽高比容器 + object-fit，避免不同尺寸的图片导致布局跳动
 * （见 docs/05-页面原型设计.md 第 3.2 节与风险表）。
 */
defineProps({
  src: { type: String, default: '' },
  alt: { type: String, default: '商品图片' },
  ratio: { type: String, default: '4 / 3' },
})
</script>

<template>
  <div class="product-image" :style="{ aspectRatio: ratio }">
    <img v-if="src" :src="src" :alt="alt" class="product-image__img" />
    <div v-else class="product-image__placeholder" aria-hidden="true">🖼️</div>
  </div>
</template>

<style scoped>
.product-image {
  width: 100%;
  overflow: hidden;
  background: var(--c-muted-soft);
  border-radius: var(--r-card);
}

.product-image__img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.product-image__placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  font-size: 32px;
  color: var(--c-text-weak);
}
</style>
