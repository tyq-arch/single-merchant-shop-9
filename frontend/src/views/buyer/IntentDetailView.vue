<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'

import { cancelIntent, queryIntent, updateIntent } from '@/api/buyer'
import StatusTag from '@/components/StatusTag.vue'
import { useBuyerStore } from '@/stores/buyer'
import { formatPrice } from '@/utils/format'

/**
 * B5 意向详情与管理 —— FR-022 / FR-023 / FR-024 / FR-025。
 *
 * 渲染完全由后端下发的 can_edit / can_cancel 驱动：
 * 排队中才显示修改与撤销入口，进入交易后两者均不可用。
 */
const PHONE_RE = /^[0-9+\-\s]{5,20}$/

const router = useRouter()
const buyerStore = useBuyerStore()

const loading = ref(true)
const actionLoading = ref(false)
const detail = ref(null)
const errorState = ref(null)

const editing = ref(false)
const editForm = reactive({ name: '', phone: '' })
const editErrors = reactive({ name: '', phone: '' })

const order = computed(() => detail.value?.order || null)
const canEdit = computed(() => Boolean(detail.value?.can_edit))
const canCancel = computed(() => Boolean(detail.value?.can_cancel))

/** 进入交易后不可撤销，但口令码仍有效（需求 3.5.5 节） */
const inTransaction = computed(() => order.value?.status === 'IN_TRANSACTION')

async function load() {
  loading.value = true
  errorState.value = null

  if (!buyerStore.code) {
    errorState.value = {
      message: '未找到口令码，请重新输入口令码后再查询。',
    }
    loading.value = false
    return
  }

  try {
    detail.value = await queryIntent(buyerStore.code)
    resetEditForm()
  } catch (error) {
    errorState.value = { message: error.message, status: error.status }
  } finally {
    loading.value = false
  }
}

function resetEditForm() {
  editForm.name = order.value?.buyer_name || ''
  editForm.phone = order.value?.buyer_phone || ''
  editErrors.name = ''
  editErrors.phone = ''
}

function startEdit() {
  resetEditForm()
  editing.value = true
}

async function submitEdit() {
  if (actionLoading.value) return

  editErrors.name = ''
  editErrors.phone = ''

  const name = editForm.name.trim()
  const phone = editForm.phone.trim()
  const payload = { code: buyerStore.code }
  let changed = false

  // 仅提交实际改动的字段；后端要求姓名与电话至少提供一项
  if (name && name !== order.value?.buyer_name) {
    if (name.length > 64) {
      editErrors.name = '姓名不能超过 64 个字'
      return
    }
    payload.buyer_name = name
    changed = true
  }

  if (phone && phone !== order.value?.buyer_phone) {
    if (!PHONE_RE.test(phone)) {
      editErrors.phone = '联系电话格式不正确'
      return
    }
    payload.buyer_phone = phone
    changed = true
  }

  if (!changed) {
    ElMessage.info('未检测到修改内容')
    return
  }

  actionLoading.value = true
  try {
    const result = await updateIntent(payload)
    // 后端返回修改后的 order 对象
    detail.value = { ...detail.value, order: result.order ?? result }
    editing.value = false
    ElMessage.success('信息已更新，排队位次不变')
  } catch (error) {
    ElMessage.error(error.message)
    if (error.status === 410) load()
  } finally {
    actionLoading.value = false
  }
}

async function onCancel() {
  try {
    await ElMessageBox.confirm(
      '撤销后将从队列移除，且无法恢复，后面的买家会自动补位。确认撤销？',
      '确认撤销排队意向',
      {
        confirmButtonText: '确认撤销',
        cancelButtonText: '再想想',
        type: 'warning',
        confirmButtonClass: 'el-button--danger',
      },
    )
  } catch {
    return
  }

  actionLoading.value = true
  try {
    await cancelIntent(buyerStore.code)
    ElMessage.success('意向已撤销，队列已自动补位')
    await load()
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    actionLoading.value = false
  }
}

function exitQuery() {
  buyerStore.reset()
  router.replace({ name: 'buyer-query' })
}

onMounted(load)
</script>

<template>
  <div v-loading="loading" class="intent-detail">
    <template v-if="!loading">
      <!-- 口令码无效 / 缺失 -->
      <div v-if="errorState" class="empty-state">
        <div class="empty-state__icon">⚠️</div>
        <p class="empty-state__text">{{ errorState.message }}</p>
        <p class="empty-state__hint">
          口令码在交易成功、交易失败、商品下架或买家主动撤销后即失效。
        </p>
        <button class="btn btn--primary" type="button" @click="router.replace('/query')">
          重新输入口令码
        </button>
      </div>

      <template v-else-if="order">
        <!-- 排队中：突出位次 -->
        <div v-if="canCancel" class="position-card">
          <p class="position-card__label">您当前排在第</p>
          <p class="position-card__value">{{ detail.position ?? order.queue_position }}</p>
          <p class="position-card__unit">位</p>
          <StatusTag :status="order.status" :label="order.status_label" size="large" />
        </div>

        <!-- 已进入交易 -->
        <div v-else-if="inTransaction" class="position-card position-card--warm">
          <p class="position-card__title">您已进入交易</p>
          <StatusTag :status="order.status" :label="order.status_label" size="large" />
        </div>

        <!-- 已终结 -->
        <div v-else class="position-card position-card--muted">
          <p class="position-card__title">本意向已结束</p>
          <StatusTag :status="order.status" :label="order.status_label" size="large" />
        </div>

        <!-- 商品快照 -->
        <div class="info-card">
          <div class="info-row">
            <span class="info-row__key">商品</span>
            <span class="info-row__value">{{ order.product_snapshot?.name || '—' }}</span>
          </div>
          <div class="info-row">
            <span class="info-row__key">金额</span>
            <span class="info-row__value price">
              {{ formatPrice(order.product_snapshot?.price) }}
            </span>
          </div>
          <div class="info-row">
            <span class="info-row__key">提交时间</span>
            <span class="info-row__value">{{ order.created_at }}</span>
          </div>
          <div v-if="order.requeue_count > 0" class="info-row">
            <span class="info-row__key">重新排队</span>
            <span class="info-row__value">已重排 {{ order.requeue_count }} 次</span>
          </div>
        </div>

        <!-- 买家信息 -->
        <div class="info-card">
          <div class="info-row">
            <span class="info-row__key">姓名</span>
            <span class="info-row__value">{{ order.buyer_name }}</span>
          </div>
          <div class="info-row">
            <span class="info-row__key">联系电话</span>
            <span class="info-row__value">{{ order.buyer_phone }}</span>
          </div>
        </div>

        <!-- 内联编辑表单 -->
        <div v-if="editing" class="edit-card">
          <label class="field">
            <span class="field__label">姓名</span>
            <input
              v-model="editForm.name"
              class="field__input"
              :class="{ 'field__input--error': editErrors.name }"
              type="text"
              maxlength="64"
            />
            <span v-if="editErrors.name" class="field__error">{{ editErrors.name }}</span>
          </label>

          <label class="field">
            <span class="field__label">联系电话</span>
            <input
              v-model="editForm.phone"
              class="field__input"
              :class="{ 'field__input--error': editErrors.phone }"
              type="tel"
              maxlength="20"
            />
            <span v-if="editErrors.phone" class="field__error">{{ editErrors.phone }}</span>
          </label>

          <p class="edit-card__note">修改后不重新排队，位次保持不变。</p>

          <div class="edit-card__actions">
            <button class="btn btn--ghost" type="button" @click="editing = false">取消</button>
            <button
              class="btn btn--primary"
              type="button"
              :disabled="actionLoading"
              @click="submitEdit"
            >
              {{ actionLoading ? '保存中…' : '保存修改' }}
            </button>
          </div>
        </div>

        <div v-else class="intent-detail__actions">
          <!-- 进入交易后不可修改、不可撤销（FR-025） -->
          <template v-if="canEdit">
            <button class="btn btn--ghost" type="button" @click="startEdit">修改我的信息</button>
          </template>

          <template v-if="canCancel">
            <button
              class="btn btn--danger"
              type="button"
              :disabled="actionLoading"
              @click="onCancel"
            >
              撤销排队意向
            </button>
          </template>

          <div v-if="inTransaction" class="notice notice--warm">
            ⚠️ 商品交易中，请与卖家完成线下交易。进入交易后不可修改信息、不可撤销。
          </div>

          <div v-if="!canEdit && !canCancel && !inTransaction" class="notice">
            本意向已结束，口令码已失效，不能再查询、修改或撤销。
          </div>
        </div>

        <div class="intent-detail__footer">
          <button class="link-btn" type="button" @click="load">刷新状态</button>
          <span class="intent-detail__divider">·</span>
          <button class="link-btn" type="button" @click="exitQuery">退出查询</button>
        </div>
      </template>
    </template>
  </div>
</template>

<style scoped>
.intent-detail {
  min-height: 240px;
}

.position-card {
  padding: var(--sp-5) var(--sp-4);
  text-align: center;
  background: var(--c-primary-soft);
  border-radius: var(--r-card);
}

.position-card--warm {
  background: var(--c-warning-soft);
}

.position-card--muted {
  background: var(--c-muted-soft);
}

.position-card__label,
.position-card__unit {
  margin: 0;
  font-size: var(--fs-sm);
  color: var(--c-text-sub);
}

.position-card__value {
  margin: var(--sp-1) 0;
  font-size: 44px;
  font-weight: 700;
  line-height: 1.1;
  color: var(--c-primary);
}

.position-card__title {
  margin: 0 0 var(--sp-3);
  font-size: var(--fs-lg);
  font-weight: 600;
}

.info-card {
  margin-top: var(--sp-3);
  padding: var(--sp-2) var(--sp-4);
  background: var(--c-surface);
  border-radius: var(--r-card);
  box-shadow: var(--shadow-sm);
}

.info-row {
  display: flex;
  justify-content: space-between;
  gap: var(--sp-4);
  padding: var(--sp-3) 0;
  font-size: var(--fs-sm);
}

.info-row + .info-row {
  border-top: 1px solid var(--c-border);
}

.info-row__key {
  flex: none;
  color: var(--c-text-weak);
}

.info-row__value {
  text-align: right;
  word-break: break-all;
}

.edit-card {
  margin-top: var(--sp-3);
  padding: var(--sp-4);
  background: var(--c-surface);
  border-radius: var(--r-card);
  box-shadow: var(--shadow-sm);
}

.field {
  display: block;
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
  font-size: var(--fs-base);
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
  display: block;
  margin-top: var(--sp-1);
  font-size: var(--fs-sm);
  color: var(--c-danger);
}

.edit-card__note {
  margin: 0 0 var(--sp-3);
  font-size: var(--fs-sm);
  color: var(--c-text-weak);
}

.edit-card__actions {
  display: flex;
  gap: var(--sp-3);
}

.intent-detail__actions {
  display: flex;
  flex-direction: column;
  gap: var(--sp-3);
  margin-top: var(--sp-4);
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

.btn--danger {
  color: var(--c-danger);
  background: var(--c-surface);
  border: 1px solid var(--c-danger);
}

.btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.edit-card__actions .btn {
  flex: 1;
}

.notice {
  padding: var(--sp-3) var(--sp-4);
  font-size: var(--fs-sm);
  color: var(--c-text-sub);
  background: var(--c-muted-soft);
  border-radius: var(--r-btn);
}

.notice--warm {
  color: var(--c-warning);
  background: var(--c-warning-soft);
}

.intent-detail__footer {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--sp-3);
  margin-top: var(--sp-5);
}

.link-btn {
  padding: 0;
  font-size: var(--fs-sm);
  color: var(--c-primary);
  background: none;
  border: none;
  cursor: pointer;
}

.intent-detail__divider {
  color: var(--c-text-weak);
}
</style>
