<script setup>
import { onMounted, onUnmounted, ref } from 'vue'
import { onBeforeRouteLeave, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'

import { queryIntent } from '@/api/buyer'
import { useBuyerStore } from '@/stores/buyer'

/**
 * B3 提交成功 / 口令码展示 —— 本项目最关键的一页（FR-048 / FR-050）。
 *
 * 口令码是买家认领意向的唯一凭证，丢失无法找回，因此：
 *   1. 展示区做视觉强调，并提供一键复制（含 execCommand 降级）
 *   2. 离开本页时二次确认「是否已保存口令码」
 *   3. 刷新后 lastSubmit 丢失时，凭 sessionStorage 中的口令码重新查询补齐
 */
const router = useRouter()
const buyerStore = useBuyerStore()

const loading = ref(true)
const code = ref('')
const position = ref(null)
const notice = ref('')
const copied = ref(false)
/** 用户确认已保存后置为 true，不再重复拦截离开 */
const savedConfirmed = ref(false)

let copiedTimer = null

async function load() {
  loading.value = true

  const submit = buyerStore.lastSubmit
  if (submit) {
    code.value = submit.command_code || buyerStore.code
    position.value = submit.queue_position ?? null
    notice.value = submit.notice || ''
    loading.value = false
    return
  }

  // 页面刷新后立刻回退：口令码仍在 sessionStorage，重新查询补齐展示数据
  if (buyerStore.code) {
    try {
      const result = await queryIntent(buyerStore.code)
      code.value = result.order?.command_code || buyerStore.code
      position.value = result.position ?? null
    } catch (error) {
      ElMessage.error(error.message || '口令码已失效')
    } finally {
      loading.value = false
    }
    return
  }

  loading.value = false
}

async function copyCode() {
  const text = code.value
  if (!text) return

  try {
    await navigator.clipboard.writeText(text)
  } catch {
    // 剪贴板 API 不可用时降级
    const textarea = document.createElement('textarea')
    textarea.value = text
    textarea.style.position = 'fixed'
    textarea.style.opacity = '0'
    document.body.appendChild(textarea)
    textarea.select()
    try {
      document.execCommand('copy')
    } catch {
      ElMessage.warning('复制失败，请长按口令码手动复制')
      document.body.removeChild(textarea)
      return
    }
    document.body.removeChild(textarea)
  }

  copied.value = true
  clearTimeout(copiedTimer)
  copiedTimer = setTimeout(() => {
    copied.value = false
  }, 2000)
}

function markSaved() {
  savedConfirmed.value = true
}

function handleBeforeUnload(event) {
  if (savedConfirmed.value) return
  // 触发浏览器原生的「离开此网站？」确认
  event.preventDefault()
  event.returnValue = ''
}

onMounted(() => {
  load()
  window.addEventListener('beforeunload', handleBeforeUnload)
})

onUnmounted(() => {
  window.removeEventListener('beforeunload', handleBeforeUnload)
  clearTimeout(copiedTimer)
})

// 站内路由跳转同样拦截，避免买家未保存就离开
onBeforeRouteLeave(async () => {
  if (savedConfirmed.value || !code.value) return true

  try {
    await ElMessageBox.confirm(
      '请确认口令码已保存（建议截图或复制到备忘录）。口令码丢失后无法找回，将无法查询、修改或撤销该意向。',
      '离开前请确认已保存口令码',
      {
        confirmButtonText: '我已保存，离开',
        cancelButtonText: '留在本页',
        type: 'warning',
        closeOnClickModal: false,
      },
    )
    savedConfirmed.value = true
    return true
  } catch {
    return false
  }
})
</script>

<template>
  <div v-loading="loading" class="success-view">
    <template v-if="!loading">
      <!-- 正常：有口令码 -->
      <template v-if="code">
        <div class="success-view__head">
          <div class="success-view__check">✅</div>
          <h2 class="success-view__title">提交成功</h2>
          <p v-if="position" class="success-view__position">
            您的排队序号：第 <strong>{{ position }}</strong> 位
          </p>
        </div>

        <div class="code-card">
          <p class="code-card__label">您的口令码</p>

          <div class="code-card__value" role="button" tabindex="0" @click="copyCode">
            {{ code }}
          </div>

          <button class="code-card__copy" type="button" @click="copyCode">
            {{ copied ? '已复制 ✓' : '复 制 口 令 码' }}
          </button>
        </div>

        <div class="warning-box">
          <p class="warning-box__title">⚠️ 请务必自行保存口令码</p>
          <p class="warning-box__text">
            {{ notice || '这是查询、修改、撤销该意向的唯一凭证，丢失无法找回。' }}
          </p>
        </div>

        <div class="success-view__actions">
          <button class="btn btn--primary" type="button" @click="markSaved(); router.push('/intent')">
            查看我的排队状态
          </button>
          <button class="btn btn--ghost" type="button" @click="markSaved(); router.push('/')">
            返回首页
          </button>
        </div>
      </template>

      <!-- 兜底：刷新后口令码也已丢失 -->
      <div v-else class="empty-state">
        <div class="empty-state__icon">🔍</div>
        <p class="empty-state__text">未找到本次提交记录</p>
        <p class="empty-state__hint">
          口令码在关闭浏览器标签页后会清除。若已保存口令码，可前往查询页手动输入。
        </p>
        <button class="btn btn--primary" type="button" @click="router.push('/query')">
          前往口令码查询
        </button>
      </div>
    </template>
  </div>
</template>

<style scoped>
.success-view {
  min-height: 240px;
}

.success-view__head {
  text-align: center;
}

.success-view__check {
  font-size: 40px;
  line-height: 1;
}

.success-view__title {
  margin: var(--sp-2) 0 0;
  font-size: var(--fs-lg);
  font-weight: 600;
}

.success-view__position {
  margin: var(--sp-2) 0 0;
  font-size: var(--fs-sm);
  color: var(--c-text-sub);
}

.success-view__position strong {
  font-size: var(--fs-lg);
  color: var(--c-primary);
}

.code-card {
  margin-top: var(--sp-5);
  padding: var(--sp-5) var(--sp-4);
  text-align: center;
  background: var(--c-primary-soft);
  border: 1px dashed var(--c-primary);
  border-radius: var(--r-card);
}

.code-card__label {
  margin: 0;
  font-size: var(--fs-sm);
  color: var(--c-text-sub);
}

/* 口令码：等宽字体 + 加大字距，避免买家抄错字符 */
.code-card__value {
  margin: var(--sp-3) 0 var(--sp-4);
  font-family: ui-monospace, 'SFMono-Regular', Consolas, monospace;
  font-size: 26px;
  font-weight: 700;
  letter-spacing: 3px;
  color: var(--c-primary);
  word-break: break-all;
  cursor: pointer;
  user-select: all;
}

.code-card__copy {
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

.warning-box {
  margin-top: var(--sp-4);
  padding: var(--sp-3) var(--sp-4);
  background: var(--c-warning-soft);
  border-left: 3px solid var(--c-warning);
  border-radius: var(--r-btn);
}

.warning-box__title {
  margin: 0;
  font-size: var(--fs-sm);
  font-weight: 600;
  color: var(--c-warning);
}

.warning-box__text {
  margin: var(--sp-2) 0 0;
  font-size: var(--fs-sm);
  color: var(--c-text-sub);
}

.success-view__actions {
  display: flex;
  flex-direction: column;
  gap: var(--sp-3);
  margin-top: var(--sp-5);
}

.btn {
  min-height: var(--touch-min);
  font-size: var(--fs-base);
  font-weight: 600;
  border-radius: var(--r-btn);
  cursor: pointer;
}

.btn--primary {
  color: #fff;
  background: var(--c-primary);
  border: none;
}

.btn--ghost {
  color: var(--c-text-sub);
  background: var(--c-surface);
  border: 1px solid var(--c-border);
}
</style>
