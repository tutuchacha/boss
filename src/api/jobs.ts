/**
 * 聘聊 · 职位 service
 * =====================================================================
 * 与 docs/api-endpoints.md §6 对应：
 *  - GET /api/v1/jobs                  列表（搜索+筛选）
 *  - GET /api/v1/search/jobs           搜索（独立路径，预留 ES）
 *  - GET /api/v1/jobs/{jobId}          详情
 *  - GET /api/v1/jobs/{jobId}/similar  相似职位
 *  - POST /api/v1/jobs/{jobId}/view    浏览埋点
 *  （发布/更新/关闭/删除等招聘者端接口略，Step 3 后端阶段实现）
 * =====================================================================
 */

import type {
  JobDetail,
  JobListParams,
  JobSummary,
  PaginatedData,
} from '@/types/api'
import { USE_MOCK } from '@/config/env'
import { request } from './client'
import { jobById, jobs } from './mock'
import type { SalaryRangeKey } from '@/types/api'

/** 薪资区间 → [min, max]（K） */
const SAL_RANGE: Record<SalaryRangeKey, [number, number]> = {
  不限: [0, 999],
  '10K以下': [0, 10],
  '10-20K': [10, 20],
  '20-40K': [20, 40],
  '40K以上': [40, 999],
}

/** 距今天数（从 publishedAt 计算） */
function daysFromNow(iso: string): number {
  const d = new Date(iso).getTime()
  return Math.max(0, Math.floor((Date.now() - d) / 86400000))
}

/**
 * 职位列表（搜索 + 筛选 + 推荐排序）
 * Mock 实现与 HTML 原型 filtered() 逻辑保持一致。
 */
export async function listJobs(params: JobListParams = {}): Promise<PaginatedData<JobSummary>> {
  if (USE_MOCK) {
    const {
      q = '',
      city = '全国',
      cat = '全部',
      sub = '全部',
      exp = '不限',
      sal = '不限',
      sort = 'recommend',
    } = params

    const [sMin, sMax] = SAL_RANGE[sal]
    const kw = q.trim().toLowerCase()

    let list = jobs.filter(j => {
      if (city !== '全国' && j.city !== city) return false
      if (cat !== '全部' && j.cat !== cat) return false
      if (sub !== '全部' && j.sub !== sub) return false
      if (exp !== '不限' && j.exp !== exp) return false
      // 薪资区间相交
      if (j.salaryMin >= sMax || j.salaryMax <= sMin) return false
      if (kw) {
        const hay = [j.title, j.company.name, j.cat, j.sub, j.tags.join(' '), j.area].join(' ').toLowerCase()
        if (!hay.includes(kw)) return false
      }
      return true
    })

    if (sort === 'latest') {
      list = list.slice().sort((a, b) => daysFromNow(a.publishedAt) - daysFromNow(b.publishedAt))
    } else {
      // 推荐：按求职者期望岗位优先
      const profile = loadProfile()
      const score = (j: JobSummary) =>
        j.sub === profile.expectSub ? 2 : j.cat === profile.expectCat ? 1 : 0
      list = list.slice().sort((a, b) => score(b) - score(a))
    }

    return {
      list,
      pagination: { nextCursor: null, hasMore: false, total: list.length },
    }
  }
  return request<PaginatedData<JobSummary>>('/jobs', {
    method: 'GET',
    query: {
      q: params.q,
      city: params.city,
      cat: params.cat,
      sub: params.sub,
      exp: params.exp,
      sal: params.sal,
      sort: params.sort,
      cursor: params.cursor,
      limit: params.limit,
    },
  })
}

/** 职位详情（含公司 + HR + 相似职位） */
export async function getJobDetail(jobId: string): Promise<JobDetail> {
  if (USE_MOCK) {
    const j = jobById(jobId)
    if (!j) throw new Error(`职位不存在：${jobId}`)
    // 相似职位：同 sub 或同 cat，排除自身，取前 6
    const similar = jobs
      .filter(x => x.id !== jobId && (x.sub === j.sub || x.cat === j.cat))
      .slice(0, 6)
    return {
      ...j,
      companyId: j.company.id,
      hrId: j.hr.id,
      welfare: ['五险一金', '带薪年假', '年度体检', '弹性工作'].slice(0, 4),
      similarJobs: similar,
    }
  }
  return request<JobDetail>(`/jobs/${jobId}`)
}

/** 浏览埋点（Mock 仅在内存中累加，无持久化） */
export async function recordJobView(jobId: string): Promise<void> {
  if (USE_MOCK) return
  return request<void>(`/jobs/${jobId}/view`, { method: 'POST' })
}

// ---- 内部工具：从 localStorage 读取求职者期望（用于推荐排序） ----
function loadProfile(): { expectCat?: string; expectSub?: string } {
  if (typeof window === 'undefined') return {}
  try {
    const raw = window.localStorage.getItem('pinliao.v3')
    if (!raw) return {}
    const data = JSON.parse(raw)
    return {
      expectCat: data?.state?.profile?.expectCat,
      expectSub: data?.state?.profile?.expectSub,
    }
  } catch {
    return {}
  }
}
