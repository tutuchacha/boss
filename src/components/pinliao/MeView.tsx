/**
 * 聘聊 · 我的视图
 * =====================================================================
 * 对齐 HTML 原型 renderMe()：
 *  - 个人卡片（头像 + 姓名 + 状态）
 *  - 求职期望行
 *  - 统计：沟通过 / 收藏 / 简历完善度
 *  - 菜单：简历预览 / 求职期望 / 收藏的职位 / 外观 / 切换招聘者身份
 * =====================================================================
 */
'use client'

import { useEffect } from 'react'
import { useTheme } from 'next-themes'
import { ChevronRight, FileText, Heart, Settings2, Moon, Sun, Monitor, ArrowLeftRight } from 'lucide-react'
import { usePinliaoStore } from '@/store/pinliao'
import { AvatarBadge } from './AvatarBadge'
import type { ThemePref } from '@/types/api'
import { RESUME_COMPLETENESS } from '@/api/mock/conversations'
import { cn } from '@/lib/utils'

export function MeView() {
  const profile = usePinliaoStore(s => s.profile)
  const conversations = usePinliaoStore(s => s.conversations)
  const favorites = usePinliaoStore(s => s.favorites)
  const pushPage = usePinliaoStore(s => s.pushPage)
  const openSheet = usePinliaoStore(s => s.openSheet)
  const toast = usePinliaoStore(s => s.toast)
  const { theme, setTheme } = useTheme()

  // next-themes 用 'system' 表示跟随系统，对应我们的 ThemePref 'auto'
  // 这里把 next-themes 的 'system' 当作 'auto' 处理
  const NEXT_THEME_MAP: Record<ThemePref, string> = {
    auto: 'system',
    light: 'light',
    dark: 'dark',
  }
  const FROM_NEXT_THEME: Record<string, ThemePref> = {
    system: 'auto',
    light: 'light',
    dark: 'dark',
  }
  const themeLabel: Record<ThemePref, string> = {
    auto: '跟随系统',
    light: '浅色',
    dark: '深色',
  }
  const currentTheme: ThemePref = FROM_NEXT_THEME[theme ?? 'system'] ?? 'auto'

  const cycleTheme = () => {
    const next: ThemePref =
      currentTheme === 'auto' ? 'light' : currentTheme === 'light' ? 'dark' : 'auto'
    setTheme(NEXT_THEME_MAP[next])
  }

  return (
    <div className="flex h-full flex-col">
      <header className="flex h-12 items-center border-b border-border bg-card px-4 pl-safe-top">
        <h1 className="text-base font-semibold text-foreground">我的</h1>
      </header>

      <div className="pl-scroll flex-1 overflow-y-auto bg-background">
        {/* 个人卡片 */}
        <section className="bg-card px-4 py-5">
          <div className="flex items-center gap-4">
            <AvatarBadge name={profile.name} size={56} round />
            <div>
              <strong className="text-base text-foreground">{profile.name}</strong>
              <small className="mt-0.5 block text-xs text-pl-sub">
                {profile.statusText}
              </small>
            </div>
          </div>
          <div className="mt-3 rounded-md bg-muted px-3 py-2 text-xs text-foreground/80">
            求职期望：{profile.expectSub}　{profile.expectCity}　{profile.expectSalary}
          </div>
          <div className="mt-3 flex divide-x divide-border">
            <button
              onClick={() => usePinliaoStore.getState().setTab('msgs')}
              className="flex-1 px-3 py-1.5 text-center"
            >
              <b className="block text-base text-foreground">{conversations.length}</b>
              <span className="text-[11px] text-pl-sub">沟通过</span>
            </button>
            <button
              onClick={() => pushPage({ type: 'favorites' })}
              className="flex-1 px-3 py-1.5 text-center"
            >
              <b className="block text-base text-foreground">{favorites.length}</b>
              <span className="text-[11px] text-pl-sub">收藏</span>
            </button>
            <div className="flex-1 px-3 py-1.5 text-center">
              <b className="block text-base text-foreground">{RESUME_COMPLETENESS}%</b>
              <span className="text-[11px] text-pl-sub">简历完善度</span>
            </div>
          </div>
        </section>

        {/* 菜单 */}
        <div className="mt-2 bg-card">
          <MenuRow
            icon={<FileText className="h-4 w-4" />}
            label="我的简历"
            trailing="预览"
            onClick={() => pushPage({ type: 'resume' })}
          />
          <MenuRow
            icon={<Settings2 className="h-4 w-4" />}
            label="求职期望"
            trailing="编辑"
            onClick={() => openSheet('wish')}
          />
          <MenuRow
            icon={<Heart className="h-4 w-4" />}
            label="收藏的职位"
            trailing={`${favorites.length} 个`}
            onClick={() => pushPage({ type: 'favorites' })}
          />
          <MenuRow
            icon={
              currentTheme === 'light' ? (
                <Sun className="h-4 w-4" />
              ) : currentTheme === 'dark' ? (
                <Moon className="h-4 w-4" />
              ) : (
                <Monitor className="h-4 w-4" />
              )
            }
            label="外观"
            trailing={themeLabel[currentTheme]}
            onClick={cycleTheme}
          />
          <MenuRow
            icon={<ArrowLeftRight className="h-4 w-4" />}
            label="切换为招聘者身份"
            trailing="即将上线"
            onClick={() => toast('招聘者端将在后续版本上线')}
            last
          />
        </div>

        <p className="mt-4 px-4 py-3 text-center text-[11px] text-pl-sub">
          聘聊 v0.3 前端原型 · 所有职位与对话均为演示数据
        </p>
        <div className="h-4" />
      </div>
    </div>
  )
}

function MenuRow({
  icon,
  label,
  trailing,
  onClick,
  last,
}: {
  icon: React.ReactNode
  label: string
  trailing?: string
  onClick?: () => void
  last?: boolean
}) {
  return (
    <button
      onClick={onClick}
      className={cn(
        'flex w-full items-center gap-3 bg-card px-4 py-3.5 text-left transition-colors hover:bg-accent/40',
        !last && 'border-b border-border',
      )}
    >
      <span className="text-pl-sub">{icon}</span>
      <span className="flex-1 text-sm text-foreground">{label}</span>
      {trailing && <span className="text-xs text-pl-sub">{trailing}</span>}
      <ChevronRight className="h-4 w-4 text-pl-sub" />
    </button>
  )
}
