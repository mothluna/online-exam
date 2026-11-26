import axios, { type AxiosInstance, type AxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'

/** 后端统一响应体，与 common/result/R.java 一一对应 */
export interface R<T> {
  code: number
  message: string
  data: T
  traceId?: string
}

export interface PageResult<T> {
  records: T[]
  total: number
  page: number
  size: number
}

const TOKEN_KEY = 'oes_token'

let unauthorizedHandler: (() => void) | null = null

/** 由 main.ts 注册，避免 request 直接依赖 router/store 形成循环引用 */
export function setUnauthorizedHandler(handler: () => void) {
  unauthorizedHandler = handler
}

const instance: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE ?? '/api',
  timeout: 20000,
})

instance.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_KEY)
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

instance.interceptors.response.use(
  (response) => {
    const body = response.data as R<unknown>
    // 文件流等非 JSON 响应直接透传
    if (body == null || typeof body.code !== 'number') {
      return response
    }
    if (body.code !== 0) {
      ElMessage.error(body.message || '请求失败')
      return Promise.reject(new Error(body.message))
    }
    return response
  },
  (error) => {
    const status = error.response?.status as number | undefined
    const message = (error.response?.data as R<unknown> | undefined)?.message

    if (status === 401) {
      // 401 一律回登录页：令牌无效、被注销、被踢下线都走这里
      unauthorizedHandler?.()
    } else if (status === 403) {
      ElMessage.error(message ?? '没有权限执行该操作')
    } else if (status === 429) {
      ElMessage.warning(message ?? '操作过于频繁，请稍后再试')
    } else {
      ElMessage.error(message ?? '网络异常，请稍后重试')
    }
    return Promise.reject(error)
  },
)

async function unwrap<T>(promise: Promise<{ data: R<T> | T }>): Promise<T> {
  const response = await promise
  const body = response.data as R<T>
  return (body && typeof body === 'object' && 'code' in body ? body.data : body) as T
}

export function get<T>(url: string, params?: Record<string, unknown>): Promise<T> {
  const config: AxiosRequestConfig = { params }
  return unwrap<T>(instance.get(url, config))
}

export function post<T>(url: string, data?: unknown): Promise<T> {
  return unwrap<T>(instance.post(url, data))
}

export function put<T>(url: string, data?: unknown): Promise<T> {
  return unwrap<T>(instance.put(url, data))
}

export function patch<T>(url: string, data?: unknown, params?: Record<string, unknown>): Promise<T> {
  return unwrap<T>(instance.patch(url, data, { params }))
}

export function del<T>(url: string): Promise<T> {
  return unwrap<T>(instance.delete(url))
}

/** 下载文件：返回 blob 与后端给出的文件名 */
export async function download(url: string, params?: Record<string, unknown>) {
  const response = await instance.get(url, { params, responseType: 'blob' })
  const disposition = response.headers['content-disposition'] as string | undefined
  let filename = 'export.xlsx'
  if (disposition) {
    const utf8 = disposition.match(/filename\*=UTF-8''([^;]+)/i)
    if (utf8) {
      filename = decodeURIComponent(utf8[1])
    }
  }
  return { blob: response.data as Blob, filename }
}

/** 上传单个文件；导入大文件耗时较长，单独放宽超时 */
export async function upload<T>(url: string, file: File, field = 'file'): Promise<T> {
  const form = new FormData()
  form.append(field, file)
  const response = await instance.post(url, form, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 180000,
  })
  return (response.data as R<T>).data
}

export type DownloadResult =
  | { mode: 'file'; blob: Blob; filename: string }
  | { mode: 'task'; taskId: string; rowCount: number }

/**
 * 下载接口有两种结果：后端小数据直接返回文件流，大数据返回 202 + JSON 任务。
 * 用 content-type 区分，而不是靠状态码（axios 对两者都当成功）。
 */
export async function downloadOrTask(
  url: string,
  params?: Record<string, unknown>,
): Promise<DownloadResult> {
  const response = await instance.get(url, { params, responseType: 'blob' })
  const contentType = String(response.headers['content-type'] ?? '')

  if (contentType.includes('application/json')) {
    const text = await (response.data as Blob).text()
    const body = JSON.parse(text) as R<{ taskId: string; rowCount: number }>
    return { mode: 'task', taskId: body.data.taskId, rowCount: body.data.rowCount }
  }

  const disposition = response.headers['content-disposition'] as string | undefined
  let filename = 'export.xlsx'
  if (disposition) {
    const utf8 = disposition.match(/filename\*=UTF-8''([^;]+)/i)
    if (utf8) {
      filename = decodeURIComponent(utf8[1])
    }
  }
  return { mode: 'file', blob: response.data as Blob, filename }
}

export { TOKEN_KEY }
