/**
 * 聘聊 · HR 工作台外壳
 * =====================================================================
 * 结构与求职者端 AppShell 对齐：430px 移动端容器 + 顶部视图 + 页面栈覆盖层。
 *
 * 布局：
 *  - 根视图：按 tab 渲染（职位管理 / 我的）
 *  - 页面栈：职位编辑器覆盖在根视图之上
 *  - 底部 TabBar + 全局 Toast
 *
 * 导航说明：与求职者端一致，用 store 的 pageStack 而非路由。
 * =====================================================================
 */

'use client'

import { Briefcase, Plus, UserRound } from 'lucide-react'
import { useAuthStore } from '@/store/auth'
import { hrCurrentPage, useHrStore, type HrTabKey } from '@/store/hr'
import { cn } from '@/lib/utils'
import { PinliaoToast } from '../PinliaoToast'
import { HrJobsPage } from './HrJobsPage'
import { HrJobEditorPage } from './HrJobEditorPage'
import { HrMePage } from './HrMePage'

/** 底部 Tab 定义 */
const TABS: { key: HrTabKey; label: string; Icon: typeof Briefcase }[] = [
  { key: 'jobs', label: '职位管理', Icon: Briefcase },
  { key: 'me', label: '我的', Icon: UserRound },
]

export function HrShell() {
  const company = useAuthStore(s => s.company)

  const tab = useHrStore(s => s.tab)
  const setTab = useHrStore(s => s.setTab)
  const pageStack = useHrStore(s => s.pageStack)
  const openEditor = useHrStore(s => s.openEditor)
  const current = useHrStore(hrCurrentPage)
  const toastText = useHrStore(s => s.toastText)
  const clearToast = useHrStore(s => s.clearToast)

  const isEditing = current.type === 'job-editor'

  return (
    <div className="flex min-h-screen flex-col bg-pl-desk">
      <div className="mx-auto flex w-full max-w-[430px] flex-1 flex-col bg-background shadow-sm md:my-4 md:min-h-[calc(100vh-2rem)] md:overflow-hidden md:rounded-xl md:border md:border-border">
        {/* 顶部栏：仅非编辑态显示，编辑态由编辑器自带顶栏 */}
        {!isEditing && (
          <header className="flex h-12 shrink-0 items-center gap-2 border-b border-border bg-card px-4">
            <span className="flex h-6 w-6 items-center justify-center rounded-md bg-primary text-[13px] font-bold text-primary-foreground">
              聘
            </span>
            <h1 className="text-[15px] font-medium text-foreground">HR 工作台</h1>
            <span className="pl-line-clamp-1 ml-auto max-w-[32%] text-[11px] text-pl-sub">
              {company?.name ?? ''}
            </span>
            {/* 常驻发布入口：职位列表非空时唯一入口（空状态另有引导按钮） */}
            <button
              type="button"
              onClick={() => openEditor()}
              className="flex shrink-0 items-center gap-1 rounded-md bg-primary px-2.5 py-1.5 text-[12px] font-medium text-primary-foreground transition-opacity hover:opacity-90 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
            >
              <Plus className="h-3.5 w-3.5" />
              发布职位
            </button>
          </header>
        )}

        {/* 内容区：根视图 + 页面栈覆盖层 */}
        <div className="relative flex flex-1 flex-col overflow-hidden">
          <div className="absolute inset-0 flex flex-col">
            {tab === 'jobs' && <HrJobsPage />}
            {tab === 'me' && <HrMePage />}
          </div>

          {pageStack.map((p, i) => {
            if (p.type !== 'job-editor') return null
            const isTop = i === pageStack.length - 1
            return (
              <div
                key={`editor-${p.jobId ?? 'new'}`}
                className="pl-page-enter absolute inset-0 flex flex-col bg-background md:rounded-xl md:overflow-hidden"
                style={{
                  pointerEvents: isTop ? 'auto' : 'none',
                  zIndex: 10 + i,
                }}
              >
                <HrJobEditorPage jobId={p.jobId} />
              </div>
            )
          })}
        </div>

        {/* 底部 Tab：编辑态隐藏，避免误触离开未保存的表单 */}
        {!isEditing && (
          <nav
            className="flex h-14 shrink-0 border-t border-border bg-card pl-safe-bottom pr-safe-bottom"
            aria-label="HR 主导航"
          >
            {TABS.map(({ key, label, Icon }) => {
              const active = tab === key
              return (
                <button
                  key={key}
                  type="button"
                  onClick={() => setTab(key)}
                  aria-current={active ? 'page' : undefined}
                  className={cn(
                    'relative flex flex-1 flex-col items-center justify-center gap-0.5 text-xs transition-colors',
                    active ? 'text-primary' : 'text-muted-foreground',
                  )}
                >
                  <Icon className="h-5 w-5" strokeWidth={active ? 2.4 : 1.8} />
                  <span className={active ? 'font-medium' : ''}>{label}</span>
                </button>
              )
            })}
          </nav>
        )}
      </div>

      {/* HR 端自己的 Toast（与求职者端 store 相互独立） */}
      <PinliaoToast text={toastText} onClear={clearToast} />
    </div>
  )
}
