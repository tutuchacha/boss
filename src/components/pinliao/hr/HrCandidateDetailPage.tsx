/**
 * 聘聊 · HR 工作台 - 候选人详情
 * =====================================================================
 * 对应接口：
 *  - GET  /api/v1/applications/{id}                              投递详情
 *  - POST /api/v1/applications/{id}/status                       标记（已查看 / 录用 / 不合适）
 *  - POST /api/v1/conversations/{id}/actions/invite-interview    约面试（覆盖页）
 *
 * 操作后 bumpDataVersion + invalidateQueries 驱动列表/详情重新取数。
 * =====================================================================
 */

'use client'

import { useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { ArrowLeft, Loader2, MessageSquare, Sparkles, Video } from 'lucide-react'
import { ApplicationStatusEnum, InterviewFormatEnum, InterviewStatusEnum } from '@/types/api'
import {
  APPLICATION_STATUS_TEXT,
  INTERVIEW_FORMAT_TEXT,
  INTERVIEW_STATUS_TEXT,
} from '@/api/mock/applications'
import {
  ensureConversationForApplication,
  getHrApplication,
  HR_APPLICATIONS_QUERY_KEY,
  updateApplicationStatus,
} from '@/api/hrApplications'
import { AI_STATUS_QUERY_KEY, getAiStatus } from '@/api/ai'
import { ApiError } from '@/api/client'
import { useHrStore } from '@/store/hr'
import { formatDateTime } from '@/lib/time'
import { cn } from '@/lib/utils'
import { AvatarBadge } from '../AvatarBadge'
import { HrAiAssistantSheet } from './HrAiAssistantSheet'
import { STATUS_TONE } from './HrCandidatesPage'

interface Props {
  applicationId: string
}

export function HrCandidateDetailPage({ applicationId }: Props) {
  const queryClient = useQueryClient()
  const popPage = useHrStore(s => s.popPage)
  const openChat = useHrStore(s => s.openChat)
  const openInterviewInvite = useHrStore(s => s.openInterviewInvite)
  const bumpDataVersion = useHrStore(s => s.bumpDataVersion)
  const toast = useHrStore(s => s.toast)
  const dataVersion = useHrStore(s => s.dataVersion)

  const [busy, setBusy] = useState<string | null>(null)
  const [aiOpen, setAiOpen] = useState(false)

  const { data: view, isPending } = useQuery({
    queryKey: ['hr', 'application', applicationId, dataVersion],
    queryFn: () => getHrApplication(applicationId),
  })

  // AI 开关：关闭（或查询失败）时不渲染入口，避免点了才报 42220
  const { data: aiStatus } = useQuery({
    queryKey: AI_STATUS_QUERY_KEY,
    queryFn: getAiStatus,
    staleTime: 5 * 60 * 1000,
    retry: false,
  })
  const aiEnabled = aiStatus?.enabled === true

  /** 操作成功后统一刷新（列表 + 详情 + 会话） */
  const refresh = async () => {
    bumpDataVersion()
    await queryClient.invalidateQueries({ queryKey: HR_APPLICATIONS_QUERY_KEY })
    await queryClient.invalidateQueries({ queryKey: ['hr', 'application', applicationId] })
  }

  const mark = async (
    status: ApplicationStatusEnum.VIEWED | ApplicationStatusEnum.OFFER | ApplicationStatusEnum.REJECTED,
    okText: string,
  ) => {
    setBusy(status)
    try {
      await updateApplicationStatus(applicationId, status)
      toast(okText)
      await refresh()
    } catch (err) {
      toast(err instanceof ApiError ? err.message : '操作失败，请重试')
    } finally {
      setBusy(null)
    }
  }

  const onMessage = async () => {
    setBusy('message')
    try {
      const convId = await ensureConversationForApplication(applicationId)
      bumpDataVersion()
      await queryClient.invalidateQueries({ queryKey: ['hr', 'conversations'] })
      openChat(convId)
    } catch (err) {
      toast(err instanceof ApiError ? err.message : '会话创建失败，请重试')
    } finally {
      setBusy(null)
    }
  }

  const back = (
    <button
      type="button"
      onClick={popPage}
      aria-label="返回"
      className="flex h-8 w-8 items-center justify-center rounded-md text-foreground hover:bg-accent/50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
    >
      <ArrowLeft className="h-4 w-4" />
    </button>
  )

  if (isPending || !view) {
    return (
      <div className="flex flex-1 flex-col overflow-hidden">
        <div className="flex shrink-0 items-center gap-2 border-b border-border bg-card px-3 py-2.5">
          {back}
          <span className="text-[14px] font-medium text-foreground">候选人详情</span>
        </div>
        <div className="flex flex-1 items-center justify-center text-[13px] text-pl-sub">
          {isPending ? (
            <span className="flex items-center gap-2">
              <Loader2 className="h-4 w-4 animate-spin" /> 加载中…
            </span>
          ) : (
            '该投递记录不存在'
          )}
        </div>
      </div>
    )
  }

  const { application, candidate, job, interview } = view
  const status = application.status
  const isWithdrawn = status === ApplicationStatusEnum.WITHDRAWN
  const pendingInterview = interview?.status === InterviewStatusEnum.PENDING
  const canMarkViewed = status === ApplicationStatusEnum.SUBMITTED
  const canInvite =
    !isWithdrawn &&
    status !== ApplicationStatusEnum.REJECTED &&
    status !== ApplicationStatusEnum.OFFER &&
    !pendingInterview
  const canDecide =
    status === ApplicationStatusEnum.SUBMITTED ||
    status === ApplicationStatusEnum.VIEWED ||
    status === ApplicationStatusEnum.INTERVIEW

  return (
    <div className="flex flex-1 flex-col overflow-hidden">
      {/* 顶栏 */}
      <div className="flex shrink-0 items-center gap-2 border-b border-border bg-card px-3 py-2.5">
        {back}
        <span className="text-[14px] font-medium text-foreground">候选人详情</span>
        <span className={cn('ml-auto rounded px-1.5 py-0.5 text-[11px]', STATUS_TONE[status])}>
          {APPLICATION_STATUS_TEXT[status]}
        </span>
      </div>

      <div className="pl-scroll flex-1 overflow-y-auto bg-background">
        {/* 候选人卡片 */}
        <section className="bg-card px-4 py-4">
          <div className="flex items-center gap-3">
            <AvatarBadge name={candidate.name} size={52} round />
            <div className="min-w-0 flex-1">
              <p className="pl-line-clamp-1 text-[16px] font-medium text-foreground">
                {candidate.name}
              </p>
              <p className="pl-line-clamp-1 text-[12px] text-pl-sub">
                {candidate.expect} · {candidate.city}
              </p>
            </div>
          </div>
          <div className="mt-3 flex flex-wrap items-center gap-x-3 gap-y-1 text-[11px] text-pl-sub">
            <span>{candidate.workYears} 年经验</span>
            <span aria-hidden>·</span>
            <span>{candidate.edu}</span>
            <span aria-hidden>·</span>
            <span>简历完善度 {candidate.resumeCompleteness}%</span>
          </div>
          <div className="mt-2 h-1.5 overflow-hidden rounded-full bg-secondary">
            <div
              className="h-full rounded-full bg-primary"
              style={{ width: `${candidate.resumeCompleteness}%` }}
            />
          </div>
        </section>

        {/* 应聘职位 */}
        <section className="mt-2 bg-card px-4 py-3.5">
          <h3 className="text-[13px] font-medium text-foreground">应聘职位</h3>
          <p className="mt-1.5 text-[14px] font-medium text-foreground">{job.title}</p>
          <p className="mt-0.5 text-[12px] text-pl-sub">
            {[job.salary, job.city].filter(Boolean).join(' · ')}
          </p>
          <p className="mt-1 text-[11px] text-pl-sub">
            投递于 {formatDateTime(application.createdAt)} · 最近更新{' '}
            {formatDateTime(application.updatedAt)}
          </p>
        </section>

        {/* AI 简历助手入口（AI 关闭时隐藏） */}
        {aiEnabled && (
          <section className="mt-2 bg-card px-4 py-3.5">
            <div className="flex items-center gap-2">
              <h3 className="flex items-center gap-1.5 text-[13px] font-medium text-foreground">
                <Sparkles className="h-3.5 w-3.5 text-primary" />
                AI 简历助手
              </h3>
              <button
                type="button"
                aria-label="打开 AI 助手"
                onClick={() => setAiOpen(true)}
                className="ml-auto flex h-8 items-center gap-1 rounded-lg border border-primary/60 px-3 text-[12px] font-medium text-primary transition-colors hover:bg-primary/5"
              >
                开始提问
              </button>
            </div>
            <p className="mt-1 text-[11px] leading-relaxed text-pl-sub">
              基于这份在线简历回答，并附上原文引用；简历里没有的信息会如实说明。
            </p>
          </section>
        )}

        {/* 打招呼语 */}
        {application.coverLetter && (
          <section className="mt-2 bg-card px-4 py-3.5">
            <h3 className="text-[13px] font-medium text-foreground">打招呼语</h3>
            <p className="mt-1.5 rounded-md bg-muted px-3 py-2 text-[13px] leading-relaxed text-foreground/90">
              {application.coverLetter}
            </p>
          </section>
        )}

        {/* 面试安排 */}
        {interview && status !== ApplicationStatusEnum.WITHDRAWN && (
          <section className="mt-2 bg-card px-4 py-3.5">
            <div className="flex items-center gap-2">
              <h3 className="flex items-center gap-1.5 text-[13px] font-medium text-foreground">
                <Video className="h-3.5 w-3.5 text-primary" />
                面试安排
              </h3>
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
                {INTERVIEW_STATUS_TEXT[interview.status]}
              </span>
            </div>
            <dl className="mt-2 space-y-1.5 text-[12px]">
              <div className="flex gap-2">
                <dt className="w-[52px] shrink-0 text-pl-sub">时间</dt>
                <dd className="text-foreground">{formatDateTime(interview.scheduledAt)}</dd>
              </div>
              <div className="flex gap-2">
                <dt className="w-[52px] shrink-0 text-pl-sub">形式</dt>
                <dd className="text-foreground">
                  {INTERVIEW_FORMAT_TEXT[interview.format]}
                  {interview.format === InterviewFormatEnum.ONSITE && interview.location
                    ? ` · ${interview.location}`
                    : ''}
                  {interview.format === InterviewFormatEnum.VIDEO && interview.onlineUrl
                    ? ` · ${interview.onlineUrl}`
                    : ''}
                </dd>
              </div>
              {interview.note && (
                <div className="flex gap-2">
                  <dt className="w-[52px] shrink-0 text-pl-sub">备注</dt>
                  <dd className="leading-relaxed text-foreground/90">{interview.note}</dd>
                </div>
              )}
            </dl>
          </section>
        )}

        <div className="h-4" />
      </div>

      {/* 底部操作区 */}
      <div className="shrink-0 border-t border-border bg-card px-4 py-3 pl-safe-bottom">
        {isWithdrawn ? (
          <p className="py-1 text-center text-[13px] text-pl-sub">候选人已撤回投递，无法继续操作</p>
        ) : (
          <div className="flex flex-wrap gap-2">
            <button
              type="button"
              disabled={busy !== null}
              onClick={() => void onMessage()}
              className="flex h-9 items-center gap-1.5 rounded-lg border border-border px-3 text-[13px] text-foreground transition-colors hover:bg-accent/40 disabled:opacity-50"
            >
              {busy === 'message' ? (
                <Loader2 className="h-3.5 w-3.5 animate-spin" />
              ) : (
                <MessageSquare className="h-3.5 w-3.5" />
              )}
              发消息
            </button>

            {canMarkViewed && (
              <button
                type="button"
                disabled={busy !== null}
                onClick={() => void mark(ApplicationStatusEnum.VIEWED, '已标记为已查看')}
                className="flex h-9 items-center rounded-lg border border-border px-3 text-[13px] text-foreground transition-colors hover:bg-accent/40 disabled:opacity-50"
              >
                {busy === ApplicationStatusEnum.VIEWED ? (
                  <Loader2 className="h-3.5 w-3.5 animate-spin" />
                ) : (
                  '标记已查看'
                )}
              </button>
            )}

            {canInvite && (
              <button
                type="button"
                disabled={busy !== null}
                onClick={() => openInterviewInvite(applicationId)}
                className="flex h-9 items-center rounded-lg bg-primary px-3.5 text-[13px] font-medium text-primary-foreground transition-opacity hover:opacity-90 disabled:opacity-50"
              >
                约面试
              </button>
            )}

            {pendingInterview && (
              <span className="flex h-9 items-center rounded-lg bg-amber-500/10 px-3 text-[12px] text-amber-600 dark:text-amber-400">
                等待候选人确认面试…
              </span>
            )}

            {canDecide && (
              <>
                <button
                  type="button"
                  disabled={busy !== null}
                  onClick={() => void mark(ApplicationStatusEnum.OFFER, '已标记为录用，等待候选人跟进')}
                  className="flex h-9 items-center rounded-lg border border-primary/60 px-3 text-[13px] text-primary transition-colors hover:bg-primary/5 disabled:opacity-50"
                >
                  {busy === ApplicationStatusEnum.OFFER ? (
                    <Loader2 className="h-3.5 w-3.5 animate-spin" />
                  ) : (
                    '录用'
                  )}
                </button>
                <button
                  type="button"
                  disabled={busy !== null}
                  onClick={() => void mark(ApplicationStatusEnum.REJECTED, '已标记为不合适')}
                  className="flex h-9 items-center rounded-lg border border-border px-3 text-[13px] text-pl-sub transition-colors hover:bg-accent/40 disabled:opacity-50"
                >
                  {busy === ApplicationStatusEnum.REJECTED ? (
                    <Loader2 className="h-3.5 w-3.5 animate-spin" />
                  ) : (
                    '不合适'
                  )}
                </button>
              </>
            )}
          </div>
        )}
      </div>

      {aiOpen && aiEnabled && (
        <HrAiAssistantSheet applicationId={applicationId} onClose={() => setAiOpen(false)} />
      )}
    </div>
  )
}
