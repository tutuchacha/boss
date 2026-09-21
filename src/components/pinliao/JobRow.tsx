/**
 * 聘聊 · 职位列表行
 * =====================================================================
 * 对齐 HTML 原型 jobRow() 视觉，使用 Tailwind + 主题变量。
 * =====================================================================
 */
'use client'

import type { JobSummary } from '@/types/api'
import { AvatarBadge } from './AvatarBadge'
import { whenText } from '@/lib/time'
import { cn } from '@/lib/utils'

interface Props {
  job: JobSummary
  isFavorite?: boolean
  onClick?: () => void
}

export function JobRow({ job, isFavorite, onClick }: Props) {
  return (
    <article
      role="button"
      tabIndex={0}
      onClick={onClick}
      onKeyDown={e => {
        if (e.key === 'Enter' || e.key === ' ') {
          e.preventDefault()
          onClick?.()
        }
      }}
      className={cn(
        'block cursor-pointer border-b border-border bg-card px-4 py-3.5',
        'transition-colors hover:bg-accent/40 focus-visible:bg-accent/40',
        'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring',
      )}
    >
      <div className="flex items-start justify-between gap-3">
        <h3 className="pl-line-clamp-1 flex-1 text-[15px] font-medium text-foreground">
          {job.title}
        </h3>
        <span className="shrink-0 text-[15px] font-semibold text-pl-sal">
          {job.salary}
        </span>
      </div>
      <div className="mt-1.5 flex flex-wrap items-center gap-x-2 gap-y-0.5 text-xs text-pl-sub">
        <span>{job.city} {job.area}</span>
        <span aria-hidden>·</span>
        <span>{job.exp}</span>
        <span aria-hidden>·</span>
        <span>{job.edu}</span>
      </div>
      {job.tags.length > 0 && (
        <div className="mt-2 flex flex-wrap gap-1.5">
          {job.tags.map(t => (
            <span
              key={t}
              className="rounded bg-secondary px-2 py-0.5 text-[11px] text-secondary-foreground"
            >
              {t}
            </span>
          ))}
        </div>
      )}
      <div className="mt-2.5 flex items-center gap-2">
        <AvatarBadge name={job.company.name} size={22} />
        <span className="pl-line-clamp-1 text-[13px] text-foreground">{job.company.name}</span>
        <span className="pl-line-clamp-1 text-[11px] text-pl-sub">
          {job.company.stage} · {job.company.size}
        </span>
        <span className="ml-auto shrink-0 text-[11px] text-pl-sub">
          {whenText(job.days)}
          {isFavorite ? ' · 已收藏' : ''}
        </span>
      </div>
    </article>
  )
}
