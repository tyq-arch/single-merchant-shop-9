<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'

import { queryIntent } from '@/api/buyer'
import { useBuyerStore } from '@/stores/buyer'
import { normalizeCode } from '@/utils/format'

/**
 * B4 口令码查询 —— FR-022 / AC-008。
 * 买家凭口令码认领意向；口令码不是账号，不能登录后台、不能查他人意向。
 */
const router = useRouter()
const buyerStore = useBuyerStore()

const input = ref('')
const loading = ref(false)
const errorText = ref('')

async function onSubmit() {
  if (loading.value) return

  const code = normalizeCode(input.value)
  errorText.value = ''

  if (!code) {
    errorText.value = '请输入口令码'
    return
  }

  loading.value = true
  try {
    // 先校验口令码有效，再进入详情页，避免跳转后才发现失效
    await queryIntent(code)
    buyerStore.saveCode(code)
    router.push({ name: 'buyer-intent' })
  } catch (error) {
    errorText.value = error.message
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="query-view">
    <div class="field">
      <label class="field__label" for="code-input">请输入您的口令码</label>
      <input
        id="code-input"
        v-model="input"
        class="field__input"
        :class="{ 'field__input--error': errorText }"
        type="text"
        placeholder="例如 K7M2PQ9XTR"
        autocapitalize="characters"
        autocomplete="off"
        spellcheck="false"
        @keyup.enter="onSubmit"
      />
      <p v-if="errorText" class="field__error">{{ errorText }}</p>
    </div>

    <button class="query-view__btn" type="button" :disabled="loading" @click="onSubmit">
      {{ loading ? '查询中…' : '查 询' }}
    </button>

    <div class="hint-box">
      <p class="hint-box__title">ℹ️ 关于口令码</p>
      <ul class="hint-box__list">
        <li>口令码是您查询、修改、撤销该笔意向的唯一凭证。</li>
        <li>丢失后无法找回，请检查是否与提交成功页显示的一致。</li>
        <li>口令码不是账号，不能用于登录卖家后台。</li>
      </ul>
    </div>
  </div>
</template>

<style scoped>
.field {
  margin-bottom: var(--sp-4);
}

.field__label {
  display: block;
  margin-bottom: var(--sp-2);
  font-size: var(--fs-sm);
  color: var(--c-text-sub);
}

.field__input {
  width: 100%;
  min-height: var(--touch-min);
  padding: 0 var(--sp-3);
  font-family: ui-monospace, 'SFMono-Regular', Consolas, monospace;
  font-size: var(--fs-lg);
  letter-spacing: 2px;
  text-transform: uppercase;
  color: var(--c-text);
  background: var(--c-surface);
  border: 1px solid var(--c-border);
  border-radius: var(--r-btn);
  outline: none;
}

.field__input:focus {
  border-color: var(--c-primary);
}

.field__input--error {
  border-color: var(--c-danger);
}

.field__error {
  margin: var(--sp-2) 0 0;
  font-size: var(--fs-sm);
  color: var(--c-danger);
}

.query-view__btn {
  width: 100%;
  min-height: var(--touch-min);
  font-size: var(--fs-base);
  font-weight: 600;
  color: #fff;
  background: var(--c-primary);
  border: none;
  border-radius: var(--r-btn);
  cursor: pointer;
}

.query-view__btn:disabled {
  background: var(--c-text-weak);
  cursor: not-allowed;
}

.hint-box {
  margin-top: var(--sp-6);
  padding: var(--sp-4);
  background: var(--c-surface);
  border-radius: var(--r-card);
}

.hint-box__title {
  margin: 0 0 var(--sp-2);
  font-size: var(--fs-sm);
  font-weight: 600;
  color: var(--c-text-sub);
}

.hint-box__list {
  margin: 0;
  padding-left: var(--sp-5);
  font-size: var(--fs-sm);
  line-height: 1.8;
  color: var(--c-text-weak);
}
</style>
