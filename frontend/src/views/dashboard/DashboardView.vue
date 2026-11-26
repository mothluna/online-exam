<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()

const shortcuts = computed(() => {
  if (userStore.isStudent) {
    return [{ label: '我的考试', path: '/my-exams', desc: '查看可参加的考试并进入作答' }]
  }
  const items = [
    { label: '题目管理', path: '/questions', desc: '维护题库、批量导入题目' },
    { label: '试卷管理', path: '/papers', desc: '手工组卷或按规则抽题' },
    { label: '考试管理', path: '/exams', desc: '发布考试、设置时间窗' },
    { label: '记录统计', path: '/stats', desc: '查看成绩分布与导出' },
  ]
  if (userStore.isAdmin) {
    items.push({ label: '用户管理', path: '/users', desc: '账号、角色与会话管理' })
  }
  return items
})
</script>

<template>
  <div class="dashboard">
    <el-card shadow="never" class="welcome">
      <div class="welcome-inner">
        <div>
          <h2>你好，{{ userStore.realName }}</h2>
          <p class="sub">
            当前角色：{{ userStore.role === 'ADMIN' ? '管理员' : userStore.role === 'TEACHER' ? '教师' : '学生' }}
          </p>
        </div>
        <el-icon :size="56" class="welcome-icon"><Reading /></el-icon>
      </div>
    </el-card>

    <div class="grid">
      <el-card v-for="item in shortcuts" :key="item.path" shadow="hover" class="card" @click="router.push(item.path)">
        <div class="card-title">{{ item.label }}</div>
        <div class="card-desc">{{ item.desc }}</div>
      </el-card>
    </div>
  </div>
</template>

<style scoped>
.dashboard {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.welcome-inner {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.welcome h2 {
  margin: 0 0 6px;
  font-size: 20px;
}

.sub {
  margin: 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.welcome-icon {
  color: var(--el-color-primary-light-5);
}

.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 16px;
}

.card {
  cursor: pointer;
}

.card-title {
  font-size: 15px;
  font-weight: 600;
  margin-bottom: 6px;
}

.card-desc {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  line-height: 1.5;
}
</style>
