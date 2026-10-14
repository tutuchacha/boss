/**
 * 聘聊 · HR 工作台 - 我的（占位 + 退出登录）
 * =====================================================================
 * 本次范围只做「登录 + 职位管理」，候选人管理与公司资料留待后续步骤。
 * 退出登录是本页的完整功能，便于在求职者端 / HR 端之间切换验收。
 * =====================================================================
 */

'use client'

import { useState } from 'react'
import {
  Building2,
  ChevronRight,
  Inbox,
  LogOut,
  ShieldCheck,
  UserRound,
} from 'lucide-react'
import { useAuthStore } from '@/store/auth'
import { useHrStore } from '@/store/hr'
import { AvatarBadge } from '../AvatarBadge'

export function HrMePage() {
  const user = useAuthStore(s => s.user)
  const company = useAuthStore(s => s.company)
  const hrProfile = useAuthStore(s => s.hrProfile)
  const member = useAuthStore(s => s.member)
  const logout = useAuthStore(s => s.logout)
  const toast = useHrStore(s => s.toast)
  const clearPageStack = useHrStore(s => s.clearPageStack)

  const [confirming, setConfirming] = useState(false)
  const [busy, setBusy] = useState(false)

  const onLogout = async () => {
    setBusy(true)
    try {
      await logout()
      clearPageStack()
    } catch {
      toast('退出失败，请重试')
      setBusy(false)
    }
  }

  return (
    <div className="pl-scroll flex-1 overflow-y-auto">
      {/* 个人卡片 */}
      <div className="border-b border-border bg-card px-4 py-4">
        <div className="flex items-center gap-3">
          <AvatarBadge name={hrProfile?.name ?? user?.phone ?? 'HR'} size={52} />
          <div className="min-w-0 flex-1">
            <p className="pl-line-clamp-1 text-[16px] font-medium text-foreground">
              {hrProfile?.name ?? '—'}
            </p>
            <p className="pl-line-clamp-1 text-[12px] text-pl-sub">
              {hrProfile?.title ?? '招聘者'}
            </p>
            <span className="mt-1 inline-flex items-center gap-1 rounded bg-primary/10 px-1.5 py-0.5 text-[11px] text-primary">
              <ShieldCheck className="h-3 w-3" />
              {member?.role === 'ADMIN' ? '企业管理员' : 'HR'}
            </span>
          </div>
        </div>

        <dl className="mt-3.5 grid grid-cols-2 gap-x-4 gap-y-2 text-[12px]">
          <div className="flex justify-between">
            <dt className="text-pl-sub">登录账号</dt>
            <dd className="font-mono text-foreground">{user?.phone}</dd>
          </div>
          <div className="flex justify-between">
            <dt className="text-pl-sub">所属公司</dt>
            <dd className="pl-line-clamp-1 text-foreground">{company?.name ?? '—'}</dd>
          </div>
        </dl>
      </div>

      {/* 公司信息 */}
      <section className="mt-2.5 bg-card px-4 py-3.5">
        <h2 className="mb-3 flex items-center gap-1.5 text-[13px] font-medium text-foreground">
          <Building2 className="h-3.5 w-3.5 text-primary" />
          公司信息
        </h2>
        <dl className="space-y-2.5 text-[13px]">
          <div className="flex items-center justify-between">
            <dt className="text-pl-sub">公司名称</dt>
            <dd className="flex items-center gap-1.5 text-foreground">
              {company?.name ?? '—'}
              {company?.verified && (
                <ShieldCheck className="h-3.5 w-3.5 text-primary" aria-label="已认证" />
              )}
            </dd>
          </div>
          <div className="flex items-center justify-between">
            <dt className="text-pl-sub">融资阶段</dt>
            <dd className="text-foreground">{company?.stage ?? '—'}</dd>
          </div>
          <div className="flex items-center justify-between">
            <dt className="text-pl-sub">公司规模</dt>
            <dd className="text-foreground">{company?.size ?? '—'}</dd>
          </div>
          <div className="flex items-center justify-between">
            <dt className="text-pl-sub">所属行业</dt>
            <dd className="text-foreground">{company?.industry ?? '—'}</dd>
          </div>
        </dl>
      </section>

      {/* 后续能力占位 */}
      <section className="mt-2.5 bg-card">
        {[
          { Icon: UserRound, label: '公司资料与品牌设置', tag: '后续' },
          { Icon: Inbox, label: '候选人池与人才库', tag: '后续' },
        ].map(({ Icon, label, tag }) => (
          <div
            key={label}
            className="flex items-center gap-2.5 border-b border-border px-4 py-3.5 last:border-b-0"
          >
            <Icon className="h-4 w-4 text-muted-foreground" />
            <span className="flex-1 text-[14px] text-foreground">{label}</span>
            <span className="rounded bg-muted px-1.5 py-0.5 text-[11px] text-muted-foreground">
              {tag}
            </span>
            <ChevronRight className="h-3.5 w-3.5 text-muted-foreground" />
          </div>
        ))}
      </section>

      {/* 退出登录 */}
      <section className="mt-2.5 bg-card px-4 py-3.5">
        {confirming ? (
          <>
            <p className="text-[13px] text-foreground">确认退出当前 HR 账号？</p>
            <p className="mt-1 text-[11px] text-pl-sub">
              退出后可切换为求职者身份登录（演示账号见登录页）。
            </p>
            <div className="mt-3 flex gap-2">
              <button
                type="button"
                disabled={busy}
                onClick={onLogout}
                className="h-9 rounded-lg bg-destructive px-4 text-[13px] font-medium text-destructive-foreground disabled:opacity-60"
              >
                {busy ? '退出中…' : '确认退出'}
              </button>
              <button
                type="button"
                onClick={() => setConfirming(false)}
                className="h-9 rounded-lg border border-border px-4 text-[13px] text-foreground hover:bg-accent/40"
              >
                取消
              </button>
            </div>
          </>
        ) : (
          <button
            type="button"
            onClick={() => setConfirming(true)}
            className="flex w-full items-center gap-2.5 text-left"
          >
            <LogOut className="h-4 w-4 text-destructive" />
            <span className="flex-1 text-[14px] text-destructive">退出登录</span>
            <ChevronRight className="h-3.5 w-3.5 text-muted-foreground" />
          </button>
        )}
      </section>

      {/* 版本与说明 */}
      <p className="px-4 py-5 text-center text-[11px] leading-relaxed text-pl-sub">
        聘聊 HR 工作台 · Mock 数据版本
        <br />
        数据仅存于浏览器内存，刷新后新增职位会重置
      </p>
    </div>
  )
}
