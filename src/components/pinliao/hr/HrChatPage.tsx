/**
 * 聘聊 · HR 工作台 - 聊天页
 * =====================================================================
 * 对应接口：
 *  - GET  /api/v1/conversations/{id}/messages
 *  - POST /api/v1/conversations/{id}/messages   以 HR 身份发送
 *  - POST /api/v1/conversations/{id}/read       标记已读
 *
 * 与求职者端 ChatPage 对称：气泡、快捷回复、候选人自动回复、正在输入提示。
 * =====================================================================
 */

'use client'

import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { ArrowLeft, Loader2, Send } from 'lucide-react'
import { MessageFromEnum } from '@/types/api'
import {
  getHrConversation,
  listHrMessages,
  markHrRead,
  replyAsCandidate,
  sendHrMessage,
} from '@/api/hrConversations'
import { useAuthStore } from '@/store/auth'
import { useHrStore } from '@/store/hr'
import { cn } from '@/lib/utils'
import { AvatarBadge } from '../AvatarBadge'

interface Props {
  conversationId: string
}

/** 快捷回复（HR 常用话术） */
const QUICK_REPLIES = [
  '您好，看了您的简历，方便聊聊吗？',
  '请问您目前的工作状态和到岗时间？',
  '这个岗位的薪资范围方便沟通一下吗？',
  '方便约个时间电话沟通吗？',
]

/** 消息时间：仅显示时刻 */
function msgTime(iso: string): string {
  const d = new Date(iso)
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

export function HrChatPage({ conversationId }: Props) {
  const queryClient = useQueryClient()
  const hrProfile = useAuthStore(s => s.hrProfile)
  const popPage = useHrStore(s => s.popPage)
  const bumpDataVersion = useHrStore(s => s.bumpDataVersion)
  const toast = useHrStore(s => s.toast)

  const [input, setInput] = useState('')
  const [sending, setSending] = useState(false)
  const [candidateTyping, setCandidateTyping] = useState(false)
  const scrollRef = useRef<HTMLDivElement | null>(null)

  /** 会话头信息 */
  const { data: view } = useQuery({
    queryKey: ['hr', 'conversation', conversationId],
    queryFn: () => getHrConversation(conversationId),
  })

  /** 消息列表 */
  const { data: messages = [], isPending } = useQuery({
    queryKey: ['hr', 'messages', conversationId],
    queryFn: () => listHrMessages(conversationId),
  })

  /** 进入会话即标记已读（并同步刷新列表角标） */
  const markRead = useCallback(async () => {
    await markHrRead(conversationId)
    bumpDataVersion()
    await queryClient.invalidateQueries({ queryKey: ['hr', 'conversations'] })
  }, [conversationId, bumpDataVersion, queryClient])

  useEffect(() => {
    void markRead()
  }, [markRead])

  /** 新消息到达后滚到底部 */
  useEffect(() => {
    const el = scrollRef.current
    if (el) el.scrollTop = el.scrollHeight
  }, [messages.length, candidateTyping])

  const onSend = async (text?: string) => {
    const content = (text ?? input).trim()
    if (!content || sending) return

    setSending(true)
    try {
      await sendHrMessage(conversationId, content, hrProfile?.id ?? '')
      setInput('')
      await queryClient.invalidateQueries({ queryKey: ['hr', 'messages', conversationId] })
      await queryClient.invalidateQueries({ queryKey: ['hr', 'conversations'] })

      // 模拟候选人回复：先显示"正在输入"，再追加消息
      setCandidateTyping(true)
      setTimeout(() => {
        void (async () => {
          await replyAsCandidate(conversationId, content)
          setCandidateTyping(false)
          await queryClient.invalidateQueries({
            queryKey: ['hr', 'messages', conversationId],
          })
          await queryClient.invalidateQueries({ queryKey: ['hr', 'conversations'] })
        })()
      }, 1200)
    } catch {
      toast('发送失败，请重试')
      setCandidateTyping(false)
    } finally {
      setSending(false)
    }
  }

  /** 按日期分组展示（简单实现：同一天不重复显示日期） */
  const rendered = useMemo(() => messages, [messages])

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
        {view && <AvatarBadge name={view.candidate.name} size={30} />}
        <div className="min-w-0 flex-1">
          <p className="pl-line-clamp-1 text-[14px] font-medium text-foreground">
            {view?.candidate.name ?? '候选人'}
          </p>
          <p className="pl-line-clamp-1 text-[11px] text-pl-sub">
            {candidateTyping
              ? '正在输入…'
              : view
                ? `应聘 ${view.job.title}`
                : ''}
          </p>
        </div>
      </div>

      {/* 候选人简历摘要条 */}
      {view && (
        <div className="shrink-0 border-b border-border bg-secondary/40 px-4 py-2">
          <div className="flex flex-wrap items-center gap-x-2 gap-y-0.5 text-[11px] text-pl-sub">
            <span className="text-foreground">{view.candidate.expect}</span>
            <span aria-hidden>·</span>
            <span>{view.candidate.workYears}年经验</span>
            <span aria-hidden>·</span>
            <span>{view.candidate.edu}</span>
            <span aria-hidden>·</span>
            <span>{view.candidate.city}</span>
            <span className="ml-auto">简历完善度 {view.candidate.resumeCompleteness}%</span>
          </div>
        </div>
      )}

      {/* 消息区 */}
      <div ref={scrollRef} className="pl-scroll flex-1 overflow-y-auto px-4 py-3">
        {isPending && (
          <p className="py-6 text-center text-[12px] text-pl-sub">加载消息中…</p>
        )}

        <div className="space-y-3">
          {rendered.map(m => {
            const mine = m.fromType === MessageFromEnum.HR
            return (
              <div key={m.id} className={cn('flex', mine ? 'justify-end' : 'justify-start')}>
                <div className={cn('flex max-w-[78%] flex-col', mine && 'items-end')}>
                  <div
                    className={cn(
                      'rounded-2xl px-3 py-2 text-[13px] leading-relaxed',
                      mine
                        ? 'bg-primary text-primary-foreground'
                        : 'bg-secondary text-secondary-foreground',
                    )}
                  >
                    {m.content}
                  </div>
                  <span className="mt-1 px-1 text-[10px] text-pl-sub">{msgTime(m.createdAt)}</span>
                </div>
              </div>
            )
          })}

          {candidateTyping && (
            <div className="flex justify-start">
              <div className="rounded-2xl bg-secondary px-3 py-2 text-[12px] text-pl-sub">
                对方正在输入…
              </div>
            </div>
          )}
        </div>
      </div>

      {/* 快捷回复 */}
      <div className="shrink-0 border-t border-border bg-card px-3 pt-2">
        <div className="flex gap-1.5 overflow-x-auto pb-2">
          {QUICK_REPLIES.map(q => (
            <button
              key={q}
              type="button"
              onClick={() => void onSend(q)}
              className="shrink-0 rounded-full bg-secondary px-2.5 py-1 text-[11px] text-secondary-foreground transition-colors hover:bg-accent"
            >
              {q}
            </button>
          ))}
        </div>
      </div>

      {/* 输入区 */}
      <div className="flex shrink-0 items-center gap-2 border-t border-border bg-card px-3 py-2.5 pl-safe-bottom">
        <input
          type="text"
          value={input}
          onChange={e => setInput(e.target.value)}
          onKeyDown={e => {
            if (e.key === 'Enter' && !e.shiftKey) {
              e.preventDefault()
              void onSend()
            }
          }}
          placeholder="输入消息，与候选人沟通…"
          aria-label="输入消息"
          className="h-10 flex-1 rounded-lg border border-border bg-background px-3 text-[14px] text-foreground placeholder:text-muted-foreground/70 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
        />
        <button
          type="button"
          disabled={sending || input.trim().length === 0}
          onClick={() => void onSend()}
          aria-label="发送"
          className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-primary text-primary-foreground transition-opacity hover:opacity-90 disabled:opacity-40"
        >
          {sending ? (
            <Loader2 className="h-4 w-4 animate-spin" />
          ) : (
            <Send className="h-4 w-4" />
          )}
        </button>
      </div>
    </div>
  )
}
