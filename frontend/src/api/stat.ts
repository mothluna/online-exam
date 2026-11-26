import { downloadOrTask, get, type DownloadResult, type PageResult } from '@/utils/request'

export interface ExamStat {
  examId: number
  title: string
  status: string
  startTime: string
  endTime: string
  paperTotalScore: number
  joinCount: number
  submitCount: number
  avgScore: number | null
  maxScore: number | null
  minScore: number | null
  passCount: number
  passRate: number
}

export interface AttemptStat {
  attemptId: number
  userId: number
  realName: string
  status: string
  totalScore: number
  isPass: number
  submitTime: string
  durationSec: number
}

export interface ScoreBucket {
  label: string
  count: number
}

export interface ExportTask {
  taskId: string
  status: 'PENDING' | 'RUNNING' | 'DONE' | 'FAILED'
  rowCount: number
  url?: string
  message?: string
  createdAt: string
}

export function fetchExamStats(params: {
  page: number
  size: number
  keyword?: string
  status?: string
}) {
  return get<PageResult<ExamStat>>('/admin/stats/exams', { ...params })
}

export function fetchAttemptStats(examId: number, page: number, size: number) {
  return get<PageResult<AttemptStat>>(`/admin/stats/exams/${examId}/attempts`, { page, size })
}

export function fetchDistribution(examId: number) {
  return get<ScoreBucket[]>(`/admin/stats/exams/${examId}/distribution`)
}

/** 导出：小数据直接拿 blob，大数据拿 taskId */
export function exportExam(examId: number): Promise<DownloadResult> {
  return downloadOrTask(`/admin/stats/exams/${examId}/export`)
}

export function fetchExportTask(taskId: string) {
  return get<ExportTask>(`/admin/stats/export-tasks/${taskId}`)
}
