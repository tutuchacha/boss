/**
 * 聘聊 · 投递 / 面试 service（HR 视角）
 * =====================================================================
 * 与 docs/api-endpoints.md 对应：
 *  - GET  /api/v1/hr/applications                    §4.4 我收到的投递
 *  - POST /api/v1/applications/{id}/status           §9.5 更新投递状态
 *  - POST /api/v1/conversations/{id}/actions/invite-interview
 *                                                   §8.7 约面试（创建 Interview）
 *
 * Mock 阶段直接操作内存数组；联调阶段切到 request()。
 * =====================================================================
 */

import type { Application, Interview, InviteInterviewInput, Message } from '@/types/api'
import { ApplicationStatusEnum } from '@/types/api'
import { USE_MOCK } from '@/config/env'
import { ApiError, request } from './client'
import type { HrApplicationView } from './mock/applications'
import {
  applicationById,
  appendInterviewInviteMessage,
  companyApplicationViews,
  createInterviewRecord,
  ensureConversationForApplication as ensureConversationMock,
  pendingInterviewOfApplication,
  setApplicationStatus as setApplicationStatusMock,
} from './mock/applications'
import { hrConversationById } from './mock/hrConversations'
import { MOCK_HR_PROFILE } from './mock/auth'

export type { HrApplicationView }

/** 列表查询键前缀（供 TanStack Query 失效使用） */
export const HR_APPLICATIONS_QUERY_KEY = ['hr', 'applications'] as const

export interface HrApplicationFilters {
  /** 投递状态；'ALL' 或省略为全部 */
  status?: ApplicationStatusEnum | 'ALL'
  /** 按职位筛选 */
  jobId?: string
  /** 关键字（候选人姓名 / 职位名） */
  q?: string
}

/** §4.4 我（所在公司）收到的投递（按更新时间倒序） */
export async function listHrApplications(
  companyId: string,
  filters: HrApplicationFilters = {},
): Promise<HrApplicationView[]> {
  if (USE_MOCK) {
    const { status = 'ALL', jobId, q = '' } = filters
    const kw = q.trim().toLowerCase()
    return companyApplicationViews(companyId).filter(v => {
      if (status !== 'ALL' && v.application.status !== status) return false
      if (jobId && v.job.id !== jobId) return false
      if (kw) {
        const hay = `${v.candidate.name} ${v.job.title}`.toLowerCase()
        if (!hay.includes(kw)) return false
      }
      return true
    })
  }
  return request<HrApplicationView[]>('/hr/applications', {
    method: 'GET',
    query: {
      status: filters.status === 'ALL' ? undefined : filters.status,
      jobId: filters.jobId,
      q: filters.q,
    },
  })
}

/** 单条投递详情（候选人管理详情页） */
export async function getHrApplication(
  applicationId: string,
): Promise<HrApplicationView | undefined> {
  if (USE_MOCK) {
    const app = applicationById(applicationId)
    if (!app) return undefined
    return companyApplicationViews(app.companyId).find(v => v.application.id === applicationId)
  }
  // 后端 §4.4 列表出参就是 HrApplicationView 超集（含 candidate / conversationId / interview），
  // 但契约没有单条详情端点（§9.3 返回的是 ApplicationDetail，形状不同），故取列表后按 id 定位
  const list = await request<HrApplicationView[]>('/hr/applications', { method: 'GET' })
  return list.find(v => v.application.id === applicationId)
}

/**
 * §9.5 更新投递状态（HR 标注）。
 * 允许的标记：已查看 / 录用 / 不合适（面试中由「约面试」自动置入）。
 */
export async function updateApplicationStatus(
  applicationId: string,
  status:
    | ApplicationStatusEnum.VIEWED
    | ApplicationStatusEnum.OFFER
    | ApplicationStatusEnum.REJECTED,
  note?: string,
): Promise<Application> {
  if (USE_MOCK) {
    const app = applicationById(applicationId)
    if (!app) {
      throw new ApiError({ code: 40410, message: '投递记录不存在', status: 404 })
    }
    if (app.status === ApplicationStatusEnum.WITHDRAWN) {
      throw new ApiError({ code: 40913, message: '候选已撤回投递，无法变更状态', status: 409 })
    }
    const updated = setApplicationStatusMock(applicationId, status)
    if (!updated) {
      throw new ApiError({ code: 40410, message: '投递记录不存在', status: 404 })
    }
    void note // Mock 阶段不落库，联调由后端记录操作日志
    return updated
  }
  return request<Application>(`/applications/${applicationId}/status`, {
    method: 'POST',
    body: { status, note },
  })
}

/**
 * 确保「候选人 × 职位」会话存在（发消息 / 约面试前调用）。
 * ⚠️ Mock 专用：联调阶段应由后端在投递时自动建会话，或另定接口。
 */
export async function ensureConversationForApplication(applicationId: string): Promise<string> {
  if (USE_MOCK) {
    const convId = ensureConversationMock(applicationId)
    if (!convId) {
      throw new ApiError({ code: 40410, message: '投递记录不存在', status: 404 })
    }
    return convId
  }
  throw new ApiError({
    code: 42210,
    message: 'Mock 专用接口：联调阶段请使用投递返回的会话上下文',
    status: 422,
  })
}

export interface InviteInterviewParams {
  applicationId: string
  /** 会话 id；Mock 阶段可省略（自动按需创建） */
  conversationId?: string
  invite: InviteInterviewInput
}

/**
 * §8.7 约面试（HR 快捷操作）：
 * 创建 Interview 记录 + 更新投递状态为「面试中」+ 发送 INTERVIEW_INVITE 消息。
 * 同一投递已有待确认邀请时拒绝（40915），避免重复约会。
 *
 * 返回值：Mock 阶段返回新建的 `Interview`；真实后端该端点返回送达的
 * `Message`（面试邀请消息），故联合类型标注。
 */
export async function inviteInterview(
  params: InviteInterviewParams,
): Promise<Interview | Message> {
  const { applicationId, invite } = params

  if (USE_MOCK) {
    const app = applicationById(applicationId)
    if (!app) {
      throw new ApiError({ code: 40410, message: '投递记录不存在', status: 404 })
    }
    const blocked: ApplicationStatusEnum[] = [
      ApplicationStatusEnum.REJECTED,
      ApplicationStatusEnum.WITHDRAWN,
    ]
    if (blocked.includes(app.status)) {
      throw new ApiError({ code: 40914, message: '当前状态不可发起面试', status: 409 })
    }
    if (pendingInterviewOfApplication(applicationId)) {
      throw new ApiError({ code: 40915, message: '已有待确认的面试邀请，请等待候选人回应', status: 409 })
    }
    if (new Date(invite.scheduledAt).getTime() <= Date.now()) {
      throw new ApiError({ code: 42210, message: '面试时间需晚于当前时间', status: 422 })
    }

    const interview = createInterviewRecord(applicationId, invite)
    if (!interview) {
      throw new ApiError({ code: 40410, message: '投递记录不存在', status: 404 })
    }

    // 通过会话送达邀请（无会话则按需创建）
    const convId = params.conversationId ?? ensureConversationMock(applicationId)
    if (convId) {
      const hrId = hrConversationById(convId)?.conversation.hrId ?? MOCK_HR_PROFILE.id
      appendInterviewInviteMessage(convId, interview, hrId)
    }
    return interview
  }

  if (!params.conversationId) {
    throw new ApiError({ code: 42210, message: '缺少会话上下文，无法发送面试邀请', status: 422 })
  }
  return request<Message>(
    `/conversations/${params.conversationId}/actions/invite-interview`,
    { method: 'POST', body: invite },
  )
}
