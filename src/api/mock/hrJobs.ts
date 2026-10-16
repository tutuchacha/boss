/**
 * 聘聊 · Mock 数据 - HR 端职位管理（可变状态）
 * =====================================================================
 * 对应契约：docs/api-endpoints.md §6.5 ~ §6.8、§4.3
 *  - GET    /api/v1/hr/jobs            我（所在公司）发布的职位
 *  - POST   /api/v1/jobs               发布职位
 *  - PATCH  /api/v1/jobs/{jobId}       更新职位
 *  - POST   /api/v1/jobs/{jobId}/close 关闭职位
 *  - POST   /api/v1/jobs/{jobId}/reopen 重新开放
 *  - DELETE /api/v1/jobs/{jobId}       删除（软删）
 *
 * 设计说明：
 *  - 模块级可变数组 `hrJobRecords`，页面内增删改立即可见（内存态，刷新重置）。
 *  - 初始数据从 jobs.ts 的真实 Mock 职位派生，保证 HR 工作台一进来就有内容。
 *  - 初始状态 OPEN/CLOSED 由 id 哈希决定（稳定，不用随机，避免 SSR/CSR 不一致）。
 * =====================================================================
 */

import type { Company, HrProfile, JobStatusEnum, JobSummary, JobUpsertInput } from '@/types/api'
import { JobStatusEnum as Status } from '@/types/api'

/** HR 端可见的记录 = 职位精简信息 + 详情字段 + 状态 */
export interface HrJobRecord extends JobSummary {
  status: JobStatusEnum
  responsibilities?: string
  requirements?: string
  welfare: string[]
  /** 软删标记：为 true 时对 HR 列表与求职者端均不可见 */
  deleted: boolean
  /** 所属公司 id（多租户字段，冗余存储便于过滤） */
  companyId: string
}

/**
 * 职位归属上下文：由 service 层按当前登录身份注入。
 * 历史上这里写死演示公司（云杉科技），导致注册的新公司 HR 发布职位
 * 会挂到云杉名下；改为显式传参后，多租户语义才成立。
 */
export interface HrJobOwner {
  company: Pick<Company, 'id' | 'name' | 'stage' | 'size'>
  hr: Pick<HrProfile, 'id' | 'name' | 'title'>
}

/** 稳定哈希（不用 Math.random，保证每次渲染结果一致） */
const stableHash = (s: string): number => {
  let h = 0
  for (let i = 0; i < s.length; i++) h = (h * 31 + s.charCodeAt(i)) >>> 0
  return h
}

const DEFAULT_WELFARE = ['五险一金', '带薪年假', '年度体检', '弹性工作']

const DEFAULT_RESP = `1. 负责业务系统的设计、开发与迭代维护；
2. 参与技术方案评审，保障代码质量与交付进度；
3. 与产品、设计、测试紧密协作，持续优化用户体验。`

const DEFAULT_REQ = `1. 本科及以上学历，计算机相关专业优先；
2. 具备扎实的编程基础与良好的工程习惯；
3. 有较强的责任心与团队协作意识。`

/**
 * 模块级可变数据。初始为空，由 initHrJobs() 从基础职位派生。
 *
 * 为什么不在模块顶层直接 `baseJobs.map(...)`：
 *   jobs.ts 需要 hrJobRecords 来合成求职者可见职位（jobById / listSeekerJobs），
 *   而本模块又需要 jobs 作为初始数据 —— 直接互相 import 会形成循环依赖，
 *   在 Next/Turbopack 下表现为模块初始化期取到 undefined 并抛错（实测 500）。
 *   故改为惰性初始化：两条边都指向一个「引导模块」，由它按序调用。
 */
export const hrJobRecords: HrJobRecord[] = []

/** 是否已初始化（引导模块调用 initHrJobs 后为 true） */
let initialized = false

/** 由 src/api/mock/bootstrap.ts 调用：用基础职位填充 HR 端记录 */
export function initHrJobs(baseJobs: JobSummary[]): void {
  if (initialized) return
  initialized = true
  hrJobRecords.push(
    ...baseJobs.map(j => ({
      ...j,
      // 用 id 哈希让约 1/4 的职位初始为「已关闭」，便于验收上下架状态
      status: stableHash(j.id) % 4 === 0 ? Status.CLOSED : Status.OPEN,
      responsibilities: DEFAULT_RESP,
      requirements: DEFAULT_REQ,
      welfare: DEFAULT_WELFARE,
      deleted: false,
      companyId: j.company.id,
    })),
  )
}

/** 生成新职位 id（用递增计数器，避免 Date.now() 造成 SSR/CSR 不一致） */
let idSeq = 0
const nextJobId = (): string => {
  idSeq += 1
  return `job-new-${idSeq}`
}

/**
 * 查询某公司发布的职位。
 * @param companyId 租户 id
 * @param hrId      可选：仅看该 HR 自己发布的
 */
export const listHrJobs = (companyId: string, hrId?: string): HrJobRecord[] =>
  hrJobRecords.filter(
    r =>
      !r.deleted &&
      r.companyId === companyId &&
      (hrId ? r.hr.id === hrId : true),
  )

/** 按 id 查（不含软删） */
export const hrJobById = (jobId: string): HrJobRecord | undefined =>
  hrJobRecords.find(r => r.id === jobId && !r.deleted)

/** 把表单入参转成完整记录 */
const toRecord = (
  input: JobUpsertInput,
  opts: { id: string; status: JobStatusEnum; owner: HrJobOwner },
): HrJobRecord => {
  const { id, status, owner } = opts
  const now = new Date().toISOString()
  return {
    id,
    title: input.title,
    salaryMin: input.salaryMin,
    salaryMax: input.salaryMax,
    salary: `${input.salaryMin}-${input.salaryMax}K`,
    city: input.city,
    area: input.area,
    exp: input.exp,
    edu: input.edu,
    cat: input.cat,
    sub: input.sub,
    tags: input.tags,
    company: {
      id: owner.company.id,
      name: owner.company.name,
      stage: owner.company.stage ?? '',
      size: owner.company.size ?? '',
    },
    hr: {
      id: owner.hr.id,
      name: owner.hr.name,
      title: owner.hr.title,
    },
    days: 0,
    publishedAt: now,
    viewCount: 0,
    applyCount: 0,
    status,
    responsibilities: input.responsibilities,
    requirements: input.requirements,
    welfare: input.welfare,
    deleted: false,
    companyId: owner.company.id,
  }
}

/** 发布职位（归属公司 / HR 由调用方注入） */
export const createHrJob = (input: JobUpsertInput, owner: HrJobOwner): HrJobRecord => {
  const rec = toRecord(input, { id: nextJobId(), status: Status.OPEN, owner })
  hrJobRecords.unshift(rec)
  return rec
}

/** 更新职位（仅允许本公司；保留原发布者与统计） */
export const updateHrJob = (
  jobId: string,
  input: JobUpsertInput,
  companyId: string,
): HrJobRecord | undefined => {
  const idx = hrJobRecords.findIndex(r => r.id === jobId && r.companyId === companyId)
  if (idx < 0) return undefined
  const prev = hrJobRecords[idx]
  // 保留原状态与统计，只替换可编辑字段
  const next: HrJobRecord = {
    ...prev,
    ...toRecord(input, {
      id: prev.id,
      status: prev.status,
      owner: { company: prev.company, hr: prev.hr },
    }),
    publishedAt: prev.publishedAt,
    viewCount: prev.viewCount,
    applyCount: prev.applyCount,
  }
  hrJobRecords[idx] = next
  return next
}

/** 关闭 / 重新开放 */
export const setHrJobStatus = (
  jobId: string,
  status: JobStatusEnum,
  companyId: string,
): HrJobRecord | undefined => {
  const rec = hrJobRecords.find(r => r.id === jobId && r.companyId === companyId && !r.deleted)
  if (!rec) return undefined
  rec.status = status
  return rec
}

/** 软删除 */
export const softDeleteHrJob = (jobId: string, companyId: string): boolean => {
  const rec = hrJobRecords.find(r => r.id === jobId && r.companyId === companyId)
  if (!rec) return false
  rec.deleted = true
  return true
}

// ============================================================
// 表单选项（HR 发布职位用，与求职者端筛选字典保持同源语义）
// ============================================================

export const HR_CITY_OPTIONS = [
  '全国', '北京', '上海', '广州', '深圳', '杭州', '成都', '武汉', '苏州', '南京',
]

export const HR_EXP_OPTIONS = ['应届', '1-3年', '3-5年', '5-10年', '经验不限']
export const HR_EDU_OPTIONS = ['大专', '本科', '硕士', '学历不限']
export const HR_WELFARE_OPTIONS = [
  '五险一金', '带薪年假', '年度体检', '弹性工作', '免费三餐',
  '股票期权', '定期团建', '带薪病假', '节日福利', '班车',
]
