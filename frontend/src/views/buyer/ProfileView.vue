<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'

import { changePassword, getMe, logout } from '@/api/customer'
import { useCustomerStore } from '@/stores/customer'
import { validateCustomerPassword } from '@/utils/validate'

/**
 * B8 个人中心 —— FR-018 / NFR-003。
 * 展示固定资料、修改密码、退出登录。
 */
const router = useRouter()
const customerStore = useCustomerStore()

const loading = ref(true)
const profile = ref(null)
const purchaseCount = ref(0)

const formRef = ref()
const submitting = ref(false)
const form = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
})

function passwordValidator(_rule, value, callback) {
  const message = validateCustomerPassword(value)
  message ? callback(new Error(message)) : callback()
}

const rules = {
  oldPassword: [{ required: true, message: '请输入当前密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { validator: passwordValidator, trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (value !== form.newPassword) {
          callback(new Error('两次输入的新密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur',
    },
  ],
}

async function load() {
  loading.value = true
  try {
    const data = await getMe()
    profile.value = data.customer
    purchaseCount.value = data.purchase_count || 0
    customerStore.updateCustomer(data.customer)
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    loading.value = false
  }
}

async function onChangePassword() {
  if (submitting.value) return

  try {
    await formRef.value.validate()
  } catch {
    return
  }

  submitting.value = true
  try {
    const result = await changePassword({
      old_password: form.oldPassword,
      new_password: form.newPassword,
    })
    ElMessage.success(result?.message || '密码修改成功')
    formRef.value.resetFields()
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    submitting.value = false
  }
}

async function onLogout() {
  try {
    await ElMessageBox.confirm('确认退出当前账号？', '退出登录', {
      confirmButtonText: '退出',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return
  }

  try {
    await logout()
  } catch {
    /* 令牌可能已失效，本地照常清场 */
  }
  customerStore.clear()
  ElMessage.success('已退出登录')
  router.replace('/')
}

onMounted(load)
</script>

<template>
  <div v-loading="loading" class="profile">
    <template v-if="profile">
      <div class="info-card">
        <div class="info-row">
          <span class="info-row__key">用户名</span>
          <span class="info-row__value">{{ profile.username }}</span>
        </div>
        <div class="info-row">
          <span class="info-row__key">联系电话</span>
          <span class="info-row__value">{{ profile.phone }}</span>
        </div>
        <div class="info-row">
          <span class="info-row__key">默认交易地点</span>
          <span class="info-row__value">{{ profile.trade_location }}</span>
        </div>
        <div class="info-row">
          <span class="info-row__key">注册时间</span>
          <span class="info-row__value">{{ profile.registered_at }}</span>
        </div>
        <div class="info-row">
          <span class="info-row__key">成交次数</span>
          <span class="info-row__value">{{ purchaseCount }} 次</span>
        </div>
      </div>

      <div class="profile__links">
        <router-link to="/my/intents">我的意向</router-link>
        <router-link to="/my/orders">历史下单</router-link>
      </div>

      <div class="password-card">
        <h2 class="password-card__title">修改密码</h2>
        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          label-position="top"
          @submit.prevent="onChangePassword"
        >
          <el-form-item label="当前密码" prop="oldPassword">
            <el-input
              v-model="form.oldPassword"
              type="password"
              show-password
              autocomplete="current-password"
            />
          </el-form-item>
          <el-form-item label="新密码" prop="newPassword">
            <el-input
              v-model="form.newPassword"
              type="password"
              show-password
              placeholder="至少 6 位"
              maxlength="128"
              autocomplete="new-password"
            />
          </el-form-item>
          <el-form-item label="确认新密码" prop="confirmPassword">
            <el-input
              v-model="form.confirmPassword"
              type="password"
              show-password
              maxlength="128"
              autocomplete="new-password"
            />
          </el-form-item>

          <el-button type="primary" :loading="submitting" @click="onChangePassword">
            保存修改
          </el-button>
        </el-form>
      </div>

      <el-button class="profile__logout" type="danger" plain @click="onLogout">
        退出登录
      </el-button>
    </template>
  </div>
</template>

<style scoped>
.profile {
  min-height: 240px;
}

.info-card {
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

.profile__links {
  display: flex;
  gap: var(--sp-4);
  margin: var(--sp-3) 0;
  font-size: var(--fs-sm);
}

.password-card {
  padding: var(--sp-4);
  background: var(--c-surface);
  border-radius: var(--r-card);
  box-shadow: var(--shadow-sm);
}

.password-card__title {
  margin: 0 0 var(--sp-3);
  font-size: var(--fs-base);
  font-weight: 600;
}

.profile__logout {
  width: 100%;
  margin-top: var(--sp-4);
}
</style>
