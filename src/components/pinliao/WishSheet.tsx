/**
 * 聘聊 · 求职期望编辑 Sheet
 * =====================================================================
 * 对齐 HTML 原型 wishSheet()：
 *  - 职位类别（一级）
 *  - 期望职位（二级，随一级变化）
 *  - 期望城市
 *  - 期望薪资
 * 临时状态存于 store.wishDraft
 * =====================================================================
 */
'use client'

import { usePinliaoStore, SUB_LIST_OF } from '@/store/pinliao'
import { CATEGORY_NAMES } from '@/api/mock/categories'
import { CITIES_RAW, EXPECT_SALARY_RANGES } from '@/api/mock/dict'
import { BottomSheet } from './BottomSheet'

export function WishSheet() {
  const sheet = usePinliaoStore(s => s.sheet)
  const wishDraft = usePinliaoStore(s => s.wishDraft)
  const setWishDraftField = usePinliaoStore(s => s.setWishDraftField)
  const applyWishDraft = usePinliaoStore(s => s.applyWishDraft)
  const closeSheet = usePinliaoStore(s => s.closeSheet)
  const toast = usePinliaoStore(s => s.toast)

  if (sheet !== 'wish' || !wishDraft) return null

  const subList = wishDraft.expectCat ? SUB_LIST_OF(wishDraft.expectCat).filter(s => s !== '全部') : []

  return (
    <BottomSheet title="求职期望" onClose={closeSheet}>
      <div className="space-y-3 p-4">
        <Field label="职位类别">
          <select
            value={wishDraft.expectCat}
            onChange={e => {
              const newCat = e.target.value
              const subs = SUB_LIST_OF(newCat).filter(s => s !== '全部')
              setWishDraftField({
                expectCat: newCat,
                expectSub: subs[0] ?? wishDraft.expectSub,
              })
            }}
            className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm outline-none focus:border-primary"
          >
            {CATEGORY_NAMES.map(c => (
              <option key={c} value={c}>
                {c}
              </option>
            ))}
          </select>
        </Field>

        <Field label="期望职位">
          <select
            value={wishDraft.expectSub}
            onChange={e => setWishDraftField({ expectSub: e.target.value })}
            className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm outline-none focus:border-primary"
          >
            {subList.map(s => (
              <option key={s} value={s}>
                {s}
              </option>
            ))}
          </select>
        </Field>

        <Field label="期望城市">
          <select
            value={wishDraft.expectCity}
            onChange={e => setWishDraftField({ expectCity: e.target.value })}
            className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm outline-none focus:border-primary"
          >
            {CITIES_RAW.filter(c => c !== '全国').map(c => (
              <option key={c} value={c}>
                {c}
              </option>
            ))}
          </select>
        </Field>

        <Field label="期望薪资">
          <select
            value={wishDraft.expectSalary}
            onChange={e => setWishDraftField({ expectSalary: e.target.value })}
            className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm outline-none focus:border-primary"
          >
            {EXPECT_SALARY_RANGES.map(s => (
              <option key={s} value={s}>
                {s}
              </option>
            ))}
          </select>
        </Field>
      </div>

      <div className="sticky bottom-0 flex gap-3 border-t border-border bg-card p-4 pl-safe-bottom">
        <button
          onClick={closeSheet}
          className="flex-1 rounded-md border border-border py-2.5 text-sm text-foreground"
        >
          取消
        </button>
        <button
          onClick={() => {
            applyWishDraft()
            toast('求职期望已保存')
          }}
          className="flex-1 rounded-md bg-primary py-2.5 text-sm font-medium text-primary-foreground"
        >
          保存
        </button>
      </div>
    </BottomSheet>
  )
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <label className="block">
      <span className="mb-1.5 block text-xs font-medium text-pl-sub">{label}</span>
      {children}
    </label>
  )
}
