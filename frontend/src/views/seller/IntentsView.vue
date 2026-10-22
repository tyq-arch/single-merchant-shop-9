<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'

import {
  exportIntents,
  getProduct,
  listIntents,
  listProducts,
  markTransaction,
  startTransaction,
} from '@/api/seller'
import StatusTag from '@/components/StatusTag.vue'
import { downloadBlob, formatPrice } from '@/utils/format'

/**
 * S5 意向队列 —— FR-022 / FR-023 / FR-026 / FR-027 / FR-028 / FR-029 / FR-030。
 *
 * 每个商品独立队列，严格先到先得；只能与队首进入交易；
 * 交易失败为「单动作」：标记失败的同时选择失败者作废或重新排队（不限次数）。
 */
const route = useRoute()
const router = useRouter()

const products = ref([])
const productId = ref(typeof route.query.product_id === 'string' ? route.query.product_id : '')
const product = ref(null)
const queueCount = ref(0)

const loadingProduct = ref(true)
const loadingQueue = ref(false)
const items = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)
const status = ref('')

const STATUS_OPTIONS = [
  { value: '', label: '全部状态' },
  { value: 'QUEUED', label: '排队中' },
  { value: 'REQUEUED', label: '重新排队中' },
  { value: 'IN_TRANSACTION', label: '已进入交易' },
  { value: 'SUCCESS', label: '交易成功' },
  { value: 'FAILED', label: '交易失败' },
  { value: 'VOIDED', label: '已作废' },
  { value: 'SOLD_OUT', label: '售罄失效' },
]

const txDialog = reactive({
  visible: false,
  loading: false,
  intent: null,
  result: 'SUCCESS',
  note: '',
  failedAction: 'VOID',
})

const inTransaction = ref(false)

async function loadProducts() {
  try {
    const data = await listProducts({ page: 1, page_size: 100 })
    products.value = data.items || []
    if (!productId.value && products.value.length) {
      productId.value = products.value[0].id
    }
  } catch (error) {
    ElMessage.error(error.message)
  }
}

async function loadProduct() {
  if (!productId.value) {
    product.value = null
    queueCount.value = 0
    loadingProduct.value = false
    return
  }

  loadingProduct.value = true
  try {
    const data = await getProduct(productId.value)
    product.value = data.product
    queueCount.value = data.queue_count || 0
    inTransaction.value = data.product?.status === 'FROZEN'
  } catch (error) {
    product.value = null
    ElMessage.error(error.message)
  } finally {
    loadingProduct.value = false
  }
}

async function loadQueue() {
  if (!productId.value) {
    items.value = []
    total.value = 0
    return
  }

  loadingQueue.value = true
  try {
    const data = await listIntents(productId.value, {
      page: page.value,
      page_size: pageSize.value,
      status: status.value || undefined,
    })
    items.value = data.items || []
    total.value = data.total || 0
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    loadingQueue.value = false
  }
}

async function onProductChange() {
  page.value = 1
  status.value = ''
  router.replace({ name: 'seller-intents', query: { product_id: productId.value } })
  await loadProduct()
  await loadQueue()
}

function onFilterChange() {
  page.value = 1
  loadQueue()
}

function onPageChange(nextPage) {
  page.value = nextPage
  loadQueue()
}

async function refreshAll() {
  await loadProduct()
  await loadQueue()
}

async function onStartTransaction() {
  if (!product.value) return
  try {
    await ElMessageBox.confirm(
      '将与队列第一位进入交易，商品自动冻结并停止接收新意向。确认继续？',
      '进入交易',
      { confirmButtonText: '确认', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return
  }

  try {
    const data = await startTransaction(product.value.id)
    ElMessage.success(data?.message || '已进入交易')
    await refreshAll()
  } catch (error) {
    ElMessage.error(error.message)
  }
}

function openTransaction(intent) {
  txDialog.intent = intent
  txDialog.result = 'SUCCESS'
  txDialog.note = ''
  txDialog.failedAction = 'VOID'
  txDialog.visible = true
}

async function submitTransaction() {
  if (!txDialog.intent || txDialog.loading) return

  if (txDialog.result === 'FAILED' && !txDialog.failedAction) {
    ElMessage.warning('交易失败时必须选择失败者处理方式')
    return
  }

  const payload = {
    result: txDialog.result,
    note: txDialog.note.trim() || undefined,
  }
  if (txDialog.result === 'FAILED') {
    payload.failed_action = txDialog.failedAction
  }

  txDialog.loading = true
  try {
    const data = await markTransaction(txDialog.intent.id, payload)
    ElMessage.success(data?.message || '交易结果已记录')
    txDialog.visible = false
    await refreshAll()
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    txDialog.loading = false
  }
}

async function onExport() {
  if (!product.value) return
  try {
    const blob = await exportIntents(product.value.id)
    downloadBlob(blob, `intents_${product.value.id}.csv`)
  } catch (error) {
    ElMessage.error(error.message)
  }
}

onMounted(async () => {
  await loadProducts()
  await loadProduct()
  await loadQueue()
})
</script>

<template>
  <div class="intents">
    <el-card shadow="never">
      <div class="intents__selector">
        <span class="intents__label">选择商品：</span>
        <el-select
          v-model="productId"
          placeholder="请选择商品"
          filterable
          class="intents__select"
          @change="onProductChange"
        >
          <el-option
            v-for="item in products"
            :key="item.id"
            :label="`${item.name}（${item.status_label}）`"
            :value="item.id"
          />
        </el-select>

        <el-button :disabled="!product" @click="onExport">导出 CSV</el-button>
        <el-button
          type="primary"
          :disabled="!product || product.status !== 'ON_SALE' || queueCount === 0"
          @click="onStartTransaction"
        >
          与队首进入交易
        </el-button>
      </div>

      <el-empty
        v-if="!products.length"
        description="还没有商品，先去发布一件吧"
        :image-size="80"
      >
        <el-button type="primary" @click="router.push({ name: 'seller-publish' })">
          去发布商品
        </el-button>
      </el-empty>

      <template v-else-if="product">
        <div v-loading="loadingProduct" class="product-summary">
          <div>
            <p class="product-summary__name">{{ product.name }}</p>
            <p class="product-summary__meta">
              <span>价格 <span class="price">{{ formatPrice(product.price) }}</span></span>
              <span>库存 {{ product.stock }}</span>
              <span>排队 {{ queueCount }} 人</span>
            </p>
          </div>
          <StatusTag :status="product.status" :label="product.status_label" size="large" />
        </div>

        <el-alert
          v-if="product.status === 'FROZEN'"
          type="warning"
          :closable="false"
          show-icon
          class="intents__alert"
        >
          商品处于冻结状态（交易中或手动冻结），不再接收新意向。
          交易结束后记得标记结果。
        </el-alert>

        <div class="intents__toolbar">
          <el-select v-model="status" class="intents__filter" @change="onFilterChange">
            <el-option
              v-for="option in STATUS_OPTIONS"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </div>

        <el-table v-loading="loadingQueue" :data="items" border>
          <el-table-column prop="queue_position" label="位次" width="70" />
          <el-table-column label="客户" width="150">
            <template #default="{ row }">{{ row.customer?.username || '—' }}</template>
          </el-table-column>
          <el-table-column label="电话" width="140">
            <template #default="{ row }">{{ row.customer?.phone || '—' }}</template>
          </el-table-column>
          <el-table-column label="默认交易地点" min-width="160">
            <template #default="{ row }">{{ row.customer?.trade_location || '—' }}</template>
          </el-table-column>
          <el-table-column label="状态" width="130">
            <template #default="{ row }">
              <StatusTag :status="row.status" :label="row.status_label" />
            </template>
          </el-table-column>
          <el-table-column prop="requeue_count" label="重排次数" width="100" />
          <el-table-column prop="created_at" label="提交时间" width="180" />
          <el-table-column label="操作" width="120" fixed="right">
            <template #default="{ row }">
              <el-button
                v-if="row.status === 'IN_TRANSACTION'"
                link
                type="primary"
                @click="openTransaction(row)"
              >
                标记结果
              </el-button>
              <span v-else class="intents__noop">—</span>
            </template>
          </el-table-column>
        </el-table>

        <el-empty v-if="!items.length && !loadingQueue" description="该商品队列为空" :image-size="80" />

        <div class="intents__pager">
          <el-pagination
            v-model:current-page="page"
            v-model:page-size="pageSize"
            :total="total"
            :page-sizes="[10, 20, 50]"
            layout="total, sizes, prev, pager, next"
            background
            @current-change="onPageChange"
            @size-change="onFilterChange"
          />
        </div>
      </template>
    </el-card>

    <el-dialog v-model="txDialog.visible" title="标记交易结果" width="480px">
      <p v-if="txDialog.intent" class="tx-dialog__customer">
        客户：{{ txDialog.intent.customer?.username }}（第
        {{ txDialog.intent.queue_position }} 位）
      </p>

      <el-form label-width="120px">
        <el-form-item label="交易结果">
          <el-radio-group v-model="txDialog.result">
            <el-radio value="SUCCESS">交易成功</el-radio>
            <el-radio value="FAILED">交易失败</el-radio>
          </el-radio-group>
        </el-form-item>

        <el-form-item v-if="txDialog.result === 'FAILED'" label="失败者处理">
          <el-radio-group v-model="txDialog.failedAction">
            <el-radio value="VOID">作废</el-radio>
            <el-radio value="REQUEUE">重新排到队尾</el-radio>
          </el-radio-group>
          <p class="tx-dialog__hint">
            与「标记失败」在同一动作内完成；重新排队不限次数。
          </p>
        </el-form-item>

        <el-form-item label="备注">
          <el-input
            v-model="txDialog.note"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
            placeholder="选填，例如：买家未到场"
          />
        </el-form-item>
      </el-form>

      <el-alert
        v-if="txDialog.result === 'SUCCESS'"
        type="warning"
        :closable="false"
        show-icon
      >
        交易成功会立即扣减 1 件库存；库存减为 0 时商品自动售罄下架，排队意向自动作废。
      </el-alert>

      <template #footer>
        <el-button @click="txDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="txDialog.loading" @click="submitTransaction">
          提交
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.intents__selector {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--sp-2);
  margin-bottom: var(--sp-4);
}

.intents__label {
  font-size: var(--fs-sm);
  color: var(--c-text-sub);
}

.intents__select {
  width: 320px;
}

.product-summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-3);
  padding: var(--sp-3);
  background: var(--c-muted-soft);
  border-radius: var(--r-btn);
}

.product-summary__name {
  margin: 0;
  font-size: var(--fs-base);
  font-weight: 600;
}

.product-summary__meta {
  display: flex;
  flex-wrap: wrap;
  gap: var(--sp-4);
  margin: var(--sp-1) 0 0;
  font-size: var(--fs-sm);
  color: var(--c-text-sub);
}

.intents__alert {
  margin-top: var(--sp-3);
}

.intents__toolbar {
  display: flex;
  justify-content: flex-end;
  margin: var(--sp-3) 0;
}

.intents__filter {
  width: 180px;
}

.intents__noop {
  color: var(--c-text-weak);
}

.intents__pager {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--sp-3);
}

.tx-dialog__customer {
  margin: 0 0 var(--sp-3);
  font-weight: 600;
}

.tx-dialog__hint {
  width: 100%;
  margin: var(--sp-1) 0 0;
  font-size: var(--fs-sm);
  color: var(--c-text-weak);
}
</style>
