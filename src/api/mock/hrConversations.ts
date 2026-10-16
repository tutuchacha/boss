/**
 * 聘聊 · Mock 数据 - HR 侧会话（可变动，内存态）
 * =====================================================================
 * 对应契约：docs/api-endpoints.md §8 会话模块（HR 视角）
 *  - GET  /api/v1/conversations                       HR 所在公司的会话
 *  - GET  /api/v1/conversations/{id}/messages
 *  - POST /api/v1/conversations/{id}/messages         以 HR 身份发送
 *  - POST /api/v1/conversations/{id}/read             标记 HR 已读
 *
 * 为什么独立于求职者侧的会话数据（src/api/conversations.ts 的 seed）：
 *   求职者侧的种子会话 companyId 是占位值（co-mock-mumianyun 等），与
 *   companies.ts 的真实哈希 id 对不上，无法被 HR 视角检索到。
 *   为了不侵入已验收的求职者端，这里为 HR 单独建立一份自洽的数据：
 *   会话基于「本 HR 所属公司在库的真实职位」派生，候选人来自固定名单。
 *
 * 数据可变：模块级数组 + 递增 id 计数（不用 Date.now/Math.random 生成 id，
 * 避免 SSR/CSR 不一致）。刷新页面即重置（Mock 阶段特性）。
 * =====================================================================
 */

import type { Conversation, Message } from '@/types/api'
import { MessageFromEnum, MessageTypeEnum } from '@/types/api'
import { jobs } from './jobs'
import { MOCK_HR_PROFILE, MOCK_COMPANY, MOCK_SEEKER_USER } from './auth'
import { CANDIDATES } from './candidates'

/** HR 侧会话视图所需的数据 */
export interface HrConversationView {
  conversation: Conversation
  /** 候选人（求职者）展示信息 */
  candidate: {
    userId: string
    name: string
    expect: string
    city: string
    workYears: number
    edu: string
    resumeCompleteness: number
  }
  job: { id: string; title: string; salary: string }
  lastMessage?: Message
}

/** 候选人名单（姓名与画像为演示数据，定义在 ./candidates，与「候选人管理」共用） */

/** 稳定的基准时间（不用 Date.now，避免服务端/客户端渲染不一致） */
const BASE_TIME = new Date(Date.UTC(2026, 8, 21, 10, 0, 0)).getTime()
const MIN = 60 * 1000

/** 初始会话数据（惰性生成，仅一次） */
export const hrConversations: Conversation[] = []
export const hrMessagesByConv: Record<string, Message[]> = {}

let seeded = false
let idSeq = 0

const nextId = (prefix: string): string => {
  idSeq += 1
  return `${prefix}-${idSeq}`
}

/** 候选人开场白（按序分配，保证内容多样且确定） */
const OPENINGS = [
  '您好，我在招聘网站上看到这个岗位，我的经历比较匹配，方便聊聊吗？',
  '您好！我对贵司这个职位很感兴趣，请问目前还在招吗？',
  '您好，我有相关项目经验，想了解一下岗位的具体业务方向。',
  '您好，请问这个岗位对技术栈有硬性要求吗？',
  '您好，我已经投递了简历，期待您的回复。',
  '您好，想确认一下工作地点和到岗时间的要求。',
]

/**
 * 生成 HR 侧会话种子。幂等：重复调用不会重复生成。
 * 会话基于「本公司真实在招职位」派生，保证与职位列表自洽。
 */
export function seedHrConversations(): void {
  if (seeded) return
  seeded = true

  const companyJobs = jobs.filter(j => j.company.id === MOCK_COMPANY.id)
  if (companyJobs.length === 0) return

  CANDIDATES.forEach((cand, i) => {
    // 让候选人分布到本公司不同职位上
    const job = companyJobs[i % companyJobs.length]
    const minutesAgo = 5 + i * 47 // 5 分钟到 4 小时前，跨度自然
    const lastAt = new Date(BASE_TIME - minutesAgo * MIN).toISOString()

    const conv: Conversation = {
      id: nextId('conv-hr'),
      jobId: job.id,
      userId: cand.userId,
      hrId: MOCK_HR_PROFILE.id,
      companyId: MOCK_COMPANY.id,
      lastMessageAt: lastAt,
      // 前 3 个会话给 HR 留未读，便于验收未读角标与筛选
      hrUnreadCount: i < 3 ? (i === 0 ? 2 : 1) : 0,
      userUnreadCount: 0,
      closedByHr: false,
      createdAt: new Date(BASE_TIME - (minutesAgo + 30) * MIN).toISOString(),
    }
    hrConversations.push(conv)

    const msgs: Message[] = [
      {
        id: nextId('msg-hr'),
        conversationId: conv.id,
        fromType: MessageFromEnum.USER,
        fromUserId: cand.userId,
        type: MessageTypeEnum.TEXT,
        content: OPENINGS[i % OPENINGS.length],
        createdAt: new Date(BASE_TIME - (minutesAgo + 20) * MIN).toISOString(),
        readByUser: true,
        // 未读会话的最后一条对 HR 为未读
        readByHr: i >= 3,
      },
    ]

    // 部分会话已有 HR 回复，让列表更真实
    if (i % 2 === 1) {
      msgs.push({
        id: nextId('msg-hr'),
        conversationId: conv.id,
        fromType: MessageFromEnum.HR,
        fromHrId: MOCK_HR_PROFILE.id,
        type: MessageTypeEnum.TEXT,
        content: '您好，您的背景我们比较感兴趣，方便约个时间电话沟通吗？',
        createdAt: new Date(BASE_TIME - (minutesAgo + 10) * MIN).toISOString(),
        readByUser: true,
        readByHr: true,
      })
    }

    // 未读会话补一条候选人追问，令未读数与 hrUnreadCount 自洽
    if (i < 3) {
      msgs.push({
        id: nextId('msg-hr'),
        conversationId: conv.id,
        fromType: MessageFromEnum.USER,
        fromUserId: cand.userId,
        type: MessageTypeEnum.TEXT,
        content: '请问方便的话，今天下午可以电话吗？',
        createdAt: lastAt,
        readByUser: true,
        readByHr: false,
      })
    }

    hrMessagesByConv[conv.id] = msgs
  })
}

/** 按公司取会话（HR 视角，按最后消息时间倒序） */
export function hrConversationsOf(companyId: string): HrConversationView[] {
  seedHrConversations()
  return hrConversations
    .filter(c => c.companyId === companyId)
    .map(c => {
      const cand =
        CANDIDATES.find(x => x.userId === c.userId) ??
        {
          userId: c.userId,
          name: '候选人',
          expect: '—',
          city: '—',
          workYears: 0,
          edu: '—',
          resumeCompleteness: 0,
        }
      const job = jobs.find(j => j.id === c.jobId)
      const msgs = hrMessagesByConv[c.id] ?? []
      return {
        conversation: c,
        candidate: {
          userId: cand.userId,
          name: cand.name,
          expect: cand.expect,
          city: cand.city,
          workYears: cand.workYears,
          edu: cand.edu,
          resumeCompleteness: cand.resumeCompleteness,
        },
        job: {
          id: c.jobId,
          title: job?.title ?? '职位',
          salary: job?.salary ?? '',
        },
        lastMessage: msgs[msgs.length - 1],
      }
    })
    .sort(
      (a, b) =>
        new Date(b.conversation.lastMessageAt).getTime() -
        new Date(a.conversation.lastMessageAt).getTime(),
    )
}

/** 按 HR 取会话（等价于公司维度，保留 hrId 便于将来多 HR 隔离） */
export function hrConversationsOfHr(companyId: string, hrId: string): HrConversationView[] {
  return hrConversationsOf(companyId).filter(v => v.conversation.hrId === hrId)
}

/** 单个会话视图 */
export function hrConversationById(id: string): HrConversationView | undefined {
  seedHrConversations()
  const c = hrConversations.find(x => x.id === id)
  if (!c) return undefined
  return hrConversationsOf(c.companyId).find(v => v.conversation.id === id)
}

/** 消息列表 */
export function hrMessagesOf(conversationId: string): Message[] {
  seedHrConversations()
  return hrMessagesByConv[conversationId] ?? []
}

/** HR 发送消息（追加并更新会话统计）。type 默认 TEXT，可传 INTERVIEW_INVITE 等 */
export function appendHrMessage(
  conversationId: string,
  content: string,
  hrId: string,
  type: MessageTypeEnum = MessageTypeEnum.TEXT,
): Message {
  const msg: Message = {
    id: nextId('msg-hr'),
    conversationId,
    fromType: MessageFromEnum.HR,
    fromHrId: hrId,
    type,
    content,
    createdAt: new Date(BASE_TIME + idSeq * MIN).toISOString(),
    readByUser: false,
    readByHr: true,
  }
  hrMessagesByConv[conversationId] = [...(hrMessagesByConv[conversationId] ?? []), msg]
  const conv = hrConversations.find(c => c.id === conversationId)
  if (conv) {
    conv.lastMessageAt = msg.createdAt
    conv.userUnreadCount += 1
  }
  return msg
}

/** 追加候选人的消息（模拟对方回复） */
export function appendCandidateMessage(conversationId: string, content: string): Message {
  const conv = hrConversations.find(c => c.id === conversationId)
  const msg: Message = {
    id: nextId('msg-hr'),
    conversationId,
    fromType: MessageFromEnum.USER,
    fromUserId: conv?.userId ?? MOCK_SEEKER_USER.id,
    type: MessageTypeEnum.TEXT,
    content,
    createdAt: new Date(BASE_TIME + idSeq * MIN).toISOString(),
    readByUser: true,
    readByHr: false,
  }
  hrMessagesByConv[conversationId] = [...(hrMessagesByConv[conversationId] ?? []), msg]
  if (conv) {
    conv.lastMessageAt = msg.createdAt
    conv.hrUnreadCount += 1
  }
  return msg
}

/** HR 标记已读 */
export function markHrConversationRead(conversationId: string): void {
  const conv = hrConversations.find(c => c.id === conversationId)
  if (!conv) return
  conv.hrUnreadCount = 0
  const msgs = hrMessagesByConv[conversationId] ?? []
  hrMessagesByConv[conversationId] = msgs.map(m =>
    m.fromType === MessageFromEnum.USER ? { ...m, readByHr: true } : m,
  )
}

/**
 * 确保「候选人 × 职位」存在一条 HR 侧会话（不存在则创建）。
 * 用途：候选人管理页的「发消息」与「发送面试邀请」——
 *   投递（Application）本身不建会话，HR 主动触达时按需创建。
 * @returns 会话 id
 */
export function ensureHrConversationForCandidate(
  userId: string,
  jobId: string,
  hrId: string,
  companyId: string,
): string {
  seedHrConversations()
  const existing = hrConversations.find(
    c => c.userId === userId && c.jobId === jobId && c.companyId === companyId,
  )
  if (existing) return existing.id

  const conv: Conversation = {
    id: nextId('conv-hr'),
    jobId,
    userId,
    hrId,
    companyId,
    lastMessageAt: new Date(BASE_TIME + idSeq * MIN).toISOString(),
    userUnreadCount: 0,
    hrUnreadCount: 0,
    closedByHr: false,
    createdAt: new Date(BASE_TIME + idSeq * MIN).toISOString(),
  }
  hrConversations.push(conv)
  hrMessagesByConv[conv.id] = []
  return conv.id
}

/** 候选人自动回复文案（与求职者侧 mockHrReply 对称） */
export function mockCandidateReply(userText: string): string {
  if (/面试|约|电话/.test(userText)) return '可以的，我这周三或周四下午都方便，看您时间。'
  if (/薪|工资|待遇|薪资/.test(userText)) return '我目前的期望是 25-35K，具体可以再沟通。'
  if (/简历|作品/.test(userText)) return '好的，我这就把最新简历发您，稍等。'
  if (/到岗|入职|什么时候/.test(userText)) return '我现在离职状态，随时可以到岗。'
  return '好的，谢谢您！我这边随时可以进一步沟通。'
}

/** 候选人名单（供 UI 展示候选人数等） */
export const hrCandidateCount = (): number => CANDIDATES.length
