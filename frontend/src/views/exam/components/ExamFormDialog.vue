<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { createExam, fetchExam, updateExam } from '@/api/exam'
import { fetchPapers } from '@/api/paper'

const props = defineProps<{
  modelValue: boolean
  examId: number | null
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  saved: []
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value),
})

const formRef = ref<FormInstance>()
const loading = ref(false)
const detailLoading = ref(false)
const paperOptions = ref<{ id: number; title: string }[]>([])

interface FormState {
  title: string
  paperId: number | undefined
  timeRange: [string, string] | null
  durationMin: number
  allowRetake: boolean
  shuffle: boolean
}

function blankForm(): FormState {
  return {
    title: '',
    paperId: undefined,
    timeRange: null,
    durationMin: 60,
    allowRetake: false,
    shuffle: false,
  }
}

const form = reactive<FormState>(blankForm())

const rules: FormRules = {
  title: [
    { required: true, message: '请输入考试名称', trigger: 'blur' },
    { max: 200, message: '不超过 200 字', trigger: 'blur' },
  ],
  paperId: [{ required: true, message: '请选择试卷', trigger: 'change' }],
  timeRange: [{ required: true, message: '请选择开始与结束时间', trigger: 'change' }],
}

const title = computed(() => (props.examId ? '编辑考试' : '新建考试'))

watch(
  () => props.modelValue,
  async (open) => {
    if (!open) {
      return
    }
    Object.assign(form, blankForm())
    formRef.value?.clearValidate()
    await loadPaperOptions()
    if (props.examId) {
      await loadDetail(props.examId)
    }
  },
)

async function loadPaperOptions() {
  // 只能绑定已发布的试卷：草稿卷可能还没抽题，总分也未定
  const result = await fetchPapers({ page: 1, size: 100, status: 'PUBLISHED' })
  paperOptions.value = result.records.map((item) => ({ id: item.id, title: item.title }))
}

async function loadDetail(id: number) {
  detailLoading.value = true
  try {
    const exam = await fetchExam(id)
    form.title = exam.title
    form.paperId = exam.paperId
    form.timeRange = [exam.startTime, exam.endTime]
    form.durationMin = Math.round(exam.durationSec / 60)
    form.allowRetake = exam.allowRetake === 1
    form.shuffle = exam.shuffle === 1
  } finally {
    detailLoading.value = false
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
  if (!form.timeRange) {
    return
  }

  const [start, end] = form.timeRange
  const windowSec = (new Date(end).getTime() - new Date(start).getTime()) / 1000
  if (windowSec <= 0) {
    ElMessage.warning('结束时间必须晚于开始时间')
    return
  }
  if (form.durationMin * 60 > windowSec) {
    ElMessage.warning(`考试时长不能超过开放时间窗（${Math.floor(windowSec / 60)} 分钟）`)
    return
  }

  const payload = {
    title: form.title.trim(),
    paperId: form.paperId as number,
    startTime: start,
    endTime: end,
    durationSec: form.durationMin * 60,
    allowRetake: form.allowRetake ? 1 : 0,
    shuffle: form.shuffle ? 1 : 0,
  }

  loading.value = true
  try {
    if (props.examId) {
      await updateExam(props.examId, payload)
      ElMessage.success('考试已更新')
    } else {
      await createExam(payload)
      ElMessage.success('考试已创建，别忘了发布')
    }
    visible.value = false
    emit('saved')
  } catch {
    // 业务错误由拦截器提示
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <el-dialog v-model="visible" :title="title" width="680px" top="8vh" destroy-on-close>
    <el-form ref="formRef" v-loading="detailLoading" :model="form" :rules="rules" label-width="100px">
      <el-form-item label="考试名称" prop="title">
        <el-input v-model="form.title" maxlength="200" show-word-limit placeholder="如：Java 基础期中测验" />
      </el-form-item>

      <el-form-item label="绑定试卷" prop="paperId">
        <el-select v-model="form.paperId" placeholder="仅可选择已发布的试卷" style="width: 100%">
          <el-option v-for="item in paperOptions" :key="item.id" :label="item.title" :value="item.id" />
        </el-select>
      </el-form-item>

      <el-form-item label="开放时间" prop="timeRange">
        <el-date-picker
          v-model="form.timeRange"
          type="datetimerange"
          start-placeholder="开始时间"
          end-placeholder="结束时间"
          value-format="YYYY-MM-DD HH:mm:ss"
          style="width: 100%"
        />
      </el-form-item>

      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="时长(分钟)">
            <el-input-number
              v-model="form.durationMin"
              :min="1"
              :max="1440"
              controls-position="right"
              style="width: 100%"
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="允许重考">
            <el-switch v-model="form.allowRetake" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-form-item label="题目乱序">
        <el-switch v-model="form.shuffle" />
        <span class="hint">开启后每位考生的题目顺序不同</span>
      </el-form-item>

      <el-alert type="info" :closable="false" show-icon>
        <template #title>
          截止时间取「开考时刻 + 时长」与「结束时间」中较早者；超时后自动交卷并标记 TIMEOUT
        </template>
      </el-alert>
    </el-form>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="loading" @click="submit">保存</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.hint {
  margin-left: 10px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
