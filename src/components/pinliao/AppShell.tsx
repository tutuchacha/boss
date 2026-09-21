/**
 * 聘聊 · 主框架
 * =====================================================================
 * 职责：
 *  - 桌面端居中 430px 容器，模拟移动端体验
 *  - 在容器内布局：顶部视图（按 tab 切换）+ 页面栈覆盖层
 *  - 全局 Sheet（城市/筛选/期望）+ Toast
 *  - 监听浏览器后退键，绑到 popPage
 * =====================================================================
 */
'use client'

import { useEffect } from 'react'
import { usePinliaoStore } from '@/store/pinliao'
import { TabBar } from './TabBar'
import { JobsView } from './JobsView'
import { MessagesView } from './MessagesView'
import { MeView } from './MeView'
import { JobDetailPage } from './JobDetailPage'
import { ChatPage } from './ChatPage'
import { ResumePage } from './ResumePage'
import { FavoritesPage } from './FavoritesPage'
import { CitySheet } from './CitySheet'
import { FilterSheet } from './FilterSheet'
import { WishSheet } from './WishSheet'
import { PinliaoToast } from './PinliaoToast'

export function AppShell() {
  const tab = usePinliaoStore(s => s.tab)
  const pageStack = usePinliaoStore(s => s.pageStack)
  const popPage = usePinliaoStore(s => s.popPage)
  const clearPageStack = usePinliaoStore(s => s.clearPageStack)

  // 监听浏览器后退 → 优先 popPage，无页面栈时清掉历史记录项
  useEffect(() => {
    // 首次进入推一个占位 state，使 back 按钮触发 popstate
    if (typeof window === 'undefined') return
    window.history.pushState({ pinliao: 'root' }, '')
    const onPop = (e: PopStateEvent) => {
      const { pageStack: ps } = usePinliaoStore.getState()
      if (ps.length > 0) {
        // 拦截后退，重新推一个 state 以保持链路
        window.history.pushState({ pinliao: 'root' }, '')
        popPage()
      }
      // 无页面栈时让浏览器默认行为（不做什么）
      e.preventDefault?.()
    }
    window.addEventListener('popstate', onPop)
    return () => window.removeEventListener('popstate', onPop)
  }, [popPage])

  // 切换 tab 时清空页面栈
  useEffect(() => {
    clearPageStack()
     
  }, [tab])

  const topPage = pageStack[pageStack.length - 1]

  return (
    <div className="flex min-h-screen flex-col bg-pl-desk">
      <div className="mx-auto flex w-full max-w-[430px] flex-1 flex-col bg-background shadow-sm md:my-4 md:min-h-[calc(100vh-2rem)] md:rounded-xl md:overflow-hidden md:border md:border-border">
        {/* 顶部视图（按 tab 切换）+ 页面栈覆盖层，均用 absolute 定位避免内容撑高容器 */}
        <div className="relative flex-1 overflow-hidden">
          {/* tab 主视图 */}
          {tab === 'jobs' && (
            <div className="absolute inset-0 flex flex-col">
              <JobsView />
            </div>
          )}
          {tab === 'msgs' && (
            <div className="absolute inset-0 flex flex-col">
              <MessagesView />
            </div>
          )}
          {tab === 'me' && (
            <div className="absolute inset-0 flex flex-col">
              <MeView />
            </div>
          )}

          {/* 页面栈覆盖层 */}
          {pageStack.map((p, i) => {
            const isTop = i === pageStack.length - 1
            return (
              <div
                key={`${p.type}-${p.id ?? i}`}
                className={cnPage(i, pageStack.length)}
                style={{
                  // 已不在栈顶的页面退到次层（被新页面盖住）
                  pointerEvents: isTop ? 'auto' : 'none',
                  zIndex: 10 + i,
                }}
              >
                {p.type === 'job' && p.id && (
                  <JobDetailPage jobId={p.id} onBack={popPage} />
                )}
                {p.type === 'chat' && p.id && (
                  <ChatPage jobId={p.id} onBack={popPage} />
                )}
                {p.type === 'favorites' && <FavoritesPage onBack={popPage} />}
                {p.type === 'resume' && <ResumePage onBack={popPage} />}
                {p.type === 'wish' && <ResumePage onBack={popPage} />}
              </div>
            )
          })}
        </div>

        {/* 底部 Tab */}
        <TabBar />
      </div>

      {/* 全局 Sheet 与 Toast（在容器外，覆盖整个视口） */}
      <CitySheet />
      <FilterSheet />
      <WishSheet />
      <PinliaoToast />
    </div>
  )
}

import { cn } from '@/lib/utils'

function cnPage(index: number, total: number): string {
  // 栈顶页面用进入动画；其它页面不动画（已在下层）
  if (index === total - 1) {
    return cn(
      'pl-page-enter absolute inset-0 flex flex-col bg-background',
      'md:rounded-xl md:overflow-hidden',
    )
  }
  return 'absolute inset-0 flex flex-col bg-background'
}
