<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'

import {
  freezeProduct,
  getCurrentProduct,
  listIntents,
  markTransaction,
  offShelf,
  requeueOrder,
  startTransaction,
  unfreezeProduct,
  voidOrder,
} from '@/api/seller'
import ProductImage from '@/components/ProductImage.vue'
import StatusTag from '@/components/StatusTag.vue'
import { formatPrice } from '@/utils/format'

/**
 * S2 工作台 —— FR-003 / FR-009~FR-012 / FR-021 / FR-027~FR-031 / FR-040。
 *
 * 关键设计：交易进行中时不显示「手动解冻」。
 * 后端 unfreeze 在存在进行中交易时返回 409（接口文档 5.7），
 * 因此按「是否存在 IN_TRANSACTION 订单」切换显示「标记交易成功/失败」或「手动解冻」，
 * 避免让卖家点到必然失败的按钮。
 *
 * 后端未提供「查询当前进行中订单」的独立接口，故通过一次队列列表请求在客户端筛选。
 * 数据量为单商品队列（个位数），无性能问题。
 */
const router = useRouter()

const loading = ref(true)
const actionLoading = ref(false)
const product = ref(null)
const intents = ref([])

const status = computed(() => product.value?.status || '')
const isOnSale = computed(() => status.value === 'ON_SALE')
const isFrozen = computed(() => status.value === 'FROZEN')

const inTransactionOrder = computed(
  () => intents.value.find((item) => item.status === 'IN_TRANSACTION') || null,
)

const failedOrders = computed(() => intents.value.filter((item) => item.status === 'FAILED'))

const queueList = computed(() =>
  intents.value
    .filter((item) => item.status === 'QUEUED' || item.status === 'REQUEUED')
    .sort((a, b) => a.queue_position - b.queue_position),
)

const queueCount = computed(() => queueList.value.length)

async function load() {
  loading.value = true
  try {
    const data = await getCurrentProduct()
    product.value = data.product

    if (product.value) {
      const result = await listIntents(product.value.id, { page: 1, page_size: 100 })
      intents.value = result.items || []
    } else {
      intents.value = []
    }
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    loading.value = false
  }
}

/** 统一的二次确认 + 执行 + 刷新流程 */
async function runAction({ confirmText, title, action, successFallback = '操作成功' }) {
  try {
    await ElMessageBox.confirm(confirmText, title, {
      confirmButtonText: '确认',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return null
  }

  actionLoading.value = true
  try {
    const result = await action()
    ElMessage.success(result?.message || successFallback)
    await load()
    return result
  } catch (error) {
    ElMessage.error(error.message)
    // 失败后同样刷新，避免界面停留在过期状态
    await load()
    return null
  } finally {
    actionLoading.value = false
  }
}

function onFreeze() {
  return runAction({
    confirmText: `确认冻结商品「${product.value.name}」？冻结后不再接收新意向。`,
    title: '手动冻结商品',
    action: () => freezeProduct(product.value.id),
  })
}

function onUnfreeze() {
  return runAction({
    confirmText: '确认解冻？商品将恢复在售并重新接收意向，不会直接进入交易。',
    title: '手动解冻商品',
    action: () => unfreezeProduct(product.value.id),
  })
}

function onOffShelf() {
  return runAction({
    confirmText: '确认下架？关联口令码将全部失效，未终结的意向会被作废且无法恢复。',
    title: '下架商品',
    action: () => offShelf(product.value.id),
  })
}

function onStartTransaction() {
  return runAction({
    confirmText: '将与队列第 1 位买家进入交易，商品自动冻结、队列封口。确认继续？',
    title: '与队首进入交易',
    action: () => startTransaction(product.value.id),
  })
}

function onMarkSuccess(order) {
  return runAction({
    confirmText: `确认与「${order.buyer_name}」交易成功？商品将下架、全部口令码失效，并进入历史记录。`,
    title: '标记交易成功',
    action: () => markTransaction(order.order_id, { result: 'SUCCESS' }),
  })
}

async function onMarkFailed(order) {
  let note = ''
  try {
    const { value } = await ElMessageBox.prompt(
      '可填写失败备注（选填，最多 500 字）',
      '标记交易失败',
      {
        confirmButtonText: '确认标记失败',
        cancelButtonText: '取消',
        inputPlaceholder: '例如：买家未到场',
        inputValidator: (input) => !input || input.length <= 500 || '备注不能超过 500 字',
        type: 'warning',
      },
    )
    note = (value || '').trim()
  } catch {
    return null
  }

  actionLoading.value = true
  try {
    const payload = { result: 'FAILED' }
    if (note) payload.note = note
    const result = await markTransaction(order.order_id, payload)
    ElMessage.success(result?.message || '已标记交易失败')
    await load()
    return result
  } catch (error) {
    ElMessage.error(error.message)
    await load()
    return null
  } finally {
    actionLoading.value = false
  }
}

function onVoid(order) {
  return runAction({
    confirmText: `确认作废「${order.buyer_name}」的意向？仅影响该买家本人，不影响已自动递补的下一位。`,
    title: '作废失败者',
    action: () => voidOrder(order.order_id),
  })
}

async function onRequeue(order) {
  try {
    await ElMessageBox.confirm(
      '将把该买家放回队尾，并生成新的口令码（原口令码已失效）。每人最多重新排队 1 次。',
      '失败者重新排队',
      { confirmButtonText: '确认重新排队', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return null
  }

  actionLoading.value = true
  try {
    const result = await requeueOrder(order.order_id)
    await load()
    await ElMessageBox.alert(
      `新口令码：${result.command_code}　（排队位次：第 ${result.queue_position} 位）请转告买家，原口令码已失效。`,
      '重新排队成功',
      { confirmButtonText: '知道了' },
    )
    return result
  } catch (error) {
    ElMessage.error(error.message)
    await load()
    return null
  } finally {
    actionLoading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div v-loading="loading" class="dashboard">
    <!-- 无商品：仅发布入口（FR-008 唯一在售） -->
    <el-card v-if="!loading && !product" shadow="never">
      <div class="empty-state">
        <div class="empty-state__icon">📦</div>
        <p class="empty-state__text">当前无在售 / 交易中的商品</p>
        <p class="empty-state__hint">
          同一时间只能有一件商品在售，下架或售出后才能发布下一件。
        </p>
        <el-button type="primary" @click="router.push({ name: 'seller-publish' })">
          发布商品
        </el-button>
      </div>
    </el-card>

    <template v-if="!loading && product">
      <!-- 商品卡片 -->
      <el-card shadow="never">
        <div class="product">
          <div class="product__thumb">
            <ProductImage :src="product.image_url" :alt="product.name" ratio="1 / 1" />
          </div>

          <div class="product__info">
            <div class="product__head">
              <h2 class="product__name">{{ product.name }}</h2>
              <StatusTag :status="product.status" :label="product.status_label" />
            </div>

            <p class="price product__price">{{ formatPrice(product.price) }}</p>

            <p class="product__meta">
              发布时间：{{ product.published_at }} · 更新于：{{ product.updated_at }}
            </p>

            <p v-if="product.description" class="product__desc">{{ product.description }}</p>
          </div>
        </div>

        <div class="product__actions">
          <!-- 在售：冻结 / 下架 / 进入交易 -->
          <template v-if="isOnSale">
            <el-button :disabled="actionLoading" @click="onFreeze">手动冻结</el-button>
            <el-button :disabled="actionLoading" @click="onOffShelf">下架商品</el-button>
            <el-tooltip
              :disabled="queueCount > 0"
              content="队列为空，无可交易对象"
              placement="top"
            >
              <span>
                <el-button
                  type="primary"
                  :disabled="actionLoading || queueCount === 0"
                  @click="onStartTransaction"
                >
                  与队首进入交易
                </el-button>
              </span>
            </el-tooltip>
          </template>

          <!-- 冻结 + 交易进行中：标记成功/失败（不显示解冻） -->
          <template v-else-if="isFrozen && inTransactionOrder">
            <el-button
              type="success"
              :disabled="actionLoading"
              @click="onMarkSuccess(inTransactionOrder)"
            >
              标记交易成功
            </el-button>
            <el-button
              type="danger"
              :disabled="actionLoading"
              @click="onMarkFailed(inTransactionOrder)"
            >
              标记交易失败
            </el-button>
          </template>

          <!-- 冻结但无进行中交易（手动冻结）：可解冻 -->
          <template v-else-if="isFrozen">
            <el-button :disabled="actionLoading" @click="onOffShelf">下架商品</el-button>
            <el-button type="primary" :disabled="actionLoading" @click="onUnfreeze">
              手动解冻
            </el-button>
          </template>
        </div>

        <p v-if="isFrozen && !inTransactionOrder" class="product__tip">
          ⚠️ 商品已冻结，不再接收新意向。解冻后恢复在售、重新接收意向，不会直接进入交易。
        </p>
        <p v-else-if="isFrozen && inTransactionOrder" class="product__tip product__tip--warm">
          ⚠️ 商品已冻结，队列已封口。交易完成后商品状态将自动变更。
        </p>
      </el-card>

      <!-- 交易进行中：订单卡片 -->
      <el-card v-if="inTransactionOrder" shadow="never" class="block">
        <template #header>
          <span class="block__title">🔶 交易进行中</span>
        </template>

        <el-descriptions :column="2" border>
          <el-descriptions-item label="买家姓名">
            {{ inTransactionOrder.buyer_name }}
          </el-descriptions-item>
          <el-descriptions-item label="联系电话">
            {{ inTransactionOrder.buyer_phone }}
          </el-descriptions-item>
          <el-descriptions-item label="排队序号">
            第 {{ inTransactionOrder.queue_position }} 位
          </el-descriptions-item>
          <el-descriptions-item label="提交时间">
            {{ inTransactionOrder.submitted_at }}
          </el-descriptions-item>
        </el-descriptions>
      </el-card>

      <!-- 交易失败的买家：作废或重新排队 -->
      <el-card v-if="failedOrders.length" shadow="never" class="block">
        <template #header>
          <span class="block__title">🔴 待处理的交易失败者</span>
        </template>

        <el-table :data="failedOrders" border>
          <el-table-column prop="buyer_name" label="姓名" width="120" />
          <el-table-column prop="buyer_phone" label="联系电话" width="160" />
          <el-table-column prop="submitted_at" label="提交时间" width="180" />
          <el-table-column label="状态" width="110">
            <template #default="{ row }">
              <StatusTag :status="row.status" :label="row.status_label" />
            </template>
          </el-table-column>
          <el-table-column label="处理" min-width="200">
            <template #default="{ row }">
              <el-button size="small" :disabled="actionLoading" @click="onVoid(row)">
                作废
              </el-button>
              <el-button
                size="small"
                type="primary"
                :disabled="actionLoading"
                @click="onRequeue(row)"
              >
                重新排队
              </el-button>
            </template>
          </el-table-column>
        </el-table>

        <p class="block__note">
          作废与重新排队只影响该买家本人，不影响已自动递补的下一位。重新排队最多 1 次。
        </p>
      </el-card>

      <!-- 排队队列概览（只读，无排序与挑人入口 —— FR-020 / AC-012） -->
      <el-card shadow="never" class="block">
        <template #header>
          <div class="block__header">
            <span class="block__title">排队队列（{{ queueCount }} 人）</span>
            <el-button link type="primary" @click="router.push({ name: 'seller-intents' })">
              查看全部 →
            </el-button>
          </div>
        </template>

        <el-table :data="queueList" border>
          <el-table-column prop="queue_position" label="序号" width="80" />
          <el-table-column prop="buyer_name" label="姓名" width="140" />
          <el-table-column prop="buyer_phone" label="联系电话" width="180" />
          <el-table-column prop="submitted_at" label="提交时间" width="200" />
          <el-table-column label="状态" min-width="120">
            <template #default="{ row }">
              <StatusTag :status="row.status" :label="row.status_label" />
            </template>
          </el-table-column>
        </el-table>

        <p v-if="!queueList.length" class="block__note">当前队列无人排队。</p>
        <p v-else class="block__note">
          排队顺序严格按提交时间先到先得，卖家不能调整顺序或挑选买家。
        </p>
      </el-card>
    </template>
  </div>
</template>

<style scoped>
.dashboard {
  display: flex;
  flex-direction: column;
  gap: var(--sp-4);
  min-height: 200px;
}

.product {
  display: flex;
  gap: var(--sp-4);
}

.product__thumb {
  width: 140px;
  flex: none;
}

.product__info {
  min-width: 0;
}

.product__head {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
}

.product__name {
  margin: 0;
  font-size: var(--fs-lg);
  font-weight: 600;
}

.product__price {
  margin: var(--sp-2) 0 0;
  font-size: 22px;
}

.product__meta {
  margin: var(--sp-1) 0 0;
  font-size: var(--fs-sm);
  color: var(--c-text-weak);
}

.product__desc {
  margin: var(--sp-3) 0 0;
  font-size: var(--fs-sm);
  color: var(--c-text-sub);
  white-space: pre-wrap;
}

.product__actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--sp-2);
  margin-top: var(--sp-5);
  padding-top: var(--sp-4);
  border-top: 1px solid var(--c-border);
}

.product__tip {
  margin: var(--sp-3) 0 0;
  font-size: var(--fs-sm);
  color: var(--c-text-sub);
}

.product__tip--warm {
  color: var(--c-warning);
}

.block {
  margin: 0;
}

.block__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.block__title {
  font-weight: 600;
}

.block__note {
  margin: var(--sp-3) 0 0;
  font-size: var(--fs-sm);
  color: var(--c-text-weak);
}

@media (max-width: 767px) {
  .product {
    flex-direction: column;
  }

  .product__thumb {
    width: 100%;
    max-width: 200px;
  }
}
</style>
