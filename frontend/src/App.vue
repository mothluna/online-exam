<script setup lang="ts">
import zhCn from 'element-plus/es/locale/lang/zh-cn'
</script>

<template>
  <!-- 按需引入后无法再用 app.use(ElementPlus, { locale })，改由 ConfigProvider 下发 -->
  <el-config-provider :locale="zhCn">
    <router-view />
  </el-config-provider>
</template>
