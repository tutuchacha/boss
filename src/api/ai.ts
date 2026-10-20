/**
 * 聘聊 · AI service（Step 4 T-AI2 非流式 / T-AI3 流式）
 * =====================================================================
 * 对应接口：
 *  - GET  /api/v1/ai/status                                   AI 开关与模型信息
 *  - POST /api/v1/hr/applications/{id}/ai/qa                   单简历问答（带引用，一次性返回）
 *  - POST /api/v1/hr/applications/{id}/ai/qa/stream            同上，SSE 流式（delta / citation / done / error）
 *
 * AI 关闭时后端返回 42220，前端入口按 status.enabled 隐藏。
 * 流式的**校验类错误**（42220 / 40301 / 42201 / 40401 / 42902）仍以普通 JSON 包络返回，
 * 只有「流开始之后」的失败才走 error 事件——所以先看响应头再决定读不读流。
 * =====================================================================
 */

import type { AiCitation, AiQaAnswer, AiStatus } from '@/types/api'
import { USE_MOCK } from '@/config/env'
import { ApiError, request, requestStream, toApiError } from './client'
import { MOCK_AI_STATUS, mockAskResumeQuestion, mockAskResumeQuestionStream } from './mock/ai'

/** 查询键：AI 开关（入口显隐依赖它） */
export const AI_STATUS_QUERY_KEY = ['ai', 'status'] as const

/** AI 开关与模型信息（后端只回显配置，不发起上游调用） */
export async function getAiStatus(): Promise<AiStatus> {
  if (USE_MOCK) return MOCK_AI_STATUS
  return request<AiStatus>('/ai/status', { method: 'GET' })
}

/**
 * 单简历问答（非流式）。
 * 约束：只基于该 HR 本公司可见投递的简历；无依据时后端返回固定话术且 citations 为空。
 */
export async function askResumeQuestion(
  applicationId: string,
  question: string,
): Promise<AiQaAnswer> {
  if (USE_MOCK) return mockAskResumeQuestion(applicationId, question)
  return request<AiQaAnswer>(`/hr/applications/${applicationId}/ai/qa`, {
    method: 'POST',
    body: { question },
  })
}

/** 流式回调：delta 追加文本，citation 逐条到达（均在 done 之前）。 */
export interface AiQaStreamHandlers {
  onDelta?: (text: string) => void
  onCitation?: (citation: AiCitation) => void
}

interface SseEvent {
  event: string
  data: string
}

/**
 * 单简历问答（流式，T-AI3）。
 *
 * <p>返回完整回答（与 {@link askResumeQuestion} 同结构），调用方以返回值为准覆盖已累积的文本：
 * 后端在模型漏标引用时会追加「来源：[1]」，这段兜底正文只出现在 done 事件里。</p>
 *
 * <p>容错：SSE 被代理缓冲 / 中途断开（未收到 done）时自动退回非流式接口，
 * 保证「打字机不可用 ≠ 问答不可用」。</p>
 */
export async function askResumeQuestionStream(
  applicationId: string,
  question: string,
  handlers: AiQaStreamHandlers = {},
  opts: { signal?: AbortSignal } = {},
): Promise<AiQaAnswer> {
  if (USE_MOCK) {
    return mockAskResumeQuestionStream(applicationId, question, handlers, opts.signal)
  }

  // 走本站根路径的 Route Handler 代理（`src/app/api/ai/qa-stream/[applicationId]/route.ts`）：
  // next.config.ts 的 rewrites 会缓冲 SSE，导致打字机退化成「等完再一次吐出」。
  // Accept 必须同时带上 application/json：校验类错误是普通 JSON 包络，只接受 text/event-stream 时
  // Spring 无法把错误正文写出去（406 → 500 空响应）。
  const res = await requestStream(`/api/ai/qa-stream/${applicationId}`, {
    method: 'POST',
    body: { question },
    signal: opts.signal,
    headers: { Accept: 'text/event-stream, application/json' },
  })

  const contentType = res.headers.get('content-type') ?? ''
  if (!res.ok || !contentType.includes('text/event-stream') || !res.body) {
    throw await toApiError(res)
  }

  try {
    for await (const evt of readSseEvents(res.body)) {
      if (evt.event === 'delta') {
        const payload = JSON.parse(evt.data) as { text?: string }
        if (payload.text) handlers.onDelta?.(payload.text)
      } else if (evt.event === 'citation') {
        handlers.onCitation?.(JSON.parse(evt.data) as AiCitation)
      } else if (evt.event === 'done') {
        return JSON.parse(evt.data) as AiQaAnswer
      } else if (evt.event === 'error') {
        const payload = JSON.parse(evt.data) as { code: number; message: string }
        throw new ApiError({ code: payload.code, message: payload.message, status: 0 })
      }
    }
  } catch (err) {
    if (err instanceof ApiError || (err as { name?: string } | null)?.name === 'AbortError') {
      throw err
    }
    // 解析失败 / 流被截断：走下面的非流式兜底
  }

  // 兜底：没有收到 done（代理缓冲、连接中断等）→ 非流式接口再取一次
  return askResumeQuestion(applicationId, question)
}

/** 解析 SSE 字节流（按空行分帧；`event:` 缺省为 message，`data:` 多行拼接）。 */
async function* readSseEvents(body: ReadableStream<Uint8Array>): AsyncGenerator<SseEvent> {
  const reader = body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  try {
    for (;;) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      buffer = buffer.replace(/\r\n/g, '\n')
      for (;;) {
        const sep = buffer.indexOf('\n\n')
        if (sep < 0) break
        const block = buffer.slice(0, sep)
        buffer = buffer.slice(sep + 2)
        const evt = parseSseBlock(block)
        if (evt) yield evt
      }
    }
    const tail = parseSseBlock(buffer)
    if (tail) yield tail
  } finally {
    reader.releaseLock()
  }
}

function parseSseBlock(block: string): SseEvent | null {
  if (!block.trim()) return null
  let event = 'message'
  const data: string[] = []
  for (const line of block.split('\n')) {
    if (!line || line.startsWith(':')) continue
    if (line.startsWith('event:')) {
      event = line.slice(6).trim()
    } else if (line.startsWith('data:')) {
      data.push(line.slice(5).replace(/^ /, ''))
    }
  }
  if (data.length === 0) return null
  return { event, data: data.join('\n') }
}
