<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { fetchMyExams, startExam, type StudentExamItem } from '@/api/exam'

const router = useRouter()
const list = ref<StudentExamItem[]>([])
const loading = ref(false)
const startingId = ref<number | null>(null)

const stateMeta: Record<string, { text: string; type: 'info' | 'success' | 'warning' | 'danger' }> = {
  NOT_STARTED: { text: '未开始', type: 'info' },
  IN_PROGRESS: { text: '进行中', type: 'warning' },
  SUBMITTED: { text: '已交卷', type: 'success' },
  ENDED: { text: '已结束', type: 'info' },
}

async function load() {
  loading.value = true
  try {
    list.value = await fetchMyExams()
  } finally {
    loading.value = false
  }
}

async function enter(exam: StudentExamItem) {
  // 已有答卷直接续考，不必再调开考接口
  if (exam.state === 'IN_PROGRESS' && exam.attemptId) {
    router.push(`/exam/${exam.attemptId}`)
    return
  }

  const confirmed = await ElMessageBox.confirm(
    `开始「${exam.title}」后计时立即开始，中途离开不会暂停。确定开始？`,
    '开始考试',
    { type: 'warning', confirmButtonText: '开始作答' },
  ).catch(() => false)
  if (!confirmed) {
    return
  }

  startingId.value = exam.id
  try {
    const attempt = await startExam(exam.id)
    router.push(`/exam/${attempt.attemptId}`)
  } catch (error) {
    // 409 表示已开考：后端会带回 attemptId，直接跳过去续考
    const data = (error as { response?: { data?: { data?: { attemptId?: number } } } }).response?.data?.data
    if (data?.attemptId) {
      router.push(`/exam/${data.attemptId}`)
    }
  } finally {
    startingId.value = null
  }
}

function formatDuration(sec: number) {
  return `${Math.round(sec / 60)} 分钟`
}

const available = computed(
  () => list.value.filter((item) => item.state === 'NOT_STARTED' || item.state === 'IN_PROGRESS').length,
)

onMounted(load)
</script>

<template>
  <div v-loading="loading" class="page">
    <el-alert
      v-if="!loading && !available"
      type="info"
      :closable="false"
      show-icon
      title="当前没有可参加的考试"
      class="empty-tip"
    />

    <el-row :gutter="16">
      <el-col v-for="exam in list" :key="exam.id" :xs="24" :sm="12" :lg="8">
        <el-card shadow="hover" class="exam-card">
          <div class="card-head">
            <span class="title">{{ exam.title }}</span>
            <el-tag :type="stateMeta[exam.state]?.type ?? 'info'" size="small">
              {{ stateMeta[exam.state]?.text ?? exam.state }}
            </el-tag>
          </div>

          <div class="meta">
            <div class="row">
              <span class="label">题量</span>{{ exam.totalCount }} 题 / {{ Number(exam.totalScore).toFixed(0) }} 分
            </div>
            <div class="row"><span class="label">时长</span>{{ formatDuration(exam.durationSec) }}</div>
            <div class="row"><span class="label">开放</span>{{ exam.startTime }}</div>
            <div class="row"><span class="label">截止</span>{{ exam.endTime }}</div>
          </div>

          <div class="actions">
            <el-button
              v-if="exam.state === 'NOT_STARTED'"
              type="primary"
              :loading="startingId === exam.id"
              @click="enter(exam)"
            >
              开始考试
            </el-button>
            <el-button v-else-if="exam.state === 'IN_PROGRESS'" type="warning" @click="enter(exam)">
              继续作答
            </el-button>
            <el-button v-else disabled>已结束</el-button>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped>
.page {
  min-height: 200px;
}

.empty-tip {
  margin-bottom: 16px;
}

.exam-card {
  margin-bottom: 16px;
}

.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 12px;
}

.title {
  font-size: 15px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.meta {
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.row .label {
  display: inline-block;
  width: 44px;
  color: var(--el-text-color-secondary);
}

.actions {
  margin-top: 16px;
  text-align: right;
}
</style>
