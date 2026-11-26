import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import type { RoleCode } from '@/api/auth'

declare module 'vue-router' {
  interface RouteMeta {
    /** 免登录 */
    public?: boolean
    /** 允许访问的角色，不写表示登录即可 */
    roles?: RoleCode[]
    title?: string
  }
}

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/login/LoginView.vue'),
    meta: { public: true, title: '登录' },
  },
  {
    path: '/',
    component: () => import('@/layout/AppLayout.vue'),
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'dashboard',
        component: () => import('@/views/dashboard/DashboardView.vue'),
        meta: { title: '首页' },
      },
      {
        path: 'questions',
        name: 'questions',
        component: () => import('@/views/question/QuestionListView.vue'),
        meta: { title: '题目管理', roles: ['TEACHER'] },
      },
      {
        path: 'papers',
        name: 'papers',
        component: () => import('@/views/paper/PaperListView.vue'),
        meta: { title: '试卷管理', roles: ['TEACHER'] },
      },
      {
        path: 'exams',
        name: 'exams',
        component: () => import('@/views/exam/ExamListView.vue'),
        meta: { title: '考试管理', roles: ['TEACHER'] },
      },
      {
        path: 'stats',
        name: 'stats',
        component: () => import('@/views/stat/StatListView.vue'),
        meta: { title: '记录统计', roles: ['TEACHER'] },
      },
      {
        path: 'users',
        name: 'users',
        component: () => import('@/views/user/UserListView.vue'),
        meta: { title: '用户管理', roles: ['ADMIN'] },
      },
      {
        path: 'my-exams',
        name: 'my-exams',
        component: () => import('@/views/student/MyExamListView.vue'),
        meta: { title: '我的考试', roles: ['STUDENT'] },
      },
      {
        path: 'exam-result/:attemptId',
        name: 'exam-result',
        component: () => import('@/views/student/AttemptResultView.vue'),
        meta: { title: '成绩详情', roles: ['STUDENT'] },
      },
    ],
  },
  {
    // 作答页单独成路由、不套主框架：考试期间不应该有侧边栏可以点走
    path: '/exam/:attemptId',
    name: 'take-exam',
    component: () => import('@/views/student/TakeExamView.vue'),
    meta: { title: '考试中', roles: ['STUDENT'] },
  },
  {
    path: '/403',
    name: 'forbidden',
    component: () => import('@/views/error/ForbiddenView.vue'),
    meta: { title: '无权访问' },
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'not-found',
    component: () => import('@/views/error/NotFoundView.vue'),
    meta: { public: true, title: '页面不存在' },
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

router.beforeEach((to) => {
  const userStore = useUserStore()

  if (to.meta.public) {
    // 已登录再进登录页，直接回首页
    if (to.path === '/login' && userStore.isLogin) {
      return { path: '/' }
    }
    return true
  }

  if (!userStore.isLogin) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }

  const roles = to.meta.roles
  if (roles && roles.length > 0) {
    // ADMIN 是超级角色，页面级同样放行；真正的权限仍由后端拦截器裁定
    if (userStore.role !== 'ADMIN' && !roles.includes(userStore.role as RoleCode)) {
      ElMessage.warning('当前角色无权访问该页面')
      return { path: '/403' }
    }
  }

  return true
})

router.afterEach((to) => {
  document.title = to.meta.title ? `${to.meta.title} - 在线考试系统` : '在线考试系统'
})

export default router
