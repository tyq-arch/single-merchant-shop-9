<script setup>
import zhCn from 'element-plus/es/locale/lang/zh-cn'
</script>

<template>
  <!-- 按需引入后不再 app.use(ElementPlus)，语言包通过 ConfigProvider 注入 -->
  <el-config-provider :locale="zhCn">
    <router-view />
  </el-config-provider>
</template>
