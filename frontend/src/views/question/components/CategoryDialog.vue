<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { createCategory, updateCategory, type CategoryNode } from '@/api/question'

const props = defineProps<{
  modelValue: boolean
  mode: 'create' | 'edit'
  /** create 时的父分类；0 表示根 */
  parentId: number
  node?: CategoryNode | null
  parentName?: string
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

const form = reactive({
  name: '',
  sortNo: 0,
  status: 1,
})

const rules: FormRules = {
  name: [
    { required: true, message: '请输入分类名称', trigger: 'blur' },
    { max: 50, message: '不超过 50 字', trigger: 'blur' },
  ],
}

const title = computed(() => (props.mode === 'create' ? '新增分类' : '编辑分类'))

watch(
  () => props.modelValue,
  (open) => {
    if (!open) {
      return
    }
    form.name = props.mode === 'edit' ? (props.node?.name ?? '') : ''
    form.sortNo = props.mode === 'edit' ? (props.node?.sortNo ?? 0) : 0
    form.status = props.mode === 'edit' ? (props.node?.status ?? 1) : 1
    formRef.value?.clearValidate()
  },
)

async function submit() {
  if (!formRef.value) {
    return
  }
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) {
    return
  }

  loading.value = true
  try {
    const payload = {
      parentId: props.mode === 'create' ? props.parentId : (props.node?.parentId ?? 0),
      name: form.name.trim(),
      sortNo: form.sortNo,
      status: form.status,
    }
    if (props.mode === 'create') {
      await createCategory(payload)
      ElMessage.success('分类已创建')
    } else if (props.node) {
      await updateCategory(props.node.id, payload)
      ElMessage.success('分类已更新')
    }
    visible.value = false
    emit('saved')
  } catch {
    // 同名分类等错误由拦截器提示
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <el-dialog v-model="visible" :title="title" width="420px" @closed="formRef?.resetFields()">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
      <el-form-item label="上级分类">
        <span class="parent">{{ props.mode === 'create' ? (parentName || '根分类') : '—' }}</span>
      </el-form-item>
      <el-form-item label="名称" prop="name">
        <el-input v-model="form.name" maxlength="50" show-word-limit placeholder="如：Java 基础" />
      </el-form-item>
      <el-form-item label="排序">
        <el-input-number v-model="form.sortNo" :min="0" :max="9999" controls-position="right" />
        <span class="hint">数字越小越靠前</span>
      </el-form-item>
      <el-form-item label="状态">
        <el-switch v-model="form.status" :active-value="1" :inactive-value="0" />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="loading" @click="submit">保存</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.parent {
  color: var(--el-text-color-regular);
}

.hint {
  margin-left: 10px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
