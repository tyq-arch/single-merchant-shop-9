<script setup>
defineProps({
  icon: { type: String, default: '📦' },
  text: { type: String, required: true },
  hint: { type: String, default: '' },
})
</script>

<template>
  <div class="empty-state">
    <div class="empty-state__icon">{{ icon }}</div>
    <p class="empty-state__text">{{ text }}</p>
    <p v-if="hint" class="empty-state__hint">{{ hint }}</p>
    <slot />
  </div>
</template>

<style scoped>
.empty-state__text {
  margin: 0;
  font-size: var(--fs-base);
  color: var(--c-text-sub);
}

.empty-state__hint {
  margin: 0;
  font-size: var(--fs-sm);
  color: var(--c-text-weak);
}
</style>
