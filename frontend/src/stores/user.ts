import { defineStore } from 'pinia'
import { login as loginApi, logout as logoutApi, type RoleCode, type UserInfo } from '@/api/auth'
import { TOKEN_KEY } from '@/utils/request'

const USER_KEY = 'oes_user'

function readUser(): UserInfo | null {
  const raw = localStorage.getItem(USER_KEY)
  if (!raw) {
    return null
  }
  try {
    return JSON.parse(raw) as UserInfo
  } catch {
    return null
  }
}

interface UserState {
  token: string
  user: UserInfo | null
}

export const useUserStore = defineStore('user', {
  state: (): UserState => ({
    token: localStorage.getItem(TOKEN_KEY) ?? '',
    user: readUser(),
  }),

  getters: {
    isLogin: (state) => Boolean(state.token),
    role: (state): RoleCode | '' => state.user?.role ?? '',
    realName: (state) => state.user?.realName ?? '',
    isAdmin: (state) => state.user?.role === 'ADMIN',
    isTeacher: (state) => state.user?.role === 'TEACHER',
    isStudent: (state) => state.user?.role === 'STUDENT',
  },

  actions: {
    async login(username: string, password: string) {
      const result = await loginApi({ username, password })
      this.token = result.token
      this.user = result.user
      localStorage.setItem(TOKEN_KEY, result.token)
      localStorage.setItem(USER_KEY, JSON.stringify(result.user))
      return result
    },

    async logout() {
      try {
        await logoutApi()
      } finally {
        // 服务端注销失败也要清本地，否则用户会卡在"已登录但用不了"的状态
        this.clear()
      }
    },

    clear() {
      this.token = ''
      this.user = null
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem(USER_KEY)
    },
  },
})
