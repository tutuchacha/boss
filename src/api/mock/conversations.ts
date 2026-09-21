/**
 * 聘聊 · Mock 数据 - 当前用户 + 预置会话
 * =====================================================================
 * 来源：HTML 原型 S.profile 与 load() 中预置的两条会话
 * =====================================================================
 */

import type {
  Conversation,
  Message,
  User,
  UserProfile,
} from '@/types/api'
import { MessageFromEnum, MessageTypeEnum, RoleEnum, SeekingStatusEnum } from '@/types/api'
import { jobByLegacyId } from './jobs'

/** 当前 Mock 用户（求职者 林小满） */
export const currentUser: User = {
  id: 'user-mock-001',
  phone: '13800000001',
  email: 'linxiaoman@example.com',
  role: RoleEnum.JOB_SEEKER,
  status: 'ACTIVE',
  createdAt: '2025-01-01T00:00:00Z',
  updatedAt: '2026-09-01T00:00:00Z',
}

/** Mock 用户画像 */
export const currentUserProfile: UserProfile = {
  id: 'profile-mock-001',
  userId: currentUser.id,
  name: '林小满',
  avatarUrl: undefined,
  gender: 'female',
  workYears: 3,
  edu: '本科',
  expectCat: '互联网',
  expectSub: '前端开发',
  expectCity: '上海',
  expectSalaryMin: 20,
  expectSalaryMax: 30,
  seekingStatus: SeekingStatusEnum.ACTIVE,
}

/** 状态文案（保留原型风格） */
export const USER_STATUS_TEXT = '离职，随时到岗'

/** Mock 简历完善度 */
export const RESUME_COMPLETENESS = 85

/** 预置会话 */
const T0 = Date.now()

const seedConversation3: Conversation = {
  id: 'conv-seed-003',
  jobId: 'job-3',
  userId: currentUser.id,
  hrId: 'hr-mock-003',
  companyId: 'co-mock-mumianyun',
  lastMessageAt: new Date(T0 - 25 * 60 * 1000).toISOString(),
  userUnreadCount: 1,
  hrUnreadCount: 0,
  closedByHr: false,
  createdAt: new Date(T0 - 25 * 60 * 1000).toISOString(),
}

const seedMessages3: Message[] = [
  {
    id: 'msg-3-1',
    conversationId: seedConversation3.id,
    fromType: MessageFromEnum.SYSTEM,
    type: MessageTypeEnum.SYSTEM,
    content: '你已向 李女士 发起沟通',
    createdAt: new Date(T0 - 25 * 60 * 1000).toISOString(),
    readByUser: true,
    readByHr: true,
  },
  {
    id: 'msg-3-2',
    conversationId: seedConversation3.id,
    fromType: MessageFromEnum.USER,
    fromUserId: currentUser.id,
    type: MessageTypeEnum.TEXT,
    content: '您好，我对「产品经理（B端）」很感兴趣，方便聊聊吗？',
    createdAt: new Date(T0 - 24 * 60 * 1000).toISOString(),
    readByUser: true,
    readByHr: true,
  },
  {
    id: 'msg-3-3',
    conversationId: seedConversation3.id,
    fromType: MessageFromEnum.HR,
    fromHrId: 'hr-mock-003',
    type: MessageTypeEnum.TEXT,
    content: '您好！看了您的简历，B 端经验很匹配，方便这周找个时间电话聊聊吗？',
    createdAt: new Date(T0 - 23 * 60 * 1000).toISOString(),
    readByUser: false,
    readByHr: true,
  },
]

const seedConversation8: Conversation = {
  id: 'conv-seed-008',
  jobId: 'job-8',
  userId: currentUser.id,
  hrId: 'hr-mock-008',
  companyId: 'co-mock-jiguang',
  lastMessageAt: new Date(T0 - 3 * 3600 * 1000).toISOString(),
  userUnreadCount: 1,
  hrUnreadCount: 0,
  closedByHr: false,
  createdAt: new Date(T0 - 3 * 3600 * 1000).toISOString(),
}

const seedMessages8: Message[] = [
  {
    id: 'msg-8-1',
    conversationId: seedConversation8.id,
    fromType: MessageFromEnum.HR,
    fromHrId: 'hr-mock-008',
    type: MessageTypeEnum.TEXT,
    content: '您好，我是极光量子的孙先生，看到您的简历，想和您聊聊 Go 后端的机会。',
    createdAt: new Date(T0 - 3 * 3600 * 1000).toISOString(),
    readByUser: false,
    readByHr: true,
  },
]

export interface SeedConversation {
  conversation: Conversation
  messages: Message[]
}

export const seedConversations: SeedConversation[] = [
  { conversation: seedConversation3, messages: seedMessages3 },
  { conversation: seedConversation8, messages: seedMessages8 },
]

/**
 * HR 自动回复（与原型 hrReply 关键词逻辑一致）
 * 上线后由后端 LLM/规则引擎实现，前端 mock 仅用于演示。
 */
export function mockHrReply(jobLegacyId: number, userText: string): string {
  const job = jobByLegacyId(jobLegacyId)
  if (!job) return '您好，请问您现在的工作状态是？大概什么时候可以到岗呢？'
  if (/简历/.test(userText)) return '收到您的简历了，我先和用人部门同步一下，有结果第一时间通知您。'
  if (/薪|工资|待遇/.test(userText))
    return `这个岗位的薪资范围是 ${job.salary}，${job.salaryMax >= 30 ? '另有年终奖和长期激励' : '含绩效奖金'}，具体会根据面试表现确定。`
  if (/面试|约/.test(userText))
    return '可以的，您本周三或周四下午方便吗？确认后我给您发面试邀请。'
  return '您好，请问您现在的工作状态是？大概什么时候可以到岗呢？'
}

/** "对方正在输入" 模拟时长（ms） */
export const TYPING_DELAY_MS = 1200
