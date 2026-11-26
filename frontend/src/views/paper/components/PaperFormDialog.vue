<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import {
  createPaper,
  fetchPaper,
  updatePaper,
  type ComposeType,
  type PaperRulePayload,
} from '@/api/paper'
import { DIFFICULTY_OPTIONS, TYPE_OPTIONS, type CategoryNode, type QuestionType } from '@/api/question'

const props = defineProps<{
  modelValue: boolean
  /** 有值表示编辑 */
  paperId: number | null
  categories: CategoryNode[]
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  saved: []
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value),
})

interface RuleRow {
  type: QuestionType
  questionCount: number
  scorePerItem: number
  categoryIds: number[]
  difficulty: number | null
}

interface FormState {
  title: string
  composeType: ComposeType
  totalScore: number
  durationMin: number
  passScore: number
  rules: RuleRow[]
}

function newRule(): RuleRow {
  return {
    type: 'SINGLE',
    questionCount: 10,
    scorePerItem: 2,
    categoryIds: [],
    difficulty: null,
  }
}

function blankForm(): FormState {
  return {
    title: '',
    composeType: 'RANDOM',
    totalScore: 100,
    durationMin: 60,
    passScore: 60,
    rules: [newRule()],
  }
}

const formRef = ref<FormInstance>()
const loading = ref(false)
const detailLoading = ref(false)
const form = reactive<FormState>(blankForm())

const rules: FormRules = {
  title: [
    { required: true, message: '请输入试卷标题', trigger: 'blur' },
    { max: 200, message: '标题不超过 200 字', trigger: 'blur' },
  ],
}

const isRandom = computed(() => form.composeType === 'RANDOM')
const title = computed(() => (props.paperId ? '编辑试卷' : '新建试卷'))

/** 规则合计（分），保留两位避免浮点尾差 */
const ruleTotal = computed(() =>
  Number(
    form.rules
      .reduce((sum, rule) => sum + (rule.questionCount || 0) * (rule.scorePerItem || 0), 0)
      .toFixed(2),
  ),
)

const ruleCount = computed(() => form.rules.reduce((sum, rule) => sum + (rule.questionCount || 0), 0))

const diff = computed(() => Number((form.totalScore - ruleTotal.value).toFixed(2)))

const balanced = computed(() => Math.abs(diff.value) < 0.005)

const canSubmit = computed(() => {
  if (!isRandom.value) {
    return true
  }
  return balanced.value && form.rules.length > 0
})

watch(
  () => props.modelValue,
  async (open) => {
    if (!open) {
      return
    }
    Object.assign(form, blankForm())
    formRef.value?.clearValidate()
    if (props.paperId) {
      await loadDetail(props.paperId)
    }
  },
)

async function loadDetail(id: number) {
  detailLoading.value = true
  try {
    const paper = await fetchPaper(id)
    form.title = paper.title
    form.composeType = paper.composeType
    form.totalScore = Number(paper.totalScore)
    form.durationMin = Math.round(paper.durationSec / 60)
    form.passScore = Number(paper.passScore)
    form.rules = paper.rules?.length
      ? paper.rules.map((rule) => ({
          type: rule.type,
          questionCount: rule.questionCount,
          scorePerItem: Number(rule.scorePerItem),
          categoryIds: rule.categoryIds ?? [],
          difficulty: rule.difficulty ?? null,
        }))
      : [newRule()]
  } finally {
    detailLoading.value = false
  }
}

function addRule() {
  if (form.rules.length >= 8) {
    ElMessage.warning('最多 8 条规则')
    return
  }
  form.rules.push(newRule())
}

function removeRule(index: number) {
  if (form.rules.length <= 1) {
    ElMessage.warning('至少保留一条规则')
    return
  }
  form.rules.splice(index, 1)
}

async function submit() {
  if (!formRef.value) {
    return
  }
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) {
    return
  }

  if (isRandom.value && !balanced.value) {
    ElMessage.warning('规则合计与设定总分不一致，请先调整')
    return
  }

  const payload = {
    title: form.title.trim(),
    composeType: form.composeType,
    totalScore: isRandom.value ? form.totalScore : undefined,
    durationSec: form.durationMin * 60,
    passScore: form.passScore,
    rules: isRandom.value
      ? form.rules.map<PaperRulePayload>((rule) => ({
          type: rule.type,
          questionCount: rule.questionCount,
          scorePerItem: rule.scorePerItem,
          categoryIds: rule.categoryIds.length ? rule.categoryIds : undefined,
          difficulty: rule.difficulty ?? undefined,
        }))
      : undefined,
  }

  loading.value = true
  try {
    if (props.paperId) {
      await updatePaper(props.paperId, payload)
      ElMessage.success('试卷已更新')
    } else {
      await createPaper(payload)
      ElMessage.success('试卷已创建，可进入详情抽题')
    }
    visible.value = false
    emit('saved')
  } catch {
    // 总分不一致等业务错误由拦截器提示
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <el-dialog v-model="visible" :title="title" width="900px" top="6vh" destroy-on-close>
    <el-form ref="formRef" v-loading="detailLoading" :model="form" :rules="rules" label-width="90px">
      <el-form-item label="试卷标题" prop="title">
        <el-input v-model="form.title" maxlength="200" show-word-limit placeholder="如：Java 基础期中测验" />
      </el-form-item>

      <el-row :gutter="16">
        <el-col :span="8">
          <el-form-item label="组卷方式">
            <el-radio-group v-model="form.composeType" :disabled="!!props.paperId">
              <el-radio-button value="RANDOM">规则抽题</el-radio-button>
              <el-radio-button value="MANUAL">手工选题</el-radio-button>
            </el-radio-group>
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="时长(分钟)">
            <el-input-number v-model="form.durationMin" :min="1" :max="1440" controls-position="right" style="width: 100%" />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="及格分">
            <el-input-number
              v-model="form.passScore"
              :min="0"
              :max="isRandom ? form.totalScore : 10000"
              :step="5"
              :precision="2"
              controls-position="right"
              style="width: 100%"
            />
          </el-form-item>
        </el-col>
      </el-row>

      <template v-if="isRandom">
        <el-form-item label="设定总分">
          <el-input-number
            v-model="form.totalScore"
            :min="1"
            :max="10000"
            :step="10"
            :precision="2"
            controls-position="right"
            style="width: 200px"
          />
          <span class="hint">规则合计必须与之完全相等，差 0.01 分也不允许保存</span>
        </el-form-item>

        <el-form-item label="抽题规则">
          <div class="rules">
            <div class="rule-head">
              <span class="c-type">题型</span>
              <span class="c-count">数量</span>
              <span class="c-score">每题分值</span>
              <span class="c-diff">难度</span>
              <span class="c-cat">限定分类</span>
              <span class="c-op">操作</span>
            </div>
            <div v-for="(rule, index) in form.rules" :key="index" class="rule-row">
              <el-select v-model="rule.type" class="c-type">
                <el-option v-for="item in TYPE_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
              <el-input-number v-model="rule.questionCount" :min="1" :max="500" class="c-count" controls-position="right" />
              <el-input-number
                v-model="rule.scorePerItem"
                :min="0.01"
                :max="100"
                :step="0.5"
                :precision="2"
                class="c-score"
                controls-position="right"
              />
              <el-select v-model="rule.difficulty" class="c-diff" placeholder="不限" clearable>
                <el-option v-for="item in DIFFICULTY_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
              <el-tree-select
                v-model="rule.categoryIds"
                class="c-cat"
                :data="categories"
                :props="{ label: 'name', children: 'children' }"
                node-key="id"
                multiple
                show-checkbox
                check-strictly
                collapse-tags
                collapse-tags-tooltip
                placeholder="不限"
              />
              <el-button text type="danger" class="c-op" @click="removeRule(index)">删除</el-button>
            </div>
            <el-button text type="primary" @click="addRule">+ 添加规则</el-button>
          </div>
        </el-form-item>

        <el-alert
          :type="balanced ? 'success' : 'error'"
          :closable="false"
          show-icon
          class="total-alert"
        >
          <template #title>
            规则合计 <b>{{ ruleTotal.toFixed(2) }}</b> 分 / {{ ruleCount }} 题
            <template v-if="!balanced">
              ，与设定总分 {{ form.totalScore.toFixed(2) }} 分相差
              <b>{{ diff > 0 ? '+' : '' }}{{ diff.toFixed(2) }}</b> 分
            </template>
            <template v-else>，与设定总分一致</template>
          </template>
        </el-alert>
      </template>

      <el-alert v-else type="info" :closable="false" show-icon>
        <template #title>手工选题：创建后在详情页逐题添加，总分与题数由已选题目自动汇总</template>
      </el-alert>
    </el-form>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="loading" :disabled="!canSubmit" @click="submit">保存</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.hint {
  margin-left: 10px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.rules {
  width: 100%;
}

.rule-head,
.rule-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.rule-head {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  padding-bottom: 6px;
}

.rule-row {
  margin-bottom: 8px;
}

.c-type {
  width: 110px;
}

.c-count {
  width: 110px;
}

.c-score {
  width: 120px;
}

.c-diff {
  width: 100px;
}

.c-cat {
  flex: 1;
  min-width: 180px;
}

.c-op {
  width: 48px;
}

.total-alert {
  margin-top: 4px;
}
</style>
