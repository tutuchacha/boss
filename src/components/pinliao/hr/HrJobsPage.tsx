/**
 * 聘聊 · HR 工作台 - 职位管理列表
 * =====================================================================
 * 对应接口：GET /api/v1/hr/jobs（docs/api-endpoints.md §4.3）
 *
 * 功能：
 *  - 展示本公司发布的职位（可切换"仅看我发布的"）
 *  - 状态筛选（全部 / 招聘中 / 已关闭）+ 关键字搜索
 *  - 每行操作：编辑、关闭/重新开放、删除
 *  - 显著入口：发布新职位（+ 按钮 / 空状态引导）
 *
 * 取数方式：TanStack Query（项目已装且 Provider 已就位）。
 *  不使用手写 useEffect + setState 取数 —— 那会触发 React Compiler 的
 *  「setState in effect」告警，也让 loading/缓存需要手工维护。
 * =====================================================================
 */

'use client'

import { useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Briefcase,
  Eye,
  Pencil,
  Plus,
  RotateCcw,
  Search,
  Square,
  Trash2,
  Users,
} from 'lucide-react'
import { JobStatusEnum } from '@/types/api'
import type { HrJobRecord } from '@/api/hrJobs'
import {
  closeJob,
  deleteJob,
  listMyJobs,
  reopenJob,
} from '@/api/hrJobs'
import { useAuthStore } from '@/store/auth'
import { useHrStore } from '@/store/hr'
import { whenText } from '@/lib/time'
import { cn } from '@/lib/utils'
import { AvatarBadge } from '../AvatarBadge'

/** 状态筛选项 */
const STATUS_TABS: { key: JobStatusEnum | 'ALL'; label: string }[] = [
  { key: 'ALL', label: '全部' },
  { key: JobStatusEnum.OPEN, label: '招聘中' },
  { key: JobStatusEnum.CLOSED, label: '已关闭' },
]

const STATUS_TEXT: Record<JobStatusEnum, string> = {
  OPEN: '招聘中',
  CLOSED: '已关闭',
  DRAFT: '草稿',
}

/** 职位列表查询的 queryKey 前缀，供变更后失效使用 */
export const HR_JOBS_QUERY_KEY = ['hr', 'jobs'] as const

export function HrJobsPage() {
  const queryClient = useQueryClient()

  const company = useAuthStore(s => s.company)
  const hrProfile = useAuthStore(s => s.hrProfile)
  const hrId = useAuthStore(s => s.hrProfile?.id)

  const filters = useHrStore(s => s.filters)
  const setFilterStatus = useHrStore(s => s.setFilterStatus)
  const setFilterQ = useHrStore(s => s.setFilterQ)
  const setOnlyMine = useHrStore(s => s.setOnlyMine)
  const openEditor = useHrStore(s => s.openEditor)
  const dataVersion = useHrStore(s => s.dataVersion)
  const toast = useHrStore(s => s.toast)

  const [confirmId, setConfirmId] = useState<string | null>(null)
  const [busyId, setBusyId] = useState<string | null>(null)

  const companyId = company?.id ?? ''
  const onlyMineHrId = filters.onlyMine ? hrId : undefined

  /** 列表数据；dataVersion 变化时自动重新拉取 */
  const { data: rows = [], isPending } = useQuery({
    queryKey: [...HR_JOBS_QUERY_KEY, companyId, onlyMineHrId ?? 'all', filters.status, filters.q, dataVersion],
    queryFn: () =>
      listMyJobs({
        companyId,
        hrId: onlyMineHrId,
        status: filters.status,
        q: filters.q,
      }),
    enabled: Boolean(companyId),
  })

  const loading = Boolean(companyId) && isPending

  /** 变更后让列表失效重取（TanStack Query 自带去重与缓存） */
  const refresh = () =>
    queryClient.invalidateQueries({ queryKey: HR_JOBS_QUERY_KEY })

  /** 统计（基于当前筛选结果） */
  const stats = rows.reduce(
    (acc, r) => {
      if (r.status === JobStatusEnum.OPEN) acc.open += 1
      acc.apply += r.applyCount
      acc.view += r.viewCount
      return acc
    },
    { open: 0, apply: 0, view: 0 },
  )

  const hasFilter = Boolean(filters.q) || filters.status !== 'ALL' || filters.onlyMine

  /** 关闭 / 重新开放 */
  const onToggleStatus = async (rec: HrJobRecord) => {
    setBusyId(rec.id)
    try {
      if (rec.status === JobStatusEnum.OPEN) {
        await closeJob(rec.id, companyId)
        toast('职位已关闭，求职者端不再展示')
      } else {
        await reopenJob(rec.id, companyId)
        toast('职位已重新开放')
      }
      await refresh()
    } catch {
      toast('操作失败，请重试')
    } finally {
      setBusyId(null)
    }
  }

  /** 删除（二次确认） */
  const onDelete = async (id: string) => {
    setBusyId(id)
    try {
      await deleteJob(id, companyId)
      toast('职位已删除')
      setConfirmId(null)
      await refresh()
    } catch {
      toast('删除失败，请重试')
    } finally {
      setBusyId(null)
    }
  }

  return (
    <div className="flex flex-1 flex-col overflow-hidden">
      {/* 公司信息条 */}
      <div className="border-b border-border bg-card px-4 py-3">
        <div className="flex items-center gap-2.5">
          <AvatarBadge name={company?.name ?? '公司'} size={34} />
          <div className="min-w-0 flex-1">
            <p className="pl-line-clamp-1 text-[14px] font-medium text-foreground">
              {company?.name ?? '未绑定公司'}
            </p>
            <p className="pl-line-clamp-1 text-[11px] text-pl-sub">
              {hrProfile?.name} · {hrProfile?.title}
            </p>
          </div>
        </div>

        {/* 数据概览 */}
        <div className="mt-3 grid grid-cols-3 gap-2">
          <div className="rounded-lg bg-secondary/60 px-2.5 py-2">
            <p className="text-[17px] font-semibold leading-none text-foreground">{stats.open}</p>
            <p className="mt-1 text-[11px] text-pl-sub">招聘中</p>
          </div>
          <div className="rounded-lg bg-secondary/60 px-2.5 py-2">
            <p className="text-[17px] font-semibold leading-none text-foreground">{stats.apply}</p>
            <p className="mt-1 text-[11px] text-pl-sub">累计投递</p>
          </div>
          <div className="rounded-lg bg-secondary/60 px-2.5 py-2">
            <p className="text-[17px] font-semibold leading-none text-foreground">{stats.view}</p>
            <p className="mt-1 text-[11px] text-pl-sub">累计浏览</p>
          </div>
        </div>
      </div>

      {/* 筛选区 */}
      <div className="border-b border-border bg-card px-4 py-2.5">
        <div className="relative">
          <Search className="pointer-events-none absolute left-2.5 top-1/2 h-3.5 w-3.5 -translate-y-1/2 text-muted-foreground" />
          <input
            type="text"
            value={filters.q}
            onChange={e => setFilterQ(e.target.value)}
            placeholder="搜索职位名称"
            aria-label="搜索职位名称"
            className="h-9 w-full rounded-lg border border-border bg-background pl-8 pr-3 text-[13px] text-foreground placeholder:text-muted-foreground/70 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
          />
        </div>

        <div className="mt-2 flex items-center gap-1.5">
          {STATUS_TABS.map(t => {
            const active = filters.status === t.key
            return (
              <button
                key={t.key}
                type="button"
                onClick={() => setFilterStatus(t.key)}
                aria-pressed={active}
                className={cn(
                  'rounded-full px-2.5 py-1 text-[12px] transition-colors',
                  active
                    ? 'bg-primary text-primary-foreground'
                    : 'bg-secondary text-secondary-foreground hover:bg-accent',
                )}
              >
                {t.label}
              </button>
            )
          })}

          <label className="ml-auto flex cursor-pointer items-center gap-1.5 text-[12px] text-pl-sub">
            <input
              type="checkbox"
              checked={filters.onlyMine}
              onChange={e => setOnlyMine(e.target.checked)}
              className="h-3.5 w-3.5 accent-[var(--primary)]"
            />
            仅看我发布的
          </label>
        </div>
      </div>

      {/* 列表 */}
      <div className="pl-scroll flex-1 overflow-y-auto">
        {loading && <p className="px-4 py-8 text-center text-[13px] text-pl-sub">加载中…</p>}

        {!loading && rows.length === 0 && (
          <div className="flex flex-col items-center px-6 py-14 text-center">
            <span className="flex h-12 w-12 items-center justify-center rounded-full bg-secondary">
              <Briefcase className="h-5 w-5 text-muted-foreground" />
            </span>
            <p className="mt-3 text-[14px] font-medium text-foreground">
              {hasFilter ? '没有符合条件的职位' : '还没有发布职位'}
            </p>
            <p className="mt-1 text-[12px] text-pl-sub">
              {hasFilter
                ? '试着调整筛选条件或清空关键字'
                : '发布后求职者即可在职位列表看到'}
            </p>
            {!hasFilter && (
              <button
                type="button"
                onClick={() => openEditor()}
                className="mt-4 flex items-center gap-1.5 rounded-lg bg-primary px-4 py-2 text-[13px] font-medium text-primary-foreground hover:opacity-90"
              >
                <Plus className="h-3.5 w-3.5" />
                发布第一个职位
              </button>
            )}
          </div>
        )}

        {!loading &&
          rows.map(rec => {
            const isOpen = rec.status === JobStatusEnum.OPEN
            const busy = busyId === rec.id
            const confirming = confirmId === rec.id

            return (
              <article key={rec.id} className="border-b border-border bg-card px-4 py-3">
                <div className="flex items-start justify-between gap-3">
                  <h3 className="pl-line-clamp-1 flex-1 text-[15px] font-medium text-foreground">
                    {rec.title}
                  </h3>
                  <span className="shrink-0 text-[15px] font-semibold text-pl-sal">
                    {rec.salary}
                  </span>
                </div>

                <div className="mt-1.5 flex flex-wrap items-center gap-x-2 gap-y-0.5 text-[12px] text-pl-sub">
                  <span
                    className={cn(
                      'rounded px-1.5 py-0.5 text-[11px] font-medium',
                      isOpen ? 'bg-primary/10 text-primary' : 'bg-muted text-muted-foreground',
                    )}
                  >
                    {STATUS_TEXT[rec.status]}
                  </span>
                  <span>
                    {rec.city} {rec.area}
                  </span>
                  <span aria-hidden>·</span>
                  <span>{rec.exp}</span>
                  <span aria-hidden>·</span>
                  <span>{rec.edu}</span>
                  <span aria-hidden>·</span>
                  <span>{whenText(rec.days)}</span>
                </div>

                <div className="mt-2 flex items-center gap-3 text-[11px] text-pl-sub">
                  <span className="flex items-center gap-1">
                    <Users className="h-3 w-3" />
                    {rec.applyCount} 人投递
                  </span>
                  <span className="flex items-center gap-1">
                    <Eye className="h-3 w-3" />
                    {rec.viewCount} 次浏览
                  </span>
                </div>

                {/* 操作区 */}
                {confirming ? (
                  <div className="mt-3 rounded-lg border border-destructive/30 bg-destructive/5 px-3 py-2.5">
                    <p className="text-[12px] text-destructive">
                      确认删除「{rec.title}」？删除后求职者端不再展示。
                    </p>
                    <div className="mt-2 flex gap-2">
                      <button
                        type="button"
                        disabled={busy}
                        onClick={() => onDelete(rec.id)}
                        className="rounded-md bg-destructive px-3 py-1.5 text-[12px] font-medium text-destructive-foreground disabled:opacity-60"
                      >
                        {busy ? '删除中…' : '确认删除'}
                      </button>
                      <button
                        type="button"
                        onClick={() => setConfirmId(null)}
                        className="rounded-md border border-border px-3 py-1.5 text-[12px] text-foreground hover:bg-accent/40"
                      >
                        取消
                      </button>
                    </div>
                  </div>
                ) : (
                  <div className="mt-3 flex items-center gap-2">
                    <button
                      type="button"
                      onClick={() => openEditor(rec.id)}
                      className="flex items-center gap-1 rounded-md border border-border px-2.5 py-1.5 text-[12px] text-foreground transition-colors hover:bg-accent/40"
                    >
                      <Pencil className="h-3 w-3" />
                      编辑
                    </button>

                    <button
                      type="button"
                      disabled={busy}
                      onClick={() => onToggleStatus(rec)}
                      className="flex items-center gap-1 rounded-md border border-border px-2.5 py-1.5 text-[12px] text-foreground transition-colors hover:bg-accent/40 disabled:opacity-60"
                    >
                      {isOpen ? (
                        <>
                          <Square className="h-3 w-3" />
                          关闭
                        </>
                      ) : (
                        <>
                          <RotateCcw className="h-3 w-3" />
                          重新开放
                        </>
                      )}
                    </button>

                    <button
                      type="button"
                      onClick={() => setConfirmId(rec.id)}
                      aria-label={`删除 ${rec.title}`}
                      className="ml-auto flex items-center gap-1 rounded-md border border-border px-2.5 py-1.5 text-[12px] text-destructive transition-colors hover:bg-destructive/10"
                    >
                      <Trash2 className="h-3 w-3" />
                      删除
                    </button>
                  </div>
                )}
              </article>
            )
          })}

        <div className="h-4" />
      </div>
    </div>
  )
}
