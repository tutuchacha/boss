/**
 * 聘聊 · 职位详情页
 * =====================================================================
 * 对齐 HTML 原型 jobHTML()：
 *  - 顶栏（返回 + 收藏）
 *  - 标题/薪资/标签/福利
 *  - HR 卡片
 *  - 岗位职责 + 任职要求
 *  - 公司信息块
 *  - 工作地点 + 发布时间
 *  - 底部 CTA（收藏 + 立即沟通/继续沟通）
 * =====================================================================
 */
'use client'

import { useMemo, useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { ArrowLeft, Star } from 'lucide-react'
import { usePinliaoStore } from '@/store/pinliao'
import { useAuthStore } from '@/store/auth'
import { getJobDetail } from '@/api/jobs'
import type { JobDetail } from '@/types/api'
import { ApplicationStatusEnum } from '@/types/api'
import { AvatarBadge } from './AvatarBadge'
import { startConversation } from '@/api/conversations'
import { applyJob, listMyApplications } from '@/api/applications'
import { ApiError } from '@/api/client'
import { whenText } from '@/lib/time'
import { responsibilitiesOf } from '@/api/mock/categories'
import { BottomSheet } from './BottomSheet'
import { cn } from '@/lib/utils'

interface Props {
  jobId: string
  onBack: () => void
}

export function JobDetailPage({ jobId, onBack }: Props) {
  const [detail, setDetail] = useState<JobDetail | null>(null)
  const [loading, setLoading] = useState(true)
  const [applyOpen, setApplyOpen] = useState(false)
  const [coverLetter, setCoverLetter] = useState('')
  const [applying, setApplying] = useState(false)
  const favorites = usePinliaoStore(s => s.favorites)
  const toggleFavorite = usePinliaoStore(s => s.toggleFavorite)
  const toast = usePinliaoStore(s => s.toast)
  const pushPage = usePinliaoStore(s => s.pushPage)
  const conversations = usePinliaoStore(s => s.conversations)
  const mutateConversations = usePinliaoStore(s => s.mutateConversations)
  const userId = useAuthStore(s => s.user?.id ?? '')
  const seekerProfile = useAuthStore(s => s.seekerProfile)
  const queryClient = useQueryClient()

  useMemo(() => {
    // mock 模式下 getJobDetail 同步 Promise，then 中取值
    void getJobDetail(jobId).then(d => {
      setDetail(d)
      setLoading(false)
    })
  }, [jobId])

  /** 我的投递：判断当前职位是否已投递（撤回后可重新投递） */
  const { data: myApplications = [] } = useQuery({
    queryKey: ['my-applications', userId],
    queryFn: () => listMyApplications(userId),
    enabled: Boolean(userId),
  })
  const myApplication = myApplications.find(
    v => v.job?.id === jobId && v.application.status !== ApplicationStatusEnum.WITHDRAWN,
  )

  if (loading || !detail) {
    return (
      <PageShell title="职位详情" onBack={onBack}>
        <div className="flex h-full items-center justify-center text-sm text-pl-sub">
          加载中…
        </div>
      </PageShell>
    )
  }

  const isFav = favorites.includes(jobId)
  const hasConv = conversations.some(c => c.jobId === jobId)
  const w = ['五险一金', '带薪年假', '年度体检', '弹性工作']
  const resp = responsibilitiesOf(detail.cat)

  const handleChat = async () => {
    // 实时读取 store，避免 hasConv 快照过期
    const state = usePinliaoStore.getState()
    const top = state.pageStack[state.pageStack.length - 1]
    // 避免重复 push（用户连续点击）
    if (top?.type === 'chat' && top.id === jobId) return

    const firstMsg = `您好，我对「${detail.title}」很感兴趣，方便聊聊吗？`
    if (!state.conversations.some(c => c.jobId === jobId)) {
      await startConversation(
        jobId,
        firstMsg,
        { conversations: state.conversations, messagesByConv: state.messagesByConv },
        mutateConversations,
      )
      toast('已发起沟通')
    }
    pushPage({ type: 'chat', id: jobId })
  }

  /** 投递简历：写入 Mock 内存 + 失效「我的投递」缓存（按钮随 query 刷新为已投递） */
  const onApply = async () => {
    if (!userId) return
    setApplying(true)
    try {
      await applyJob(userId, {
        jobId,
        coverLetter: coverLetter.trim() || undefined,
      })
      toast('投递成功，可在「我的投递」查看进度')
      setApplyOpen(false)
      await queryClient.invalidateQueries({ queryKey: ['my-applications', userId] })
    } catch (err) {
      toast(err instanceof ApiError ? err.message : '投递失败，请重试')
    } finally {
      setApplying(false)
    }
  }

  return (
    <PageShell
      title="职位详情"
      onBack={onBack}
      extra={
        <button
          onClick={() => {
            toggleFavorite(jobId)
            toast(isFav ? '已取消收藏' : '已收藏')
          }}
          aria-label={isFav ? '取消收藏' : '收藏'}
          aria-pressed={isFav}
          className={cn(
            'flex h-9 w-9 items-center justify-center rounded-md',
            isFav ? 'text-primary' : 'text-pl-sub hover:text-foreground',
          )}
        >
          <Star className="h-5 w-5" fill={isFav ? 'currentColor' : 'none'} />
        </button>
      }
    >
      {/* 滚动内容区 + sticky footer（footer 作为 scroll 内的 sticky 元素，
           内容短时紧贴底部，内容长时随滚动常驻视口底部，无视觉空隙） */}
      <div className="pl-scroll relative flex-1 overflow-y-auto bg-background">
        <div className="flex min-h-full flex-col">
        {/* 标题区 + 内联 HR 卡片 + 主 CTA（首屏可见，避免必须滑到底部才能投递） */}
        <section className="bg-card px-4 py-4">
          <h1 className="text-lg font-semibold text-foreground">{detail.title}</h1>
          <div className="mt-1 text-lg font-semibold text-pl-sal">{detail.salary}</div>
          <div className="mt-1.5 flex flex-wrap items-center gap-x-2 gap-y-0.5 text-xs text-pl-sub">
            <span>{detail.city} {detail.area}</span>
            <span aria-hidden>·</span>
            <span>{detail.exp}</span>
            <span aria-hidden>·</span>
            <span>{detail.edu}</span>
          </div>
          {detail.tags.length > 0 && (
            <div className="mt-2.5 flex flex-wrap gap-1.5">
              {detail.tags.map(t => (
                <span key={t} className="rounded bg-secondary px-2 py-0.5 text-[11px] text-secondary-foreground">
                  {t}
                </span>
              ))}
            </div>
          )}
          <div className="mt-2.5 flex flex-wrap gap-1.5">
            {w.map(t => (
              <span key={t} className="rounded bg-accent/60 px-2 py-0.5 text-[11px] text-accent-foreground">
                {t}
              </span>
            ))}
          </div>

          {/* 内联 HR + 立即沟通（首屏即可见） */}
          <div className="mt-3 flex items-center gap-2 rounded-md bg-muted/50 px-2.5 py-2">
            <AvatarBadge name={detail.hr.name} size={32} round />
            <div className="min-w-0 flex-1">
              <div className="pl-line-clamp-1 text-[13px] font-medium text-foreground">{detail.hr.name}</div>
              <div className="pl-line-clamp-1 text-[11px] text-pl-sub">
                {detail.company.name} · {detail.hr.title}
              </div>
            </div>
            <button
              onClick={handleChat}
              className="shrink-0 rounded-md bg-primary px-3.5 py-1.5 text-xs font-medium text-primary-foreground active:scale-95"
            >
              {hasConv ? '继续沟通' : '立即沟通'}
            </button>
          </div>
        </section>

        {/* 职责与要求 */}
        <section className="mt-2 bg-card px-4 py-4">
          <h2 className="text-sm font-semibold text-foreground">岗位职责</h2>
          <ul className="mt-2 space-y-1.5">
            {resp.map((t, i) => (
              <li key={i} className="flex gap-1.5 text-[13px] text-foreground/90">
                <span className="text-pl-sub">·</span>
                <span>{t}</span>
              </li>
            ))}
          </ul>
          <h2 className="mt-4 text-sm font-semibold text-foreground">任职要求</h2>
          <ul className="mt-2 space-y-1.5">
            <li className="flex gap-1.5 text-[13px] text-foreground/90">
              <span className="text-pl-sub">·</span>
              <span>
                {detail.exp === '应届' ? '应届毕业生' : detail.exp === '经验不限' ? '经验不限' : `${detail.exp}相关工作经验`}
                ，{detail.edu === '学历不限' ? '学历不限' : `${detail.edu}及以上学历`}
              </span>
            </li>
            <li className="flex gap-1.5 text-[13px] text-foreground/90">
              <span className="text-pl-sub">·</span>
              <span>掌握或熟悉：{detail.tags.join('、')}</span>
            </li>
            <li className="flex gap-1.5 text-[13px] text-foreground/90">
              <span className="text-pl-sub">·</span>
              <span>沟通顺畅，责任心强，有良好的团队协作意识</span>
            </li>
          </ul>
        </section>

        {/* 公司信息 */}
        <section className="mt-2 bg-card px-4 py-3.5">
          <div className="flex items-center gap-3">
            <AvatarBadge name={detail.company.name} size={44} />
            <div className="flex-1">
              <div className="text-sm font-medium text-foreground">{detail.company.name}</div>
              <div className="text-xs text-pl-sub">
                {[detail.company.stage, detail.company.size].filter(Boolean).join(' · ')}
              </div>
            </div>
          </div>
        </section>

        {/* 工作地点 */}
        <section className="mt-2 bg-card px-4 py-3.5">
          <h2 className="text-sm font-semibold text-foreground">工作地点</h2>
          <p className="mt-1 text-[13px] text-foreground/90">
            {detail.city} {detail.area}
          </p>
          <p className="mt-0.5 text-[11px] text-pl-sub">发布于{whenText(detail.days)}</p>
        </section>

        {/* 底部 CTA（sticky 跟随 scroll，内容短时紧贴底部，长时常驻视口底部） */}
        <footer className="sticky bottom-0 z-10 mt-auto flex items-center gap-3 border-t border-border bg-card/95 px-4 py-2.5 backdrop-blur pl-safe-bottom">
          <button
            onClick={() => {
              toggleFavorite(jobId)
              toast(isFav ? '已取消收藏' : '已收藏')
            }}
            className={cn(
              'flex flex-col items-center justify-center rounded-md border px-4 py-2 text-xs',
              isFav ? 'border-primary text-primary' : 'border-border text-pl-sub',
            )}
            aria-label={isFav ? '已收藏' : '收藏'}
          >
            <Star className="h-4 w-4" fill={isFav ? 'currentColor' : 'none'} />
            <span className="mt-0.5">{isFav ? '已收藏' : '收藏'}</span>
          </button>
          <button
            onClick={() => {
              if (myApplication) return
              setApplyOpen(true)
            }}
            disabled={Boolean(myApplication)}
            className={cn(
              'flex-1 rounded-md border py-2.5 text-sm',
              myApplication ? 'border-border text-pl-sub' : 'border-primary text-primary',
            )}
          >
            {myApplication ? '已投递' : '投递简历'}
          </button>
          <button
            onClick={handleChat}
            className="flex-1 rounded-md bg-primary py-2.5 text-sm font-medium text-primary-foreground"
          >
            {hasConv ? '继续沟通' : '立即沟通'}
          </button>
        </footer>
        </div>
      </div>

      {/* 投递简历 Sheet（复用全局 BottomSheet，含遮罩 + Esc 关闭） */}
      {applyOpen && (
        <BottomSheet title="投递简历" onClose={() => !applying && setApplyOpen(false)}>
          <div className="px-4 pb-4 pt-3">
            <div className="rounded-md bg-muted px-3 py-2.5">
              <p className="text-[13px] font-medium text-foreground">
                在线简历 · {seekerProfile?.name ?? '我的简历'}
              </p>
              <p className="mt-0.5 text-[11px] text-pl-sub">
                投递后 HR 可查看你的在线简历与联系方式
              </p>
            </div>
            <label className="mt-3 block text-[12px] text-pl-sub" htmlFor="apply-cover">
              打招呼语（选填）
            </label>
            <textarea
              id="apply-cover"
              value={coverLetter}
              onChange={e => setCoverLetter(e.target.value)}
              rows={3}
              maxLength={200}
              placeholder="说点什么，让 HR 更快注意到你"
              className="mt-1.5 w-full resize-none rounded-lg border border-border bg-background px-3 py-2 text-[13px] text-foreground placeholder:text-muted-foreground/70 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
            />
            <button
              type="button"
              disabled={applying}
              onClick={() => void onApply()}
              className="mt-3 flex h-11 w-full items-center justify-center rounded-lg bg-primary text-[15px] font-medium text-primary-foreground transition-opacity hover:opacity-90 disabled:opacity-60"
            >
              {applying ? '投递中…' : '确认投递'}
            </button>
          </div>
        </BottomSheet>
      )}
    </PageShell>
  )
}

/** 通用页面外壳：顶栏（返回 + 标题）+ 内容 */
export function PageShell({
  title,
  onBack,
  children,
  extra,
}: {
  title: string
  onBack: () => void
  children: React.ReactNode
  extra?: React.ReactNode
}) {
  return (
    <div className="flex h-full flex-col bg-background">
      <header className="flex h-12 items-center gap-2 border-b border-border bg-card px-2 pl-safe-top">
        <button
          onClick={onBack}
          aria-label="返回"
          className="flex h-9 w-9 items-center justify-center rounded-md text-foreground hover:bg-accent"
        >
          <ArrowLeft className="h-5 w-5" />
        </button>
        <span className="flex-1 text-sm font-medium text-foreground">{title}</span>
        {extra}
      </header>
      {children}
    </div>
  )
}
