/**
 * 聘聊 · 我的投递
 * =====================================================================
 * 对应接口：
 *  - GET  /api/v1/applications                     我的投递列表（§9.2）
 *  - POST /api/v1/applications/{id}/withdraw       撤回投递（§9.4）
 *  - POST /api/v1/interviews/{id}/accept           接受面试（§10.4）
 *  - POST /api/v1/interviews/{id}/decline          拒绝面试（§10.5）
 *
 * 跨端说明：Mock 数据在内存中两端共享，操作后同时失效
 * ['my-applications'] 与 ['hr'] 前缀的查询，保证切换账号后 HR
 * 候选人页看到的是最新状态（应用内退出登录切换，勿刷新页面）。
 * =====================================================================
 */

'use client'

import { useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { ClipboardList, Loader2 } from 'lucide-react'
import { ApplicationStatusEnum, InterviewFormatEnum, InterviewStatusEnum } from '@/types/api'
import {
  APPLICATION_STATUS_TEXT,
  INTERVIEW_FORMAT_TEXT,
} from '@/api/mock/applications'
import {
  acceptInterview,
  declineInterview,
  listMyApplications,
  withdrawApplication,
} from '@/api/applications'
import { ApiError } from '@/api/client'
import { useAuthStore } from '@/store/auth'
import { usePinliaoStore } from '@/store/pinliao'
import { formatDateTime } from '@/lib/time'
import { cn } from '@/lib/utils'
import { PageShell } from './JobDetailPage'

interface Props {
  onBack: () => void
}

/** 投递状态 → 徽标配色（与 HR 端语义一致） */
const STATUS_TONE: Record<ApplicationStatusEnum, string> = {
  [ApplicationStatusEnum.SUBMITTED]: 'bg-amber-500/10 text-amber-600 dark:text-amber-400',
  [ApplicationStatusEnum.VIEWED]: 'bg-sky-500/10 text-sky-600 dark:text-sky-400',
  [ApplicationStatusEnum.INTERVIEW]: 'bg-primary/10 text-primary',
  [ApplicationStatusEnum.OFFER]: 'bg-emerald-500/10 text-emerald-600 dark:text-emerald-400',
  [ApplicationStatusEnum.REJECTED]: 'bg-muted text-muted-foreground',
  [ApplicationStatusEnum.WITHDRAWN]: 'bg-muted text-muted-foreground',
}

/** 可撤回的投递状态 */
const WITHDRAWABLE: ApplicationStatusEnum[] = [
  ApplicationStatusEnum.SUBMITTED,
  ApplicationStatusEnum.VIEWED,
  ApplicationStatusEnum.INTERVIEW,
]

/** 面试状态 → 求职者侧文案（站在候选人视角，与 HR 端口径不同） */
const SEEKER_INTERVIEW_STATUS_TEXT: Record<InterviewStatusEnum, string> = {
  [InterviewStatusEnum.PENDING]: '待你确认',
  [InterviewStatusEnum.ACCEPTED]: '已接受邀请',
  [InterviewStatusEnum.DECLINED]: '已拒绝邀请',
  [InterviewStatusEnum.COMPLETED]: '已完成',
  [InterviewStatusEnum.CANCELLED]: '已取消',
}

export function MyApplicationsPage({ onBack }: Props) {
  const queryClient = useQueryClient()
  const userId = useAuthStore(s => s.user?.id ?? '')
  const pushPage = usePinliaoStore(s => s.pushPage)
  const toast = usePinliaoStore(s => s.toast)

  const [busy, setBusy] = useState<string | null>(null)
  const [confirmId, setConfirmId] = useState<string | null>(null)

  const { data: views = [], isPending } = useQuery({
    queryKey: ['my-applications', userId],
    queryFn: () => listMyApplications(userId),
    enabled: Boolean(userId),
  })

  /** 操作成功后刷新（含 HR 端查询，保证跨端一致） */
  const refresh = async () => {
    await queryClient.invalidateQueries({ queryKey: ['my-applications', userId] })
    await queryClient.invalidateQueries({ queryKey: ['hr'] })
  }

  const onAccept = async (interviewId: string) => {
    setBusy(interviewId)
    try {
      await acceptInterview(interviewId, userId)
      toast('已接受面试邀请')
      await refresh()
    } catch (err) {
      toast(err instanceof ApiError ? err.message : '操作失败，请重试')
    } finally {
      setBusy(null)
    }
  }

  const onDecline = async (interviewId: string) => {
    setBusy(interviewId)
    try {
      await declineInterview(interviewId, userId)
      toast('已婉拒面试邀请')
      await refresh()
    } catch (err) {
      toast(err instanceof ApiError ? err.message : '操作失败，请重试')
    } finally {
      setBusy(null)
    }
  }

  const onWithdraw = async (applicationId: string) => {
    setBusy(applicationId)
    try {
      await withdrawApplication(applicationId, userId)
      toast('已撤回投递')
      setConfirmId(null)
      await refresh()
    } catch (err) {
      toast(err instanceof ApiError ? err.message : '撤回失败，请重试')
    } finally {
      setBusy(null)
    }
  }

  return (
    <PageShell title="我的投递" onBack={onBack}>
      <div className="pl-scroll flex-1 overflow-y-auto bg-background">
        {isPending && (
          <p className="py-10 text-center text-[13px] text-pl-sub">加载中…</p>
        )}

        {!isPending && views.length === 0 && (
          <div className="flex flex-col items-center px-6 py-16 text-center">
            <span className="flex h-12 w-12 items-center justify-center rounded-full bg-secondary">
              <ClipboardList className="h-5 w-5 text-muted-foreground" />
            </span>
            <p className="mt-3 text-[14px] font-medium text-foreground">暂无投递记录</p>
            <p className="mt-1 text-[12px] text-pl-sub">在职位详情页点击「投递简历」即可投递</p>
            <button
              type="button"
              onClick={() => usePinliaoStore.getState().setTab('jobs')}
              className="mt-4 rounded-lg bg-primary px-4 py-2 text-[13px] font-medium text-primary-foreground"
            >
              去看看职位
            </button>
          </div>
        )}

        {!isPending &&
          views.map(v => {
            const { application, job, interview } = v
            const showInterview =
              interview && application.status === ApplicationStatusEnum.INTERVIEW
            const where = interview
              ? interview.format === InterviewFormatEnum.ONSITE
                ? interview.location
                : interview.format === InterviewFormatEnum.VIDEO
                  ? interview.onlineUrl
                  : undefined
              : undefined

            return (
              <article key={application.id} className="mt-2 bg-card px-4 py-3.5">
                {/* 职位 + 状态 */}
                <div className="flex items-start justify-between gap-2">
                  <button
                    type="button"
                    disabled={!job}
                    onClick={() => job && pushPage({ type: 'job', id: job.id })}
                    className="min-w-0 flex-1 text-left"
                  >
                    <p className="pl-line-clamp-1 text-[15px] font-medium text-foreground">
                      {job?.title ?? '职位已下线'}
                    </p>
                    <p className="mt-0.5 pl-line-clamp-1 text-[12px] text-pl-sub">
                      {[job?.company.name, job?.salary, job?.city].filter(Boolean).join(' · ')}
                    </p>
                  </button>
                  <span
                    className={cn(
                      'shrink-0 rounded px-1.5 py-0.5 text-[10px]',
                      STATUS_TONE[application.status],
                    )}
                  >
                    {APPLICATION_STATUS_TEXT[application.status]}
                  </span>
                </div>
                <p className="mt-1.5 text-[11px] text-pl-sub">
                  投递于 {formatDateTime(application.createdAt)}
                </p>

                {/* 面试邀请卡 */}
                {showInterview && interview && (
                  <div className="mt-2.5 rounded-lg border border-primary/20 bg-primary/5 px-3 py-2.5">
                    <div className="flex items-center gap-2">
                      <p className="text-[12px] font-medium text-foreground">面试邀请</p>
                      <span
                        className={cn(
                          'ml-auto rounded px-1.5 py-0.5 text-[10px]',
                          interview.status === InterviewStatusEnum.PENDING
                            ? 'bg-amber-500/10 text-amber-600 dark:text-amber-400'
                            : interview.status === InterviewStatusEnum.ACCEPTED
                              ? 'bg-emerald-500/10 text-emerald-600 dark:text-emerald-400'
                              : 'bg-muted text-muted-foreground',
                        )}
                      >
                        {SEEKER_INTERVIEW_STATUS_TEXT[interview.status]}
                      </span>
                    </div>
                    <p className="mt-1.5 text-[12px] leading-relaxed text-foreground">
                      {formatDateTime(interview.scheduledAt)} · {INTERVIEW_FORMAT_TEXT[interview.format]}
                      {where ? ` · ${where}` : ''}
                    </p>
                    {interview.note && (
                      <p className="mt-1 text-[11px] leading-relaxed text-pl-sub">{interview.note}</p>
                    )}

                    {interview.status === InterviewStatusEnum.PENDING && (
                      <div className="mt-2 flex gap-2">
                        <button
                          type="button"
                          disabled={busy !== null}
                          onClick={() => void onAccept(interview.id)}
                          className="flex h-8 items-center gap-1.5 rounded-md bg-primary px-3 text-[12px] font-medium text-primary-foreground disabled:opacity-60"
                        >
                          {busy === interview.id && <Loader2 className="h-3 w-3 animate-spin" />}
                          接受面试
                        </button>
                        <button
                          type="button"
                          disabled={busy !== null}
                          onClick={() => void onDecline(interview.id)}
                          className="h-8 rounded-md border border-border px-3 text-[12px] text-pl-sub transition-colors hover:bg-accent/40 disabled:opacity-60"
                        >
                          婉拒
                        </button>
                      </div>
                    )}
                    {interview.status === InterviewStatusEnum.ACCEPTED && (
                      <p className="mt-1.5 text-[11px] text-primary">已接受邀请，请按时参加面试</p>
                    )}
                    {interview.status === InterviewStatusEnum.DECLINED && (
                      <p className="mt-1.5 text-[11px] text-pl-sub">已婉拒该面试邀请</p>
                    )}
                  </div>
                )}

                {/* 操作 */}
                <div className="mt-2.5 flex flex-wrap items-center gap-2">
                  {WITHDRAWABLE.includes(application.status) &&
                    (confirmId === application.id ? (
                      <>
                        <button
                          type="button"
                          disabled={busy !== null}
                          onClick={() => void onWithdraw(application.id)}
                          className="flex h-8 items-center gap-1.5 rounded-md bg-destructive px-3 text-[12px] font-medium text-destructive-foreground disabled:opacity-60"
                        >
                          {busy === application.id && <Loader2 className="h-3 w-3 animate-spin" />}
                          确认撤回
                        </button>
                        <button
                          type="button"
                          onClick={() => setConfirmId(null)}
                          className="h-8 rounded-md border border-border px-3 text-[12px] text-foreground hover:bg-accent/40"
                        >
                          取消
                        </button>
                      </>
                    ) : (
                      <button
                        type="button"
                        onClick={() => setConfirmId(application.id)}
                        className="h-8 rounded-md border border-border px-3 text-[12px] text-pl-sub transition-colors hover:bg-accent/40"
                      >
                        撤回投递
                      </button>
                    ))}
                  {job && (
                    <button
                      type="button"
                      onClick={() => pushPage({ type: 'job', id: job.id })}
                      className="h-8 rounded-md border border-border px-3 text-[12px] text-foreground transition-colors hover:bg-accent/40"
                    >
                      查看职位
                    </button>
                  )}
                </div>
              </article>
            )
          })}

        <div className="h-4" />
      </div>
    </PageShell>
  )
}
