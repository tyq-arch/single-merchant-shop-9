<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

import {
  createAddress,
  listAddresses,
  removeAddress,
  setDefaultAddress,
  updateAddress,
} from '@/api/customer'
import EmptyState from '@/components/EmptyState.vue'
import { normalizeAddress } from '@/utils/shop'

/**
 * C2 收货地址管理 —— FR-046 / FR-047。
 *
 * 客户可维护多个收货地址并设默认；下单时默认带出、可改选，
 * 但下单页不能当场新建/编辑地址（TBD-07）。
 */
const loading = ref(true)
const items = ref([])

const dialogVisible = ref(false)
const submitting = ref(false)
const editingId = ref('')
const formRef = ref()

const form = reactive({
  receiver: '',
  phone: '',
  province: '',
  city: '',
  district: '',
  detail: '',
  postalCode: '',
  isDefault: false,
})

const rules = {
  receiver: [
    { required: true, message: '请填写收货人', trigger: 'blur' },
    { max: 20, message: '收货人最多 20 个字', trigger: 'blur' },
  ],
  phone: [
    { required: true, message: '请填写联系电话', trigger: 'blur' },
    {
      pattern: /^\d{6,20}$/,
      message: '电话应为 6–20 位数字',
      trigger: 'blur',
    },
  ],
  province: [{ required: true, message: '请填写省份', trigger: 'blur' }],
  city: [{ required: true, message: '请填写城市', trigger: 'blur' }],
  district: [{ required: true, message: '请填写区县', trigger: 'blur' }],
  detail: [
    { required: true, message: '请填写详细地址', trigger: 'blur' },
    { max: 100, message: '详细地址最多 100 个字', trigger: 'blur' },
  ],
  postalCode: [
    {
      pattern: /^$|^\d{6}$/,
      message: '邮编应为 6 位数字',
      trigger: 'blur',
    },
  ],
}

const dialogTitle = computed(() =>
  editingId.value ? '编辑收货地址' : '新增收货地址',
)

async function load() {
  loading.value = true
  try {
    const data = await listAddresses()
    items.value = (data.items || []).map(normalizeAddress)
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    loading.value = false
  }
}

function resetForm() {
  form.receiver = ''
  form.phone = ''
  form.province = ''
  form.city = ''
  form.district = ''
  form.detail = ''
  form.postalCode = ''
  form.isDefault = false
}

function openCreate() {
  editingId.value = ''
  resetForm()
  form.isDefault = items.value.length === 0
  dialogVisible.value = true
  formRef.value?.clearValidate()
}

function openEdit(address) {
  editingId.value = address.id
  form.receiver = address.receiver
  form.phone = address.phone
  form.province = address.province
  form.city = address.city
  form.district = address.district
  form.detail = address.detail
  form.postalCode = address.postalCode
  form.isDefault = address.isDefault
  dialogVisible.value = true
  formRef.value?.clearValidate()
}

async function onSave() {
  if (submitting.value) return

  try {
    await formRef.value.validate()
  } catch {
    return
  }

  const payload = {
    receiver: form.receiver.trim(),
    phone: form.phone.trim(),
    province: form.province.trim(),
    city: form.city.trim(),
    district: form.district.trim(),
    detail: form.detail.trim(),
    postal_code: form.postalCode.trim(),
    is_default: form.isDefault,
  }

  submitting.value = true
  try {
    if (editingId.value) {
      await updateAddress(editingId.value, payload)
      ElMessage.success('地址已更新')
    } else {
      await createAddress(payload)
      ElMessage.success('地址已添加')
    }
    dialogVisible.value = false
    await load()
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    submitting.value = false
  }
}

async function onSetDefault(address) {
  if (address.isDefault) return
  try {
    await setDefaultAddress(address.id)
    ElMessage.success('已设为默认地址')
    await load()
  } catch (error) {
    ElMessage.error(error.message)
  }
}

async function onDelete(address) {
  try {
    await ElMessageBox.confirm(
      `确认删除「${address.receiver}」的收货地址？`,
      '删除地址',
      { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return
  }

  try {
    await removeAddress(address.id)
    ElMessage.success('地址已删除')
    await load()
  } catch (error) {
    ElMessage.error(error.message)
  }
}

onMounted(load)
</script>

<template>
  <div class="addresses">
    <div class="addresses__bar">
      <div>
        <h1 class="addresses__title">收货地址</h1>
        <p class="addresses__hint">下单时默认带出默认地址，可临时改选其他地址</p>
      </div>
      <el-button type="primary" @click="openCreate">新增地址</el-button>
    </div>

    <div v-loading="loading" class="addresses__body">
      <template v-if="items.length">
        <div v-for="address in items" :key="address.id" class="address-card">
          <div class="address-card__head">
            <span class="address-card__receiver">{{ address.receiver }}</span>
            <span class="address-card__phone">{{ address.phone }}</span>
            <el-tag v-if="address.isDefault" size="small" type="primary">
              默认
            </el-tag>
          </div>

          <p class="address-card__text">{{ address.fullAddress || '—' }}</p>
          <p v-if="address.postalCode" class="address-card__postal">
            邮编：{{ address.postalCode }}
          </p>

          <div class="address-card__actions">
            <el-button
              v-if="!address.isDefault"
              link
              type="primary"
              @click="onSetDefault(address)"
            >
              设为默认
            </el-button>
            <el-button link type="primary" @click="openEdit(address)">
              编辑
            </el-button>
            <el-button link type="danger" @click="onDelete(address)">
              删除
            </el-button>
          </div>
        </div>
      </template>

      <EmptyState
        v-else-if="!loading"
        icon="📍"
        text="还没有收货地址"
        hint="添加地址后，下单时就能直接选择"
      >
        <el-button type="primary" @click="openCreate">新增地址</el-button>
      </EmptyState>
    </div>

    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="480px"
      append-to-body
      :close-on-click-modal="!submitting"
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        @submit.prevent="onSave"
      >
        <div class="address-form__row">
          <el-form-item label="收货人" prop="receiver">
            <el-input v-model="form.receiver" maxlength="20" />
          </el-form-item>
          <el-form-item label="联系电话" prop="phone">
            <el-input v-model="form.phone" maxlength="20" />
          </el-form-item>
        </div>

        <div class="address-form__row address-form__row--three">
          <el-form-item label="省份" prop="province">
            <el-input v-model="form.province" />
          </el-form-item>
          <el-form-item label="城市" prop="city">
            <el-input v-model="form.city" />
          </el-form-item>
          <el-form-item label="区县" prop="district">
            <el-input v-model="form.district" />
          </el-form-item>
        </div>

        <el-form-item label="详细地址" prop="detail">
          <el-input
            v-model="form.detail"
            type="textarea"
            :rows="2"
            maxlength="100"
            show-word-limit
            placeholder="街道、门牌号等"
          />
        </el-form-item>

        <el-form-item label="邮政编码（选填）" prop="postalCode">
          <el-input v-model="form.postalCode" maxlength="6" />
        </el-form-item>

        <el-checkbox v-model="form.isDefault">设为默认地址</el-checkbox>
      </el-form>

      <template #footer>
        <el-button :disabled="submitting" @click="dialogVisible = false">
          取消
        </el-button>
        <el-button type="primary" :loading="submitting" @click="onSave">
          保存
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.addresses__bar {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--sp-3);
  margin-bottom: var(--sp-3);
}

.addresses__title {
  margin: 0;
  font-size: var(--fs-lg);
  font-weight: 600;
}

.addresses__hint {
  margin: var(--sp-1) 0 0;
  font-size: var(--fs-sm);
  color: var(--c-text-weak);
}

.addresses__body {
  min-height: 160px;
}

.address-card {
  padding: var(--sp-3);
  background: var(--c-surface);
  border-radius: var(--r-card);
  box-shadow: var(--shadow-sm);
}

.address-card + .address-card {
  margin-top: var(--sp-3);
}

.address-card__head {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--sp-2);
}

.address-card__receiver {
  font-size: var(--fs-base);
  font-weight: 600;
}

.address-card__phone {
  font-size: var(--fs-sm);
  color: var(--c-text-sub);
}

.address-card__text {
  margin: var(--sp-2) 0 0;
  font-size: var(--fs-sm);
  color: var(--c-text-sub);
  word-break: break-all;
}

.address-card__postal {
  margin: var(--sp-1) 0 0;
  font-size: 12px;
  color: var(--c-text-weak);
}

.address-card__actions {
  display: flex;
  justify-content: flex-end;
  gap: var(--sp-2);
  margin-top: var(--sp-2);
  padding-top: var(--sp-2);
  border-top: 1px solid var(--c-border);
}

.address-form__row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--sp-3);
}

.address-form__row--three {
  grid-template-columns: 1fr 1fr 1fr;
}

@media (max-width: 480px) {
  .address-form__row,
  .address-form__row--three {
    grid-template-columns: 1fr;
    gap: 0;
  }
}
</style>
