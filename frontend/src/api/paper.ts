import { del, get, post, put, type PageResult } from '@/utils/request'
import type { QuestionType } from '@/api/question'

export type ComposeType = 'RANDOM' | 'MANUAL'
export type PaperStatus = 'DRAFT' | 'PUBLISHED'

export interface PaperRule {
  type: QuestionType
  typeLabel?: string
  questionCount: number
  /** 每题分值（分） */
  scorePerItem: number
  categoryIds: number[]
  difficulty: number | null
}

export interface PaperItem {
  id: number
  title: string
  composeType: ComposeType
  /** 分 */
  totalScore: number
  totalCount: number
  passScore: number
  durationSec: number
  status: PaperStatus
  createdAt: string
  rules: PaperRule[]
  /** 实际入库的题数与总分 */
  actualCount?: number
  actualScore?: number
}

export interface PaperQuestionItem {
  questionId: number
  type: QuestionType
  typeLabel: string
  stem: string
  score: number
  sortNo: number
}

export interface PaperRulePayload {
  type: QuestionType
  questionCount: number
  scorePerItem: number
  categoryIds?: number[]
  difficulty?: number
}

export interface PaperPayload {
  title: string
  composeType: ComposeType
  /** 设定总分，仅 RANDOM 必填；MANUAL 由题目汇总 */
  totalScore?: number
  durationSec: number
  passScore: number
  rules?: PaperRulePayload[]
}

export function fetchPapers(params: {
  page: number
  size: number
  status?: string
  keyword?: string
}) {
  return get<PageResult<PaperItem>>('/admin/papers', { ...params })
}

export function fetchPaper(id: number) {
  return get<PaperItem>(`/admin/papers/${id}`)
}

export function createPaper(data: PaperPayload) {
  return post<number>('/admin/papers', data)
}

export function updatePaper(id: number, data: PaperPayload) {
  return put<void>(`/admin/papers/${id}`, data)
}

export function deletePaper(id: number) {
  return del<void>(`/admin/papers/${id}`)
}

export function randomizePaper(id: number) {
  return post<{ totalCount: number; totalScore: number; elapsedMs: number }>(
    `/admin/papers/${id}/randomize`,
  )
}

export function fetchPaperQuestions(id: number) {
  return get<PaperQuestionItem[]>(`/admin/papers/${id}/questions`)
}

export function addPaperQuestions(id: number, items: { questionId: number; score?: number }[]) {
  return post<void>(`/admin/papers/${id}/questions`, { items })
}

export function removePaperQuestion(id: number, questionId: number) {
  return del<void>(`/admin/papers/${id}/questions/${questionId}`)
}

export function publishPaper(id: number) {
  return post<void>(`/admin/papers/${id}/publish`)
}
