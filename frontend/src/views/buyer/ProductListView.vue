<script setup>
import { onMounted, ref } from 'vue'
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
  try {
    const result = await listProducts({
      page: page.value,
      page_size: pageSize.value,
      keyword: keyword.value.trim() || undefined,
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

onMounted(() => {
  loadCategories()
  load()
})
</script>

<template>
  <div class="product-list">
    <div class="product-list__toolbar">
      <el-input
        v-model="keyword"
        placeholder="搜索商品名称"
        clearable
        @keyup.enter="search"
        @clear="search"
      >
        <template #append>
          <el-button @click="search">搜索</el-button>
        </template>
      </el-input>

      <el-cascader
        v-model="categoryId"
        :options="categoryOptions"
        :props="cascaderProps"
        placeholder="全部类别"
        clearable
        @change="search"
      />
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
        icon="🛍️"
        text="没有找到商品"
        hint="换个关键词或类别试试，卖家发布商品后会显示在这里"
      />
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
