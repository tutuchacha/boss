/**
 * 聘聊 · 简历预览页（静态）
 * =====================================================================
 * 对齐 HTML 原型 resumeHTML()。
 * 后期接后端后改为可编辑。
 * =====================================================================
 */
'use client'

import { usePinliaoStore } from '@/store/pinliao'
import { PageShell } from './JobDetailPage'
import { AvatarBadge } from './AvatarBadge'

interface Props {
  onBack: () => void
}

export function ResumePage({ onBack }: Props) {
  const profile = usePinliaoStore(s => s.profile)
  return (
    <PageShell title="我的简历" onBack={onBack}>
      <div className="pl-scroll flex-1 overflow-y-auto bg-background px-4 py-4">
        <section className="bg-card rounded-md px-4 py-4">
          <h2 className="text-sm font-semibold text-foreground">个人信息</h2>
          <p className="mt-1.5 text-[13px] text-foreground/90">
            <AvatarBadge name={profile.name} size={32} className="mr-2 align-middle" />
            {profile.name}　{profile.statusText}
          </p>

          <h2 className="mt-4 text-sm font-semibold text-foreground">求职期望</h2>
          <p className="mt-1.5 text-[13px] text-foreground/90">
            {profile.expectCat} / {profile.expectSub}　{profile.expectCity}　{profile.expectSalary}
          </p>

          <h2 className="mt-4 text-sm font-semibold text-foreground">工作经历</h2>
          <p className="mt-1.5 text-[13px] text-foreground/90">
            <strong>某某公司</strong>　{profile.expectSub}　2022.07 - 2025.08
          </p>
          <p className="mt-0.5 text-[12px] text-pl-sub">
            负责相关业务的日常工作，独立完成关键任务并达成目标。
          </p>

          <h2 className="mt-4 text-sm font-semibold text-foreground">教育经历</h2>
          <p className="mt-1.5 text-[13px] text-foreground/90">某某大学　本科　2018 - 2022</p>

          <h2 className="mt-4 text-sm font-semibold text-foreground">个人优势</h2>
          <div className="mt-2 flex flex-wrap gap-1.5">
            {['沟通协作', '学习能力强', '责任心', '执行力'].map(t => (
              <span key={t} className="rounded bg-secondary px-2 py-0.5 text-[11px] text-secondary-foreground">
                {t}
              </span>
            ))}
          </div>
        </section>
        <p className="mt-4 text-center text-[11px] text-pl-sub">
          简历编辑功能将在后续版本上线
        </p>
      </div>
    </PageShell>
  )
}
