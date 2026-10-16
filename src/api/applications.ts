/**
 * 聘聊 · 投递 / 面试 service（求职者视角）
 * =====================================================================
 * 与 docs/api-endpoints.md 对应：
 *  - POST /api/v1/applications                      §9.1 投递职位
 *  - GET  /api/v1/applications                      §9.2 我的投递列表
 *  - POST /api/v1/applications/{id}/withdraw        §9.4 撤回投递
 *  - POST /api/v1/interviews/{id}/accept            §10.4 接受面试
 *  - POST /api/v1/interviews/{id}/decline           §10.5 拒绝面试
 *
 * Mock 阶段直接操作 src/api/mock/applications.ts 的内存数组。
 * =====================================================================
 */

import type {
  Application,
  ApplicationWithJob,
  ApplyInput,
  Interview,
  PaginatedData,
} from '@/types/api'
import { ApplicationStatusEnum, InterviewStatusEnum } from '@/types/api'
import { USE_MOCK } from '@/config/env'
import { ApiError, request } from './client'
import type { SeekerApplicationView } from './mock/applications'
import {
  applicationById,
  applicationByUserJob,
  createApplication,
  interviewById,
  seekerApplicationViews,
  setInterviewStatus,
  withdrawApplicationRecord,
} from './mock/applications'
import { jobById } from './mock/jobs'

export type { SeekerApplicationView }

/** Mock 阶段的默认简历 id（联调后由简历模块提供真实 id） */
export const MOCK_RESUME_ID = 'resume-seeker'

/** §9.2 我的投递列表（按更新时间倒序） */
export async function listMyApplications(userId: string): Promise<SeekerApplicationView[]> {
  if (USE_MOCK) return seekerApplicationViews(userId)
  const data = await request<PaginatedData<ApplicationWithJob>>('/applications', { method: 'GET' })
  return data.list.map(a => ({ application: a, job: a.job, interview: null }))
}

/** §9.1 投递职位；(userId, jobId) 唯一，重复投递抛 40911 */
export async function applyJob(
  userId: string,
  input: Omit<ApplyInput, 'resumeId'> & { resumeId?: string },
): Promise<Application> {
  if (USE_MOCK) {
    const job = jobById(input.jobId)
    if (!job) {
      throw new ApiError({ code: 40410, message: '职位不存在或已下线', status: 404 })
    }
    const existing = applicationByUserJob(userId, input.jobId)
    if (existing && existing.status !== ApplicationStatusEnum.WITHDRAWN) {
      throw new ApiError({ code: 40911, message: '您已投递过该职位', status: 409 })
    }
    return createApplication({
      userId,
      jobId: input.jobId,
      companyId: job.company.id,
      resumeId: input.resumeId ?? MOCK_RESUME_ID,
      coverLetter: input.coverLetter,
    })
  }
  return request<Application>('/applications', {
    method: 'POST',
    body: { jobId: input.jobId, resumeId: input.resumeId ?? '', coverLetter: input.coverLetter },
  })
}

/** §9.4 撤回投递（仅待处理 / 已查看 / 面试中可撤回） */
export async function withdrawApplication(
  applicationId: string,
  userId: string,
): Promise<Application> {
  if (USE_MOCK) {
    const app = applicationById(applicationId)
    if (!app || app.userId !== userId) {
      throw new ApiError({ code: 40410, message: '投递记录不存在', status: 404 })
    }
    const withdrawable: ApplicationStatusEnum[] = [
      ApplicationStatusEnum.SUBMITTED,
      ApplicationStatusEnum.VIEWED,
      ApplicationStatusEnum.INTERVIEW,
    ]
    if (!withdrawable.includes(app.status)) {
      throw new ApiError({ code: 40913, message: '当前状态不可撤回', status: 409 })
    }
    return withdrawApplicationRecord(applicationId)!
  }
  return request<Application>(`/applications/${applicationId}/withdraw`, { method: 'POST' })
}

/** §10.4 接受面试邀请（校验该面试确实属于当前用户） */
export async function acceptInterview(interviewId: string, userId: string): Promise<Interview> {
  if (USE_MOCK) {
    assertInterviewOwner(interviewId, userId)
    const updated = setInterviewStatus(interviewId, InterviewStatusEnum.ACCEPTED)
    if (!updated) {
      throw new ApiError({ code: 40410, message: '面试邀请不存在', status: 404 })
    }
    return updated
  }
  return request<Interview>(`/interviews/${interviewId}/accept`, { method: 'POST' })
}

/** §10.5 拒绝面试邀请 */
export async function declineInterview(
  interviewId: string,
  userId: string,
  reason?: string,
): Promise<Interview> {
  if (USE_MOCK) {
    assertInterviewOwner(interviewId, userId)
    const updated = setInterviewStatus(interviewId, InterviewStatusEnum.DECLINED)
    if (!updated) {
      throw new ApiError({ code: 40410, message: '面试邀请不存在', status: 404 })
    }
    return updated
  }
  return request<Interview>(`/interviews/${interviewId}/decline`, {
    method: 'POST',
    body: { reason },
  })
}

/** 面试 → 投递 → 用户归属校验（Mock；联调由后端鉴权兜底） */
function assertInterviewOwner(interviewId: string, userId: string): void {
  const interview = interviewById(interviewId)
  const app = interview ? applicationById(interview.applicationId) : undefined
  if (!interview || !app || app.userId !== userId) {
    throw new ApiError({ code: 40410, message: '面试邀请不存在', status: 404 })
  }
}
