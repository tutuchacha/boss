/**
 * 聘聊 · API 统一客户端
 * =====================================================================
 * 职责：
 *  - 统一 fetch 封装（鉴权头、超时、错误转换）
 *  - 解包统一响应包络 { code, message, data, traceId }
 *  - 抛出业务异常（含 code/message/字段错误），由调用方或 TanStack Query 处理
 *
 * 当前 Step 1 阶段：USE_MOCK=true，service 层直接返回 mock 数据，不经过此 client。
 * 联调阶段：USE_MOCK=false，service 层走此 client。
 * =====================================================================
 */

import type { ApiEnvelope, FieldError } from '@/types/api'
import { API_BASE_URL, USE_MOCK } from '@/config/env'

/** 业务异常 */
export class ApiError extends Error {
  code: number
  status: number
  errors?: FieldError[]
  traceId?: string

  constructor(opts: {
    code: number
    message: string
    status?: number
    errors?: FieldError[]
    traceId?: string
  }) {
    super(opts.message)
    this.name = 'ApiError'
    this.code = opts.code
    this.status = opts.status ?? 0
    this.errors = opts.errors
    this.traceId = opts.traceId
  }
}

/** 从 localStorage 取 accessToken（联调时启用） */
function getToken(): string | null {
  if (typeof window === 'undefined') return null
  return window.localStorage.getItem('pinliao.accessToken')
}

interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'
  query?: Record<string, string | number | boolean | undefined | null>
  body?: unknown
  signal?: AbortSignal
  /** 不带 Authorization 头（如登录接口） */
  noAuth?: boolean
  /** 自定义 headers */
  headers?: Record<string, string>
}

function buildUrl(path: string, query?: RequestOptions['query']): string {
  // 以 `/api/` 开头的路径视为**站点根路径**（如 T-AI3 的 SSE 代理 `/api/ai/qa-stream/...`），
  // 不拼 API_BASE_URL；其余路径按 API_BASE_URL（默认 /api/v1）拼接。
  const target =
    path.startsWith('http') || path.startsWith('/api/') ? path : `${API_BASE_URL}${path}`
  const url = new URL(
    target,
    typeof window === 'undefined' ? 'http://localhost' : window.location.origin,
  )
  if (query) {
    for (const [k, v] of Object.entries(query)) {
      if (v === undefined || v === null || v === '') continue
      url.searchParams.set(k, String(v))
    }
  }
  // 仅返回相对路径，避免跨域
  return `${url.pathname}${url.search}`
}

/**
 * 通用请求方法。返回 envelope.data（已解包）。
 * 失败时抛出 ApiError，由调用方捕获。
 */
export async function request<T = unknown>(
  path: string,
  opts: RequestOptions = {},
): Promise<T> {
  if (USE_MOCK) {
    // Mock 模式下不应走到这里；service 层已自行返回 mock 数据。
    // 此处仅做兜底，便于发现遗漏。
    throw new ApiError({
      code: 50000,
      message: `Mock 模式下不应走 client.request：${path}`,
      status: 500,
    })
  }

  const res = await send(path, opts, 'application/json')

  let envelope: ApiEnvelope<T> | null = null
  try {
    envelope = (await res.json()) as ApiEnvelope<T>
  } catch {
    throw new ApiError({
      code: 50000,
      message: `响应不是合法 JSON：HTTP ${res.status}`,
      status: res.status,
    })
  }

  if (!envelope) {
    throw new ApiError({
      code: 50000,
      message: '空响应',
      status: res.status,
    })
  }

  if (envelope.code !== 0) {
    throw new ApiError({
      code: envelope.code,
      message: envelope.message,
      status: res.status,
      errors: envelope.errors,
      traceId: envelope.traceId,
    })
  }

  return envelope.data
}

/** 发起请求（统一带鉴权头 / JSON body），返回原始 Response，不解包。 */
function send(path: string, opts: RequestOptions, accept: string): Promise<Response> {
  const method = opts.method ?? 'GET'
  const url = buildUrl(path, opts.query)
  const headers: Record<string, string> = {
    Accept: accept,
    ...(opts.headers ?? {}),
  }
  if (opts.body !== undefined) {
    headers['Content-Type'] = 'application/json'
  }
  if (!opts.noAuth) {
    const token = getToken()
    if (token) headers.Authorization = `Bearer ${token}`
  }
  return fetch(url, {
    method,
    headers,
    body: opts.body !== undefined ? JSON.stringify(opts.body) : undefined,
    signal: opts.signal,
    credentials: 'same-origin',
  })
}

/**
 * 流式请求（SSE，Step 4 T-AI3）：返回原始 Response，由调用方读 ReadableStream。
 *
 * <p>为什么不复用 {@link request}：SSE 响应不是 JSON 包络，且必须边到边读。
 * 校验类错误仍由后端返回普通 JSON 包络（非 2xx），调用方用 {@link toApiError} 转换。</p>
 */
export function requestStream(path: string, opts: RequestOptions = {}): Promise<Response> {
  if (USE_MOCK) {
    throw new ApiError({
      code: 50000,
      message: `Mock 模式下不应走 client.requestStream：${path}`,
      status: 500,
    })
  }
  return send(path, opts, 'text/event-stream')
}

/** 把失败响应（JSON 包络）转成 {@link ApiError}；无法解析时按 HTTP 状态兜底。 */
export async function toApiError(res: Response): Promise<ApiError> {
  try {
    const envelope = (await res.json()) as ApiEnvelope<unknown>
    if (envelope && typeof envelope.code === 'number' && envelope.code !== 0) {
      return new ApiError({
        code: envelope.code,
        message: envelope.message,
        status: res.status,
        errors: envelope.errors,
        traceId: envelope.traceId,
      })
    }
  } catch {
    // 非 JSON（如代理层 502）：落到下面的 HTTP 兜底
  }
  return new ApiError({
    code: 50000,
    message: `请求失败：HTTP ${res.status}`,
    status: res.status,
  })
}
