/**
 * 聘聊 · Mock 数据同步过滤
 * =====================================================================
 * 仅用于 JobsView 在 mock 模式下的同步列表渲染。
 * 与 src/api/jobs.ts listJobs 逻辑保持一致。
 * Step 3 联调阶段此文件不再被调用。
 * =====================================================================
 */

import type { JobSummary, SalaryRangeKey } from '@/types/api'
import { jobs } from '@/api/mock/jobs'

const SAL_RANGE: Record<SalaryRangeKey, [number, number]> = {
  不限: [0, 999],
  '10K以下': [0, 10],
  '10-20K': [10, 20],
  '20-40K': [20, 40],
  '40K以上': [40, 999],
}

function daysFromNow(iso: string): number {
  return Math.max(0, Math.floor((Date.now() - new Date(iso).getTime()) / 86400000))
}

export interface FilterParams {
  q?: string
  city?: string
  cat?: string
  sub?: string
  exp?: string
  sal?: SalaryRangeKey
  sort?: 'recommend' | 'latest'
  expectCat?: string
  expectSub?: string
}

export function filterJobsSync(params: FilterParams): JobSummary[] {
  const {
    q = '',
    city = '全国',
    cat = '全部',
    sub = '全部',
    exp = '不限',
    sal = '不限',
    sort = 'recommend',
    expectCat,
    expectSub,
  } = params

  const [sMin, sMax] = SAL_RANGE[sal]
  const kw = q.trim().toLowerCase()

  let list = jobs.filter(j => {
    if (city !== '全国' && j.city !== city) return false
    if (cat !== '全部' && j.cat !== cat) return false
    if (sub !== '全部' && j.sub !== sub) return false
    if (exp !== '不限' && j.exp !== exp) return false
    if (j.salaryMin >= sMax || j.salaryMax <= sMin) return false
    if (kw) {
      const hay = [j.title, j.company.name, j.cat, j.sub, j.tags.join(' '), j.area]
        .join(' ')
        .toLowerCase()
      if (!hay.includes(kw)) return false
    }
    return true
  })

  if (sort === 'latest') {
    list = list.slice().sort((a, b) => daysFromNow(a.publishedAt) - daysFromNow(b.publishedAt))
  } else {
    const score = (j: JobSummary) =>
      j.sub === expectSub ? 2 : j.cat === expectCat ? 1 : 0
    list = list.slice().sort((a, b) => score(b) - score(a))
  }
  return list
}
