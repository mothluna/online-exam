import { del, get, post, put, type PageResult } from '@/utils/request'
import type { QuestionType } from '@/api/question'

export type ExamStatus = 'DRAFT' | 'PUBLISHED'
export type ExamState = 'NOT_STARTED' | 'IN_PROGRESS' | 'SUBMITTED' | 'ENDED'
export type AttemptStatus = 'IN_PROGRESS' | 'SUBMITTED' | 'TIMEOUT' | 'GRADED'

export interface ExamItem {
  id: number
  paperId: number
  paperTitle: string
  title: string
  startTime: string
  endTime: string
  durationSec: number
  allowRetake: number
  shuffle: number
  status: ExamStatus
  totalScore: number
  totalCount: number
  attemptCount?: number
}

export interface ExamPayload {
  paperId: number
  title: string
  startTime: string
  endTime: string
  durationSec: number
  allowRetake: number
  shuffle: number
}

export interface StudentExamItem {
  id: number
  title: string
  startTime: string
  endTime: string
  durationSec: number
  totalScore: number
  totalCount: number
  state: ExamState
  attemptId?: number
}

export interface AttemptQuestion {
  questionId: number
  type: QuestionType
  typeLabel: string
  stem: string
  options: { key: string; content: string }[]
  score: number
  sortNo: number
}

export interface AttemptAnswer {
  questionId: number
  answer: string[]
}

export interface Attempt {
  attemptId: number
  examId: number
  examTitle: string
  status: AttemptStatus
  startTime: string
  /** 后端计算的截止时间，前端倒计时以它为准 */
  deadline: string
  /** 服务端当前时间，用于校正本机时钟偏差 */
  serverTime: string
  totalScore: number
  totalCount: number
  questions: AttemptQuestion[]
  answers: AttemptAnswer[]
}

export interface SubmitResult {
  attemptId: number
  status: AttemptStatus
  submitTime: string
  totalScore: number
  isPass: number
  needReview: boolean
}

// ---------------- 教师端 ----------------

export function fetchExams(params: { page: number; size: number; status?: string; keyword?: string }) {
  return get<PageResult<ExamItem>>('/admin/exams', { ...params })
}

export function fetchExam(id: number) {
  return get<ExamItem>(`/admin/exams/${id}`)
}

export function createExam(data: ExamPayload) {
  return post<number>('/admin/exams', data)
}

export function updateExam(id: number, data: ExamPayload) {
  return put<void>(`/admin/exams/${id}`, data)
}

export function publishExam(id: number) {
  return post<void>(`/admin/exams/${id}/publish`)
}

export function deleteExam(id: number) {
  return del<void>(`/admin/exams/${id}`)
}

// ---------------- 学生端 ----------------

export function fetchMyExams() {
  return get<StudentExamItem[]>('/student/exams')
}

export function startExam(examId: number) {
  return post<Attempt>(`/student/exams/${examId}/start`)
}

export function fetchAttempt(attemptId: number) {
  return get<Attempt>(`/student/attempts/${attemptId}`)
}

export function saveAnswers(attemptId: number, items: { questionId: number; answer: string[] }[]) {
  return put<void>(`/student/attempts/${attemptId}/answers`, { items })
}

export function submitAttempt(attemptId: number) {
  return post<SubmitResult>(`/student/attempts/${attemptId}/submit`)
}
