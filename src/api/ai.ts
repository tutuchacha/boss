/**
 * 聘聊 · AI service（Step 4 T-AI2）
 * =====================================================================
 * 对应接口：
 *  - GET  /api/v1/ai/status                              AI 开关与模型信息
 *  - POST /api/v1/hr/applications/{id}/ai/qa             单简历问答（带引用）
 *
 * 只读接口，不写入业务数据；AI 关闭时后端返回 42220，前端入口按 status.enabled 隐藏。
 * =====================================================================
 */

import type { AiQaAnswer, AiStatus } from '@/types/api'
import { USE_MOCK } from '@/config/env'
import { request } from './client'
import { MOCK_AI_STATUS, mockAskResumeQuestion } from './mock/ai'

/** 查询键：AI 开关（入口显隐依赖它） */
export const AI_STATUS_QUERY_KEY = ['ai', 'status'] as const

/** AI 开关与模型信息（后端只回显配置，不发起上游调用） */
export async function getAiStatus(): Promise<AiStatus> {
  if (USE_MOCK) return MOCK_AI_STATUS
  return request<AiStatus>('/ai/status', { method: 'GET' })
}

/**
 * 单简历问答。
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
