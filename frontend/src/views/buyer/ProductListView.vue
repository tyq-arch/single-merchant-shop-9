<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'

import { getCategories, listProducts } from '@/api/public'
import EmptyState from '@/components/EmptyState.vue'
import ProductImage from '@/components/ProductImage.vue'
import StatusTag from '@/components/StatusTag.vue'
import { toCascaderOptions } from '@/utils/category'
import { formatPrice } from '@/utils/format'

/**
 * B1 商品列表 / 搜索 —— FR-010 / FR-019。
 * 游客免登录即可浏览、搜索、按类别筛选。
 */
const loading = ref(true)
const items = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)

const keyword = ref('')
const categoryId = ref(null)
const categoryOptions = ref([])

/** 已生效的搜索条件 —— 用于结果摘要，避免用户还没点搜索就显示筛选结果 */
const appliedKeyword = ref('')
const appliedCategoryId = ref(null)

const hasFilter = computed(() => Boolean(appliedKeyword.value || appliedCategoryId.value))

const resultSummary = computed(() => {
  if (!hasFilter.value) return ''
  if (appliedKeyword.value) return `「${appliedKeyword.value}」的搜索结果`
  return '当前类别下的商品'
})

const cascaderProps = {
  value: 'value',
  label: 'label',
  children: 'children',
  checkStrictly: true,
  emitPath: false,
}

async function loadCategories() {
  try {
    const data = await getCategories()
    categoryOptions.value = toCascaderOptions(data.items)
  } catch (error) {
    ElMessage.error(error.message)
  }
}

async function load() {
  loading.value = true
  const kw = keyword.value.trim()
  const category = categoryId.value || null
  try {
    const result = await listProducts({
      page: page.value,
      page_size: pageSize.value,
      keyword: kw || undefined,
      category_id: category || undefined,
    })
    items.value = result.items || []
    total.value = result.total || 0
    appliedKeyword.value = kw
    appliedCategoryId.value = category
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    loading.value = false
  }
}

function search() {
  page.value = 1
  load()
  // 搜索结果在顶部，滚回顶部让用户看到「找到 N 件」的反馈
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

function clearFilters() {
  keyword.value = ''
  categoryId.value = null
  search()
}

function onPageChange(nextPage) {
  page.value = nextPage
  load()
}

onMounted(() => {
  loadCategories()
  load()
})
</script>

<template>
  <div class="product-list">
    <div class="product-list__toolbar">
      <div class="product-list__search-row">
        <el-input
          v-model="keyword"
          class="product-list__search-input"
          placeholder="搜索商品名称"
          clearable
          @keyup.enter="search"
          @clear="search"
        />
        <el-button
          class="product-list__search-btn"
          type="primary"
          :loading="loading"
          @click="search"
        >
          搜索
        </el-button>
      </div>

      <el-cascader
        v-model="categoryId"
        :options="categoryOptions"
        :props="cascaderProps"
        placeholder="全部类别"
        clearable
        @change="search"
      />
    </div>

    <div v-if="hasFilter" class="product-list__summary">
      <span class="product-list__summary-text">
        {{ resultSummary }} · 共 <strong>{{ total }}</strong> 件
      </span>
      <button class="product-list__clear" type="button" @click="clearFilters">
        清除筛选
      </button>
    </div>

    <div v-loading="loading" class="product-list__body">
      <div v-if="items.length" class="product-grid">
        <router-link
          v-for="item in items"
          :key="item.id"
          :to="{ name: 'buyer-product-detail', params: { id: item.id } }"
          class="product-card"
        >
          <ProductImage :src="item.primary_image" :alt="item.name" ratio="1 / 1" />
          <div class="product-card__body">
            <p class="product-card__name">{{ item.name }}</p>
            <p class="product-card__price price">{{ formatPrice(item.price) }}</p>
            <div class="product-card__meta">
              <StatusTag :status="item.status" :label="item.status_label" />
              <span>库存 {{ item.stock }}</span>
            </div>
          </div>
        </router-link>
      </div>

      <EmptyState
        v-else-if="!loading"
        icon="🔍"
        :text="hasFilter ? '没有找到符合条件的商品' : '还没有在售商品'"
        :hint="
          hasFilter
            ? '换个关键词或类别试试，也可以直接看全部商品'
            : '卖家发布商品后会显示在这里'
        "
      >
        <button
          v-if="hasFilter"
          class="product-list__clear-btn"
          type="button"
          @click="clearFilters"
        >
          清除筛选，看全部商品
        </button>
      </EmptyState>
    </div>

    <div v-if="total > pageSize" class="product-list__pager">
      <el-pagination
        v-model:current-page="page"
        :page-size="pageSize"
        :total="total"
        layout="prev, pager, next"
        small
        background
        @current-change="onPageChange"
      />
    </div>
  </div>
</template>

<style scoped>
.product-list__toolbar {
  display: flex;
  flex-direction: column;
  gap: var(--sp-2);
  margin-bottom: var(--sp-4);
}

.product-list__body {
  min-height: 240px;
}

/* 搜索行：输入框 + 独立蓝色按钮（放 append 里会被 el-input-group 压成灰底灰字） */
.product-list__search-row {
  display: flex;
  gap: var(--sp-2);
}

.product-list__search-input {
  flex: 1 1 auto;
  min-width: 0;
}

/* 搜索按钮：常态主色蓝，hover / 聚焦转中性灰，点击反馈明显 */
.product-list__search-btn {
  flex: 0 0 auto;
  min-width: 80px;
  --el-button-bg-color: var(--c-primary);
  --el-button-border-color: var(--c-primary);
  --el-button-text-color: #ffffff;
  --el-button-hover-bg-color: #e2e8f0;
  --el-button-hover-border-color: #cbd5e1;
  --el-button-hover-text-color: var(--c-text-sub);
  --el-button-active-bg-color: #cbd5e1;
  --el-button-active-border-color: #94a3b8;
  --el-button-active-text-color: var(--c-text);
}

/* 搜索结果摘要：让「搜索有没有生效」一眼可见 */
.product-list__summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-2);
  margin-bottom: var(--sp-3);
  font-size: var(--fs-sm);
  color: var(--c-text-sub);
}

.product-list__summary-text strong {
  color: var(--c-text);
}

.product-list__clear {
  padding: 0;
  font-size: var(--fs-sm);
  color: var(--c-primary);
  background: none;
  border: none;
  cursor: pointer;
  white-space: nowrap;
}

.product-list__clear-btn {
  margin-top: var(--sp-2);
  padding: 8px 16px;
  font-size: var(--fs-sm);
  color: var(--c-primary);
  cursor: pointer;
  background: var(--c-primary-soft);
  border: none;
  border-radius: var(--r-btn);
}

.product-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: var(--sp-3);
}

.product-card {
  display: flex;
  gap: var(--sp-3);
  padding: var(--sp-3);
  color: inherit;
  background: var(--c-surface);
  border-radius: var(--r-card);
  box-shadow: var(--shadow-sm);
}

.product-card :deep(.product-image) {
  width: 104px;
  flex: none;
}

.product-card__body {
  display: flex;
  flex-direction: column;
  justify-content: center;
  min-width: 0;
}

.product-card__name {
  margin: 0;
  font-size: var(--fs-base);
  font-weight: 600;
  word-break: break-all;
}

.product-card__price {
  margin: var(--sp-1) 0;
}

.product-card__meta {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  font-size: var(--fs-sm);
  color: var(--c-text-weak);
}

.product-list__pager {
  display: flex;
  justify-content: center;
  margin-top: var(--sp-4);
}
</style>
