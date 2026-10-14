/**
 * 聘聊 · HR 工作台 - 沟通（候选人会话列表）
 * =====================================================================
 * 对应接口：GET /api/v1/conversations（HR 视角，docs/api-endpoints.md §8）
 *
 * 功能：
 *  - 展示本公司职位的候选人会话（按最后消息时间倒序）
 *  - 未读角标 + 仅看未读筛选 + 按职位筛选
 *  - 点击进入聊天页
 *
 * 这是此前缺失的「与求职者联系」入口 —— 原先 HR 只管理职位，
 * 无法看到或回复候选人消息。
 * =====================================================================
 */

'use client'

import { useMemo, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { MessageSquare, Search } from 'lucide-react'
import { listHrConversations } from '@/api/hrConversations'
import type { HrConversationView } from '@/api/hrConversations'
import { useAuthStore } from '@/store/auth'
import { useHrStore } from '@/store/hr'
import { lastMessageText } from '@/lib/time'
import { cn } from '@/lib/utils'
import { AvatarBadge } from '../AvatarBadge'

/** 会话列表查询键前缀 */
export const HR_CONVERSATIONS_QUERY_KEY = ['hr', 'conversations'] as const

/** 时间显示：今天显示时刻，否则显示月-日 */
function timeText(iso: string): string {
  const d = new Date(iso)
  const now = new Date()
  const sameDay = d.toDateString() === now.toDateString()
  if (sameDay) {
    return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
  }
  return `${d.getMonth() + 1}月${d.getDate()}日`
}

export function HrMessagesPage() {
  const company = useAuthStore(s => s.company)
  const openChat = useHrStore(s => s.openChat)
  const dataVersion = useHrStore(s => s.dataVersion)

  const [onlyUnread, setOnlyUnread] = useState(false)
  const [jobFilter, setJobFilter] = useState<string>('ALL')
  const [q, setQ] = useState('')

  const companyId = company?.id ?? ''

  const { data: views = [], isPending } = useQuery({
    queryKey: [...HR_CONVERSATIONS_QUERY_KEY, companyId, dataVersion],
    queryFn: () => listHrConversations(companyId),
    enabled: Boolean(companyId),
  })

  /** 本公司涉及到的职位（用于筛选下拉） */
  const jobOptions = useMemo(() => {
    const map = new Map<string, string>()
    for (const v of views) map.set(v.job.id, v.job.title)
    return Array.from(map.entries())
  }, [views])

  /** 应用筛选 */
  const filtered = useMemo(() => {
    const kw = q.trim().toLowerCase()
    return views.filter(v => {
      if (onlyUnread && v.conversation.hrUnreadCount === 0) return false
      if (jobFilter !== 'ALL' && v.job.id !== jobFilter) return false
      if (kw) {
        const hay = `${v.candidate.name} ${v.job.title} ${lastMessageText(v.lastMessage)}`.toLowerCase()
        if (!hay.includes(kw)) return false
      }
      return true
    })
  }, [views, onlyUnread, jobFilter, q])

  const unreadTotal = views.reduce((a, v) => a + v.conversation.hrUnreadCount, 0)

  return (
    <div className="flex flex-1 flex-col overflow-hidden">
      {/* 概览 + 筛选 */}
      <div className="border-b border-border bg-card px-4 py-3">
        <div className="flex items-center gap-2">
          <MessageSquare className="h-4 w-4 text-primary" />
          <h2 className="text-[14px] font-medium text-foreground">候选人沟通</h2>
          <span className="ml-auto text-[11px] text-pl-sub">
            共 {views.length} 个会话
            {unreadTotal > 0 && (
              <span className="ml-1.5 text-destructive">{unreadTotal} 条未读</span>
            )}
          </span>
        </div>

        <div className="relative mt-2.5">
          <Search className="pointer-events-none absolute left-2.5 top-1/2 h-3.5 w-3.5 -translate-y-1/2 text-muted-foreground" />
          <input
            type="text"
            value={q}
            onChange={e => setQ(e.target.value)}
            placeholder="搜索候选人 / 职位 / 消息"
            aria-label="搜索候选人"
            className="h-9 w-full rounded-lg border border-border bg-background pl-8 pr-3 text-[13px] text-foreground placeholder:text-muted-foreground/70 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
          />
        </div>

        <div className="mt-2 flex flex-wrap items-center gap-1.5">
          <button
            type="button"
            onClick={() => setOnlyUnread(v => !v)}
            aria-pressed={onlyUnread}
            className={cn(
              'rounded-full px-2.5 py-1 text-[12px] transition-colors',
              onlyUnread
                ? 'bg-primary text-primary-foreground'
                : 'bg-secondary text-secondary-foreground hover:bg-accent',
            )}
          >
            仅看未读
          </button>

          <select
            value={jobFilter}
            onChange={e => setJobFilter(e.target.value)}
            aria-label="按职位筛选"
            className="max-w-[55%] rounded-full border border-border bg-background px-2 py-1 text-[12px] text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
          >
            <option value="ALL">全部职位</option>
            {jobOptions.map(([id, title]) => (
              <option key={id} value={id}>
                {title}
              </option>
            ))}
          </select>
        </div>
      </div>

      {/* 会话列表 */}
      <div className="pl-scroll flex-1 overflow-y-auto">
        {isPending && <p className="px-4 py-8 text-center text-[13px] text-pl-sub">加载中…</p>}

        {!isPending && filtered.length === 0 && (
          <div className="flex flex-col items-center px-6 py-14 text-center">
            <span className="flex h-12 w-12 items-center justify-center rounded-full bg-secondary">
              <MessageSquare className="h-5 w-5 text-muted-foreground" />
            </span>
            <p className="mt-3 text-[14px] font-medium text-foreground">
              {views.length === 0 ? '还没有候选人联系你' : '没有符合条件的会话'}
            </p>
            <p className="mt-1 text-[12px] text-pl-sub">
              {views.length === 0
                ? '求职者从职位详情页发起沟通后会出现在这里'
                : '试着调整筛选条件'}
            </p>
          </div>
        )}

        {!isPending &&
          filtered.map(v => {
            const unread = v.conversation.hrUnreadCount
            return (
              <button
                key={v.conversation.id}
                type="button"
                onClick={() => openChat(v.conversation.id)}
                className="flex w-full items-start gap-2.5 border-b border-border bg-card px-4 py-3 text-left transition-colors hover:bg-accent/40 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
              >
                <AvatarBadge name={v.candidate.name} size={40} />
                <span className="min-w-0 flex-1">
                  <span className="flex items-baseline gap-2">
                    <span className="pl-line-clamp-1 text-[14px] font-medium text-foreground">
                      {v.candidate.name}
                    </span>
                    <span className="shrink-0 text-[11px] text-pl-sub">
                      {v.candidate.expect} · {v.candidate.workYears}年 · {v.candidate.edu}
                    </span>
                    <span className="ml-auto shrink-0 text-[11px] text-pl-sub">
                      {timeText(v.conversation.lastMessageAt)}
                    </span>
                  </span>
                  <span className="mt-0.5 block pl-line-clamp-1 text-[11px] text-primary/90">
                    应聘：{v.job.title}
                    {v.job.salary ? ` · ${v.job.salary}` : ''}
                  </span>
                  <span className="mt-1 flex items-center gap-2">
                    <span
                      className={cn(
                        'pl-line-clamp-1 flex-1 text-[12px]',
                        unread > 0 ? 'font-medium text-foreground' : 'text-pl-sub',
                      )}
                    >
                      {lastMessageText(v.lastMessage) || '暂无消息'}
                    </span>
                    {unread > 0 && (
                      <span
                        aria-label={`${unread}条未读`}
                        className="min-w-4 shrink-0 rounded-full bg-destructive px-1 text-center text-[10px] leading-4 text-destructive-foreground"
                      >
                        {unread > 99 ? '99+' : unread}
                      </span>
                    )}
                  </span>
                </span>
              </button>
            )
          })}

        <div className="h-4" />
      </div>
    </div>
  )
}
