/**
 * 聘聊 · 会话与消息 service
 * =====================================================================
 * 与 docs/api-endpoints.md §8 对应：
 *  - GET    /api/v1/conversations                     会话列表
 *  - GET    /api/v1/conversations/{id}/messages        消息列表
 *  - POST   /api/v1/conversations                      发起会话
 *  - POST   /api/v1/conversations/{id}/messages        发送消息
 *  - POST   /api/v1/conversations/{id}/read            标记已读
 *  - POST   /api/v1/conversations/{id}/typing          通知正在输入
 *  - POST   /api/v1/conversations/{id}/actions/...     快捷操作
 *  - POST   /api/v1/conversations/{id}/close           关闭会话
 *
 * Mock 实现：
 *  - 所有会话与消息存在 Zustand store 内存中（+ localStorage 持久化）
 *  - 发送消息后用 setTimeout 模拟 HR 自动回复 + "对方正在输入"
 *  - 联调阶段切到真实 WebSocket，组件无感
 * =====================================================================
 */

import type {
  Conversation,
  ConversationListItem,
  Message,
  SendMessageInput,
} from '@/types/api'
import { MessageFromEnum, MessageTypeEnum } from '@/types/api'
import { USE_MOCK } from '@/config/env'
import { request } from './client'
import { jobById, seedConversations, mockHrReply, TYPING_DELAY_MS } from './mock'

/** 会话列表项（含末条消息 + HR/公司信息） */
export interface ConversationListContext {
  conversations: Conversation[]
  messagesByConv: Record<string, Message[]>
}

/** 会话列表（求职者视角） */
export async function listConversations(
  ctx: ConversationListContext,
): Promise<ConversationListItem[]> {
  if (USE_MOCK) {
    const items = ctx.conversations
      .slice()
      .sort((a, b) => new Date(b.lastMessageAt).getTime() - new Date(a.lastMessageAt).getTime())
      .map(c => {
        const job = jobById(c.jobId)
        const msgs = ctx.messagesByConv[c.id] ?? []
        const last = msgs[msgs.length - 1]
        return {
          conversation: c,
          job: job
            ? { id: job.id, title: job.title, city: job.city, area: job.area, salary: job.salary }
            : { id: c.jobId, title: '职位已下线', city: '', area: '', salary: '' },
          hr: { id: c.hrId, name: 'HR', title: '' }, // mock 中 HR 名通过 job 推断，简化处理
          company: { id: c.companyId, name: job?.company.name ?? '' },
          lastMessage: last,
        } satisfies ConversationListItem
      })
    return items
  }
  return request<ConversationListItem[]>('/conversations')
}

/** 会话消息列表 */
export async function listMessages(
  conversationId: string,
  ctx: ConversationListContext,
): Promise<Message[]> {
  if (USE_MOCK) {
    return ctx.messagesByConv[conversationId] ?? []
  }
  return request<Message[]>(`/conversations/${conversationId}/messages`)
}

/** 获取种子会话与消息（应用初始化时调用） */
export function getSeedConversations(): ConversationListContext {
  const conversations: Conversation[] = []
  const messagesByConv: Record<string, Message[]> = {}
  for (const sc of seedConversations) {
    conversations.push(sc.conversation)
    messagesByConv[sc.conversation.id] = sc.messages.slice()
  }
  return { conversations, messagesByConv }
}

/** "对方正在输入"回调类型 */
export type TypingListener = (conversationId: string, isTyping: boolean) => void

const typingListeners = new Set<TypingListener>()
export function onTyping(listener: TypingListener): () => void {
  typingListeners.add(listener)
  return () => typingListeners.delete(listener)
}
function emitTyping(conversationId: string, isTyping: boolean) {
  typingListeners.forEach(fn => fn(conversationId, isTyping))
}

/** 发送消息（Mock 模式下同步处理并触发自动回复） */
export async function sendMessage(
  conversationId: string,
  input: SendMessageInput,
  ctx: ConversationListContext,
  mutate: (fn: (ctx: ConversationListContext) => void) => void,
): Promise<Message> {
  if (USE_MOCK) {
    const conv = ctx.conversations.find(c => c.id === conversationId)
    if (!conv) throw new Error(`会话不存在：${conversationId}`)
    const jobLegacyId = Number(conv.jobId.replace('job-', ''))
    const userText = input.type === MessageTypeEnum.TEXT ? input.content : input.type === MessageTypeEnum.RESUME ? '简历' : ''

    const msg: Message = {
      id: `msg-${Date.now()}-${Math.random().toString(36).slice(2, 6)}`,
      conversationId,
      fromType: MessageFromEnum.USER,
      fromUserId: 'user-mock-001',
      type: input.type,
      content: input.content,
      createdAt: new Date().toISOString(),
      readByUser: true,
      readByHr: false,
    }

    mutate(draft => {
      draft.messagesByConv[conversationId] = draft.messagesByConv[conversationId] ?? []
      draft.messagesByConv[conversationId].push(msg)
      const c = draft.conversations.find(x => x.id === conversationId)
      if (c) {
        c.lastMessageAt = msg.createdAt
        c.hrUnreadCount++
      }
    })

    // 触发 HR 自动回复
    if (userText) {
      setTimeout(() => emitTyping(conversationId, true), 200)
      setTimeout(() => {
        emitTyping(conversationId, false)
        const reply = mockHrReply(jobLegacyId, userText)
        const hrMsg: Message = {
          id: `msg-${Date.now()}-${Math.random().toString(36).slice(2, 6)}`,
          conversationId,
          fromType: MessageFromEnum.HR,
          fromHrId: conv.hrId,
          type: MessageTypeEnum.TEXT,
          content: reply,
          createdAt: new Date().toISOString(),
          readByUser: false,
          readByHr: true,
        }
        mutate(draft => {
          draft.messagesByConv[conversationId] = draft.messagesByConv[conversationId] ?? []
          draft.messagesByConv[conversationId].push(hrMsg)
          const c = draft.conversations.find(x => x.id === conversationId)
          if (c) {
            c.lastMessageAt = hrMsg.createdAt
            c.userUnreadCount++
          }
        })
      }, 200 + TYPING_DELAY_MS)
    }
    return msg
  }
  return request<Message>(`/conversations/${conversationId}/messages`, {
    method: 'POST',
    body: input,
  })
}

/** 发起会话（求职者从职位详情页"立即沟通"） */
export async function startConversation(
  jobId: string,
  firstMessage: string,
  ctx: ConversationListContext,
  mutate: (fn: (ctx: ConversationListContext) => void) => void,
): Promise<Conversation> {
  if (USE_MOCK) {
    // 已存在则返回
    const existing = ctx.conversations.find(c => c.jobId === jobId)
    if (existing) return existing

    const job = jobById(jobId)
    if (!job) throw new Error(`职位不存在：${jobId}`)

    const now = new Date().toISOString()
    const conv: Conversation = {
      id: `conv-${Date.now()}`,
      jobId,
      userId: 'user-mock-001',
      hrId: job.hr.id,
      companyId: job.company.id,
      lastMessageAt: now,
      userUnreadCount: 0,
      hrUnreadCount: 0,
      closedByHr: false,
      createdAt: now,
    }
    const sysMsg: Message = {
      id: `msg-sys-${Date.now()}`,
      conversationId: conv.id,
      fromType: MessageFromEnum.SYSTEM,
      type: MessageTypeEnum.SYSTEM,
      content: `你已向 ${job.hr.name} 发起沟通`,
      createdAt: now,
      readByUser: true,
      readByHr: true,
    }
    const userMsg: Message = {
      id: `msg-${Date.now()}-${Math.random().toString(36).slice(2, 6)}`,
      conversationId: conv.id,
      fromType: MessageFromEnum.USER,
      fromUserId: 'user-mock-001',
      type: MessageTypeEnum.TEXT,
      content: firstMessage,
      createdAt: now,
      readByUser: true,
      readByHr: false,
    }

    mutate(draft => {
      draft.conversations.push(conv)
      draft.messagesByConv[conv.id] = [sysMsg, userMsg]
    })

    // 触发 HR 自动回复
    setTimeout(() => emitTyping(conv.id, true), 200)
    setTimeout(() => {
      emitTyping(conv.id, false)
      const jobLegacyId = Number(jobId.replace('job-', ''))
      const reply = mockHrReply(jobLegacyId, firstMessage)
      const hrMsg: Message = {
        id: `msg-${Date.now()}-${Math.random().toString(36).slice(2, 6)}`,
        conversationId: conv.id,
        fromType: MessageFromEnum.HR,
        fromHrId: conv.hrId,
        type: MessageTypeEnum.TEXT,
        content: reply,
        createdAt: new Date().toISOString(),
        readByUser: false,
        readByHr: true,
      }
      mutate(draft => {
        draft.messagesByConv[conv.id] = draft.messagesByConv[conv.id] ?? []
        draft.messagesByConv[conv.id].push(hrMsg)
        const c = draft.conversations.find(x => x.id === conv.id)
        if (c) {
          c.lastMessageAt = hrMsg.createdAt
          c.userUnreadCount++
        }
      })
    }, 200 + TYPING_DELAY_MS)

    return conv
  }
  return request<Conversation>('/conversations', {
    method: 'POST',
    body: { jobId, firstMessage },
  })
}

/** 标记会话已读（求职者视角） */
export async function markConversationRead(
  conversationId: string,
  mutate: (fn: (ctx: ConversationListContext) => void) => void,
): Promise<void> {
  if (USE_MOCK) {
    mutate(draft => {
      const c = draft.conversations.find(x => x.id === conversationId)
      if (c) c.userUnreadCount = 0
      const msgs = draft.messagesByConv[conversationId] ?? []
      msgs.forEach(m => {
        if (m.fromType !== MessageFromEnum.USER) m.readByUser = true
      })
    })
    return
  }
  return request<void>(`/conversations/${conversationId}/read`, { method: 'POST' })
}
