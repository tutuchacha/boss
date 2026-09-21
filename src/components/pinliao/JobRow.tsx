/**
 * 聘聊 · 职位列表行（紧凑版）
 * =====================================================================
 * 设计目标：单卡片高度约 96px（移动端），3 行内容：
 *  L1 标题 + 薪资
 *  L2 城市·经验·学历
 *  L3 公司 + 时间（省去 stage/size，进详情页才看）
 * 标签从 4 个裁剪到最多 2 个，避免卡片过高。
 * =====================================================================
 */
'use client'

import type { JobSummary } from '@/types/api'
import { whenText } from '@/lib/time'
import { cn } from '@/lib/utils'

interface Props {
  job: JobSummary
  isFavorite?: boolean
  onClick?: () => void
}

export function JobRow({ job, isFavorite, onClick }: Props) {
  // 标签最多保留 2 个，避免占据额外高度
  const tags = job.tags.slice(0, 2)
  const extraTagCount = Math.max(0, job.tags.length - 2)

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
        'block cursor-pointer border-b border-border bg-card px-4 py-2.5',
        'transition-colors hover:bg-accent/40 focus-visible:bg-accent/40',
        'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring',
      )}
    >
      {/* L1：标题 + 薪资 */}
      <div className="flex items-baseline justify-between gap-2">
        <h3 className="pl-line-clamp-1 flex-1 text-[15px] font-medium leading-tight text-foreground">
          {job.title}
        </h3>
        <span className="shrink-0 text-[15px] font-semibold leading-tight text-pl-sal">
          {job.salary}
        </span>
      </div>

      {/* L2：城市·经验·学历 + 标签（同行，超长截断） */}
      <div className="mt-1 flex items-center gap-1.5 text-xs text-pl-sub">
        <span className="shrink-0">{job.city}{job.area ? ` ${job.area}` : ''}</span>
        <span aria-hidden className="text-pl-sub/60">·</span>
        <span className="shrink-0">{job.exp}</span>
        <span aria-hidden className="text-pl-sub/60">·</span>
        <span className="shrink-0">{job.edu}</span>
        {tags.length > 0 && (
          <>
            <span aria-hidden className="text-pl-sub/60">·</span>
            <span className="pl-line-clamp-1 flex-1 text-pl-sub">
              {tags.join(' / ')}
              {extraTagCount > 0 && <span className="text-pl-sub/70"> +{extraTagCount}</span>}
            </span>
          </>
        )}
      </div>

      {/* L3：公司 + 时间（同行） */}
      <div className="mt-1.5 flex items-center gap-2 text-[11px] text-pl-sub">
        <span className="pl-line-clamp-1 flex-1 text-foreground/85">{job.company.name}</span>
        <span className="shrink-0">
          {whenText(job.days)}
          {isFavorite ? ' · 已收藏' : ''}
        </span>
      </div>
    </article>
  )
}
