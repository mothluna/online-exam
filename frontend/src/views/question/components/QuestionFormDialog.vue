<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import {
  TYPE_OPTIONS,
  createQuestion,
  fetchQuestion,
  updateQuestion,
  type CategoryNode,
  type OptionItem,
  type QuestionType,
} from '@/api/question'

const props = defineProps<{
  modelValue: boolean
  categories: CategoryNode[]
  /** 有值表示编辑，否则新增 */
  questionId: number | null
  defaultCategoryId?: number | null
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  saved: []
}>()

const MAX_OPTIONS = 6

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value),
})

const formRef = ref<FormInstance>()
const loading = ref(false)
const detailLoading = ref(false)

interface FormState {
  categoryId: number | undefined
  type: QuestionType
  stem: string
  options: OptionItem[]
  singleAnswer: string
  multipleAnswer: string[]
  judgeAnswer: string
  /** 填空：一行一个空，| 分隔同空的多种写法 */
  blankText: string
  /** 简答：逗号分隔关键词 */
  shortText: string
  analysis: string
  difficulty: number
  defaultScore: number
  status: number
}

function blankForm(): FormState {
  return {
    categoryId: props.defaultCategoryId ?? undefined,
    type: 'SINGLE',
    stem: '',
    options: [
      { key: 'A', content: '' },
      { key: 'B', content: '' },
    ],
    singleAnswer: '',
    multipleAnswer: [],
    judgeAnswer: '正确',
    blankText: '',
    shortText: '',
    analysis: '',
    difficulty: 2,
    defaultScore: 2,
    status: 1,
  }
}

const form = reactive<FormState>(blankForm())

const rules: FormRules = {
  categoryId: [{ required: true, message: '请选择分类', trigger: 'change' }],
  type: [{ required: true, message: '请选择题型', trigger: 'change' }],
  stem: [
    { required: true, message: '请输入题干', trigger: 'blur' },
    { max: 5000, message: '题干过长', trigger: 'blur' },
  ],
}

const isChoice = computed(() => form.type === 'SINGLE' || form.type === 'MULTIPLE')
const title = computed(() => (props.questionId ? '编辑题目' : '新增题目'))

watch(
  () => props.modelValue,
  async (open) => {
    if (!open) {
      return
    }
    Object.assign(form, blankForm())
    formRef.value?.clearValidate()
    if (props.questionId) {
      await loadDetail(props.questionId)
    }
  },
)

async function loadDetail(id: number) {
  detailLoading.value = true
  try {
    const detail = await fetchQuestion(id)
    form.categoryId = detail.categoryId
    form.type = detail.type
    form.stem = detail.stem
    form.options = detail.options?.length
      ? detail.options.map((item) => ({ ...item }))
      : [
          { key: 'A', content: '' },
          { key: 'B', content: '' },
        ]
    form.analysis = detail.analysis ?? ''
    form.difficulty = detail.difficulty
    form.defaultScore = Number(detail.score)
    form.status = detail.status

    const answer = detail.answer ?? []
    if (detail.type === 'SINGLE') {
      form.singleAnswer = answer[0] ?? ''
    } else if (detail.type === 'MULTIPLE') {
      form.multipleAnswer = [...answer]
    } else if (detail.type === 'JUDGE') {
      form.judgeAnswer = answer[0] === '错误' ? '错误' : '正确'
    } else if (detail.type === 'BLANK') {
      form.blankText = answer.join('\n')
    } else if (detail.type === 'SHORT') {
      form.shortText = answer.join(',')
    }
  } finally {
    detailLoading.value = false
  }
}

function nextKey(): string {
  const used = new Set(form.options.map((item) => item.key))
  for (let i = 0; i < MAX_OPTIONS; i++) {
    const key = String.fromCharCode(65 + i)
    if (!used.has(key)) {
      return key
    }
  }
  return String.fromCharCode(65 + form.options.length)
}

function addOption() {
  if (form.options.length >= MAX_OPTIONS) {
    ElMessage.warning(`最多 ${MAX_OPTIONS} 个选项`)
    return
  }
  form.options.push({ key: nextKey(), content: '' })
}

function removeOption(index: number) {
  const removed = form.options[index]
  form.options.splice(index, 1)
  // 删掉选项后，答案里残留该标识会让整题无法保存
  form.multipleAnswer = form.multipleAnswer.filter((key) => key !== removed.key)
  if (form.singleAnswer === removed.key) {
    form.singleAnswer = ''
  }
}

function onTypeChange() {
  form.singleAnswer = ''
  form.multipleAnswer = []
  form.judgeAnswer = '正确'
  form.blankText = ''
  form.shortText = ''
}

/** 组装答案；返回 null 表示校验不通过（已给出提示） */
function buildAnswer(): string[] | null {
  switch (form.type) {
    case 'SINGLE':
      return form.singleAnswer ? [form.singleAnswer] : null
    case 'MULTIPLE':
      return form.multipleAnswer.length >= 2 ? [...form.multipleAnswer] : null
    case 'JUDGE':
      return [form.judgeAnswer]
    case 'BLANK':
      return form.blankText
        .split('\n')
        .map((line) => line.trim())
        .filter((line) => line.length > 0)
    case 'SHORT':
      return form.shortText
        .split(/[,，;；\s]+/)
        .map((word) => word.trim())
        .filter((word) => word.length > 0)
    default:
      return null
  }
}

async function submit() {
  if (!formRef.value) {
    return
  }
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) {
    return
  }

  if (isChoice.value) {
    if (form.options.length < 2) {
      ElMessage.warning('选择题至少需要 2 个选项')
      return
    }
    if (form.options.some((item) => !item.content.trim())) {
      ElMessage.warning('选项内容不能为空')
      return
    }
  }

  const answer = buildAnswer()
  if (!answer || answer.length === 0) {
    ElMessage.warning(
      form.type === 'MULTIPLE' ? '多选题至少选择两个正确答案' : '请填写正确答案',
    )
    return
  }

  const payload = {
    categoryId: form.categoryId as number,
    type: form.type,
    stem: form.stem.trim(),
    options: isChoice.value
      ? form.options.map((item) => ({ key: item.key, content: item.content.trim() }))
      : undefined,
    answer,
    analysis: form.analysis.trim() || undefined,
    difficulty: form.difficulty,
    defaultScore: form.defaultScore,
    status: form.status,
  }

  loading.value = true
  try {
    if (props.questionId) {
      await updateQuestion(props.questionId, payload)
      ElMessage.success('题目已更新')
    } else {
      await createQuestion(payload)
      ElMessage.success('题目已新增')
    }
    visible.value = false
    emit('saved')
  } catch {
    // 题干重复等业务错误由拦截器提示
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <el-dialog v-model="visible" :title="title" width="760px" top="6vh" destroy-on-close>
    <el-form
      ref="formRef"
      v-loading="detailLoading"
      :model="form"
      :rules="rules"
      label-width="90px"
    >
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="分类" prop="categoryId">
            <el-tree-select
              v-model="form.categoryId"
              :data="categories"
              :props="{ label: 'name', children: 'children' }"
              node-key="id"
              check-strictly
              :render-after-expand="false"
              placeholder="选择分类"
              style="width: 100%"
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="题型" prop="type">
            <el-select v-model="form.type" style="width: 100%" @change="onTypeChange">
              <el-option
                v-for="item in TYPE_OPTIONS"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>

      <el-form-item label="题干" prop="stem">
        <el-input
          v-model="form.stem"
          type="textarea"
          :rows="3"
          maxlength="5000"
          show-word-limit
          placeholder="请输入题干。保存时会按归一化后的题干查重，与已有题目重复将被拒绝"
        />
      </el-form-item>

      <!-- 选择题：选项编辑 -->
      <el-form-item v-if="isChoice" label="选项">
        <div class="options">
          <div v-for="(item, index) in form.options" :key="item.key" class="option-row">
            <el-tag class="option-key" type="info">{{ item.key }}</el-tag>
            <el-input v-model="item.content" :placeholder="`选项 ${item.key} 内容`" />
            <el-button
              text
              type="danger"
              :disabled="form.options.length <= 2"
              @click="removeOption(index)"
            >
              删除
            </el-button>
          </div>
          <el-button text type="primary" :disabled="form.options.length >= MAX_OPTIONS" @click="addOption">
            + 添加选项
          </el-button>
        </div>
      </el-form-item>

      <!-- 答案：按题型切换控件 -->
      <el-form-item label="正确答案">
        <el-radio-group v-if="form.type === 'SINGLE'" v-model="form.singleAnswer">
          <el-radio v-for="item in form.options" :key="item.key" :value="item.key">
            {{ item.key }}
          </el-radio>
        </el-radio-group>

        <el-checkbox-group v-else-if="form.type === 'MULTIPLE'" v-model="form.multipleAnswer">
          <el-checkbox v-for="item in form.options" :key="item.key" :value="item.key">
            {{ item.key }}
          </el-checkbox>
        </el-checkbox-group>

        <el-radio-group v-else-if="form.type === 'JUDGE'" v-model="form.judgeAnswer">
          <el-radio value="正确">正确</el-radio>
          <el-radio value="错误">错误</el-radio>
        </el-radio-group>

        <div v-else-if="form.type === 'BLANK'" class="stack">
          <el-input
            v-model="form.blankText"
            type="textarea"
            :rows="2"
            placeholder="一行一个空；同一空的多种写法用 | 分隔，如：Object|java.lang.Object"
          />
          <span class="hint">按空计分，答对空数占比 × 分值</span>
        </div>

        <div v-else class="stack">
          <el-input v-model="form.shortText" placeholder="关键词用逗号分隔，如：可达性分析,分代,回收" />
          <span class="hint">简答题由关键词命中率给参考分，提交后仍需教师复核</span>
        </div>
      </el-form-item>

      <el-form-item label="解析">
        <el-input v-model="form.analysis" type="textarea" :rows="2" placeholder="选填" />
      </el-form-item>

      <el-row :gutter="16">
        <el-col :span="8">
          <el-form-item label="难度">
            <el-radio-group v-model="form.difficulty">
              <el-radio-button :value="1">易</el-radio-button>
              <el-radio-button :value="2">中</el-radio-button>
              <el-radio-button :value="3">难</el-radio-button>
            </el-radio-group>
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="分值">
            <el-input-number
              v-model="form.defaultScore"
              :min="0.01"
              :max="100"
              :step="0.5"
              :precision="2"
              controls-position="right"
              style="width: 100%"
            />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="状态">
            <el-switch v-model="form.status" :active-value="1" :inactive-value="0" />
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="loading" @click="submit">保存</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.options {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
}

.option-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.option-key {
  width: 32px;
  justify-content: center;
}

.stack {
  display: flex;
  flex-direction: column;
  gap: 4px;
  width: 100%;
}

.hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
