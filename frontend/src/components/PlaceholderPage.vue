<script setup lang="ts">
defineProps<{
  title: string
  points: string[]
}>()
</script>

<template>
  <el-card shadow="never">
    <template #header>
      <div class="ph-header">
        <span>{{ title }}</span>
        <el-tag type="warning" effect="plain" size="small">建设中</el-tag>
      </div>
    </template>
    <el-empty description="页面骨架已就位，功能待接入">
      <div class="ph-list">
        <div v-for="item in points" :key="item" class="ph-item">
          <el-icon><Check /></el-icon>
          <span>{{ item }}</span>
        </div>
      </div>
    </el-empty>
  </el-card>
</template>

<style scoped>
.ph-header {
  display: flex;
  align-items: center;
  gap: 10px;
  font-weight: 600;
}
.ph-list {
  text-align: left;
  display: inline-flex;
  flex-direction: column;
  gap: 8px;
  color: var(--el-text-color-regular);
}
.ph-item {
  display: flex;
  align-items: center;
  gap: 8px;
}
.ph-item .el-icon {
  color: var(--el-color-success);
}
</style>
