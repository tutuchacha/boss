/**
 * 聘聊 · 城市选择 Sheet
 * =====================================================================
 */
'use client'

import { usePinliaoStore } from '@/store/pinliao'
import { BottomSheet } from './BottomSheet'
import { CITIES_RAW } from '@/api/mock/dict'
import { cn } from '@/lib/utils'

export function CitySheet() {
  const sheet = usePinliaoStore(s => s.sheet)
  const filters = usePinliaoStore(s => s.filters)
  const setCity = usePinliaoStore(s => s.setCity)
  const closeSheet = usePinliaoStore(s => s.closeSheet)

  if (sheet !== 'city') return null

  return (
    <BottomSheet title="选择城市" onClose={closeSheet}>
      <div className="grid grid-cols-3 gap-2 p-4 sm:grid-cols-4">
        {CITIES_RAW.map(c => (
          <button
            key={c}
            onClick={() => {
              setCity(c)
              closeSheet()
            }}
            className={cn(
              'rounded-md px-3 py-2 text-sm transition-colors',
              c === filters.city
                ? 'bg-primary text-primary-foreground'
                : 'bg-muted text-foreground hover:bg-accent',
            )}
          >
            {c}
          </button>
        ))}
      </div>
    </BottomSheet>
  )
}
