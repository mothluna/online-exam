<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  fetchAttempt,
  saveAnswers,
  submitAttempt,
  type Attempt,
  type SubmitResult,
} from '@/api/exam'

const route = useRoute()
const router = useRouter()
const attemptId = Number(route.params.attemptId)

/**
 * 简答题字数上限。数据库 answer_json 是 JSON 列，本身没有这个限制，
 * 这里纯粹是客户端体验约束：给出可见的计数器，避免提交几千字的文本把页面撑坏。
 * 判分依据 judge_detail 只有 500 字符，超长答案对机器粗筛也没有额外价值。
 */
const SHORT_MAX_LENGTH = 2000

const attempt = ref<Attempt | null>(null)
const loading = ref(true)
const saving = ref(false)
const submitting = ref(false)
const result = ref<SubmitResult | null>(null)
const lastSavedAt = ref<string>('')
const remaining = ref(0)

/** 服务端时间与本机时间的偏差，用于校正倒计时 */
const clockOffset = ref(0)

const answers = reactive<Record<number, string[]>>({})
const dirty = new Set<number>()
// 已被服务端清洗过的题目只提示一次，避免每次自动暂存都弹窗
const sanitizedNotified = new Set<number>()

/** 连续失败次数，用于指数退避；成功一次就清零 */
const saveFailures = ref(0)
/** 早于这个时刻不重试（退避窗口），避免断网时每 10 秒刷屏 */
const nextRetryAt = ref(0)
/** 每秒刷新的时间戳，用来驱动"x 秒后重试"的倒计时 */
const nowTs = ref(Date.now())

let tickTimer: number | undefined
let autoSaveTimer: number | undefined
let debounceTimer: number | undefined

const isRunning = computed(() => attempt.value?.status === 'IN_PROGRESS' && !result.value)

const answeredCount = computed(() => {
  if (!attempt.value) {
    return 0
  }
  return attempt.value.questions.filter((q) => (answers[q.questionId] ?? []).some((v) => v !== '')).length
})

const progress = computed(() => {
  if (!attempt.value?.questions.length) {
    return 0
  }
  return Math.round((answeredCount.value / attempt.value.questions.length) * 100)
})

const countdownText = computed(() => {
  const total = remaining.value
  const h = Math.floor(total / 3600)
  const m = Math.floor((total % 3600) / 60)
  const s = total % 60
  const pad = (n: number) => String(n).padStart(2, '0')
  return h > 0 ? `${pad(h)}:${pad(m)}:${pad(s)}` : `${pad(m)}:${pad(s)}`
})

const countdownDanger = computed(() => remaining.value <= 300)

/** 后端返回 "yyyy-MM-dd HH:mm:ss"，Safari 不接受空格分隔，统一换 T */
function parseTime(value: string): number {
  return new Date(value.replace(' ', 'T')).getTime()
}

/**
 * 填空题空数：优先用服务端下发的 blank_count（= 答案数组长度）。
 * 只有在字段缺失（老接口/老数据）时才退回数下划线，且保持至少 1 空。
 */
function blankCount(question: { blankCount?: number; stem: string }): number {
  if (question.blankCount && question.blankCount > 0) {
    return question.blankCount
  }
  const matched = question.stem.match(/_{2,}/g)
  return matched && matched.length ? matched.length : 1
}

async function load() {
  loading.value = true
  try {
    const data = await fetchAttempt(attemptId)
    attempt.value = data
    clockOffset.value = parseTime(data.serverTime) - Date.now()

    for (const question of data.questions) {
      answers[question.questionId] = []
    }
    for (const saved of data.answers) {
      answers[saved.questionId] = [...saved.answer]
    }

    if (data.status === 'IN_PROGRESS') {
      tick()
      tickTimer = window.setInterval(tick, 1000)
      autoSaveTimer = window.setInterval(() => void flush(), 10000)
      window.addEventListener('beforeunload', onBeforeUnload)
    }
  } finally {
    loading.value = false
  }
}

/** 距下次重试还有多少秒（用于状态条倒计时） */
const retryIn = computed(() => Math.max(0, Math.ceil((nextRetryAt.value - nowTs.value) / 1000)))

function tick() {
  nowTs.value = Date.now()
  if (!attempt.value) {
    return
  }
  // 截止时间是服务端时钟，减掉偏差换算到本机时钟
  const deadlineLocal = parseTime(attempt.value.deadline) - clockOffset.value
  remaining.value = Math.max(0, Math.floor((deadlineLocal - Date.now()) / 1000))

  if (remaining.value === 0) {
    stopTimers()
    void submit(true)
  }
}

function stopTimers() {
  if (tickTimer) {
    window.clearInterval(tickTimer)
    tickTimer = undefined
  }
  if (autoSaveTimer) {
    window.clearInterval(autoSaveTimer)
    autoSaveTimer = undefined
  }
  if (debounceTimer) {
    window.clearTimeout(debounceTimer)
    debounceTimer = undefined
  }
  window.removeEventListener('beforeunload', onBeforeUnload)
}

function onBeforeUnload(event: BeforeUnloadEvent) {
  if (isRunning.value) {
    event.preventDefault()
    event.returnValue = ''
  }
}

function markDirty(questionId: number) {
  dirty.add(questionId)
  if (debounceTimer) {
    window.clearTimeout(debounceTimer)
  }
  // 停止输入 800ms 后再暂存，避免每敲一个字就发请求
  debounceTimer = window.setTimeout(() => void flush(), 800)
}

async function flush(force = false): Promise<boolean> {
  if (!isRunning.value || dirty.size === 0) {
    return true
  }
  // 退避窗口内跳过自动重试；交卷/离开页面这类需要抢救答案的场景用 force 绕过
  if (!force && Date.now() < nextRetryAt.value) {
    return false
  }
  const ids = [...dirty]
  dirty.clear()
  saving.value = true
  try {
    const cleaned = await saveAnswers(
      attemptId,
      ids.map((id) => ({ questionId: id, answer: answers[id] ?? [] })),
    )
    // 服务端会剔除非法取值（如判断题传了 B）。不阻断保存，但要如实告诉考生哪几题没存成
    if (cleaned?.length) {
      const fresh = cleaned.filter((id) => !sanitizedNotified.has(id))
      fresh.forEach((id) => sanitizedNotified.add(id))
      if (fresh.length) {
        const labels = fresh.map((id) => {
          const item = attempt.value?.questions.find((q) => q.questionId === id)
          return item ? `第 ${item.sortNo} 题` : `题目 ${id}`
        })
        ElMessage.warning(`${labels.join('、')}的作答格式不被接受，已按未作答保存`)
      }
    }
    lastSavedAt.value = new Date().toLocaleTimeString('zh-CN', { hour12: false })
    saveFailures.value = 0
    nextRetryAt.value = 0
    return true
  } catch (error) {
    // 失败的题目放回队列，下一轮自动重试；不要静默丢弃用户答案
    ids.forEach((id) => dirty.add(id))
    const status = (error as { response?: { status?: number } }).response?.status
    if (status === 409) {
      // 已超时或已提交：停止自动暂存，提示用户交卷
      stopTimers()
      ElMessage.warning('考试已结束，正在为你交卷')
      void submit(true)
      return false
    }
    // 连续失败时指数退避：10s → 20s → 40s，封顶 60s。
    // 断网时每 10 秒重试一次只会刷屏，也让"没保存上"这件事更不显眼。
    saveFailures.value += 1
    const backoff = Math.min(10000 * 2 ** (saveFailures.value - 1), 60000)
    nextRetryAt.value = Date.now() + backoff
    return false
  } finally {
    saving.value = false
  }
}

function setSingle(questionId: number, value: string) {
  answers[questionId] = [value]
  markDirty(questionId)
}

function setMultiple(questionId: number, value: unknown) {
  // el-checkbox-group 回传的是 unknown，收口成 string[]，避免模板里写类型断言
  answers[questionId] = Array.isArray(value) ? value.map((item) => String(item)) : []
  markDirty(questionId)
}

function setBlank(questionId: number, index: number, value: string) {
  const current = answers[questionId] ?? []
  const next = [...current]
  next[index] = value
  answers[questionId] = next
  markDirty(questionId)
}

function scrollToQuestion(questionId: number) {
  document.getElementById(`q-${questionId}`)?.scrollIntoView({ behavior: 'smooth', block: 'center' })
}

function setText(questionId: number, value: string) {
  answers[questionId] = [value]
  markDirty(questionId)
}

async function submit(auto = false) {
  if (!attempt.value || submitting.value || result.value) {
    return
  }
  if (attempt.value.status !== 'IN_PROGRESS') {
    return
  }

  if (!auto) {
    const unanswered = attempt.value.questions.length - answeredCount.value
    const tip = unanswered > 0 ? `还有 ${unanswered} 题未作答，` : ''
    const confirmed = await ElMessageBox.confirm(`${tip}提交后不可修改，确定交卷？`, '交卷确认', {
      type: 'warning',
      confirmButtonText: '确认交卷',
    }).catch(() => false)
    if (!confirmed) {
      return
    }
  }

  submitting.value = true
  try {
    await flush(true)
    const data = await submitAttempt(attemptId)
    result.value = data
    attempt.value.status = data.status
    stopTimers()
    ElMessage.success('交卷成功')
  } catch {
    // 交卷失败不锁死页面，用户可以再次点击重试
  } finally {
    submitting.value = false
  }
}

function backToList() {
  router.replace('/my-exams')
}

onBeforeRouteLeave(async () => {
  if (!isRunning.value) {
    return true
  }
  const confirmed = await ElMessageBox.confirm(
    '考试尚未提交，离开后计时继续且答案只保留已暂存部分。确定离开？',
    '离开考试',
    { type: 'warning', confirmButtonText: '离开' },
  ).catch(() => false)
  return confirmed
})

onMounted(load)

onBeforeUnmount(() => {
  stopTimers()
})
</script>

<template>
  <div v-loading="loading" class="exam-page">
    <template v-if="attempt">
      <!-- 顶部信息条：倒计时是服务端时间换算，改本机时钟无效 -->
      <div class="topbar">
        <div class="left">
          <span class="title">{{ attempt.examTitle }}</span>
          <el-tag size="small" type="info" effect="plain">{{ attempt.totalCount }} 题 / {{ Number(attempt.totalScore).toFixed(0) }} 分</el-tag>
        </div>
        <div class="right">
          <!-- 失败必须显性化：只在控制台/网络面板里是看不见的 -->
          <span class="saved error" v-if="saveFailures > 0">
            未保存（连续失败 {{ saveFailures }} 次）<template v-if="retryIn > 0">，{{ retryIn }}s 后重试</template>
          </span>
          <span class="saved" v-else-if="lastSavedAt">已自动保存 {{ lastSavedAt }}</span>
          <span class="saved saving" v-else-if="saving">保存中…</span>
          <div class="countdown" :class="{ danger: countdownDanger }">
            <el-icon><Timer /></el-icon>
            {{ countdownText }}
          </div>
          <el-button type="primary" :loading="submitting" :disabled="!isRunning" @click="submit(false)">
            交卷
          </el-button>
        </div>
      </div>

      <div class="body">
        <div class="questions">
          <el-card v-for="(q, index) in attempt.questions" :id="`q-${q.questionId}`" :key="q.questionId" shadow="never" class="q-card">
            <div class="q-head">
              <span class="q-index">{{ index + 1 }}.</span>
              <el-tag size="small" effect="plain">{{ q.typeLabel }}</el-tag>
              <span class="q-score">{{ Number(q.score).toFixed(2) }} 分</span>
            </div>
            <div class="q-stem">{{ q.stem }}</div>

            <!-- 单选 -->
            <el-radio-group
              v-if="q.type === 'SINGLE'"
              :model-value="answers[q.questionId]?.[0] ?? ''"
              :disabled="!isRunning"
              class="options"
              @update:model-value="(v: unknown) => setSingle(q.questionId, String(v))"
            >
              <el-radio v-for="opt in q.options" :key="opt.key" :value="opt.key" class="option">
                {{ opt.key }}. {{ opt.content }}
              </el-radio>
            </el-radio-group>

            <!-- 多选 -->
            <el-checkbox-group
              v-else-if="q.type === 'MULTIPLE'"
              :model-value="answers[q.questionId] ?? []"
              :disabled="!isRunning"
              class="options"
              @update:model-value="(v: unknown) => setMultiple(q.questionId, v)"
            >
              <el-checkbox v-for="opt in q.options" :key="opt.key" :value="opt.key" class="option">
                {{ opt.key }}. {{ opt.content }}
              </el-checkbox>
            </el-checkbox-group>

            <!-- 判断 -->
            <el-radio-group
              v-else-if="q.type === 'JUDGE'"
              :model-value="answers[q.questionId]?.[0] ?? ''"
              :disabled="!isRunning"
              class="options"
              @update:model-value="(v: unknown) => setSingle(q.questionId, String(v))"
            >
              <el-radio value="正确">正确</el-radio>
              <el-radio value="错误">错误</el-radio>
            </el-radio-group>

            <!-- 填空：按题干中下划线数量渲染输入框 -->
            <div v-else-if="q.type === 'BLANK'" class="blanks">
              <el-input
                v-for="i in blankCount(q)"
                :key="i"
                :model-value="answers[q.questionId]?.[i - 1] ?? ''"
                :disabled="!isRunning"
                :placeholder="`第 ${i} 空`"
                class="blank-input"
                @update:model-value="(v: unknown) => setBlank(q.questionId, i - 1, String(v))"
              />
            </div>

            <!-- 简答：给字数上限与实时计数，避免超长文本把排版撑坏 -->
            <el-input
              v-else
              :model-value="answers[q.questionId]?.[0] ?? ''"
              :disabled="!isRunning"
              type="textarea"
              :rows="4"
              :maxlength="SHORT_MAX_LENGTH"
              show-word-limit
              placeholder="请作答"
              @update:model-value="(v: unknown) => setText(q.questionId, String(v))"
            />
          </el-card>
        </div>

        <div class="sheet">
          <el-card shadow="never">
            <div class="sheet-title">答题卡</div>
            <div class="sheet-progress">已答 {{ answeredCount }} / {{ attempt.totalCount }}（{{ progress }}%）</div>
            <div class="sheet-grid">
              <a
                v-for="(q, index) in attempt.questions"
                :key="q.questionId"
                class="sheet-item"
                :class="{ done: (answers[q.questionId] ?? []).some((v) => v !== '') }"
                @click.prevent="scrollToQuestion(q.questionId)"
              >
                {{ index + 1 }}
              </a>
            </div>
            <el-button class="sheet-submit" type="primary" :disabled="!isRunning" :loading="submitting" @click="submit(false)">
              交卷
            </el-button>
          </el-card>
        </div>
      </div>
    </template>

    <el-result
      v-else-if="!loading"
      icon="warning"
      title="答卷不可用"
      sub-title="该答卷不存在，或不属于当前账号"
    >
      <template #extra>
        <el-button type="primary" @click="backToList">返回考试列表</el-button>
      </template>
    </el-result>

    <el-dialog :model-value="!!result" title="交卷结果" width="420px" :close-on-click-modal="false" :show-close="false">
      <div class="result">
        <el-result
          :icon="result?.status === 'TIMEOUT' ? 'warning' : 'success'"
          :title="result?.status === 'TIMEOUT' ? '已超时自动交卷' : '交卷成功'"
        >
          <template #sub-title>
            <div class="result-line">得分：<b>{{ Number(result?.totalScore ?? 0).toFixed(2) }}</b> 分</div>
            <div class="result-line">
              {{ result?.isPass === 1 ? '已达到及格线' : '未达到及格线' }}
            </div>
            <div v-if="result?.needReview" class="result-line tip">含主观题，成绩以教师复核为准</div>
          </template>
        </el-result>
      </div>
      <template #footer>
        <el-button @click="backToList">返回考试列表</el-button>
        <el-button type="primary" @click="router.replace(`/exam-result/${attemptId}`)">查看成绩</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.exam-page {
  min-height: 100vh;
  background: var(--el-bg-color-page);
  padding-bottom: 40px;
}

.topbar {
  position: sticky;
  top: 0;
  z-index: 10;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 24px;
  background: #fff;
  border-bottom: 1px solid var(--el-border-color-light);
}

.left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.title {
  font-size: 16px;
  font-weight: 600;
}

.right {
  display: flex;
  align-items: center;
  gap: 16px;
}

.saved {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.saved.saving {
  color: var(--el-color-primary);
}

.saved.error {
  color: var(--el-color-danger);
  font-weight: 500;
}

.countdown {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 18px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  color: var(--el-color-primary);
}

.countdown.danger {
  color: var(--el-color-danger);
}

.body {
  display: flex;
  gap: 16px;
  padding: 16px 24px;
  align-items: flex-start;
}

.questions {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.q-card {
  scroll-margin-top: 80px;
}

.q-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
}

.q-index {
  font-weight: 600;
}

.q-score {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.q-stem {
  font-size: 15px;
  line-height: 1.7;
  margin-bottom: 12px;
  white-space: pre-wrap;
  word-break: break-word;
}

.options {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.option {
  height: auto;
  padding: 6px 0;
  white-space: normal;
  align-items: flex-start;
}

.blanks {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.blank-input {
  max-width: 420px;
}

.sheet {
  width: 240px;
  flex-shrink: 0;
  position: sticky;
  top: 80px;
}

.sheet-title {
  font-weight: 600;
  margin-bottom: 6px;
}

.sheet-progress {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-bottom: 10px;
}

.sheet-grid {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 6px;
}

.sheet-item {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 28px;
  border: 1px solid var(--el-border-color);
  border-radius: 4px;
  font-size: 12px;
  cursor: pointer;
  color: var(--el-text-color-regular);
}

.sheet-item.done {
  background: var(--el-color-primary);
  border-color: var(--el-color-primary);
  color: #fff;
}

.sheet-submit {
  width: 100%;
  margin-top: 14px;
}

.result-line {
  font-size: 14px;
  line-height: 1.9;
  color: var(--el-text-color-regular);
}

.result-line.tip {
  font-size: 12px;
  color: var(--el-color-warning);
}
</style>
