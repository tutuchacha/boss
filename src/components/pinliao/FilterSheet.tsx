/**
 * 聘聊 · 筛选 Sheet
 * =====================================================================
 * 三个筛选项：排序 / 经验 / 薪资
 * 临时状态存于 store.filterDraft，确认后才写入 filters
 * =====================================================================
 */
'use client'

import { usePinliaoStore } from '@/store/pinliao'
import { BottomSheet } from './BottomSheet'
import { EXPS_RAW, SALS_RAW } from '@/api/mock/dict'
import { cn } from '@/lib/utils'

const SORTS = [
  { key: 'recommend', label: '推荐' },
  { key: 'latest', label: '最新' },
] as const

function Section({
  title,
  options,
  value,
  onPick,
}: {
  title: string
  options: string[]
  value: string
  onPick: (v: string) => void
}) {
  return (
    <div className="px-4 py-3">
      <h3 className="mb-2 text-xs font-medium text-pl-sub">{title}</h3>
      <div className="grid grid-cols-3 gap-2 sm:grid-cols-4">
        {options.map(o => (
          <button
            key={o}
            onClick={() => onPick(o)}
            className={cn(
              'rounded-md px-3 py-2 text-sm transition-colors',
              o === value
                ? 'bg-primary text-primary-foreground'
                : 'bg-muted text-foreground hover:bg-accent',
            )}
          >
            {o}
          </button>
        ))}
      </div>
    </div>
  )
}

export function FilterSheet() {
  const sheet = usePinliaoStore(s => s.sheet)
  const draft = usePinliaoStore(s => s.filterDraft)
  const setFilterDraft = usePinliaoStore(s => s.setFilterDraft)
  const applyFilterDraft = usePinliaoStore(s => s.applyFilterDraft)
  const resetFilter = usePinliaoStore(s => s.resetFilter)
  const closeSheet = usePinliaoStore(s => s.closeSheet)

  if (sheet !== 'filter') return null

  return (
    <BottomSheet title="筛选职位" onClose={closeSheet}>
      <Section
        title="排序"
        options={SORTS.map(s => s.label)}
        value={SORTS.find(s => s.key === draft.sort)?.label ?? '推荐'}
        onPick={label => {
          const key = SORTS.find(s => s.label === label)?.key ?? 'recommend'
          setFilterDraft({ sort: key })
        }}
      />
      <Section
        title="经验要求"
        options={EXPS_RAW}
        value={draft.exp}
        onPick={v => setFilterDraft({ exp: v })}
      />
      <Section
        title="薪资范围"
        options={SALS_RAW}
        value={draft.sal}
        onPick={v => setFilterDraft({ sal: v })}
      />

      <div className="sticky bottom-0 flex gap-3 border-t border-border bg-card p-4 pl-safe-bottom">
        <button
          onClick={resetFilter}
          className="flex-1 rounded-md border border-border py-2.5 text-sm text-foreground"
        >
          重置
        </button>
        <button
          onClick={applyFilterDraft}
          className="flex-1 rounded-md bg-primary py-2.5 text-sm font-medium text-primary-foreground"
        >
          查看结果
        </button>
      </div>
    </BottomSheet>
  )
}
