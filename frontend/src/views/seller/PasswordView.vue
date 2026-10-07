<script setup>
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'

import { changePassword } from '@/api/seller'

/**
 * S7 修改密码 —— FR-035 / FR-041。
 *
 * 需求 FR-034 明确后台无退出登录，故修改成功后不主动登出，
 * 当前令牌保持有效直至自然过期。
 */
const formRef = ref()
const submitting = ref(false)

const form = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
})

const rules = {
  oldPassword: [{ required: true, message: '请输入当前密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 128, message: '新密码长度需为 6–128 位', trigger: 'blur' },
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

async function onSubmit() {
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
    // 本地令牌有效时，本接口的 401 即「旧密码不正确」，由 http 拦截器放行到此处展示
    ElMessage.error(error.message)
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <el-card shadow="never">
    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-width="120px"
      class="password-form"
    >
      <el-form-item label="当前密码" prop="oldPassword">
        <el-input
          v-model="form.oldPassword"
          type="password"
          show-password
          placeholder="请输入当前密码"
          autocomplete="current-password"
        />
      </el-form-item>

      <el-form-item label="新密码" prop="newPassword">
        <el-input
          v-model="form.newPassword"
          type="password"
          show-password
          placeholder="6–128 位"
          autocomplete="new-password"
        />
      </el-form-item>

      <el-form-item label="确认新密码" prop="confirmPassword">
        <el-input
          v-model="form.confirmPassword"
          type="password"
          show-password
          placeholder="请再次输入新密码"
          autocomplete="new-password"
          @keyup.enter="onSubmit"
        />
      </el-form-item>

      <el-form-item>
        <el-button type="primary" :loading="submitting" @click="onSubmit">确认修改</el-button>
      </el-form-item>
    </el-form>

    <el-alert
      type="info"
      :closable="false"
      show-icon
      title="后台不提供退出登录功能"
      description="修改密码后当前登录状态继续有效，直至令牌自然过期。"
      class="password__alert"
    />
  </el-card>
</template>

<style scoped>
.password-form {
  max-width: 560px;
}

.password__alert {
  max-width: 560px;
  margin-top: var(--sp-4);
}
</style>
