/**
 * 聘聊 · HR 端职位管理 service
 * =====================================================================
 * 与 docs/api-endpoints.md 对应：
 *  - GET    /api/v1/hr/jobs             §4.3 我发布的职位
 *  - POST   /api/v1/jobs                §6.5 发布职位
 *  - PATCH  /api/v1/jobs/{jobId}        §6.6 更新职位
 *  - POST   /api/v1/jobs/{jobId}/close  §6.7 关闭职位
 *  - POST   /api/v1/jobs/{jobId}/reopen §6.7 重新开放
 *  - DELETE /api/v1/jobs/{jobId}        §6.8 删除（软删）
 *
 * 鉴权：HR / COMPANY_ADMIN（本公司）
 * Mock 阶段直接操作 src/api/mock/hrJobs.ts 的内存数组。
 * =====================================================================
 */

import type { JobStatusEnum, JobUpsertInput } from '@/types/api'
import { JobStatusEnum as Status } from '@/types/api'
import { USE_MOCK } from '@/config/env'
import { ApiError, request } from './client'
import type { HrJobRecord } from './mock'
import {
  createHrJob,
  hrJobById,
  listHrJobs,
  setHrJobStatus,
  softDeleteHrJob,
  updateHrJob,
} from './mock'

export type { HrJobRecord }

/** 表单选项（转发自 mock 字典，便于组件只依赖 service 层） */
export {
  HR_CITY_OPTIONS,
  HR_EDU_OPTIONS,
  HR_EXP_OPTIONS,
  HR_WELFARE_OPTIONS,
} from './mock'

/** 列表过滤条件 */
export interface HrJobListParams {
  companyId: string
  /** HR profile id；传了就只看自己发布的 */
  hrId?: string
  status?: JobStatusEnum | 'ALL'
  /** 关键字（标题模糊匹配） */
  q?: string
}

/** §4.3 我（所在公司）发布的职位 */
export async function listMyJobs(
  params: HrJobListParams,
): Promise<HrJobRecord[]> {
  if (USE_MOCK) {
    const { companyId, hrId, status = 'ALL', q = '' } = params
    const kw = q.trim().toLowerCase()
    return listHrJobs(companyId, hrId).filter(r => {
      if (status !== 'ALL' && r.status !== status) return false
      if (kw && !r.title.toLowerCase().includes(kw)) return false
      return true
    })
  }
  return request<HrJobRecord[]>('/hr/jobs', {
    method: 'GET',
    query: {
      status: params.status === 'ALL' ? undefined : params.status,
      q: params.q,
      hrId: params.hrId,
    },
  })
}

/** 单个职位（HR 视角，含状态） */
export async function getMyJob(jobId: string): Promise<HrJobRecord> {
  if (USE_MOCK) {
    const rec = hrJobById(jobId)
    if (!rec) {
      throw new ApiError({ code: 40410, message: '职位不存在', status: 404 })
    }
    return rec
  }
  return request<HrJobRecord>(`/hr/jobs/${jobId}`)
}

/** §6.5 发布职位 */
export async function createJob(input: JobUpsertInput): Promise<HrJobRecord> {
  if (USE_MOCK) {
    await new Promise(r => setTimeout(r, 280))
    return createHrJob(input)
  }
  return request<HrJobRecord>('/jobs', { method: 'POST', body: input })
}

/** §6.6 更新职位（本公司） */
export async function updateJob(
  jobId: string,
  input: JobUpsertInput,
  companyId: string,
): Promise<HrJobRecord> {
  if (USE_MOCK) {
    await new Promise(r => setTimeout(r, 280))
    const rec = updateHrJob(jobId, input, companyId)
    if (!rec) {
      throw new ApiError({ code: 40310, message: '无权操作该职位', status: 403 })
    }
    return rec
  }
  return request<HrJobRecord>(`/jobs/${jobId}`, { method: 'PATCH', body: input })
}

/** §6.7 关闭 / 重新开放 */
export async function closeJob(
  jobId: string,
  companyId: string,
): Promise<HrJobRecord> {
  return changeStatus(jobId, Status.CLOSED, companyId, 'close')
}

export async function reopenJob(
  jobId: string,
  companyId: string,
): Promise<HrJobRecord> {
  return changeStatus(jobId, Status.OPEN, companyId, 'reopen')
}

async function changeStatus(
  jobId: string,
  status: JobStatusEnum,
  companyId: string,
  action: 'close' | 'reopen',
): Promise<HrJobRecord> {
  if (USE_MOCK) {
    const rec = setHrJobStatus(jobId, status, companyId)
    if (!rec) {
      throw new ApiError({ code: 40310, message: '无权操作该职位', status: 403 })
    }
    return rec
  }
  return request<HrJobRecord>(`/jobs/${jobId}/${action}`, { method: 'POST' })
}

/** §6.8 删除（软删） */
export async function deleteJob(
  jobId: string,
  companyId: string,
): Promise<void> {
  if (USE_MOCK) {
    const ok = softDeleteHrJob(jobId, companyId)
    if (!ok) {
      throw new ApiError({ code: 40310, message: '无权操作该职位', status: 403 })
    }
    return
  }
  return request<void>(`/jobs/${jobId}`, { method: 'DELETE' })
}

// ============================================================
// 表单校验（与契约的字段约束对齐；返回字段级错误）
// ============================================================

export interface JobFormDraft {
  title: string
  salaryMin: string
  salaryMax: string
  city: string
  area: string
  exp: string
  edu: string
  cat: string
  sub: string
  tagsText: string
  responsibilities: string
  requirements: string
  welfare: string[]
}

export type JobFormErrors = Partial<Record<keyof JobFormDraft, string>>

export const EMPTY_JOB_FORM: JobFormDraft = {
  title: '',
  salaryMin: '',
  salaryMax: '',
  city: '上海',
  area: '',
  exp: '3-5年',
  edu: '本科',
  cat: '互联网',
  sub: '前端开发',
  tagsText: '',
  responsibilities: '',
  requirements: '',
  welfare: ['五险一金', '带薪年假'],
}

/** 表单校验：返回错误对象（空对象表示通过） */
export function validateJobForm(draft: JobFormDraft): JobFormErrors {
  const e: JobFormErrors = {}

  const title = draft.title.trim()
  if (!title) e.title = '请填写职位名称'
  else if (title.length < 2) e.title = '职位名称至少 2 个字'
  else if (title.length > 30) e.title = '职位名称不超过 30 个字'

  const min = Number(draft.salaryMin)
  const max = Number(draft.salaryMax)
  if (!draft.salaryMin) e.salaryMin = '请填写最低薪资'
  else if (!Number.isFinite(min) || min <= 0) e.salaryMin = '薪资需为正数'
  else if (min > 999) e.salaryMin = '薪资数值过大'

  if (!draft.salaryMax) e.salaryMax = '请填写最高薪资'
  else if (!Number.isFinite(max) || max <= 0) e.salaryMax = '薪资需为正数'
  else if (max > 999) e.salaryMax = '薪资数值过大'

  if (!e.salaryMin && !e.salaryMax && min >= max) {
    e.salaryMax = '最高薪资需大于最低薪资'
  }

  if (!draft.city.trim()) e.city = '请选择城市'
  if (!draft.cat.trim()) e.cat = '请选择职位大类'
  if (!draft.sub.trim()) e.sub = '请选择职位子类'

  const tags = parseTags(draft.tagsText)
  if (tags.length > 8) e.tagsText = '标签最多 8 个'

  return e
}

/** 标签文本 → 数组（逗号/顿号/空格分隔，去重去空） */
export function parseTags(text: string): string[] {
  return Array.from(
    new Set(
      text
        .split(/[,，、\s]+/)
        .map(t => t.trim())
        .filter(Boolean),
    ),
  )
}

/** 表单草稿 → 契约入参 */
export function draftToInput(draft: JobFormDraft): JobUpsertInput {
  return {
    title: draft.title.trim(),
    salaryMin: Number(draft.salaryMin),
    salaryMax: Number(draft.salaryMax),
    city: draft.city.trim(),
    area: draft.area.trim() || undefined,
    exp: draft.exp,
    edu: draft.edu,
    cat: draft.cat.trim(),
    sub: draft.sub.trim(),
    tags: parseTags(draft.tagsText),
    responsibilities: draft.responsibilities.trim() || undefined,
    requirements: draft.requirements.trim() || undefined,
    welfare: draft.welfare,
  }
}

/** 已有职位记录 → 表单草稿（编辑时回填） */
export function recordToDraft(rec: HrJobRecord): JobFormDraft {
  return {
    title: rec.title,
    salaryMin: String(rec.salaryMin),
    salaryMax: String(rec.salaryMax),
    city: rec.city,
    area: rec.area ?? '',
    exp: rec.exp,
    edu: rec.edu,
    cat: rec.cat,
    sub: rec.sub,
    tagsText: rec.tags.join('、'),
    responsibilities: rec.responsibilities ?? '',
    requirements: rec.requirements ?? '',
    welfare: rec.welfare ?? [],
  }
}

export { Status as JobStatus }
