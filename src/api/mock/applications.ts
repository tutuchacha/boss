/**
 * 聘聊 · Mock 数据 - 投递与面试（可变，内存态）
 * =====================================================================
 * 对应契约：docs/api-endpoints.md §9 投递模块、§10 面试模块
 *  - GET  /api/v1/applications                       我的投递（求职者）
 *  - POST /api/v1/applications                       投递职位
 *  - POST /api/v1/applications/{id}/withdraw         撤回投递
 *  - GET  /api/v1/hr/applications                    我收到的投递（HR）
 *  - POST /api/v1/applications/{id}/status           更新投递状态（HR）
 *  - POST /api/v1/interviews/{id}/accept|decline     接受 / 拒绝面试（求职者）
 *  - POST /api/v1/conversations/{id}/actions/invite-interview  约面试（HR）
 *
 * 设计说明：
 *  - 数据为模块级可变数组，页面内增删改立即可见（刷新重置）。
 *  - 种子与 hrConversations 共用 ./candidates 的候选人名单，
 *    保证「候选人管理」与「沟通」两个页面姓名/画像一致。
 *  - 时间锚点 T0 在首次访问时取 Date.now()：这些数据仅在登录后的
 *    客户端路径渲染（RootGate 水合前不渲染任何业务数据），不参与 SSR。
 * =====================================================================
 */

import type {
  Application,
  Interview,
  JobSummary,
  UserProfile,
} from '@/types/api'
import {
  ApplicationStatusEnum,
  InterviewFormatEnum,
  InterviewStatusEnum,
  MessageTypeEnum,
} from '@/types/api'
import { jobById, listSeekerJobs, jobs } from './jobs'
import { hrJobById } from './hrJobs'
import {
  appendHrMessage,
  ensureHrConversationForCandidate,
  hrConversationsOf,
} from './hrConversations'
import { CANDIDATES, candidateById, type CandidateSeed } from './candidates'
import {
  MOCK_COMPANY,
  MOCK_HR_PROFILE,
  MOCK_SEEKER_PROFILE,
  MOCK_SEEKER_USER,
} from './auth'

// ============================================================
// 视图类型
// ============================================================

/** 求职者视角：一条投递 = 投递记录 + 职位（职位下线则为 null）+ 当前面试 */
export interface SeekerApplicationView {
  application: Application
  job: JobSummary | null
  interview: Interview | null
}

/** HR 视角：一条投递 = 投递记录 + 候选人画像 + 职位 + 关联会话 + 最近面试 */
export interface HrApplicationView {
  application: Application
  candidate: CandidateSeed
  job: { id: string; title: string; salary: string; city: string }
  /** 候选人 × 职位 的会话（尚未创建则为 undefined，「发消息」时按需创建） */
  conversationId?: string
  interview?: Interview
}

/** 投递状态 → 中文文案（两端共用） */
export const APPLICATION_STATUS_TEXT: Record<ApplicationStatusEnum, string> = {
  [ApplicationStatusEnum.SUBMITTED]: '待处理',
  [ApplicationStatusEnum.VIEWED]: '已查看',
  [ApplicationStatusEnum.INTERVIEW]: '面试中',
  [ApplicationStatusEnum.OFFER]: '已录用',
  [ApplicationStatusEnum.REJECTED]: '未通过',
  [ApplicationStatusEnum.WITHDRAWN]: '已撤回',
}

/** 面试形式 → 中文文案 */
export const INTERVIEW_FORMAT_TEXT: Record<InterviewFormatEnum, string> = {
  [InterviewFormatEnum.ONSITE]: '现场面试',
  [InterviewFormatEnum.VIDEO]: '视频面试',
  [InterviewFormatEnum.PHONE]: '电话面试',
}

/** 面试状态 → 中文文案 */
export const INTERVIEW_STATUS_TEXT: Record<InterviewStatusEnum, string> = {
  [InterviewStatusEnum.PENDING]: '待候选人确认',
  [InterviewStatusEnum.ACCEPTED]: '候选人已接受',
  [InterviewStatusEnum.DECLINED]: '候选人已拒绝',
  [InterviewStatusEnum.COMPLETED]: '已完成',
  [InterviewStatusEnum.CANCELLED]: '已取消',
}

// ============================================================
// 可变数据
// ============================================================

export const applications: Application[] = []
export const interviews: Interview[] = []

let seeded = false
let T0 = 0
let appSeq = 0
let intSeq = 0

const HOUR = 60 * 60 * 1000
const DAY = 24 * HOUR

const iso = (ms: number): string => new Date(ms).toISOString()

function ensureSeed(): void {
  if (seeded) return
  seeded = true
  T0 = Date.now()

  // ---- HR 侧：6 位候选人各有一条投递，状态覆盖完整枚举 ----
  const companyJobs = jobs.filter(j => j.company.id === MOCK_COMPANY.id)
  if (companyJobs.length > 0) {
    const hrStatuses: ApplicationStatusEnum[] = [
      ApplicationStatusEnum.SUBMITTED, // 林小满 · 待处理
      ApplicationStatusEnum.VIEWED,    // 周叙白 · 已查看
      ApplicationStatusEnum.SUBMITTED, // 沈禾 · 待处理
      ApplicationStatusEnum.INTERVIEW, // 赵之衡 · 面试中（含待确认邀请）
      ApplicationStatusEnum.REJECTED,  // 苏晚 · 未通过
      ApplicationStatusEnum.OFFER,     // 何知遥 · 已录用（含已完成面试）
    ]
    CANDIDATES.forEach((cand, i) => {
      const job = companyJobs[i % companyJobs.length]
      const status = hrStatuses[i]
      const app: Application = {
        id: `app-hr-0${i + 1}`,
        userId: cand.userId,
        jobId: job.id,
        companyId: MOCK_COMPANY.id,
        resumeId: `resume-${cand.userId}`,
        status,
        coverLetter: i === 0 ? '您好，我对该岗位非常感兴趣，期待与您沟通。' : undefined,
        createdAt: iso(T0 - (i + 1) * 8 * HOUR),
        updatedAt: iso(T0 - (i + 1) * 4 * HOUR),
      }
      applications.push(app)

      if (status === ApplicationStatusEnum.INTERVIEW) {
        interviews.push({
          id: 'int-hr-01',
          applicationId: app.id,
          companyId: MOCK_COMPANY.id,
          scheduledAt: iso(T0 + 2 * DAY),
          format: InterviewFormatEnum.VIDEO,
          onlineUrl: 'https://meeting.pinliao.dev/demo-room',
          note: '请提前 5 分钟进入会议，准备好项目介绍。',
          status: InterviewStatusEnum.PENDING,
          createdAt: iso(T0 - 2 * HOUR),
          updatedAt: iso(T0 - 2 * HOUR),
        })
      }
      if (status === ApplicationStatusEnum.OFFER) {
        interviews.push({
          id: 'int-hr-02',
          applicationId: app.id,
          companyId: MOCK_COMPANY.id,
          scheduledAt: iso(T0 - 3 * DAY),
          format: InterviewFormatEnum.ONSITE,
          location: '上海市浦东新区云杉大厦 12F 会议室 A',
          status: InterviewStatusEnum.COMPLETED,
          createdAt: iso(T0 - 6 * DAY),
          updatedAt: iso(T0 - 3 * DAY),
        })
      }
    })
  }

  // ---- 求职者（林晓）侧：3 条投递，均投给非云杉公司，避免与 HR 演示数据打架 ----
  const isNotYunshan = (j: JobSummary): boolean => j.company.id !== MOCK_COMPANY.id
  const visible = listSeekerJobs()
  let used: string[] = []
  const pick = (pred: (j: JobSummary) => boolean): JobSummary | undefined => {
    const job = visible.find(j => !used.includes(j.id) && pred(j))
    if (job) used = [...used, job.id]
    return job
  }

  const s1 = pick(j => j.sub === '前端开发' && isNotYunshan(j)) ??
    pick(j => j.cat === '互联网' && isNotYunshan(j))
  const s2 = pick(j => (j.sub === 'UI/UX设计' || j.sub === '平面设计') && isNotYunshan(j))
  const s3 = pick(j => j.sub === '数据分析' && isNotYunshan(j))

  const seekerSeeds: { job?: JobSummary; status: ApplicationStatusEnum }[] = [
    { job: s1, status: ApplicationStatusEnum.INTERVIEW }, // 含待确认面试邀请
    { job: s2, status: ApplicationStatusEnum.SUBMITTED },
    { job: s3, status: ApplicationStatusEnum.VIEWED },
  ]
  seekerSeeds.forEach((seed, i) => {
    if (!seed.job) return
    const app: Application = {
      id: `app-seek-0${i + 1}`,
      userId: MOCK_SEEKER_USER.id,
      jobId: seed.job.id,
      companyId: seed.job.company.id,
      resumeId: 'resume-seeker',
      status: seed.status,
      createdAt: iso(T0 - (i + 2) * DAY),
      updatedAt: iso(T0 - (i + 1) * 6 * HOUR),
    }
    applications.push(app)

    if (seed.status === ApplicationStatusEnum.INTERVIEW) {
      interviews.push({
        id: 'int-seek-01',
        applicationId: app.id,
        companyId: app.companyId,
        scheduledAt: iso(T0 + 3 * DAY + 2 * HOUR),
        format: InterviewFormatEnum.VIDEO,
        onlineUrl: 'https://meeting.pinliao.dev/room-6088',
        note: '一面预计 45 分钟，请准备一段自我介绍。',
        status: InterviewStatusEnum.PENDING,
        createdAt: iso(T0 - 6 * HOUR),
        updatedAt: iso(T0 - 6 * HOUR),
      })
    }
  })
}

// ============================================================
// 基础查询
// ============================================================

/** 按 user × job 查投递（用于「已投递」判断与幂等） */
export function applicationByUserJob(userId: string, jobId: string): Application | undefined {
  ensureSeed()
  return applications.find(a => a.userId === userId && a.jobId === jobId)
}

export function applicationById(id: string): Application | undefined {
  ensureSeed()
  return applications.find(a => a.id === id)
}

export function interviewById(id: string): Interview | undefined {
  ensureSeed()
  return interviews.find(i => i.id === id)
}

/** 某投递的全部面试（按创建时间倒序） */
export function interviewsOfApplication(applicationId: string): Interview[] {
  ensureSeed()
  return interviews
    .filter(i => i.applicationId === applicationId)
    .sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime())
}

/** 某投递当前待确认的面试（若有） */
export function pendingInterviewOfApplication(applicationId: string): Interview | undefined {
  return interviewsOfApplication(applicationId).find(i => i.status === InterviewStatusEnum.PENDING)
}

// ============================================================
// 变更操作（条件校验在 service 层做）
// ============================================================

/** 创建投递（若此前撤回，则复用原记录重新激活） */
export function createApplication(input: {
  userId: string
  jobId: string
  companyId: string
  resumeId: string
  coverLetter?: string
}): Application {
  ensureSeed()
  const nowIso = new Date().toISOString()
  const existing = applications.find(a => a.userId === input.userId && a.jobId === input.jobId)
  if (existing) {
    // 撤回后重新投递
    existing.status = ApplicationStatusEnum.SUBMITTED
    existing.coverLetter = input.coverLetter
    existing.updatedAt = nowIso
    return existing
  }
  appSeq += 1
  const app: Application = {
    id: `app-${appSeq}`,
    userId: input.userId,
    jobId: input.jobId,
    companyId: input.companyId,
    resumeId: input.resumeId,
    coverLetter: input.coverLetter,
    status: ApplicationStatusEnum.SUBMITTED,
    createdAt: nowIso,
    updatedAt: nowIso,
  }
  applications.unshift(app)
  return app
}

/** 撤回投递 */
export function withdrawApplicationRecord(id: string): Application | undefined {
  const app = applicationById(id)
  if (!app) return undefined
  app.status = ApplicationStatusEnum.WITHDRAWN
  app.updatedAt = new Date().toISOString()
  return app
}

/** 更新投递状态（HR 标记） */
export function setApplicationStatus(id: string, status: ApplicationStatusEnum): Application | undefined {
  const app = applicationById(id)
  if (!app) return undefined
  app.status = status
  app.updatedAt = new Date().toISOString()
  return app
}

/** 创建面试邀请（HR），并把投递置为「面试中」；返回新面试记录 */
export function createInterviewRecord(
  applicationId: string,
  invite: {
    scheduledAt: string
    format: InterviewFormatEnum
    location?: string
    onlineUrl?: string
    note?: string
  },
): Interview | undefined {
  const app = applicationById(applicationId)
  if (!app) return undefined
  intSeq += 1
  const nowIso = new Date().toISOString()
  const interview: Interview = {
    id: `int-${intSeq}`,
    applicationId,
    companyId: app.companyId,
    scheduledAt: invite.scheduledAt,
    format: invite.format,
    location: invite.location,
    onlineUrl: invite.onlineUrl,
    note: invite.note,
    status: InterviewStatusEnum.PENDING,
    createdAt: nowIso,
    updatedAt: nowIso,
  }
  interviews.unshift(interview)
  app.status = ApplicationStatusEnum.INTERVIEW
  app.updatedAt = nowIso
  return interview
}

/** 更新面试状态（接受 / 拒绝） */
export function setInterviewStatus(id: string, status: InterviewStatusEnum): Interview | undefined {
  const interview = interviewById(id)
  if (!interview) return undefined
  interview.status = status
  interview.updatedAt = new Date().toISOString()
  return interview
}

// ============================================================
// 候选人画像（与 ./candidates 对齐；求职者测试账号补充真实画像）
// ============================================================

export function candidateInfoOf(userId: string): CandidateSeed {
  const cand = candidateById(userId)
  if (cand) return cand
  if (userId === MOCK_SEEKER_USER.id) {
    const p: UserProfile = MOCK_SEEKER_PROFILE
    return {
      userId,
      name: p.name,
      expect: p.expectSub ?? '—',
      city: p.expectCity ?? '—',
      workYears: p.workYears ?? 0,
      edu: p.edu ?? '—',
      resumeCompleteness: 85,
    }
  }
  return { userId, name: '候选人', expect: '—', city: '—', workYears: 0, edu: '—', resumeCompleteness: 0 }
}

// ============================================================
// 视图组装
// ============================================================

/** 求职者视角列表（按更新时间倒序；职位下线时 job = null） */
export function seekerApplicationViews(userId: string): SeekerApplicationView[] {
  ensureSeed()
  return applications
    .filter(a => a.userId === userId)
    .sort((a, b) => new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime())
    .map(a => ({
      application: a,
      job: jobById(a.jobId) ?? null,
      interview: pendingInterviewOfApplication(a.id) ?? interviewsOfApplication(a.id)[0] ?? null,
    }))
}

/** HR 视角列表（公司维度，按更新时间倒序） */
export function companyApplicationViews(companyId: string): HrApplicationView[] {
  ensureSeed()
  return applications
    .filter(a => a.companyId === companyId)
    .sort((a, b) => new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime())
    .map(a => {
      const job = hrJobById(a.jobId) ?? jobs.find(j => j.id === a.jobId)
      return {
        application: a,
        candidate: candidateInfoOf(a.userId),
        job: {
          id: a.jobId,
          title: job?.title ?? '职位已删除',
          salary: job?.salary ?? '',
          city: job?.city ?? '',
        },
        conversationId: findConversationId(a.userId, a.jobId, companyId),
        interview: pendingInterviewOfApplication(a.id) ?? interviewsOfApplication(a.id)[0],
      } satisfies HrApplicationView
    })
}

/** 查已存在的「候选人 × 职位」会话 id */
function findConversationId(userId: string, jobId: string, companyId: string): string | undefined {
  return hrConversationsOf(companyId).find(
    v => v.conversation.userId === userId && v.conversation.jobId === jobId,
  )?.conversation.id
}

/**
 * 确保「候选人 × 职位」会话存在（投递 → HR 发消息 / 约面试用）。
 * 返回会话 id；联调阶段由后端在 HR 主动触达时创建。
 */
export function ensureConversationForApplication(applicationId: string): string | undefined {
  const app = applicationById(applicationId)
  if (!app) return undefined
  const hrId = hrJobById(app.jobId)?.hr.id ?? jobs.find(j => j.id === app.jobId)?.hr.id ?? MOCK_HR_PROFILE.id
  return ensureHrConversationForCandidate(app.userId, app.jobId, hrId, app.companyId)
}

/** 发送面试邀请消息（INTERVIEW_INVITE 类型，content 为 JSON 字符串） */
export function appendInterviewInviteMessage(
  conversationId: string,
  interview: Interview,
  hrId: string,
): void {
  const payload = {
    interviewId: interview.id,
    scheduledAt: interview.scheduledAt,
    format: interview.format,
    location: interview.location,
    onlineUrl: interview.onlineUrl,
    note: interview.note,
  }
  appendHrMessage(conversationId, JSON.stringify(payload), hrId, MessageTypeEnum.INTERVIEW_INVITE)
}
