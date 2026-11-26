<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { fetchAttemptResult, type AttemptResult, type QuestionResult } from '@/api/exam'

const route = useRoute()
const router = useRouter()
const attemptId = Number(route.params.attemptId)

const data = ref<AttemptResult | null>(null)
const loading = ref(true)
const error = ref('')

const passed = computed(() => data.value?.isPass === 1)

const fullTotal = computed(() => {
  const item = data.value
  if (!item) {
    return 0
  }
  return item.questions.reduce((sum, q) => sum + Number(q.fullScore), 0)
})

function scoreTag(q: QuestionResult) {
  const got = Number(q.score)
  const full = Number(q.fullScore)
  if (q.score == null) {
    return { text: '待公布', type: 'info' as const }
  }
  if (q.judgeType === 'UNJUDGED') {
    return { text: '待复核', type: 'warning' as const }
  }
  if (got >= full && full > 0) {
    return { text: '满分', type: 'success' as const }
  }
  if (got > 0) {
    return { text: '部分得分', type: 'warning' as const }
  }
  return { text: '未得分', type: 'danger' as const }
}

function optionClass(q: QuestionResult, key: string) {
  const mine = q.myAnswer?.includes(key)
  const correct = q.correctAnswer?.includes(key)
  return {
    'opt-mine': mine && !correct,
    'opt-correct': Boolean(correct),
    'opt-picked-correct': Boolean(mine && correct),
  }
}

function hasAnswer(q: QuestionResult) {
  return (q.myAnswer ?? []).some((v) => v !== '')
}

async function load() {
  loading.value = true
  try {
    data.value = await fetchAttemptResult(attemptId)
  } catch (e) {
    error.value = (e as Error).message || '成绩加载失败'
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div v-loading="loading" class="page">
    <template v-if="data">
      <el-card shadow="never" class="summary">
        <div class="summary-left">
          <div class="exam-title">{{ data.examTitle }}</div>
          <div class="meta">
            <span>交卷时间：{{ data.submitTime ?? '—' }}</span>
            <el-tag size="small" :type="data.status === 'TIMEOUT' ? 'warning' : 'success'">
              {{ data.status === 'TIMEOUT' ? '超时交卷' : '已交卷' }}
            </el-tag>
          </div>
          <div class="breakdown">
            <span>客观题 {{ Number(data.objectiveScore).toFixed(2) }} 分</span>
            <span>主观题 {{ Number(data.subjectiveScore).toFixed(2) }} 分</span>
            <span>及格线 {{ Number(data.passScore).toFixed(2) }} 分</span>
          </div>
          <el-alert
            v-if="data.needReview"
            type="warning"
            :closable="false"
            show-icon
            class="review-tip"
            title="含主观题，当前分数为机器初判，最终成绩以教师复核为准"
          />
        </div>

        <div class="score-box" :class="{ passed }">
          <div class="score">{{ Number(data.totalScore).toFixed(2) }}</div>
          <div class="score-total">/ {{ fullTotal.toFixed(0) }} 分</div>
          <el-tag :type="passed ? 'success' : 'info'" effect="dark" class="pass-tag">
            {{ passed ? '已通过' : '未通过' }}
          </el-tag>
        </div>
      </el-card>

      <el-alert
        v-if="!data.answerVisible"
        type="info"
        :closable="false"
        show-icon
        class="lock-tip"
        :title="`正确答案与逐题得分将在考试结束（${data.answerReleaseTime}）后开放查看，避免提前泄露给同场次考生`"
      />

      <div class="list-head">
        <span>逐题明细</span>
        <span class="count">{{ data.questions.length }} 题</span>
      </div>

      <el-card v-for="q in data.questions" :key="q.questionId" shadow="never" class="q-card">
        <div class="q-head">
          <span class="q-index">{{ q.sortNo }}.</span>
          <el-tag size="small" effect="plain">{{ q.typeLabel }}</el-tag>
          <el-tag size="small" :type="scoreTag(q).type" effect="light">{{ scoreTag(q).text }}</el-tag>
          <span class="q-score">
            <template v-if="q.score != null">{{ Number(q.score).toFixed(2) }} / {{ Number(q.fullScore).toFixed(2) }} 分</template>
            <template v-else>满分 {{ Number(q.fullScore).toFixed(2) }} 分 · 考试结束后公布</template>
          </span>
        </div>

        <div class="q-stem">{{ q.stem }}</div>

        <!-- 选择题：逐项标出我选的与正确的 -->
        <div v-if="q.options?.length" class="options">
          <div v-for="opt in q.options" :key="opt.key" class="option" :class="optionClass(q, opt.key)">
            <span class="opt-key">{{ opt.key }}</span>
            <span class="opt-content">{{ opt.content }}</span>
            <span v-if="q.myAnswer?.includes(opt.key)" class="opt-badge mine">我的选择</span>
            <span v-if="q.correctAnswer?.includes(opt.key)" class="opt-badge correct">正确答案</span>
          </div>
        </div>

        <!-- 填空/简答 -->
        <div v-else class="text-answer">
          <div class="answer-row">
            <span class="label">我的作答</span>
            <span v-if="hasAnswer(q)" class="value">{{ q.myAnswer.join('；') }}</span>
            <span v-else class="value empty">未作答</span>
          </div>
          <div v-if="q.correctAnswer?.length" class="answer-row">
            <span class="label">参考答案</span>
            <span class="value correct-text">{{ q.correctAnswer.join('；') }}</span>
          </div>
          <div v-else-if="!data.answerVisible" class="answer-row">
            <span class="label">参考答案</span>
            <span class="value empty">考试结束后开放</span>
          </div>
        </div>
      </el-card>

      <div class="footer">
        <el-button @click="router.replace('/my-exams')">返回考试列表</el-button>
      </div>
    </template>

    <el-result v-else-if="!loading" icon="warning" title="无法查看成绩" :sub-title="error">
      <template #extra>
        <el-button type="primary" @click="router.replace('/my-exams')">返回考试列表</el-button>
      </template>
    </el-result>
  </div>
</template>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.summary {
  display: flex;
}

.summary :deep(.el-card__body) {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  gap: 24px;
}

.exam-title {
  font-size: 18px;
  font-weight: 600;
  margin-bottom: 8px;
}

.meta {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
  margin-bottom: 8px;
}

.breakdown {
  display: flex;
  gap: 16px;
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.review-tip {
  margin-top: 10px;
}

.score-box {
  text-align: center;
  padding: 8px 24px;
  border-left: 1px solid var(--el-border-color-lighter);
  min-width: 160px;
}

.score {
  font-size: 40px;
  font-weight: 700;
  line-height: 1.1;
  color: var(--el-color-danger);
}

.score-box.passed .score {
  color: var(--el-color-success);
}

.score-total {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-bottom: 8px;
}

.pass-tag {
  font-weight: 500;
}

.lock-tip {
  margin-bottom: 4px;
}

.list-head {
  display: flex;
  align-items: baseline;
  gap: 10px;
  font-weight: 600;
  margin-top: 4px;
}

.count {
  font-size: 12px;
  font-weight: 400;
  color: var(--el-text-color-secondary);
}

.q-card :deep(.el-card__body) {
  padding: 14px 16px;
}

.q-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.q-index {
  font-weight: 600;
}

.q-score {
  margin-left: auto;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.q-stem {
  font-size: 14px;
  line-height: 1.7;
  margin-bottom: 10px;
  white-space: pre-wrap;
}

.options {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.option {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 10px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
  font-size: 13px;
}

.opt-key {
  font-weight: 600;
  width: 16px;
}

.opt-content {
  flex: 1;
}

.opt-mine {
  border-color: var(--el-color-danger-light-5);
  background: var(--el-color-danger-light-9);
}

.opt-correct {
  border-color: var(--el-color-success-light-5);
  background: var(--el-color-success-light-9);
}

.opt-picked-correct {
  border-color: var(--el-color-success);
  background: var(--el-color-success-light-8);
}

.opt-badge {
  font-size: 11px;
  padding: 0 6px;
  border-radius: 3px;
  line-height: 18px;
}

.opt-badge.mine {
  color: var(--el-color-danger);
  background: var(--el-color-danger-light-8);
}

.opt-badge.correct {
  color: var(--el-color-success);
  background: var(--el-color-success-light-8);
}

.text-answer {
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 13px;
}

.answer-row {
  display: flex;
  gap: 10px;
}

.label {
  width: 64px;
  color: var(--el-text-color-secondary);
  flex-shrink: 0;
}

.value {
  flex: 1;
  white-space: pre-wrap;
  word-break: break-word;
}

.value.empty {
  color: var(--el-text-color-placeholder);
}

.value.correct-text {
  color: var(--el-color-success);
}

.footer {
  display: flex;
  justify-content: center;
  padding: 8px 0 24px;
}
</style>
