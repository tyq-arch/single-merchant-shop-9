<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'

import {
  acceptAfterSale,
  getAfterSale,
  listAfterSales,
  rejectAfterSale,
  resolveAfterSale,
} from '@/api/seller'
import StatusTag from '@/components/StatusTag.vue'
import { formatPrice } from '@/utils/format'
import {
  AFTER_SALE_RESULT_OPTIONS,
  AFTER_SALE_STATUS_OPTIONS,
  normalizeAfterSale,
} from '@/utils/shop'

/**
 * C3 售后管理 —— FR-053 / FR-054。
 *
 * 客户按订单项发起售后；商家线下协商后在系统内记录结果
 * （退货 / 退款 / 换货等）。退货成功回补库存，纯退款不回补（TBD-12）。
 */
const route = useRoute()
const router = useRouter()

const loading = ref(true)
const items = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)
const status = ref(
  typeof route.query.status === 'string' ? route.query.status : '',
)
const keyword = ref('')

const drawerVisible = ref(false)
const detailLoading = ref(false)
const detail = ref(null)
const acting = ref(false)

const resolveVisible = ref(false)
const resolving = ref(false)
const resolveFormRef = ref()
const resolveForm = reactive({
  result: 'REFUND',
  note: '',
  refundAmount: 0,
})

const resolveRules = {
  result: [{ required: true, message: '请选择售后结果', trigger: 'change' }],
  note: [{ required: true, message: '请填写协商说明', trigger: 'blur' }],
  refundAmount: [
    {
      validator: (_rule, value, callback) => {
        if (resolveForm.result !== 'REFUND') return callback()
        const amount = Number(value)
        if (!Number.isFinite(amount) || amount <= 0) {
          return callback(new Error('退款时请填写大于 0 的退款金额'))
        }
        callback()
      },
      trigger: 'blur',
    },
  ],
}

const canAccept = computed(() => detail.value?.status === 'PENDING')
const canReject = computed(() =>
  ['PENDING', 'NEGOTIATING'].includes(detail.value?.status),
)
const canResolve = computed(() => detail.value?.status === 'NEGOTIATING')

async function load() {
  loading.value = true
  try {
    const data = await listAfterSales({
      page: page.value,
      page_size: pageSize.value,
      status: status.value || undefined,
      keyword: keyword.value.trim() || undefined,
    })
    items.value = (data.items || []).map(normalizeAfterSale)
    total.value = Number(data.total ?? items.value.length) || 0
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    loading.value = false
  }
}

function search() {
  page.value = 1
  load()
}

function onPageChange(nextPage) {
  page.value = nextPage
  load()
}

async function refreshDetail() {
  if (!detail.value?.id) return
  try {
    const data = await getAfterSale(detail.value.id)
    detail.value = normalizeAfterSale(data.aftersale || data)
  } catch (error) {
    ElMessage.error(error.message)
    drawerVisible.value = false
  } finally {
    detailLoading.value = false
  }
}

async function openDetail(row) {
  drawerVisible.value = true
  detailLoading.value = true
  detail.value = normalizeAfterSale(row)
  await refreshDetail()
}

async function onAccept() {
  if (!detail.value || acting.value) return
  try {
    await ElMessageBox.confirm(
      '受理后售后进入「协商中」，请线下与客户沟通处理方案。',
      '受理售后',
      { confirmButtonText: '受理', cancelButtonText: '取消', type: 'info' },
    )
  } catch {
    return
  }

  acting.value = true
  try {
    const data = await acceptAfterSale(detail.value.id)
    ElMessage.success(data?.message || '已受理')
    await Promise.all([load(), refreshDetail()])
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    acting.value = false
  }
}

async function onReject() {
  if (!detail.value || acting.value) return

  let reason = ''
  try {
    const result = await ElMessageBox.prompt(
      '请填写拒绝原因，客户可在售后详情看到该说明。',
      '拒绝售后',
      {
        confirmButtonText: '确认拒绝',
        cancelButtonText: '取消',
        inputType: 'textarea',
        inputValidator: (value) =>
          (value && value.trim().length > 0) || '请填写拒绝原因',
      },
    )
    reason = result.value.trim()
  } catch {
    return
  }

  acting.value = true
  try {
    const data = await rejectAfterSale(detail.value.id, reason)
    ElMessage.success(data?.message || '已拒绝')
    await Promise.all([load(), refreshDetail()])
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    acting.value = false
  }
}

function openResolve() {
  resolveForm.result = 'REFUND'
  resolveForm.note = ''
  resolveForm.refundAmount = detail.value?.refundAmount || 0
  resolveVisible.value = true
  resolveFormRef.value?.clearValidate()
}

async function onResolve() {
  if (!detail.value || resolving.value) return

  try {
    await resolveFormRef.value.validate()
  } catch {
    return
  }

  const payload = {
    result: resolveForm.result,
    note: resolveForm.note.trim(),
  }
  if (resolveForm.result === 'REFUND') {
    payload.refund_amount = Number(resolveForm.refundAmount)
  }

  resolving.value = true
  try {
    const data = await resolveAfterSale(detail.value.id, payload)
    ElMessage.success(data?.message || '售后结果已记录')
    resolveVisible.value = false
    await Promise.all([load(), refreshDetail()])
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    resolving.value = false
  }
}

function openOrder() {
  if (!detail.value?.orderId) return
  router.push({
    name: 'seller-order-detail',
    params: { id: detail.value.orderId },
  })
}

/** 工作台带 ?status=PENDING 跳进来时同步筛选条件 */
watch(
  () => route.query.status,
  (value) => {
    const next = typeof value === 'string' ? value : ''
    if (next === status.value) return
    status.value = next
    page.value = 1
    load()
  },
)

onMounted(load)
</script>

<template>
  <el-card shadow="never">
    <div class="aftersales__toolbar">
      <el-select
        v-model="status"
        class="aftersales__status"
        placeholder="全部状态"
        @change="search"
      >
        <el-option
          v-for="option in AFTER_SALE_STATUS_OPTIONS"
          :key="option.value"
          :label="option.label"
          :value="option.value"
        />
      </el-select>

      <el-input
        v-model="keyword"
        placeholder="搜索订单号或商品"
        clearable
        class="aftersales__keyword"
        @keyup.enter="search"
        @clear="search"
      />
      <el-button type="primary" @click="search">查询</el-button>
    </div>

    <el-table v-loading="loading" :data="items" border>
      <el-table-column label="订单号" min-width="170">
        <template #default="{ row }">{{ row.orderNo || row.orderId || '—' }}</template>
      </el-table-column>
      <el-table-column label="商品" min-width="160">
        <template #default="{ row }">{{ row.productName }}</template>
      </el-table-column>
      <el-table-column prop="title" label="售后标题" min-width="180" />
      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <StatusTag :status="row.status" :label="row.statusLabel" />
        </template>
      </el-table-column>
      <el-table-column label="结果" width="110">
        <template #default="{ row }">
          <span v-if="row.resultLabel">{{ row.resultLabel }}</span>
          <span v-else>—</span>
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="申请时间" width="180" />
      <el-table-column label="操作" width="110" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row)">
            查看 / 处理
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-empty
      v-if="!items.length && !loading"
      description="暂无售后申请"
      :image-size="80"
    />

    <div class="aftersales__pager">
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        background
        @current-change="onPageChange"
        @size-change="search"
      />
    </div>
  </el-card>

  <el-drawer v-model="drawerVisible" title="售后详情" size="560px">
    <div v-loading="detailLoading" class="aftersale-detail">
      <template v-if="detail">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="订单号">
            <el-button link type="primary" @click="openOrder">
              {{ detail.orderNo || detail.orderId || '—' }}
            </el-button>
          </el-descriptions-item>
          <el-descriptions-item label="商品">
            {{ detail.productName }}
          </el-descriptions-item>
          <el-descriptions-item label="售后状态">
            <StatusTag :status="detail.status" :label="detail.statusLabel" />
          </el-descriptions-item>
          <el-descriptions-item label="申请时间">
            {{ detail.createdAt || '—' }}
          </el-descriptions-item>
          <el-descriptions-item v-if="detail.resultLabel" label="处理结果">
            {{ detail.resultLabel }}
            <span v-if="detail.refundAmount > 0" class="price">
              {{ formatPrice(detail.refundAmount) }}
            </span>
          </el-descriptions-item>
          <el-descriptions-item v-if="detail.resultNote" label="处理说明">
            {{ detail.resultNote }}
          </el-descriptions-item>
          <el-descriptions-item v-if="detail.handledAt" label="处理时间">
            {{ detail.handledAt }}
          </el-descriptions-item>
        </el-descriptions>

        <h3 class="aftersale-detail__title">{{ detail.title || '售后申请' }}</h3>
        <p class="aftersale-detail__text">{{ detail.description || '—' }}</p>

        <div v-if="detail.images.length" class="aftersale-detail__images">
          <el-image
            v-for="(image, index) in detail.images"
            :key="image"
            :src="image"
            :preview-src-list="detail.images"
            :initial-index="index"
            fit="cover"
            class="aftersale-detail__image"
          />
        </div>

        <div class="aftersale-detail__actions">
          <el-button
            v-if="canAccept"
            type="primary"
            :loading="acting"
            @click="onAccept"
          >
            受理（进入协商）
          </el-button>
          <el-button
            v-if="canResolve"
            type="success"
            :loading="acting"
            @click="openResolve"
          >
            记录处理结果
          </el-button>
          <el-button
            v-if="canReject"
            type="danger"
            plain
            :loading="acting"
            @click="onReject"
          >
            拒绝售后
          </el-button>
        </div>

        <el-alert
          v-if="!canAccept && !canResolve && !canReject"
          class="aftersale-detail__alert"
          type="info"
          :closable="false"
          show-icon
          title="该售后已结束，结果对客户可见"
        />
      </template>
    </div>
  </el-drawer>

  <el-dialog
    v-model="resolveVisible"
    title="记录售后结果"
    width="440px"
    append-to-body
    :close-on-click-modal="!resolving"
  >
    <el-form
      ref="resolveFormRef"
      :model="resolveForm"
      :rules="resolveRules"
      label-position="top"
      @submit.prevent="onResolve"
    >
      <el-form-item label="处理结果" prop="result">
        <el-radio-group v-model="resolveForm.result">
          <el-radio
            v-for="option in AFTER_SALE_RESULT_OPTIONS"
            :key="option.value"
            :value="option.value"
          >
            {{ option.label }}
          </el-radio>
        </el-radio-group>
      </el-form-item>

      <el-form-item
        v-if="resolveForm.result === 'REFUND'"
        label="退款金额"
        prop="refundAmount"
      >
        <el-input-number
          v-model="resolveForm.refundAmount"
          :min="0"
          :precision="2"
          :step="1"
          class="resolve-form__amount"
        />
        <p class="resolve-form__hint">退款与取消退款走同一原路退回通道</p>
      </el-form-item>

      <el-form-item label="协商说明" prop="note">
        <el-input
          v-model="resolveForm.note"
          type="textarea"
          :rows="3"
          maxlength="200"
          show-word-limit
          placeholder="例如：已与客户电话协商，退货后原路退款"
        />
      </el-form-item>

      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="退货成功会回补库存；纯退款不回补库存"
      />
    </el-form>

    <template #footer>
      <el-button :disabled="resolving" @click="resolveVisible = false">
        取消
      </el-button>
      <el-button type="primary" :loading="resolving" @click="onResolve">
        保存结果
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.aftersales__toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: var(--sp-2);
  margin-bottom: var(--sp-3);
}

.aftersales__status {
  width: 160px;
}

.aftersales__keyword {
  width: 240px;
}

.aftersales__pager {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--sp-3);
}

.aftersale-detail {
  min-height: 160px;
}

.aftersale-detail__title {
  margin: var(--sp-5) 0 var(--sp-2);
  font-size: var(--fs-base);
  font-weight: 600;
}

.aftersale-detail__text {
  margin: 0;
  font-size: var(--fs-sm);
  color: var(--c-text-sub);
  white-space: pre-wrap;
  word-break: break-word;
}

.aftersale-detail__images {
  display: flex;
  flex-wrap: wrap;
  gap: var(--sp-2);
  margin-top: var(--sp-3);
}

.aftersale-detail__image {
  width: 88px;
  height: 88px;
  border-radius: var(--r-btn);
}

.aftersale-detail__actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--sp-2);
  margin-top: var(--sp-4);
}

.aftersale-detail__alert {
  margin-top: var(--sp-4);
}

.resolve-form__amount {
  width: 180px;
}

.resolve-form__hint {
  margin: var(--sp-1) 0 0;
  font-size: 12px;
  color: var(--c-text-weak);
}
</style>
