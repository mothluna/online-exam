import { get, post } from '@/utils/request'

export type RoleCode = 'ADMIN' | 'TEACHER' | 'STUDENT'

export interface UserInfo {
  id: number
  username: string
  realName: string
  role: RoleCode
}

export interface LoginResult {
  token: string
  tokenType: string
  expiresIn: number
  user: UserInfo
}

export function login(data: { username: string; password: string }) {
  return post<LoginResult>('/auth/login', data)
}

export function logout() {
  return post<void>('/auth/logout')
}

export function fetchMe() {
  return get<UserInfo>('/auth/me')
}
