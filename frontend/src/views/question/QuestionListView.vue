<script setup lang="ts">
import PlaceholderPage from '@/components/PlaceholderPage.vue'
</script>

<template>
  <PlaceholderPage
    title="题目管理"
    :points="[
      '分类树（最多 3 级）与题目分页查询',
      '五类题型的增删改查，选项与答案校验',
      'Excel 批量导入：模板下载、失败明细回传',
      '题干重复拦截（归一化 + SHA-256）',
    ]"
  />
</template>
