import { del, download, get, patch, post, put, upload, type PageResult } from '@/utils/request'

export type QuestionType = 'SINGLE' | 'MULTIPLE' | 'JUDGE' | 'BLANK' | 'SHORT'

export const TYPE_OPTIONS: { value: QuestionType; label: string }[] = [
  { value: 'SINGLE', label: '单选题' },
  { value: 'MULTIPLE', label: '多选题' },
  { value: 'JUDGE', label: '判断题' },
  { value: 'BLANK', label: '填空题' },
  { value: 'SHORT', label: '简答题' },
]

export const DIFFICULTY_OPTIONS = [
  { value: 1, label: '易' },
  { value: 2, label: '中' },
  { value: 3, label: '难' },
]

export interface CategoryNode {
  id: number
  parentId: number
  name: string
  sortNo: number
  status: number
  questionCount: number
  children: CategoryNode[]
}

export interface CategoryPayload {
  parentId: number
  name: string
  sortNo?: number
  status?: number
}

export interface OptionItem {
  key: string
  content: string
}

export interface QuestionItem {
  id: number
  categoryId: number
  categoryName: string
  type: QuestionType
  typeLabel: string
  stem: string
  options: OptionItem[]
  answer?: string[]
  analysis?: string
  difficulty: number
  score: number
  status: number
  createdAt: string
}

export interface QuestionPayload {
  categoryId: number
  type: QuestionType
  stem: string
  options?: OptionItem[]
  answer: string[]
  analysis?: string
  difficulty: number
  /** 单位：分 */
  defaultScore: number
  status?: number
}

export interface QuestionQuery {
  page: number
  size: number
  categoryId?: number
  type?: string
  difficulty?: number
  status?: number
  keyword?: string
}

export interface ImportResult {
  total: number
  success: number
  failed: number
  elapsedMs: number
  errors: string[]
  errorFileUrl?: string
  errorFileKey?: string
}

// ---------------- 分类 ----------------

export function fetchCategories() {
  return get<CategoryNode[]>('/admin/categories')
}

export function createCategory(data: CategoryPayload) {
  return post<number>('/admin/categories', data)
}

export function updateCategory(id: number, data: CategoryPayload) {
  return put<void>(`/admin/categories/${id}`, data)
}

export function deleteCategory(id: number) {
  return del<void>(`/admin/categories/${id}`)
}

// ---------------- 题目 ----------------

export function fetchQuestions(query: QuestionQuery) {
  return get<PageResult<QuestionItem>>('/admin/questions', { ...query })
}

export function fetchQuestion(id: number) {
  return get<QuestionItem>(`/admin/questions/${id}`)
}

export function createQuestion(data: QuestionPayload) {
  return post<number>('/admin/questions', data)
}

export function updateQuestion(id: number, data: QuestionPayload) {
  return put<void>(`/admin/questions/${id}`, data)
}

export function deleteQuestion(id: number) {
  return del<void>(`/admin/questions/${id}`)
}

export function changeQuestionStatus(id: number, status: number) {
  return patch<void>(`/admin/questions/${id}/status`, undefined, { status })
}

export function importQuestions(file: File) {
  return upload<ImportResult>('/admin/questions/import', file)
}

export function downloadTemplate() {
  return download('/admin/questions/template')
}
