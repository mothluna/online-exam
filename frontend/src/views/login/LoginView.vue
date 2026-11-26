<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const formRef = ref<FormInstance>()
const loading = ref(false)

const form = reactive({
  username: '',
  password: '',
})

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

const demoAccounts = [
  { label: '管理员', username: 'admin' },
  { label: '教师', username: 'teacher' },
  { label: '学生', username: 'student' },
]

function fillDemo(username: string) {
  form.username = username
  form.password = '123456'
}

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
    const result = await userStore.login(form.username, form.password)
    ElMessage.success(`欢迎，${result.user.realName}`)
    const redirect = route.query.redirect as string | undefined
    if (redirect) {
      router.replace(redirect)
    } else {
      // 学生进考试列表，教师/管理员进首页，省一次点击
      router.replace(result.user.role === 'STUDENT' ? '/my-exams' : '/dashboard')
    }
  } catch {
    // 错误提示由请求拦截器统一弹出
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <div class="login-card">
      <div class="brand">
        <h1>在线考试系统</h1>
        <p>Online Examination System</p>
      </div>

      <el-form ref="formRef" :model="form" :rules="rules" size="large" @keyup.enter="submit">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="用户名" :prefix-icon="'User'" clearable />
        </el-form-item>
        <el-form-item prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="密码"
            :prefix-icon="'Lock'"
            show-password
          />
        </el-form-item>
        <el-button type="primary" class="submit" :loading="loading" @click="submit">登 录</el-button>
      </el-form>

      <div class="demo">
        <span class="demo-label">演示账号（密码 123456）</span>
        <div class="demo-list">
          <el-button v-for="acc in demoAccounts" :key="acc.username" text size="small" @click="fillDemo(acc.username)">
            {{ acc.label }}
          </el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #1f2d3d 0%, #2f4b6e 100%);
}

.login-card {
  width: 380px;
  padding: 40px 36px 28px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.25);
}

.brand {
  text-align: center;
  margin-bottom: 28px;
}

.brand h1 {
  margin: 0;
  font-size: 22px;
  color: #1f2d3d;
  letter-spacing: 1px;
}

.brand p {
  margin: 6px 0 0;
  font-size: 12px;
  color: #909399;
  letter-spacing: 1px;
}

.submit {
  width: 100%;
  margin-top: 4px;
}

.demo {
  margin-top: 22px;
  padding-top: 16px;
  border-top: 1px dashed #ebeef5;
  text-align: center;
}

.demo-label {
  font-size: 12px;
  color: #909399;
}

.demo-list {
  display: flex;
  justify-content: center;
  gap: 4px;
  margin-top: 6px;
}
</style>
