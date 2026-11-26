<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { ROLE_OPTIONS, createUser, updateUser, type UserItem } from '@/api/user'
import type { RoleCode } from '@/api/auth'

const props = defineProps<{
  modelValue: boolean
  /** 有值表示编辑 */
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
const isEdit = computed(() => Boolean(props.user))
const title = computed(() => (isEdit.value ? '编辑用户' : '新增用户'))

const form = reactive({
  username: '',
  password: '',
  realName: '',
  role: 'STUDENT' as RoleCode,
})

const rules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { pattern: /^[a-zA-Z0-9_]{3,20}$/, message: '3~20 位字母、数字或下划线', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入初始密码', trigger: 'blur' },
    { min: 6, max: 64, message: '密码长度 6~64 位', trigger: 'blur' },
  ],
  realName: [
    { required: true, message: '请输入姓名', trigger: 'blur' },
    { max: 50, message: '不超过 50 字', trigger: 'blur' },
  ],
  role: [{ required: true, message: '请选择角色', trigger: 'change' }],
}

watch(
  () => props.modelValue,
  (open) => {
    if (!open) {
      return
    }
    form.username = props.user?.username ?? ''
    form.realName = props.user?.realName ?? ''
    form.role = props.user?.role ?? 'STUDENT'
    form.password = ''
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
    if (props.user) {
      // 改角色会让该用户全部令牌失效，后端已处理，这里只需提示
      const roleChanged = props.user.role !== form.role
      await updateUser(props.user.id, { realName: form.realName.trim(), role: form.role })
      ElMessage.success(roleChanged ? '已更新，该用户需重新登录' : '已更新')
    } else {
      await createUser({
        username: form.username.trim(),
        password: form.password,
        realName: form.realName.trim(),
        role: form.role,
      })
      ElMessage.success('用户已创建')
    }
    visible.value = false
    emit('saved')
  } catch {
    // 用户名重复等错误由拦截器提示
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <el-dialog v-model="visible" :title="title" width="460px" @closed="formRef?.resetFields()">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
      <el-form-item label="用户名" prop="username">
        <el-input v-model="form.username" :disabled="isEdit" placeholder="登录账号，创建后不可改" />
      </el-form-item>

      <el-form-item v-if="!isEdit" label="初始密码" prop="password">
        <el-input v-model="form.password" type="password" show-password placeholder="至少 6 位" />
      </el-form-item>

      <el-form-item label="姓名" prop="realName">
        <el-input v-model="form.realName" maxlength="50" placeholder="真实姓名" />
      </el-form-item>

      <el-form-item label="角色" prop="role">
        <el-select v-model="form.role" style="width: 100%">
          <el-option v-for="item in ROLE_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </el-form-item>

      <el-alert v-if="isEdit" type="warning" :closable="false" show-icon>
        <template #title>修改角色会使该用户已登录的会话立即失效，需要重新登录</template>
      </el-alert>
    </el-form>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="loading" @click="submit">保存</el-button>
    </template>
  </el-dialog>
</template>
