<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  fetchPaper,
  fetchPaperQuestions,
  publishPaper,
  randomizePaper,
  removePaperQuestion,
  type PaperItem,
  type PaperQuestionItem,
} from '@/api/paper'
import type { CategoryNode } from '@/api/question'
import QuestionPickerDialog from './QuestionPickerDialog.vue'

const props = defineProps<{
  modelValue: boolean
  paperId: number | null
  categories: CategoryNode[]
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  changed: []
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value),
})

const loading = ref(false)
const randomizing = ref(false)
const publishing = ref(false)
const pickerVisible = ref(false)
const paper = ref<PaperItem | null>(null)
const questions = ref<PaperQuestionItem[]>([])

const isDraft = computed(() => paper.value?.status === 'DRAFT')
const isRandom = computed(() => paper.value?.composeType === 'RANDOM')
const questionIds = computed(() => questions.value.map((item) => item.questionId))

const groupSummary = computed(() => {
  const map = new Map<string, { count: number; score: number }>()
  for (const item of questions.value) {
    const entry = map.get(item.typeLabel) ?? { count: 0, score: 0 }
    entry.count += 1
    entry.score += Number(item.score)
    map.set(item.typeLabel, entry)
  }
  return [...map.entries()].map(([label, value]) => ({
    label,
    count: value.count,
    score: Number(value.score.toFixed(2)),
  }))
})

watch(
  () => props.modelValue,
  (open) => {
    if (open && props.paperId) {
      load(props.paperId)
    }
  },
)

async function load(id: number) {
  loading.value = true
  try {
    const [detail, list] = await Promise.all([fetchPaper(id), fetchPaperQuestions(id)])
    paper.value = detail
    questions.value = list
  } finally {
    loading.value = false
  }
}

async function reload() {
  if (props.paperId) {
    await load(props.paperId)
    emit('changed')
  }
}

async function handleRandomize() {
  if (!paper.value) {
    return
  }
  const confirmed = await ElMessageBox.confirm(
    questions.value.length
      ? '重新抽题会覆盖当前已有题目，确定继续？'
      : '将按规则从题库随机抽题，确定继续？',
    '随机抽题',
    { type: 'warning' },
  ).catch(() => false)
  if (!confirmed) {
    return
  }

  randomizing.value = true
  try {
    const result = await randomizePaper(paper.value.id)
    ElMessage.success(`抽题完成：${result.totalCount} 题 / ${Number(result.totalScore).toFixed(2)} 分，耗时 ${result.elapsedMs}ms`)
    await reload()
  } catch {
    // 题池不足等错误由拦截器提示
  } finally {
    randomizing.value = false
  }
}

async function handlePublish() {
  if (!paper.value) {
    return
  }
  const confirmed = await ElMessageBox.confirm(
    '发布后不可再修改题目与规则，确定发布？',
    '发布试卷',
    { type: 'warning' },
  ).catch(() => false)
  if (!confirmed) {
    return
  }

  publishing.value = true
  try {
    await publishPaper(paper.value.id)
    ElMessage.success('试卷已发布')
    await reload()
  } catch {
    // 总分事实校验失败时后端会给出实际值与声明值的差异
  } finally {
    publishing.value = false
  }
}

async function handleRemove(row: PaperQuestionItem) {
  if (!paper.value) {
    return
  }
  const confirmed = await ElMessageBox.confirm('从本卷移除该题？', '移除题目', { type: 'warning' }).catch(
    () => false,
  )
  if (!confirmed) {
    return
  }
  await removePaperQuestion(paper.value.id, row.questionId)
  ElMessage.success('已移除')
  await reload()
}

async function onQuestionsAdded() {
  await reload()
}
</script>

<template>
  <el-drawer v-model="visible" size="760px" :with-header="false">
    <div v-loading="loading" class="drawer">
      <template v-if="paper">
        <div class="head">
          <div>
            <h3>{{ paper.title }}</h3>
            <div class="meta">
              <el-tag :type="paper.status === 'PUBLISHED' ? 'success' : 'info'" size="small">
                {{ paper.status === 'PUBLISHED' ? '已发布' : '草稿' }}
              </el-tag>
              <el-tag type="warning" effect="plain" size="small">
                {{ paper.composeType === 'RANDOM' ? '规则抽题' : '手工选题' }}
              </el-tag>
              <span class="text">{{ paper.totalCount }} 题 / {{ Number(paper.totalScore).toFixed(2) }} 分</span>
              <span class="text">及格 {{ Number(paper.passScore).toFixed(2) }} 分</span>
              <span class="text">时长 {{ Math.round(paper.durationSec / 60) }} 分钟</span>
            </div>
          </div>
          <div class="head-actions">
            <el-button v-if="isDraft" @click="pickerVisible = true">添加题目</el-button>
            <el-button
              v-if="isDraft && isRandom"
              type="warning"
              :loading="randomizing"
              @click="handleRandomize"
            >
              {{ questions.length ? '重新抽题' : '随机抽题' }}
            </el-button>
            <el-button v-if="isDraft" type="primary" :loading="publishing" @click="handlePublish">
              发布
            </el-button>
          </div>
        </div>

        <el-alert
          v-if="isDraft && isRandom && !questions.length"
          type="info"
          :closable="false"
          show-icon
          title="该试卷按规则组卷，尚未抽题。点击「随机抽题」按规则生成题目。"
          class="notice"
        />

        <div v-if="paper.rules?.length" class="section">
          <div class="section-title">抽题规则</div>
          <el-table :data="paper.rules" border size="small">
            <el-table-column prop="typeLabel" label="题型" width="90" />
            <el-table-column prop="questionCount" label="数量" width="80" />
            <el-table-column label="每题分值" width="100">
              <template #default="{ row }">{{ Number(row.scorePerItem).toFixed(2) }}</template>
            </el-table-column>
            <el-table-column label="难度" width="80">
              <template #default="{ row }">
                {{ row.difficulty ? ['', '易', '中', '难'][row.difficulty] : '不限' }}
              </template>
            </el-table-column>
            <el-table-column label="限定分类" min-width="140">
              <template #default="{ row }">
                {{ row.categoryIds?.length ? row.categoryIds.join(',') : '不限' }}
              </template>
            </el-table-column>
          </el-table>
        </div>

        <div class="section">
          <div class="section-title">
            题目预览
            <span class="count">共 {{ questions.length }} 题</span>
            <span v-if="groupSummary.length" class="summary">
              <el-tag v-for="item in groupSummary" :key="item.label" size="small" effect="plain" class="summary-tag">
                {{ item.label }} {{ item.count }} 题 / {{ item.score }} 分
              </el-tag>
            </span>
          </div>

          <el-table :data="questions" border size="small" height="calc(100vh - 420px)">
            <el-table-column prop="sortNo" label="#" width="56" />
            <el-table-column prop="typeLabel" label="题型" width="80" />
            <el-table-column prop="stem" label="题干" min-width="300" show-overflow-tooltip />
            <el-table-column label="分值" width="80">
              <template #default="{ row }">{{ Number(row.score).toFixed(2) }}</template>
            </el-table-column>
            <el-table-column v-if="isDraft" label="操作" width="80">
              <template #default="{ row }">
                <el-button text type="danger" size="small" @click="handleRemove(row)">移除</el-button>
              </template>
            </el-table-column>
            <template #empty>暂无题目</template>
          </el-table>
        </div>
      </template>
    </div>

    <QuestionPickerDialog
      v-if="paper"
      v-model="pickerVisible"
      :paper-id="paper.id"
      :categories="categories"
      :existing-ids="questionIds"
      @added="onQuestionsAdded"
    />
  </el-drawer>
</template>

<style scoped>
.drawer {
  height: 100%;
}

.head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.head h3 {
  margin: 0 0 8px;
  font-size: 17px;
}

.meta {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.text {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.head-actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}

.notice {
  margin-top: 12px;
}

.section {
  margin-top: 16px;
}

.section-title {
  display: flex;
  align-items: center;
  gap: 10px;
  font-weight: 600;
  margin-bottom: 8px;
  font-size: 14px;
}

.count {
  font-size: 12px;
  font-weight: 400;
  color: var(--el-text-color-secondary);
}

.summary {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.summary-tag {
  font-weight: 400;
}
</style>
