<script setup lang="ts">
import PlaceholderPage from '@/components/PlaceholderPage.vue'
</script>

<template>
  <PlaceholderPage
    title="试卷管理"
    :points="[
      '手工组卷：选题、调分、排序',
      '按规则抽题：题型占比、分类与难度限定',
      '规则合计与设定总分实时对账',
      '发布前的总分事实校验',
    ]"
  />
</template>
