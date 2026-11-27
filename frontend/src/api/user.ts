import { del, get, patch, post, put, type PageResult } from '@/utils/request'
import type { RoleCode } from '@/api/auth'

export interface UserItem {
  id: number
  username: string
  realName: string
  role: RoleCode
  roleLabel: string
  status: number
  lastLoginAt?: string
  createdAt: string
}

export interface UserQuery {
  page: number
  size: number
  keyword?: string
  role?: string
  status?: number
}

export interface UserCreatePayload {
  username: string
  password: string
  realName: string
  role: RoleCode
}

export interface UserUpdatePayload {
  realName: string
  role: RoleCode
}

export function fetchUsers(query: UserQuery) {
  return get<PageResult<UserItem>>('/admin/users', { ...query })
}

export function createUser(data: UserCreatePayload) {
  return post<number>('/admin/users', data)
}

export function updateUser(id: number, data: UserUpdatePayload) {
  return put<void>(`/admin/users/${id}`, data)
}

export function resetPassword(id: number, newPassword: string) {
  return put<void>(`/admin/users/${id}/password`, { newPassword })
}

export function changeUserStatus(id: number, status: number) {
  return patch<void>(`/admin/users/${id}/status`, undefined, { status })
}

export interface BatchStatusResult {
  success: number
  failures: { userId: number; reason: string }[]
}

/** 批量启用/停用：逐条执行，返回跳过明细 */
export function batchChangeUserStatus(userIds: number[], status: number) {
  return put<BatchStatusResult>('/admin/users/batch/status', { userIds, status })
}

export function kickUser(id: number) {
  return post<void>(`/admin/users/${id}/kick`)
}

export function deleteUser(id: number) {
  return del<void>(`/admin/users/${id}`)
}

export const ROLE_OPTIONS: { value: RoleCode; label: string }[] = [
  { value: 'ADMIN', label: '管理员' },
  { value: 'TEACHER', label: '教师' },
  { value: 'STUDENT', label: '学生' },
]
