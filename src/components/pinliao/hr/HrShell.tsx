/**
 * 聘聊 · HR 工作台外壳
 * =====================================================================
 * 结构与求职者端 AppShell 对齐：430px 移动端容器 + 顶部视图 + 页面栈覆盖层。
 *
 * 布局：
 *  - 根视图：按 tab 渲染（职位管理 / 候选人沟通 / 我的）
 *  - 页面栈：职位编辑器、聊天页覆盖在根视图之上
 *  - 底部 TabBar（含沟通未读角标）+ 全局 Toast
 *
 * 导航说明：与求职者端一致，用 store 的 pageStack 而非路由。
 * 顶栏的「发布职位」只在职位管理 Tab 显示（避免在沟通/我的页造成误解）。
 * =====================================================================
 */

'use client'

import { useQuery } from '@tanstack/react-query'
import { Briefcase, MessageSquare, Plus, UserRound, Users } from 'lucide-react'
import { listHrConversations } from '@/api/hrConversations'
import { useAuthStore } from '@/store/auth'
import { useHrCurrentPage, useHrStore, type HrPage, type HrTabKey } from '@/store/hr'
import { cn } from '@/lib/utils'
import { PinliaoToast } from '../PinliaoToast'
import { HrJobsPage } from './HrJobsPage'
import { HrJobEditorPage } from './HrJobEditorPage'
import { HrCandidatesPage } from './HrCandidatesPage'
import { HrCandidateDetailPage } from './HrCandidateDetailPage'
import { HrInterviewInvitePage } from './HrInterviewInvitePage'
import { HrMessagesPage } from './HrMessagesPage'
import { HrChatPage } from './HrChatPage'
import { HrTalentMatchPage } from './HrTalentMatchPage'
import { HrMePage } from './HrMePage'
import { HrCompanyEditPage } from './HrCompanyEditPage'
import { HrCompanyVerifyPage } from './HrCompanyVerifyPage'

/** 底部 Tab 定义 */
const TABS: { key: HrTabKey; label: string; Icon: typeof Briefcase }[] = [
  { key: 'jobs', label: '职位管理', Icon: Briefcase },
  { key: 'candidates', label: '候选人', Icon: Users },
  { key: 'messages', label: '沟通', Icon: MessageSquare },
  { key: 'me', label: '我的', Icon: UserRound },
]

/** 覆盖层页面类型：自带顶栏，隐藏主顶栏与底部 Tab */
const OVERLAY_TYPES = new Set<HrPage['type']>([
  'job-editor',
  'hr-chat',
  'candidate-detail',
  'interview-invite',
  'talent-match',
  'company-edit',
  'company-verify',
])

export function HrShell() {
  const company = useAuthStore(s => s.company)

  const tab = useHrStore(s => s.tab)
  const setTab = useHrStore(s => s.setTab)
  const pageStack = useHrStore(s => s.pageStack)
  const openEditor = useHrStore(s => s.openEditor)
  const current = useHrCurrentPage()
  const toastText = useHrStore(s => s.toastText)
  const clearToast = useHrStore(s => s.clearToast)
  const dataVersion = useHrStore(s => s.dataVersion)

  const companyId = company?.id ?? ''

  /** 沟通未读角标：与列表页共用同一 queryKey，避免重复请求 */
  const { data: convViews = [] } = useQuery({
    queryKey: ['hr', 'conversations', companyId, dataVersion],
    queryFn: () => listHrConversations(companyId),
    enabled: Boolean(companyId),
  })
  const unreadTotal = convViews.reduce((a, v) => a + v.conversation.hrUnreadCount, 0)

  /** 覆盖层页面（编辑器 / 聊天 / 候选人详情等）会自带顶栏，此时隐藏主顶栏与底部 Tab */
  const isOverlay = OVERLAY_TYPES.has(current.type)

  return (
    <div className="flex min-h-screen flex-col bg-pl-desk">
      <div className="mx-auto flex w-full max-w-[430px] flex-1 flex-col bg-background shadow-sm md:my-4 md:min-h-[calc(100vh-2rem)] md:overflow-hidden md:rounded-xl md:border md:border-border">
        {/* 顶部栏 */}
        {!isOverlay && (
          <header className="flex h-12 shrink-0 items-center gap-2 border-b border-border bg-card px-4">
            <span className="flex h-6 w-6 items-center justify-center rounded-md bg-primary text-[13px] font-bold text-primary-foreground">
              聘
            </span>
            <h1 className="text-[15px] font-medium text-foreground">HR 工作台</h1>
            <span className="pl-line-clamp-1 ml-auto max-w-[38%] text-[11px] text-pl-sub">
              {company?.name ?? ''}
            </span>
            {/* 发布入口只在职位管理 Tab 显示 */}
            {tab === 'jobs' && (
              <button
                type="button"
                onClick={() => openEditor()}
                className="flex shrink-0 items-center gap-1 rounded-md bg-primary px-2.5 py-1.5 text-[12px] font-medium text-primary-foreground transition-opacity hover:opacity-90 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
              >
                <Plus className="h-3.5 w-3.5" />
                发布职位
              </button>
            )}
          </header>
        )}

        {/* 内容区：根视图 + 页面栈覆盖层 */}
        <div className="relative flex flex-1 flex-col overflow-hidden">
          <div className="absolute inset-0 flex flex-col">
            {tab === 'jobs' && <HrJobsPage />}
            {tab === 'candidates' && <HrCandidatesPage />}
            {tab === 'messages' && <HrMessagesPage />}
            {tab === 'me' && <HrMePage />}
          </div>

          {pageStack.map((p, i) => {
            const isTop = i === pageStack.length - 1
            const overlayStyle = {
              pointerEvents: isTop ? ('auto' as const) : ('none' as const),
              zIndex: 10 + i,
            }
            const overlayCls =
              'pl-page-enter absolute inset-0 flex flex-col bg-background md:rounded-xl md:overflow-hidden'

            if (p.type === 'job-editor') {
              return (
                <div key={`editor-${p.jobId ?? 'new'}`} className={overlayCls} style={overlayStyle}>
                  <HrJobEditorPage jobId={p.jobId} />
                </div>
              )
            }
            if (p.type === 'hr-chat') {
              return (
                <div key={`chat-${p.conversationId}`} className={overlayCls} style={overlayStyle}>
                  <HrChatPage conversationId={p.conversationId} />
                </div>
              )
            }
            if (p.type === 'candidate-detail') {
              return (
                <div key={`cand-${p.applicationId}`} className={overlayCls} style={overlayStyle}>
                  <HrCandidateDetailPage applicationId={p.applicationId} />
                </div>
              )
            }
            if (p.type === 'interview-invite') {
              return (
                <div key={`invite-${p.applicationId}`} className={overlayCls} style={overlayStyle}>
                  <HrInterviewInvitePage applicationId={p.applicationId} />
                </div>
              )
            }
            if (p.type === 'talent-match') {
              return (
                <div key="talent-match" className={overlayCls} style={overlayStyle}>
                  <HrTalentMatchPage />
                </div>
              )
            }
            if (p.type === 'company-edit') {
              return (
                <div key="company-edit" className={overlayCls} style={overlayStyle}>
                  <HrCompanyEditPage />
                </div>
              )
            }
            if (p.type === 'company-verify') {
              return (
                <div key="company-verify" className={overlayCls} style={overlayStyle}>
                  <HrCompanyVerifyPage />
                </div>
              )
            }
            return null
          })}
        </div>

        {/* 底部 Tab：编辑/聊天态隐藏 */}
        {!isOverlay && (
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
                  <span className="relative">
                    <Icon className="h-5 w-5" strokeWidth={active ? 2.4 : 1.8} />
                    {key === 'messages' && unreadTotal > 0 && (
                      <span
                        aria-label={`${unreadTotal}条未读`}
                        className="absolute -right-2 -top-1 min-w-4 rounded-full bg-destructive px-1 text-center text-[10px] leading-4 text-destructive-foreground"
                      >
                        {unreadTotal > 99 ? '99+' : unreadTotal}
                      </span>
                    )}
                  </span>
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
