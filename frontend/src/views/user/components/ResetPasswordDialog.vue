<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { resetPassword, type UserItem } from '@/api/user'

const props = defineProps<{
  modelValue: boolean
  user: UserItem | null
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
  newPassword: '',
  confirmPassword: '',
})

const rules: FormRules = {
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 64, message: '密码长度 6~64 位', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入', trigger: 'blur' },
    {
      validator: (_rule, value: string, callback) => {
        if (value !== form.newPassword) {
          callback(new Error('两次输入不一致'))
          return
        }
        callback()
      },
      trigger: 'blur',
    },
  ],
}

watch(
  () => props.modelValue,
  (open) => {
    if (open) {
      form.newPassword = ''
      form.confirmPassword = ''
      formRef.value?.clearValidate()
    }
  },
)

async function submit() {
  if (!formRef.value || !props.user) {
    return
  }
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) {
    return
  }

  loading.value = true
  try {
    await resetPassword(props.user.id, form.newPassword)
    ElMessage.success('密码已重置，该用户的登录状态已失效')
    visible.value = false
    emit('saved')
  } catch {
    // 错误由拦截器提示
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <el-dialog v-model="visible" title="重置密码" width="420px">
    <el-alert type="warning" :closable="false" show-icon class="tip">
      <template #title>重置后该用户所有已登录设备会立即掉线，需要用新密码重新登录</template>
    </el-alert>

    <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
      <el-form-item label="用户">
        <span class="user">{{ user?.realName }}（{{ user?.username }}）</span>
      </el-form-item>
      <el-form-item label="新密码" prop="newPassword">
        <el-input v-model="form.newPassword" type="password" show-password placeholder="至少 6 位" />
      </el-form-item>
      <el-form-item label="确认密码" prop="confirmPassword">
        <el-input v-model="form.confirmPassword" type="password" show-password placeholder="再次输入" />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="loading" @click="submit">确认重置</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.tip {
  margin-bottom: 12px;
}

.user {
  color: var(--el-text-color-regular);
}
</style>
