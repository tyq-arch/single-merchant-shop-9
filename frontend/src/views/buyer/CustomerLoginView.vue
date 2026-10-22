<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

import { login } from '@/api/customer'
import { useCustomerStore } from '@/stores/customer'

/**
 * B3 客户登录 —— FR-018。
 * 游客可浏览；下单、我的意向、历史下单需登录。
 */
const route = useRoute()
const router = useRouter()
const customerStore = useCustomerStore()

const formRef = ref()
const loading = ref(false)

const form = reactive({
  username: typeof route.query.username === 'string' ? route.query.username : '',
  password: '',
})

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

/** 仅允许站内相对路径，避免开放重定向 */
function safeRedirect(value) {
  if (typeof value !== 'string') return '/'
  if (!value.startsWith('/') || value.startsWith('//')) return '/'
  if (value.startsWith('/login') || value.startsWith('/register')) return '/'
  return value
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
    const data = await login({ username: form.username.trim(), password: form.password })
    customerStore.save(data)
    ElMessage.success('登录成功')
    router.replace(safeRedirect(route.query.redirect))
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
        <h1 class="auth-card__title">客户登录</h1>
        <p class="auth-card__subtitle">登录后即可下单、查看意向与历史记录</p>
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
            placeholder="请输入用户名"
            autocomplete="username"
          />
        </el-form-item>

        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="请输入密码"
            show-password
            autocomplete="current-password"
            @keyup.enter="onSubmit"
          />
        </el-form-item>

        <el-button
          class="auth-card__submit"
          type="primary"
          size="large"
          :loading="loading"
          @click="onSubmit"
        >
          登 录
        </el-button>
      </el-form>

      <p class="auth-card__switch">
        还没有账号？
        <router-link :to="{ name: 'customer-register', query: route.query }">
          立即注册
        </router-link>
      </p>

      <p class="auth-card__guest">
        <router-link to="/">先随便逛逛 →</router-link>
      </p>

      <p class="auth-card__seller">
        卖家账号请走
        <router-link to="/seller/login">卖家后台登录 →</router-link>
      </p>
    </div>
  </div>
</template>

<style scoped>
.auth-page {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 60vh;
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

.auth-card__guest {
  margin: var(--sp-2) 0 0;
  font-size: var(--fs-sm);
  text-align: center;
}

.auth-card__seller {
  margin: var(--sp-3) 0 0;
  padding-top: var(--sp-3);
  font-size: var(--fs-sm);
  color: var(--c-text-weak);
  text-align: center;
  border-top: 1px dashed var(--c-border);
}
</style>
