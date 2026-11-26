<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()
</script>

<template>
  <div class="tip-page">
    <el-result icon="warning" title="403" sub-title="当前角色无权访问该页面">
      <template #extra>
        <el-button type="primary" @click="router.replace('/dashboard')">返回首页</el-button>
      </template>
    </el-result>
  </div>
</template>

<style scoped>
.tip-page {
  display: flex;
  justify-content: center;
  padding-top: 80px;
}
</style>
