<script setup lang="ts">
import { computed } from 'vue'
import type { ScoreBucket } from '@/api/stat'

const props = defineProps<{
  buckets: ScoreBucket[]
}>()

const total = computed(() => props.buckets.reduce((sum, item) => sum + item.count, 0))
const max = computed(() => Math.max(1, ...props.buckets.map((item) => item.count)))

function height(count: number) {
  // 最小 2%，避免 0 值柱完全消失看不出档位
  return `${Math.max(2, Math.round((count / max.value) * 100))}%`
}

function percent(count: number) {
  return total.value === 0 ? 0 : Math.round((count / total.value) * 1000) / 10
}
</script>

<template>
  <div class="chart">
    <div class="bars">
      <div v-for="item in buckets" :key="item.label" class="col">
        <div class="col-value">{{ item.count }}</div>
        <div class="track">
          <div class="bar" :style="{ height: height(item.count) }" :title="`${item.label}: ${item.count} 人（${percent(item.count)}%）`" />
        </div>
        <div class="col-label">{{ item.label }}</div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.chart {
  width: 100%;
}

.bars {
  display: flex;
  align-items: flex-end;
  gap: 8px;
  height: 200px;
}

.col {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  height: 100%;
}

.col-value {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-bottom: 4px;
}

.track {
  flex: 1;
  width: 100%;
  display: flex;
  align-items: flex-end;
  background: var(--el-fill-color-lighter);
  border-radius: 4px 4px 0 0;
}

.bar {
  width: 100%;
  background: linear-gradient(180deg, var(--el-color-primary-light-3), var(--el-color-primary));
  border-radius: 4px 4px 0 0;
  transition: height 0.4s ease;
}

.col-label {
  margin-top: 6px;
  font-size: 11px;
  color: var(--el-text-color-secondary);
  white-space: nowrap;
}
</style>
