import { get, put } from '@/utils/request'

export interface AttemptReviewAnswer {
  answerId: number
  questionId: number
  sortNo: number
  type: string
  typeLabel: string
  stem: string
  options: { key: string; content: string }[]
  myAnswer: string[]
  correctAnswerText?: string | null
  score: number
  fullScore: number
  judgeType: string
  judgeDetail?: string | null
}

export interface AttemptDetail {
  attemptId: number
  examId: number
  examTitle: string
  userId: number
  username: string
  realName: string
  status: string
  submitTime: string
  objectiveScore: number
  subjectiveScore: number
  totalScore: number
  passScore: number
  isPass: number
  needReview: boolean
  pendingReviewCount: number
  answers: AttemptReviewAnswer[]
}

export interface ReviewResult {
  attemptId: number
  subjectiveScore: number
  totalScore: number
  isPass: number
  pendingReviewCount: number
  needReview: boolean
}

export function fetchAttemptDetail(attemptId: number) {
  return get<AttemptDetail>(`/admin/attempts/${attemptId}/detail`)
}

export function reviewAnswer(answerId: number, data: { score: number; comment?: string }) {
  return put<ReviewResult>(`/admin/answers/${answerId}/review`, data)
}
