/**
 * 聘聊 · 收藏列表页
 * =====================================================================
 * 对齐 HTML 原型 favsHTML()。
 * =====================================================================
 */
'use client'

import { useMemo } from 'react'
import { usePinliaoStore } from '@/store/pinliao'
import { PageShell } from './JobDetailPage'
import { JobRow } from './JobRow'
import { jobById } from '@/api/mock/jobs'

interface Props {
  onBack: () => void
}

export function FavoritesPage({ onBack }: Props) {
  const favorites = usePinliaoStore(s => s.favorites)
  const pushPage = usePinliaoStore(s => s.pushPage)

  const list = useMemo(
    () =>
      favorites
        .map(id => jobById(id))
        .filter((j): j is NonNullable<typeof j> => !!j),
    [favorites],
  )

  return (
    <PageShell title="收藏的职位" onBack={onBack}>
      {list.length === 0 ? (
        <div className="flex flex-col items-center justify-center gap-2 px-8 py-20 text-center">
          <p className="text-sm font-medium text-foreground">还没有收藏的职位</p>
          <p className="text-xs text-pl-sub">
            在职位详情点右上角的星标，就会出现在这里。
          </p>
        </div>
      ) : (
        <div className="pl-scroll flex-1 overflow-y-auto bg-background">
          {list.map(j => (
            <JobRow
              key={j.id}
              job={j}
              isFavorite
              onClick={() => pushPage({ type: 'job', id: j.id })}
            />
          ))}
        </div>
      )}
    </PageShell>
  )
}
