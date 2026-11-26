<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import type { RoleCode } from '@/api/auth'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

interface MenuItem {
  path: string
  label: string
  icon: string
  roles?: RoleCode[]
}

const allMenus: MenuItem[] = [
  { path: '/dashboard', label: '首页', icon: 'HomeFilled' },
  { path: '/questions', label: '题目管理', icon: 'Document', roles: ['TEACHER'] },
  { path: '/papers', label: '试卷管理', icon: 'Files', roles: ['TEACHER'] },
  { path: '/exams', label: '考试管理', icon: 'Calendar', roles: ['TEACHER'] },
  { path: '/stats', label: '记录统计', icon: 'DataAnalysis', roles: ['TEACHER'] },
  { path: '/users', label: '用户管理', icon: 'User', roles: ['ADMIN'] },
  { path: '/my-exams', label: '我的考试', icon: 'EditPen', roles: ['STUDENT'] },
]

const menus = computed(() =>
  allMenus.filter((item) => {
    if (!item.roles) {
      return true
    }
    // 管理员与后端一致：视为超级角色，教师端菜单也可见
    return userStore.isAdmin || item.roles.includes(userStore.role as RoleCode)
  }),
)

const currentTitle = computed(() => {
  const matched = menus.value.find((item) => item.path === route.path)
  return matched?.label ?? (route.meta.title as string | undefined) ?? ''
})

const roleLabel = computed(() => {
  switch (userStore.role) {
    case 'ADMIN':
      return '管理员'
    case 'TEACHER':
      return '教师'
    case 'STUDENT':
      return '学生'
    default:
      return ''
  }
})

async function handleLogout() {
  await ElMessageBox.confirm('确定要退出登录吗？', '提示', {
    type: 'warning',
    confirmButtonText: '退出',
    cancelButtonText: '取消',
  }).catch(() => false)

  await userStore.logout()
  router.replace('/login')
}
</script>

<template>
  <el-container class="layout">
    <el-aside width="210px" class="aside">
      <div class="logo">
        <el-icon :size="20"><Reading /></el-icon>
        <span>在线考试系统</span>
      </div>
      <el-menu :default-active="route.path" router class="menu">
        <el-menu-item v-for="item in menus" :key="item.path" :index="item.path">
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.label }}</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header height="56px" class="header">
        <div class="title">{{ currentTitle }}</div>
        <el-dropdown @command="handleLogout">
          <span class="user">
            {{ userStore.realName }}
            <el-tag size="small" effect="plain" class="role-tag">{{ roleLabel }}</el-tag>
            <el-icon><ArrowDown /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="logout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </el-header>

      <el-main class="main">
        <router-view v-slot="{ Component }">
          <component :is="Component" />
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.layout {
  height: 100vh;
}

.aside {
  background: #1f2d3d;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.logo {
  height: 56px;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 18px;
  color: #fff;
  font-size: 15px;
  font-weight: 600;
  letter-spacing: 0.5px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.menu {
  border-right: none;
  background: transparent;
  flex: 1;
}

.menu :deep(.el-menu-item) {
  color: rgba(255, 255, 255, 0.75);
}

.menu :deep(.el-menu-item:hover) {
  background: rgba(255, 255, 255, 0.06);
  color: #fff;
}

.menu :deep(.el-menu-item.is-active) {
  background: var(--el-color-primary);
  color: #fff;
}

.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  border-bottom: 1px solid var(--el-border-color-light);
}

.title {
  font-size: 16px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.user {
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  color: var(--el-text-color-regular);
  outline: none;
}

.role-tag {
  margin-left: 2px;
}

.main {
  background: var(--el-bg-color-page);
  padding: 16px;
  overflow-y: auto;
}
</style>
