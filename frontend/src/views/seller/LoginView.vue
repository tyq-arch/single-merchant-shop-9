<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

import { login } from '@/api/seller'
import { useSellerStore } from '@/stores/seller'

/**
 * S1 卖家登录 —— FR-032。
 *
 * 刻意不提供：注册入口（FR-033 / AC-001）、退出登录（FR-034）。
 * 卖家账号由系统初始化时固定写入，见后端技术栈报告第 6 节。
 */
const route = useRoute()
const router = useRouter()
const sellerStore = useSellerStore()

const formRef = ref()
const loading = ref(false)
const form = reactive({ username: '', password: '' })

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
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
    const data = await login({ username: form.username, password: form.password })
    sellerStore.save(data)
    ElMessage.success('登录成功')
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/seller'
    router.replace(redirect)
  } catch (error) {
    // 401 由 http 拦截器放行到此处，message 为后端的「用户名或密码错误」
    ElMessage.error(error.message)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <div class="login-card">
      <header class="login-card__head">
        <h1 class="login-card__title">单卖家小店</h1>
        <p class="login-card__subtitle">卖家后台</p>
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
          <el-input v-model="form.username" placeholder="请输入用户名" autocomplete="username" />
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
          class="login-card__submit"
          type="primary"
          size="large"
          :loading="loading"
          @click="onSubmit"
        >
          登 录
        </el-button>
      </el-form>

      <p class="login-card__hint">
        默认账号：seller / seller123
      </p>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  padding: var(--sp-4);
  background: linear-gradient(160deg, #eff6ff 0%, #f8fafc 60%);
}

.login-card {
  width: 100%;
  max-width: 400px;
  padding: var(--sp-8) var(--sp-6);
  background: var(--c-surface);
  border-radius: var(--r-card);
  box-shadow: var(--shadow-md);
}

.login-card__head {
  margin-bottom: var(--sp-6);
  text-align: center;
}

.login-card__title {
  margin: 0;
  font-size: 24px;
  font-weight: 600;
}

.login-card__subtitle {
  margin: var(--sp-1) 0 0;
  font-size: var(--fs-sm);
  color: var(--c-text-weak);
}

.login-card__submit {
  width: 100%;
  margin-top: var(--sp-2);
}

.login-card__hint {
  margin: var(--sp-5) 0 0;
  font-size: var(--fs-sm);
  color: var(--c-text-weak);
  text-align: center;
}
</style>
