/**
 * 聘聊 · 消息列表视图
 * =====================================================================
 * 对齐 HTML 原型 renderMsgs() / convRow()：
 *  - 顶栏：消息
 *  - 会话列表（按 lastMessageAt 降序）
 *  - 未读角标
 *  - 空状态
 * =====================================================================
 */
'use client'

import { usePinliaoStore } from '@/store/pinliao'
import { AvatarBadge } from './AvatarBadge'
import { convTimeText, lastMessageText } from '@/lib/time'
import { jobById } from '@/api/mock/jobs'
import { cn } from '@/lib/utils'

export function MessagesView() {
  const conversations = usePinliaoStore(s => s.conversations)
  const messagesByConv = usePinliaoStore(s => s.messagesByConv)
  const pushPage = usePinliaoStore(s => s.pushPage)

  const list = conversations.slice().sort(
    (a, b) => new Date(b.lastMessageAt).getTime() - new Date(a.lastMessageAt).getTime(),
  )

  return (
    <div className="flex h-full flex-col">
      <header className="flex h-12 items-center border-b border-border bg-card px-4 pl-safe-top">
        <h1 className="text-base font-semibold text-foreground">消息</h1>
      </header>
      <div className="pl-scroll flex-1 overflow-y-auto bg-background">
        {list.length === 0 ? (
          <div className="flex flex-col items-center justify-center gap-3 px-8 py-20 text-center">
            <p className="text-sm font-medium text-foreground">还没有沟通记录</p>
            <p className="text-xs text-pl-sub">在职位详情里点「立即沟通」，就能和招聘者聊起来。</p>
            <button
              onClick={() => usePinliaoStore.getState().setTab('jobs')}
              className="rounded-md bg-primary px-3 py-1.5 text-xs text-primary-foreground"
            >
              去看职位
            </button>
          </div>
        ) : (
          list.map(c => {
            const job = jobById(c.jobId)
            const hrName = job?.hr.name ?? 'HR'
            const company = job?.company.name ?? ''
            const hrTitle = job?.hr.title ?? ''
            const msgs = messagesByConv[c.id] ?? []
            const last = msgs[msgs.length - 1]
            return (
              <button
                key={c.id}
                onClick={() => pushPage({ type: 'chat', id: c.jobId })}
                className="flex w-full items-center gap-3 border-b border-border bg-card px-4 py-3 text-left transition-colors hover:bg-accent/40"
              >
                <AvatarBadge name={hrName} size={46} round />
                <div className="flex-1 min-w-0">
                  <div className="flex items-baseline justify-between gap-2">
                    <strong className="pl-line-clamp-1 text-sm text-foreground">{hrName}</strong>
                    <time className="shrink-0 text-[11px] text-pl-sub">
                      {convTimeText(c.lastMessageAt)}
                    </time>
                  </div>
                  <div className="pl-line-clamp-1 text-xs text-pl-sub">
                    {company} {hrTitle}
                  </div>
                  <div className="mt-1 flex items-center gap-2">
                    <span className="pl-line-clamp-1 flex-1 text-[13px] text-foreground/80">
                      {lastMessageText(last)}
                    </span>
                    {c.userUnreadCount > 0 && (
                      <span className="inline-flex h-4 min-w-4 items-center justify-center rounded-full bg-destructive px-1 text-[10px] text-destructive-foreground">
                        {c.userUnreadCount > 99 ? '99+' : c.userUnreadCount}
                      </span>
                    )}
                  </div>
                </div>
              </button>
            )
          })
        )}
      </div>
    </div>
  )
}
