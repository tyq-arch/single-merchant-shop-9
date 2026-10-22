<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

import {
  createCategory,
  deleteCategory,
  listCategories,
  renameCategory,
} from '@/api/seller'

/**
 * S4 类别管理 —— FR-004 / FR-038 / AC-002。
 *
 * 固定两级，仅支持新增 / 重命名 / 删除，不支持移动；
 * 有子类或有商品的类别禁止删除（由后端 409 兜底）。
 */
const loading = ref(true)
const tree = ref([])

const adding = ref(false)
const addForm = reactive({
  name: '',
  parentId: '',
})

async function load() {
  loading.value = true
  try {
    const data = await listCategories()
    tree.value = data.items || []
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    loading.value = false
  }
}

async function onAdd() {
  const name = addForm.name.trim()
  if (!name) {
    ElMessage.warning('请输入类别名称')
    return
  }

  adding.value = true
  try {
    const data = await createCategory({
      name,
      parent_id: addForm.parentId || undefined,
    })
    ElMessage.success(data?.message || '类别创建成功')
    addForm.name = ''
    await load()
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    adding.value = false
  }
}

async function onAddChild(parent) {
  let name = ''
  try {
    const result = await ElMessageBox.prompt(
      `在「${parent.name}」下新增二级类别`,
      '新增二级类别',
      {
        confirmButtonText: '创建',
        cancelButtonText: '取消',
        inputPlaceholder: '请输入小类名称',
        inputValidator: (value) => (value && value.trim() ? true : '请输入类别名称'),
      },
    )
    name = result.value
  } catch {
    return
  }

  try {
    const data = await createCategory({ name: name.trim(), parent_id: parent.id })
    ElMessage.success(data?.message || '类别创建成功')
    await load()
  } catch (error) {
    ElMessage.error(error.message)
  }
}

async function onRename(category) {
  let name = ''
  try {
    const result = await ElMessageBox.prompt('请输入新的类别名称', '重命名类别', {
      confirmButtonText: '保存',
      cancelButtonText: '取消',
      inputValue: category.name,
      inputValidator: (value) => (value && value.trim() ? true : '请输入类别名称'),
    })
    name = result.value
  } catch {
    return
  }

  try {
    const data = await renameCategory(category.id, name.trim())
    ElMessage.success(data?.message || '类别重命名成功')
    await load()
  } catch (error) {
    ElMessage.error(error.message)
  }
}

async function onDelete(category) {
  try {
    await ElMessageBox.confirm(
      `删除类别「${category.name}」后不可恢复。有子类或有商品的类别不能删除。确认继续？`,
      '删除类别',
      {
        confirmButtonText: '删除',
        cancelButtonText: '取消',
        type: 'warning',
      },
    )
  } catch {
    return
  }

  try {
    const data = await deleteCategory(category.id)
    ElMessage.success(data?.message || '类别已删除')
    await load()
  } catch (error) {
    ElMessage.error(error.message)
  }
}

onMounted(load)
</script>

<template>
  <div v-loading="loading" class="categories">
    <el-card shadow="never" class="categories__add">
      <template #header>新增类别</template>

      <div class="categories__add-row">
        <el-select v-model="addForm.parentId" class="categories__parent">
          <el-option label="作为一级类别（大类）" value="" />
          <el-option
            v-for="parent in tree"
            :key="parent.id"
            :label="`作为「${parent.name}」的二级类别`"
            :value="parent.id"
          />
        </el-select>

        <el-input
          v-model="addForm.name"
          class="categories__name"
          placeholder="请输入类别名称"
          maxlength="64"
          @keyup.enter="onAdd"
        />

        <el-button type="primary" :loading="adding" @click="onAdd">新增</el-button>
      </div>

      <p class="categories__hint">
        类别固定两级：大类 > 小类。商品必须挂在二级类别下，已发布商品的类别不随类别改名或删除而变化。
      </p>
    </el-card>

    <el-card shadow="never" class="categories__tree">
      <template #header>类别树</template>

      <el-empty v-if="!tree.length && !loading" description="还没有类别，先新增一级类别" :image-size="80" />

      <div v-for="parent in tree" :key="parent.id" class="category-group">
        <div class="category-row">
          <div class="category-row__main">
            <span class="category-row__name">{{ parent.name }}</span>
            <el-tag size="small" type="info">一级</el-tag>
            <span class="category-row__count">商品 {{ parent.product_count }}</span>
          </div>
          <div class="category-row__actions">
            <el-button link type="primary" @click="onAddChild(parent)">新增子类</el-button>
            <el-button link type="primary" @click="onRename(parent)">重命名</el-button>
            <el-button link type="danger" @click="onDelete(parent)">删除</el-button>
          </div>
        </div>

        <div
          v-for="child in parent.children || []"
          :key="child.id"
          class="category-row category-row--child"
        >
          <div class="category-row__main">
            <span class="category-row__name">{{ child.name }}</span>
            <el-tag size="small">二级</el-tag>
            <span class="category-row__count">商品 {{ child.product_count }}</span>
          </div>
          <div class="category-row__actions">
            <el-button link type="primary" @click="onRename(child)">重命名</el-button>
            <el-button link type="danger" @click="onDelete(child)">删除</el-button>
          </div>
        </div>

        <p v-if="!(parent.children || []).length" class="category-group__empty">
          该大类下还没有小类，商品无法挂到它下面。
        </p>
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.categories__add-row {
  display: flex;
  flex-wrap: wrap;
  gap: var(--sp-2);
}

.categories__parent {
  width: 260px;
}

.categories__name {
  width: 240px;
}

.categories__hint {
  margin: var(--sp-3) 0 0;
  font-size: var(--fs-sm);
  color: var(--c-text-weak);
}

.categories__tree {
  margin-top: var(--sp-4);
}

.category-group + .category-group {
  margin-top: var(--sp-3);
  padding-top: var(--sp-3);
  border-top: 1px solid var(--c-border);
}

.category-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-3);
  padding: var(--sp-2) var(--sp-3);
  background: var(--c-muted-soft);
  border-radius: var(--r-btn);
}

.category-row--child {
  margin-top: var(--sp-1);
  margin-left: var(--sp-6);
  background: var(--c-surface);
  border: 1px solid var(--c-border);
}

.category-row__main {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  min-width: 0;
}

.category-row__name {
  font-weight: 600;
  word-break: break-all;
}

.category-row__count {
  font-size: var(--fs-sm);
  color: var(--c-text-weak);
}

.category-group__empty {
  margin: var(--sp-2) 0 0 var(--sp-6);
  font-size: var(--fs-sm);
  color: var(--c-text-weak);
}
</style>
