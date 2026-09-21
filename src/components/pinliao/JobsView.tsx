/**
 * 聘聊 · 职位列表视图
 * =====================================================================
 * 对齐 HTML 原型 renderJobs() / filtered()：
 *  - 顶部搜索栏（城市按钮 + 搜索框）
 *  - 一级分类 chips + 筛选按钮
 *  - 二级岗位 chips（仅 cat !== '全部' 时）
 *  - 列表（按筛选条件过滤 + 排序）
 *  - 空状态
 * =====================================================================
 */
'use client'

import { useMemo } from 'react'
import { Search, ChevronDown, SlidersHorizontal } from 'lucide-react'
import { usePinliaoStore, CATEGORY_LIST, SUB_LIST_OF, activeFilterCount } from '@/store/pinliao'
import { filterJobsSync } from '@/api/mock/filter'
import { JobRow } from './JobRow'
import { cn } from '@/lib/utils'

export function JobsView() {
  const filters = usePinliaoStore(s => s.filters)
  const setCity = usePinliaoStore(s => s.setCity)
  const setQ = usePinliaoStore(s => s.setQ)
  const setCat = usePinliaoStore(s => s.setCat)
  const setSub = usePinliaoStore(s => s.setSub)
  const openSheet = usePinliaoStore(s => s.openSheet)
  const pushPage = usePinliaoStore(s => s.pushPage)
  const favorites = usePinliaoStore(s => s.favorites)
  const profile = usePinliaoStore(s => s.profile)

  // mock 模式下用 filterJobsSync 同步取；联调阶段切到 useQuery，组件无需改动
  const list = useMemo(
    () =>
      filterJobsSync({
        q: filters.q,
        city: filters.city,
        cat: filters.cat,
        sub: filters.sub,
        exp: filters.exp,
        sal: filters.sal as never,
        sort: filters.sort,
        expectCat: profile.expectCat,
        expectSub: profile.expectSub,
      }),
    [filters, profile.expectCat, profile.expectSub],
  )

  const subList = filters.cat === '全部' ? [] : SUB_LIST_OF(filters.cat)
  const filterBadge = activeFilterCount({ filters } as never)

  return (
    <div className="flex h-full flex-col">
      {/* ====== Header ====== */}
      <header className="border-b border-border bg-card px-4 pt-2 pb-2 pl-safe-top">
        <div className="flex items-center gap-2">
          <button
            onClick={() => openSheet('city')}
            className="flex shrink-0 items-center gap-0.5 text-sm text-foreground"
            aria-label={`选择城市，当前${filters.city}`}
          >
            <span className="max-w-16 truncate">{filters.city}</span>
            <ChevronDown className="h-3.5 w-3.5 text-pl-sub" />
          </button>
          <label className="flex flex-1 items-center gap-1.5 rounded-md bg-muted px-2.5 py-1.5">
            <Search className="h-4 w-4 text-pl-sub" />
            <input
              type="search"
              value={filters.q}
              onChange={e => setQ(e.target.value)}
              placeholder="搜索职位、公司或技能"
              aria-label="搜索职位"
              className="w-full bg-transparent text-sm outline-none placeholder:text-pl-sub"
            />
          </label>
        </div>

        <div className="mt-2.5 flex items-center gap-2">
          {/* 一级分类 chips */}
          <div className="pl-scroll flex-1 overflow-x-auto">
            <div className="flex gap-1.5">
              {CATEGORY_LIST.map(c => (
                <button
                  key={c}
                  onClick={() => setCat(c)}
                  className={cn(
                    'shrink-0 whitespace-nowrap rounded-full px-3 py-1 text-xs transition-colors',
                    c === filters.cat
                      ? 'bg-primary text-primary-foreground'
                      : 'bg-secondary text-secondary-foreground',
                  )}
                >
                  {c}
                </button>
              ))}
            </div>
          </div>
          <button
            onClick={() => openSheet('filter')}
            className={cn(
              'flex shrink-0 items-center gap-1 rounded-full border px-2.5 py-1 text-xs transition-colors',
              filterBadge > 0
                ? 'border-primary text-primary'
                : 'border-border text-pl-sub',
            )}
            aria-label="筛选职位"
          >
            <SlidersHorizontal className="h-3.5 w-3.5" />
            <span>筛选</span>
            {filterBadge > 0 && (
              <span className="ml-0.5 inline-flex h-4 min-w-4 items-center justify-center rounded-full bg-primary px-1 text-[10px] text-primary-foreground">
                {filterBadge}
              </span>
            )}
          </button>
        </div>

        {/* 二级岗位 chips */}
        {subList.length > 0 && (
          <div className="pl-scroll mt-2 overflow-x-auto">
            <div className="flex gap-1.5">
              {subList.map(s => (
                <button
                  key={s}
                  onClick={() => setSub(s)}
                  className={cn(
                    'shrink-0 whitespace-nowrap rounded-full px-2.5 py-0.5 text-[11px] transition-colors',
                    s === filters.sub
                      ? 'bg-accent text-accent-foreground'
                      : 'bg-muted text-pl-sub',
                  )}
                >
                  {s}
                </button>
              ))}
            </div>
          </div>
        )}
      </header>

      {/* ====== 列表 ====== */}
      <div className="pl-scroll flex-1 overflow-y-auto bg-background">
        {list.length === 0 ? (
          <div className="flex flex-col items-center justify-center gap-3 px-8 py-20 text-center">
            <p className="text-sm font-medium text-foreground">没有符合条件的职位</p>
            <p className="text-xs text-pl-sub">换个关键词，或放宽筛选条件试试。</p>
            <button
              onClick={() =>
                usePinliaoStore.setState(s => ({
                  filters: {
                    city: s.filters.city,
                    q: '',
                    cat: '全部',
                    sub: '全部',
                    sort: 'recommend',
                    exp: '不限',
                    sal: '不限',
                  },
                }))
              }
              className="rounded-md bg-primary px-3 py-1.5 text-xs text-primary-foreground"
            >
              清除搜索和筛选
            </button>
          </div>
        ) : (
          <>
            <div className="px-4 py-2 text-[11px] text-pl-sub">
              共 {list.length} 个职位
              {filters.sort === 'latest'
                ? '，按发布时间排序'
                : `，优先展示「${profile.expectSub}」相关`}
            </div>
            <div>
              {list.map(j => (
                <JobRow
                  key={j.id}
                  job={j}
                  isFavorite={favorites.includes(j.id)}
                  onClick={() => pushPage({ type: 'job', id: j.id })}
                />
              ))}
            </div>
          </>
        )}
      </div>
    </div>
  )
}
