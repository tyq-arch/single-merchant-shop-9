<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

import { register } from '@/api/customer'
import {
  PHONE_LENGTH,
  validateCustomerPassword,
  validatePhone,
} from '@/utils/validate'

/**
 * B4 客户注册 —— FR-017 / AC-006。
 *
 * 注册字段：用户名、密码、电话、默认交易地点。
 * 手机号统一按中国大陆 11 位规则校验（utils/validate.js）。
 */
const route = useRoute()
const router = useRouter()

const formRef = ref()
const loading = ref(false)

const form = reactive({
  username: '',
  password: '',
  confirmPassword: '',
  phone: '',
  tradeLocation: '',
})

function phoneValidator(_rule, value, callback) {
  const message = validatePhone(value)
  message ? callback(new Error(message)) : callback()
}

function passwordValidator(_rule, value, callback) {
  const message = validateCustomerPassword(value)
  message ? callback(new Error(message)) : callback()
}

const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 1, max: 64, message: '用户名长度需为 1–64 位', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { validator: passwordValidator, trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (value !== form.password) {
          callback(new Error('两次输入的密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur',
    },
  ],
  phone: [{ required: true, message: '请输入联系电话', trigger: 'blur' }, { validator: phoneValidator, trigger: 'blur' }],
  tradeLocation: [
    { required: true, message: '请输入默认交易地点', trigger: 'blur' },
    { max: 200, message: '交易地点不能超过 200 个字符', trigger: 'blur' },
  ],
}

async function onSubmit() {
  if (loading.value) return

  try {
    await formRef.value.validate()
  } catch {
    return
  }

  loading.value = true
  try {
    const data = await register({
      username: form.username.trim(),
      password: form.password,
      phone: form.phone.trim(),
      trade_location: form.tradeLocation.trim(),
    })
    ElMessage.success(data?.message || '注册成功，请登录')
    router.replace({
      name: 'customer-login',
      query: { username: form.username.trim(), ...route.query },
    })
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="auth-page">
    <div class="auth-card">
      <header class="auth-card__head">
        <h1 class="auth-card__title">注册客户</h1>
        <p class="auth-card__subtitle">浏览无需登录，购买需要先注册</p>
      </header>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        size="large"
        @submit.prevent="onSubmit"
      >
        <el-form-item label="用户名" prop="username">
          <el-input
            v-model="form.username"
            placeholder="1–64 位，登录时使用"
            maxlength="64"
            autocomplete="username"
          />
        </el-form-item>

        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="至少 6 位"
            show-password
            maxlength="128"
            autocomplete="new-password"
          />
        </el-form-item>

        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input
            v-model="form.confirmPassword"
            type="password"
            placeholder="请再次输入密码"
            show-password
            maxlength="128"
            autocomplete="new-password"
          />
        </el-form-item>

        <el-form-item label="联系电话" prop="phone">
          <el-input
            v-model="form.phone"
            type="tel"
            inputmode="numeric"
            :placeholder="`请输入 ${PHONE_LENGTH} 位手机号`"
            :maxlength="PHONE_LENGTH"
            autocomplete="tel"
          />
        </el-form-item>

        <el-form-item label="默认交易地点" prop="tradeLocation">
          <el-input
            v-model="form.tradeLocation"
            placeholder="线下交易约定地点，例如：一号门"
            maxlength="200"
          />
        </el-form-item>

        <el-button
          class="auth-card__submit"
          type="primary"
          size="large"
          :loading="loading"
          @click="onSubmit"
        >
          注 册
        </el-button>
      </el-form>

      <p class="auth-card__switch">
        已有账号？
        <router-link :to="{ name: 'customer-login', query: route.query }">
          去登录
        </router-link>
      </p>
    </div>
  </div>
</template>

<style scoped>
.auth-page {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--sp-4) 0;
}

.auth-card {
  width: 100%;
  padding: var(--sp-6) var(--sp-5);
  background: var(--c-surface);
  border-radius: var(--r-card);
  box-shadow: var(--shadow-md);
}

.auth-card__head {
  margin-bottom: var(--sp-5);
  text-align: center;
}

.auth-card__title {
  margin: 0;
  font-size: var(--fs-xl);
  font-weight: 600;
}

.auth-card__subtitle {
  margin: var(--sp-1) 0 0;
  font-size: var(--fs-sm);
  color: var(--c-text-weak);
}

.auth-card__submit {
  width: 100%;
  margin-top: var(--sp-2);
}

.auth-card__switch {
  margin: var(--sp-4) 0 0;
  font-size: var(--fs-sm);
  color: var(--c-text-sub);
  text-align: center;
}
</style>
