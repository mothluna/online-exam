<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import {
  fetchAttemptDetail,
  reviewAnswer,
  type AttemptDetail,
  type AttemptReviewAnswer,
} from '@/api/review'

const props = defineProps<{ modelValue: boolean; attemptId: number | null }>()
const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  reviewed: []
}>()

const detail = ref<AttemptDetail | null>(null)
const loading = ref(false)
const savingId = ref(0)
const scores = ref<Record<number, number>>({})
const comments = ref<Record<number, string>>({})

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value),
})

const statusText: Record<string, string> = {
  GRADED: '已判分',
  SUBMITTED: '已交卷',
  TIMEOUT: '超时交卷',
  IN_PROGRESS: '进行中',
}

async function load() {
  if (!props.attemptId) {
    return
  }
  loading.value = true
  try {
    detail.value = await fetchAttemptDetail(props.attemptId)
    scores.value = {}
    comments.value = {}
    for (const item of detail.value.answers) {
      if (item.type === 'SHORT') {
        scores.value[item.answerId] = Number(item.score ?? 0)
        comments.value[item.answerId] = ''
      }
    }
  } catch (error) {
    ElMessage.error((error as Error).message || '加载答卷失败')
    detail.value = null
  } finally {
    loading.value = false
  }
}

watch(
  () => props.modelValue,
  (open: boolean) => {
    if (open) {
      void load()
    }
  },
)

async function save(item: AttemptReviewAnswer) {
  savingId.value = item.answerId
  try {
    const result = await reviewAnswer(item.answerId, {
      score: scores.value[item.answerId] ?? 0,
      comment: comments.value[item.answerId]?.trim() || undefined,
    })
    ElMessage.success(
      result.pendingReviewCount > 0
        ? `已保存，还剩 ${result.pendingReviewCount} 道主观题待复核`
        : '已保存，本卷主观题已全部复核',
    )
    emit('reviewed')
    await load()
  } finally {
    savingId.value = 0
  }
}

function judgeTag(item: AttemptReviewAnswer) {
  if (item.type === 'SHORT') {
    if (item.judgeType === 'MANUAL') {
      return { text: '已复核', type: 'success' as const }
    }
    if (item.judgeType === 'UNJUDGED') {
      return { text: '待复核', type: 'warning' as const }
    }
  }
  return { text: item.judgeType === 'AUTO' ? '自动判分' : item.judgeType, type: 'info' as const }
}

function answerText(list: string[]) {
  const filled = (list ?? []).filter((value) => value !== '')
  return filled.length ? filled.join('；') : '未作答'
}
</script>

<template>
  <el-drawer v-model="visible" title="答卷复核" size="720px" destroy-on-close>
    <div v-loading="loading" class="drawer-body">
      <template v-if="detail">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="考生">
            {{ detail.realName }}（{{ detail.username }}）
          </el-descriptions-item>
          <el-descriptions-item label="考试">{{ detail.examTitle }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            {{ statusText[detail.status] ?? detail.status }}
          </el-descriptions-item>
          <el-descriptions-item label="交卷时间">{{ detail.submitTime ?? '—' }}</el-descriptions-item>
          <el-descriptions-item label="总分">
            <b>{{ Number(detail.totalScore).toFixed(2) }}</b>
            <span class="muted"> / 及格线 {{ Number(detail.passScore).toFixed(2) }}</span>
            <el-tag :type="detail.isPass === 1 ? 'success' : 'info'" size="small" class="pass-tag">
              {{ detail.isPass === 1 ? '及格' : '不及格' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="客观 / 主观">
            {{ Number(detail.objectiveScore).toFixed(2) }} / {{ Number(detail.subjectiveScore).toFixed(2) }} 分
          </el-descriptions-item>
        </el-descriptions>

        <el-alert
          v-if="detail.pendingReviewCount > 0"
          type="warning"
          :closable="false"
          show-icon
          class="review-tip"
          :title="`还有 ${detail.pendingReviewCount} 道主观题待复核，当前分数为机器初判`"
        />
        <el-alert
          v-else
          type="success"
          :closable="false"
          show-icon
          class="review-tip"
          title="本卷主观题已全部复核"
        />

        <div v-for="item in detail.answers" :key="item.answerId" class="answer-card">
          <div class="answer-head">
            <span class="idx">{{ item.sortNo }}.</span>
            <el-tag size="small" effect="plain">{{ item.typeLabel }}</el-tag>
            <el-tag size="small" :type="judgeTag(item).type" effect="light">
              {{ judgeTag(item).text }}
            </el-tag>
            <span class="score">
              {{ Number(item.score).toFixed(2) }} / {{ Number(item.fullScore).toFixed(2) }} 分
            </span>
          </div>

          <div class="stem">{{ item.stem }}</div>

          <div v-if="item.options?.length" class="options">
            <div v-for="opt in item.options" :key="opt.key" class="option">
              <span class="opt-key">{{ opt.key }}</span>
              <span class="opt-content">{{ opt.content }}</span>
              <span v-if="item.myAnswer?.includes(opt.key)" class="badge">我的选择</span>
            </div>
          </div>

          <div class="ans-row">
            <span class="label">作答</span>
            <span class="value">{{ answerText(item.myAnswer) }}</span>
          </div>
          <div v-if="item.correctAnswerText" class="ans-row">
            <span class="label">参考答案</span>
            <span class="value correct">{{ item.correctAnswerText }}</span>
          </div>
          <div v-if="item.judgeDetail" class="ans-row">
            <span class="label">判分依据</span>
            <span class="value detail">{{ item.judgeDetail }}</span>
          </div>

          <div v-if="item.type === 'SHORT'" class="review-box">
            <el-input-number
              v-model="scores[item.answerId]"
              :min="0"
              :max="Number(item.fullScore)"
              :precision="2"
              :step="0.5"
              size="small"
            />
            <el-input
              v-model="comments[item.answerId]"
              size="small"
              placeholder="评语（可选，会写入判分依据）"
              maxlength="200"
              class="comment-input"
            />
            <el-button
              type="primary"
              size="small"
              :loading="savingId === item.answerId"
              @click="save(item)"
            >
              保存分数
            </el-button>
          </div>
        </div>
      </template>

      <el-empty v-else-if="!loading" description="暂无答卷数据" />
    </div>
  </el-drawer>
</template>

<style scoped>
.drawer-body {
  min-height: 200px;
}

.muted {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.pass-tag {
  margin-left: 6px;
}

.review-tip {
  margin: 12px 0;
}

.answer-card {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  padding: 12px 14px;
  margin-top: 12px;
}

.answer-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.idx {
  font-weight: 600;
}

.score {
  margin-left: auto;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.stem {
  font-size: 14px;
  line-height: 1.7;
  margin-bottom: 8px;
  white-space: pre-wrap;
}

.options {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-bottom: 8px;
}

.option {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
}

.opt-key {
  font-weight: 600;
  width: 16px;
}

.opt-content {
  flex: 1;
}

.badge {
  font-size: 11px;
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-8);
  padding: 0 6px;
  border-radius: 3px;
  line-height: 18px;
}

.ans-row {
  display: flex;
  gap: 10px;
  font-size: 13px;
  margin-top: 4px;
}

.label {
  width: 64px;
  flex-shrink: 0;
  color: var(--el-text-color-secondary);
}

.value {
  flex: 1;
  white-space: pre-wrap;
  word-break: break-word;
}

.value.correct {
  color: var(--el-color-success);
}

.value.detail {
  color: var(--el-text-color-regular);
  font-size: 12px;
}

.review-box {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px dashed var(--el-border-color-lighter);
}

.comment-input {
  flex: 1;
}
</style>
