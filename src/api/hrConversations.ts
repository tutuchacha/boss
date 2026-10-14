/**
 * 聘聊 · HR 端会话 service
 * =====================================================================
 * 与 docs/api-endpoints.md §8 对应（HR 视角）：
 *  - GET  /api/v1/conversations                     本公司在招职位的会话
 *  - GET  /api/v1/conversations/{id}/messages       消息列表
 *  - POST /api/v1/conversations/{id}/messages       以 HR 身份发送
 *  - POST /api/v1/conversations/{id}/read           标记 HR 已读
 *
 * Mock 阶段直接操作 src/api/mock/hrConversations.ts；联调阶段切到 request()。
 * =====================================================================
 */

import type { Message } from '@/types/api'
import { USE_MOCK } from '@/config/env'
import { request } from './client'
import type { HrConversationView } from './mock/hrConversations'
import {
  appendCandidateMessage,
  appendHrMessage,
  hrConversationById,
  hrConversationsOf,
  hrMessagesOf,
  markHrConversationRead,
  mockCandidateReply,
} from './mock/hrConversations'

export type { HrConversationView }

/** 会话列表（HR 所在公司） */
export async function listHrConversations(
  companyId: string,
): Promise<HrConversationView[]> {
  if (USE_MOCK) return hrConversationsOf(companyId)
  return request<HrConversationView[]>('/conversations', { method: 'GET' })
}

/** 单个会话 */
export async function getHrConversation(
  conversationId: string,
): Promise<HrConversationView | undefined> {
  if (USE_MOCK) return hrConversationById(conversationId)
  return request<HrConversationView>(`/conversations/${conversationId}`)
}

/** 消息列表 */
export async function listHrMessages(conversationId: string): Promise<Message[]> {
  if (USE_MOCK) return hrMessagesOf(conversationId)
  return request<Message[]>(`/conversations/${conversationId}/messages`)
}

/**
 * 以 HR 身份发送消息，并触发一次候选人自动回复（Mock 演示用）。
 * @returns 本条消息
 */
export async function sendHrMessage(
  conversationId: string,
  content: string,
  hrId: string,
): Promise<Message> {
  if (USE_MOCK) {
    const msg = appendHrMessage(conversationId, content.trim(), hrId)
    return msg
  }
  return request<Message>(`/conversations/${conversationId}/messages`, {
    method: 'POST',
    body: { type: 'TEXT', content },
  })
}

/** 让候选人回一条（Mock 专用，供 UI 模拟"对方正在输入"后的回复） */
export async function replyAsCandidate(
  conversationId: string,
  userText: string,
): Promise<Message> {
  if (USE_MOCK) return appendCandidateMessage(conversationId, mockCandidateReply(userText))
  throw new Error('replyAsCandidate 仅 Mock 阶段可用')
}

/** 标记 HR 已读 */
export async function markHrRead(conversationId: string): Promise<void> {
  if (USE_MOCK) {
    markHrConversationRead(conversationId)
    return
  }
  return request<void>(`/conversations/${conversationId}/read`, { method: 'POST' })
}

/** 未读会话总数（用于 Tab 角标） */
export function unreadConversationCount(views: HrConversationView[]): number {
  return views.reduce((a, v) => a + v.conversation.hrUnreadCount, 0)
}
