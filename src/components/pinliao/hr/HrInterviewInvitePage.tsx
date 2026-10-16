/**
 * 聘聊 · HR 工作台 - 发起面试邀请
 * =====================================================================
 * 对应接口：POST /api/v1/conversations/{id}/actions/invite-interview
 *           （docs/api-endpoints.md §8.7，service: inviteInterview）
 *
 * 交互：
 *  - 常见时间一键预选（明天 10:00 / 后天 14:00 …），也可用原生控件微调
 *  - 面试形式：视频 / 现场 / 电话，字段随形式切换
 *  - 成功后返回候选人详情，并由 dataVersion 驱动刷新（状态置为「面试中」）
 *
 * 说明：预设时间基于本地时区在「用户点击」时计算，页面仅在登录后的
 * 客户端路径出现，不参与 SSR（水合安全）。
 * =====================================================================
 */

'use client'

import { useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { ArrowLeft, CalendarClock, Loader2 } from 'lucide-react'
import { InterviewFormatEnum } from '@/types/api'
import { getHrApplication, HR_APPLICATIONS_QUERY_KEY, inviteInterview } from '@/api/hrApplications'
import { ApiError } from '@/api/client'
import { useHrStore } from '@/store/hr'
import { cn } from '@/lib/utils'

interface Props {
  applicationId: string
}

/** Date → datetime-local 输入值（本地时区） */
function toLocalInput(d: Date): string {
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T${p(d.getHours())}:${p(d.getMinutes())}`
}

/** 预设：days 天后 hour 点（整点） */
function presetAt(days: number, hour: number): Date {
  const d = new Date()
  d.setDate(d.getDate() + days)
  d.setHours(hour, 0, 0, 0)
  return d
}

const TIME_PRESETS: { label: string; days: number; hour: number }[] = [
  { label: '明天 10:00', days: 1, hour: 10 },
  { label: '明天 14:00', days: 1, hour: 14 },
  { label: '后天 10:00', days: 2, hour: 10 },
  { label: '3 天后 14:00', days: 3, hour: 14 },
]

const FORMATS: { key: InterviewFormatEnum; label: string }[] = [
  { key: InterviewFormatEnum.VIDEO, label: '视频面试' },
  { key: InterviewFormatEnum.ONSITE, label: '现场面试' },
  { key: InterviewFormatEnum.PHONE, label: '电话面试' },
]

export function HrInterviewInvitePage({ applicationId }: Props) {
  const queryClient = useQueryClient()
  const popPage = useHrStore(s => s.popPage)
  const bumpDataVersion = useHrStore(s => s.bumpDataVersion)
  const toast = useHrStore(s => s.toast)
  const dataVersion = useHrStore(s => s.dataVersion)

  const { data: view } = useQuery({
    queryKey: ['hr', 'application', applicationId, dataVersion],
    queryFn: () => getHrApplication(applicationId),
  })

  const [scheduledAt, setScheduledAt] = useState(() => toLocalInput(presetAt(1, 10)))
  const [format, setFormat] = useState<InterviewFormatEnum>(InterviewFormatEnum.VIDEO)
  const [location, setLocation] = useState('')
  const [onlineUrl, setOnlineUrl] = useState('https://meeting.pinliao.dev/room-demo')
  const [note, setNote] = useState('')
  const [saving, setSaving] = useState(false)

  const onSubmit = async () => {
    const when = new Date(scheduledAt)
    if (Number.isNaN(when.getTime())) {
      toast('请选择面试时间')
      return
    }
    if (when.getTime() <= Date.now()) {
      toast('面试时间需晚于当前时间')
      return
    }
    if (format === InterviewFormatEnum.ONSITE && !location.trim()) {
      toast('现场面试请填写面试地点')
      return
    }

    setSaving(true)
    try {
      await inviteInterview({
        applicationId,
        invite: {
          scheduledAt: when.toISOString(),
          format,
          location: format === InterviewFormatEnum.ONSITE ? location.trim() : undefined,
          onlineUrl: format === InterviewFormatEnum.VIDEO ? onlineUrl.trim() || undefined : undefined,
          note: note.trim() || undefined,
        },
      })
      toast('面试邀请已发送，等待候选人确认')
      bumpDataVersion()
      await queryClient.invalidateQueries({ queryKey: HR_APPLICATIONS_QUERY_KEY })
      await queryClient.invalidateQueries({ queryKey: ['hr', 'conversations'] })
      popPage()
    } catch (err) {
      toast(err instanceof ApiError ? err.message : '发送失败，请重试')
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="flex flex-1 flex-col overflow-hidden">
      {/* 顶栏 */}
      <div className="flex shrink-0 items-center gap-2 border-b border-border bg-card px-3 py-2.5">
        <button
          type="button"
          onClick={popPage}
          aria-label="返回"
          className="flex h-8 w-8 items-center justify-center rounded-md text-foreground hover:bg-accent/50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
        >
          <ArrowLeft className="h-4 w-4" />
        </button>
        <span className="text-[14px] font-medium text-foreground">发起面试邀请</span>
      </div>

      <div className="pl-scroll flex-1 overflow-y-auto bg-background">
        {/* 候选人上下文 */}
        {view && (
          <div className="border-b border-border bg-secondary/40 px-4 py-2.5">
            <p className="text-[13px] text-foreground">
              邀请 <span className="font-medium">{view.candidate.name}</span> 参加「{view.job.title}
              」面试
            </p>
            <p className="mt-0.5 text-[11px] text-pl-sub">
              {view.candidate.expect} · {view.candidate.workYears}年经验 · {view.candidate.edu}
            </p>
          </div>
        )}

        <section className="mt-2 bg-card px-4 py-4">
          <h3 className="flex items-center gap-1.5 text-[13px] font-medium text-foreground">
            <CalendarClock className="h-3.5 w-3.5 text-primary" />
            面试时间
          </h3>
          <div className="mt-2 flex flex-wrap gap-1.5">
            {TIME_PRESETS.map(p => {
              const value = toLocalInput(presetAt(p.days, p.hour))
              const active = scheduledAt === value
              return (
                <button
                  key={p.label}
                  type="button"
                  aria-pressed={active}
                  onClick={() => setScheduledAt(value)}
                  className={cn(
                    'rounded-full px-2.5 py-1 text-[12px] transition-colors',
                    active
                      ? 'bg-primary text-primary-foreground'
                      : 'bg-secondary text-secondary-foreground hover:bg-accent',
                  )}
                >
                  {p.label}
                </button>
              )
            })}
          </div>
          <input
            type="datetime-local"
            value={scheduledAt}
            onChange={e => setScheduledAt(e.target.value)}
            aria-label="自定义面试时间"
            className="mt-2.5 h-10 w-full rounded-lg border border-border bg-background px-3 text-[13px] text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
          />
        </section>

        <section className="mt-2 bg-card px-4 py-4">
          <h3 className="text-[13px] font-medium text-foreground">面试形式</h3>
          <div className="mt-2 flex flex-wrap gap-1.5">
            {FORMATS.map(f => {
              const active = format === f.key
              return (
                <button
                  key={f.key}
                  type="button"
                  aria-pressed={active}
                  onClick={() => setFormat(f.key)}
                  className={cn(
                    'rounded-full px-2.5 py-1 text-[12px] transition-colors',
                    active
                      ? 'bg-primary text-primary-foreground'
                      : 'bg-secondary text-secondary-foreground hover:bg-accent',
                  )}
                >
                  {f.label}
                </button>
              )
            })}
          </div>

          {format === InterviewFormatEnum.ONSITE && (
            <>
              <label className="mt-3 block text-[12px] text-pl-sub" htmlFor="invite-location">
                面试地点
              </label>
              <input
                id="invite-location"
                type="text"
                value={location}
                onChange={e => setLocation(e.target.value)}
                placeholder="如：上海市浦东新区云杉大厦 12F 会议室 A"
                className="mt-1.5 h-10 w-full rounded-lg border border-border bg-background px-3 text-[13px] text-foreground placeholder:text-muted-foreground/70 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
              />
            </>
          )}

          {format === InterviewFormatEnum.VIDEO && (
            <>
              <label className="mt-3 block text-[12px] text-pl-sub" htmlFor="invite-url">
                会议链接
              </label>
              <input
                id="invite-url"
                type="text"
                value={onlineUrl}
                onChange={e => setOnlineUrl(e.target.value)}
                placeholder="https://"
                className="mt-1.5 h-10 w-full rounded-lg border border-border bg-background px-3 text-[13px] text-foreground placeholder:text-muted-foreground/70 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
              />
            </>
          )}

          {format === InterviewFormatEnum.PHONE && (
            <p className="mt-3 text-[12px] leading-relaxed text-pl-sub">
              将通过候选人的联系电话沟通，请留意接听。
            </p>
          )}
        </section>

        <section className="mt-2 bg-card px-4 py-4">
          <label className="text-[13px] font-medium text-foreground" htmlFor="invite-note">
            备注（选填）
          </label>
          <textarea
            id="invite-note"
            value={note}
            onChange={e => setNote(e.target.value)}
            rows={3}
            maxLength={200}
            placeholder="如：请提前 5 分钟进入会议，准备一段项目介绍"
            className="mt-2 w-full resize-none rounded-lg border border-border bg-background px-3 py-2 text-[13px] text-foreground placeholder:text-muted-foreground/70 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
          />
        </section>

        <div className="h-4" />
      </div>

      {/* 底部提交 */}
      <div className="shrink-0 border-t border-border bg-card px-4 py-3 pl-safe-bottom">
        <button
          type="button"
          disabled={saving}
          onClick={() => void onSubmit()}
          className="flex h-11 w-full items-center justify-center gap-2 rounded-lg bg-primary text-[15px] font-medium text-primary-foreground transition-opacity hover:opacity-90 disabled:opacity-60"
        >
          {saving && <Loader2 className="h-4 w-4 animate-spin" />}
          {saving ? '发送中…' : '发起面试邀请'}
        </button>
      </div>
    </div>
  )
}
