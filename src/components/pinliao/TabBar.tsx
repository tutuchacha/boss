/**
 * 聘聊 · TabBar（底部导航）
 * =====================================================================
 */
'use client'

import { Briefcase, MessageSquare, User } from 'lucide-react'
import { usePinliaoStore } from '@/store/pinliao'
import type { TabKey } from '@/store/pinliao'
import { cn } from '@/lib/utils'

const TABS: { key: TabKey; label: string; Icon: typeof Briefcase }[] = [
  { key: 'jobs', label: '职位', Icon: Briefcase },
  { key: 'msgs', label: '消息', Icon: MessageSquare },
  { key: 'me', label: '我的', Icon: User },
]

export function TabBar() {
  const tab = usePinliaoStore(s => s.tab)
  const setTab = usePinliaoStore(s => s.setTab)
  const unread = usePinliaoStore(s => s.unreadTotal())

  return (
    <nav
      className="flex h-14 border-t border-border bg-card pl-safe-top pr-safe-top pl-safe-bottom pr-safe-bottom"
      aria-label="主导航"
    >
      {TABS.map(({ key, label, Icon }) => {
        const active = tab === key
        return (
          <button
            key={key}
            onClick={() => setTab(key)}
            aria-current={active ? 'page' : undefined}
            className={cn(
              'relative flex flex-1 flex-col items-center justify-center gap-0.5 text-xs transition-colors',
              active ? 'text-primary' : 'text-muted-foreground',
            )}
          >
            <span className="relative">
              <Icon className="h-5 w-5" strokeWidth={active ? 2.4 : 1.8} />
              {key === 'msgs' && unread > 0 && (
                <span
                  className="absolute -right-2 -top-1 min-w-4 px-1 text-center text-[10px] leading-4 text-destructive-foreground bg-destructive rounded-full"
                  aria-label={`${unread}条未读`}
                >
                  {unread > 99 ? '99+' : unread}
                </span>
              )}
            </span>
            <span className={active ? 'font-medium' : ''}>{label}</span>
          </button>
        )
      })}
    </nav>
  )
}
