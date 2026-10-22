<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'

import {
  freezeProduct,
  getProduct,
  listCategories,
  listProducts,
  offShelf,
  onShelf,
  startTransaction,
  unfreezeProduct,
  updateStock,
} from '@/api/seller'
import ProductImage from '@/components/ProductImage.vue'
import StatusTag from '@/components/StatusTag.vue'
import { toCascaderOptions } from '@/utils/category'
import { formatPrice } from '@/utils/format'

/**
 * S2 商品管理 —— FR-001 / FR-005 / FR-006 / FR-011 / FR-039。
 *
 * 商品发布后不可编辑（FR-007），本页只提供：调库存、上下架、冻结/解冻、
 * 与队首进入交易、查看排队名单。
 */
const route = useRoute()
const router = useRouter()

const loading = ref(true)
const items = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)
const keyword = ref('')
const status = ref(typeof route.query.status === 'string' ? route.query.status : '')
const categoryId = ref(null)
const categoryOptions = ref([])

const cascaderProps = {
  value: 'value',
  label: 'label',
  children: 'children',
  checkStrictly: true,
  emitPath: false,
}

const STATUS_OPTIONS = [
  { value: '', label: '全部状态' },
  { value: 'ON_SALE', label: '在售' },
  { value: 'FROZEN', label: '交易中 / 冻结' },
  { value: 'SOLD_OUT', label: '售罄下架' },
  { value: 'OFF_SHELF', label: '手动下架' },
]

const stockDialog = reactive({
  visible: false,
  loading: false,
  product: null,
  value: 0,
})

async function loadCategories() {
  try {
    const data = await listCategories()
    categoryOptions.value = toCascaderOptions(data.items)
  } catch (error) {
    ElMessage.error(error.message)
  }
}

async function load() {
  loading.value = true
  try {
    const result = await listProducts({
      page: page.value,
      page_size: pageSize.value,
      keyword: keyword.value.trim() || undefined,
      status: status.value || undefined,
      category_id: categoryId.value || undefined,
    })
    items.value = result.items || []
    total.value = result.total || 0
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

function openStock(row) {
  stockDialog.product = row
  stockDialog.value = row.stock
  stockDialog.visible = true
}

async function submitStock() {
  if (!stockDialog.product) return
  const value = Number(stockDialog.value)
  if (!Number.isInteger(value) || value < 0) {
    ElMessage.warning('库存必须为非负整数')
    return
  }

  stockDialog.loading = true
  try {
    const data = await updateStock(stockDialog.product.id, value)
    ElMessage.success(data?.message || '库存已调整')
    stockDialog.visible = false
    await load()
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    stockDialog.loading = false
  }
}

async function confirmAction(message, action, fallback) {
  try {
    await ElMessageBox.confirm(message, '请确认', {
      confirmButtonText: '确认',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return
  }

  try {
    const data = await action()
    ElMessage.success(data?.message || fallback)
    await load()
  } catch (error) {
    ElMessage.error(error.message)
  }
}

function onOffShelf(row) {
  confirmAction(
    `手动下架「${row.name}」后，排队中的意向将全部作废，确认继续？`,
    () => offShelf(row.id),
    '商品已下架',
  )
}

function onOnShelf(row) {
  confirmAction(`确认重新上架「${row.name}」？`, () => onShelf(row.id), '商品已上架')
}

function onFreeze(row) {
  confirmAction(
    `冻结「${row.name}」后将暂停接收新意向，已有队列保留。确认继续？`,
    () => freezeProduct(row.id),
    '商品已冻结',
  )
}

function onUnfreeze(row) {
  confirmAction(`确认解冻「${row.name}」并恢复接收意向？`, () => unfreezeProduct(row.id), '商品已解冻')
}

function onStartTransaction(row) {
  confirmAction(
    `将与「${row.name}」队列第一位进入交易，商品自动冻结并停止接收新意向。确认继续？`,
    async () => {
      const data = await startTransaction(row.id)
      await router.push({ name: 'seller-intents', query: { product_id: row.id } })
      return data
    },
    '已进入交易',
  )
}

async function goQueue(row) {
  try {
    await getProduct(row.id)
    router.push({ name: 'seller-intents', query: { product_id: row.id } })
  } catch (error) {
    ElMessage.error(error.message)
  }
}

onMounted(() => {
  loadCategories()
  load()
})
</script>

<template>
  <div class="products">
    <el-card shadow="never">
      <div class="products__toolbar">
        <el-input
          v-model="keyword"
          placeholder="搜索商品名称"
          clearable
          class="products__keyword"
          @keyup.enter="search"
          @clear="search"
        />
        <el-select v-model="status" placeholder="全部状态" class="products__status" @change="search">
          <el-option
            v-for="option in STATUS_OPTIONS"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
        <el-cascader
          v-model="categoryId"
          :options="categoryOptions"
          :props="cascaderProps"
          placeholder="全部类别"
          clearable
          class="products__category"
          @change="search"
        />
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="router.push({ name: 'seller-publish' })">＋ 发布商品</el-button>
      </div>

      <el-table v-loading="loading" :data="items" border>
        <el-table-column label="商品" min-width="240">
          <template #default="{ row }">
            <div class="product-cell">
              <div class="product-cell__thumb">
                <ProductImage :src="row.primary_image" :alt="row.name" ratio="1 / 1" />
              </div>
              <span class="product-cell__name">{{ row.name }}</span>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="类别" width="160">
          <template #default="{ row }">
            {{ row.parent_category_name }} / {{ row.category_name }}
          </template>
        </el-table-column>

        <el-table-column label="价格" width="110">
          <template #default="{ row }">
            <span class="price">{{ formatPrice(row.price) }}</span>
          </template>
        </el-table-column>

        <el-table-column prop="stock" label="库存" width="80" />

        <el-table-column label="状态" width="130">
          <template #default="{ row }">
            <StatusTag :status="row.status" :label="row.status_label" />
          </template>
        </el-table-column>

        <el-table-column prop="published_at" label="发布时间" width="180" />

        <el-table-column label="操作" width="330" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openStock(row)">调库存</el-button>
            <el-button link type="primary" @click="goQueue(row)">排队名单</el-button>

            <el-button
              v-if="row.status === 'ON_SALE'"
              link
              type="success"
              @click="onStartTransaction(row)"
            >
              进入交易
            </el-button>

            <el-button
              v-if="row.status === 'ON_SALE'"
              link
              type="warning"
              @click="onFreeze(row)"
            >
              冻结
            </el-button>

            <el-button
              v-if="row.status === 'FROZEN'"
              link
              type="warning"
              @click="onUnfreeze(row)"
            >
              解冻
            </el-button>

            <el-button
              v-if="row.status === 'OFF_SHELF'"
              link
              type="success"
              @click="onOnShelf(row)"
            >
              上架
            </el-button>

            <el-button
              v-if="row.status !== 'OFF_SHELF' && row.status !== 'FROZEN'"
              link
              type="danger"
              @click="onOffShelf(row)"
            >
              下架
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!items.length && !loading" description="没有符合条件的商品" :image-size="80" />

      <div class="products__pager">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next"
          background
          @current-change="onPageChange"
          @size-change="search"
        />
      </div>
    </el-card>

    <el-dialog v-model="stockDialog.visible" title="调整库存" width="360px">
      <p v-if="stockDialog.product" class="stock-dialog__name">
        {{ stockDialog.product.name }}
      </p>
      <el-input-number
        v-model="stockDialog.value"
        :min="0"
        :max="999999"
        :precision="0"
        :step="1"
        controls-position="right"
        class="stock-dialog__input"
      />
      <p class="stock-dialog__hint">
        库存调为 0 将自动售罄下架，并作废全部排队意向；由 0 调为大于 0 会自动恢复在售。
      </p>
      <template #footer>
        <el-button @click="stockDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="stockDialog.loading" @click="submitStock">
          保存
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.products__toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: var(--sp-2);
  margin-bottom: var(--sp-3);
}

.products__keyword {
  width: 220px;
}

.products__status {
  width: 160px;
}

.products__category {
  width: 220px;
}

.product-cell {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
}

.product-cell__thumb {
  width: 44px;
  flex: none;
}

.product-cell__name {
  word-break: break-all;
}

.products__pager {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--sp-3);
}

.stock-dialog__name {
  margin: 0 0 var(--sp-3);
  font-weight: 600;
}

.stock-dialog__input {
  width: 100%;
}

.stock-dialog__hint {
  margin: var(--sp-3) 0 0;
  font-size: var(--fs-sm);
  color: var(--c-text-weak);
}
</style>
