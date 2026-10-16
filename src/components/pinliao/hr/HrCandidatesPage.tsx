/**
 * 聘聊 · HR 工作台 - 候选人管理
 * =====================================================================
 * 对应接口：GET /api/v1/hr/applications（docs/api-endpoints.md §4.4）
 *
 * 功能：
 *  - 公司收到的全部投递，按更新时间倒序
 *  - 状态筛选（待处理 / 已查看 / 面试中 / 已录用 / 未通过 / 已撤回）
 *  - 关键字搜索（候选人姓名 / 职位名）
 *  - 点击进入候选人详情（约面试、录用、不合适、发消息）
 *
 * 取数：TanStack Query + dataVersion（Mock 数据原地变更后自增触发刷新）。
 * =====================================================================
 */

'use client'

import { useMemo, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Search, Users } from 'lucide-react'
import { ApplicationStatusEnum } from '@/types/api'
import { APPLICATION_STATUS_TEXT } from '@/api/mock/applications'
import { HR_APPLICATIONS_QUERY_KEY, listHrApplications } from '@/api/hrApplications'
import { useAuthStore } from '@/store/auth'
import { useHrStore } from '@/store/hr'
import { convTimeText, formatDateTime } from '@/lib/time'
import { cn } from '@/lib/utils'
import { AvatarBadge } from '../AvatarBadge'

/** 状态筛选 chips（'ALL' = 全部） */
const STATUS_CHIPS: { key: ApplicationStatusEnum | 'ALL'; label: string }[] = [
  { key: 'ALL', label: '全部' },
  { key: ApplicationStatusEnum.SUBMITTED, label: '待处理' },
  { key: ApplicationStatusEnum.VIEWED, label: '已查看' },
  { key: ApplicationStatusEnum.INTERVIEW, label: '面试中' },
  { key: ApplicationStatusEnum.OFFER, label: '已录用' },
  { key: ApplicationStatusEnum.REJECTED, label: '未通过' },
  { key: ApplicationStatusEnum.WITHDRAWN, label: '已撤回' },
]

/** 投递状态 → 徽标配色（候选人列表 / 详情共用） */
export const STATUS_TONE: Record<ApplicationStatusEnum, string> = {
  [ApplicationStatusEnum.SUBMITTED]: 'bg-amber-500/10 text-amber-600 dark:text-amber-400',
  [ApplicationStatusEnum.VIEWED]: 'bg-sky-500/10 text-sky-600 dark:text-sky-400',
  [ApplicationStatusEnum.INTERVIEW]: 'bg-primary/10 text-primary',
  [ApplicationStatusEnum.OFFER]: 'bg-emerald-500/10 text-emerald-600 dark:text-emerald-400',
  [ApplicationStatusEnum.REJECTED]: 'bg-muted text-muted-foreground',
  [ApplicationStatusEnum.WITHDRAWN]: 'bg-muted text-muted-foreground',
}

export function HrCandidatesPage() {
  const company = useAuthStore(s => s.company)
  const openCandidate = useHrStore(s => s.openCandidate)
  const dataVersion = useHrStore(s => s.dataVersion)

  const [status, setStatus] = useState<ApplicationStatusEnum | 'ALL'>('ALL')
  const [q, setQ] = useState('')

  const companyId = company?.id ?? ''

  const { data: views = [], isPending } = useQuery({
    queryKey: [...HR_APPLICATIONS_QUERY_KEY, companyId, dataVersion],
    queryFn: () => listHrApplications(companyId),
    enabled: Boolean(companyId),
  })

  /** 各状态数量（chips 角标） */
  const counts = useMemo(() => {
    const m = new Map<ApplicationStatusEnum | 'ALL', number>()
    m.set('ALL', views.length)
    for (const v of views) {
      m.set(v.application.status, (m.get(v.application.status) ?? 0) + 1)
    }
    return m
  }, [views])

  const filtered = useMemo(() => {
    const kw = q.trim().toLowerCase()
    return views.filter(v => {
      if (status !== 'ALL' && v.application.status !== status) return false
      if (kw) {
        const hay = `${v.candidate.name} ${v.job.title}`.toLowerCase()
        if (!hay.includes(kw)) return false
      }
      return true
    })
  }, [views, status, q])

  return (
    <div className="flex flex-1 flex-col overflow-hidden">
      {/* 概览 + 筛选 */}
      <div className="border-b border-border bg-card px-4 py-3">
        <div className="flex items-center gap-2">
          <Users className="h-4 w-4 text-primary" />
          <h2 className="text-[14px] font-medium text-foreground">候选人管理</h2>
          <span className="ml-auto text-[11px] text-pl-sub">共 {views.length} 位候选人</span>
        </div>

        <div className="relative mt-2.5">
          <Search className="pointer-events-none absolute left-2.5 top-1/2 h-3.5 w-3.5 -translate-y-1/2 text-muted-foreground" />
          <input
            type="text"
            value={q}
            onChange={e => setQ(e.target.value)}
            placeholder="搜索候选人 / 职位"
            aria-label="搜索候选人"
            className="h-9 w-full rounded-lg border border-border bg-background pl-8 pr-3 text-[13px] text-foreground placeholder:text-muted-foreground/70 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
          />
        </div>

        <div className="mt-2 flex gap-1.5 overflow-x-auto pb-0.5">
          {STATUS_CHIPS.map(c => {
            const active = status === c.key
            const n = counts.get(c.key) ?? 0
            return (
              <button
                key={c.key}
                type="button"
                onClick={() => setStatus(c.key)}
                aria-pressed={active}
                className={cn(
                  'shrink-0 rounded-full px-2.5 py-1 text-[12px] transition-colors',
                  active
                    ? 'bg-primary text-primary-foreground'
                    : 'bg-secondary text-secondary-foreground hover:bg-accent',
                )}
              >
                {c.label}
                {c.key !== 'ALL' && n > 0 && <span className="ml-1 opacity-80">{n}</span>}
              </button>
            )
          })}
        </div>
      </div>

      {/* 候选人列表 */}
      <div className="pl-scroll flex-1 overflow-y-auto">
        {isPending && <p className="px-4 py-8 text-center text-[13px] text-pl-sub">加载中…</p>}

        {!isPending && filtered.length === 0 && (
          <div className="flex flex-col items-center px-6 py-14 text-center">
            <span className="flex h-12 w-12 items-center justify-center rounded-full bg-secondary">
              <Users className="h-5 w-5 text-muted-foreground" />
            </span>
            <p className="mt-3 text-[14px] font-medium text-foreground">
              {views.length === 0 ? '还没有收到投递' : '没有符合条件的候选人'}
            </p>
            <p className="mt-1 text-[12px] text-pl-sub">
              {views.length === 0
                ? '求职者投递职位后会出现在这里'
                : '试着调整筛选条件或搜索关键字'}
            </p>
          </div>
        )}

        {!isPending &&
          filtered.map(v => (
            <button
              key={v.application.id}
              type="button"
              onClick={() => openCandidate(v.application.id)}
              className="flex w-full items-start gap-2.5 border-b border-border bg-card px-4 py-3 text-left transition-colors hover:bg-accent/40 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
            >
              <AvatarBadge name={v.candidate.name} size={40} />
              <span className="min-w-0 flex-1">
                <span className="flex items-baseline gap-2">
                  <span className="pl-line-clamp-1 text-[14px] font-medium text-foreground">
                    {v.candidate.name}
                  </span>
                  <span className="pl-line-clamp-1 shrink-0 text-[11px] text-pl-sub">
                    {v.candidate.expect} · {v.candidate.workYears}年 · {v.candidate.edu}
                  </span>
                  <span className="ml-auto shrink-0 text-[11px] text-pl-sub">
                    {convTimeText(v.application.updatedAt)}
                  </span>
                </span>
                <span className="mt-0.5 block pl-line-clamp-1 text-[11px] text-primary/90">
                  应聘：{v.job.title}
                  {v.job.salary ? ` · ${v.job.salary}` : ''}
                </span>
                <span className="mt-1.5 flex items-center gap-2">
                  <span
                    className={cn(
                      'rounded px-1.5 py-0.5 text-[10px]',
                      STATUS_TONE[v.application.status],
                    )}
                  >
                    {APPLICATION_STATUS_TEXT[v.application.status]}
                  </span>
                  {v.interview && v.application.status === ApplicationStatusEnum.INTERVIEW && (
                    <span className="pl-line-clamp-1 text-[10px] text-pl-sub">
                      面试：{formatDateTime(v.interview.scheduledAt)}
                    </span>
                  )}
                </span>
              </span>
            </button>
          ))}

        <div className="h-4" />
      </div>
    </div>
  )
}
