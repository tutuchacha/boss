/**
 * 聘聊 · 聊天页
 * =====================================================================
 * 对齐 HTML 原型 chatHTML() / startChat() / openChat() / sendFrom() / hrReply()：
 *  - 顶栏（返回 + HR 名称 + 公司职位；显示"对方正在输入"）
 *  - 职位条（职位标题 + 薪资 + 城市）
 *  - 消息列表（system/text/resume 三类气泡）
 *  - 快捷回复（发送简历/问薪资/约面试）
 *  - 输入框 + 发送按钮
 * =====================================================================
 */
'use client'

import { useEffect, useRef, useState } from 'react'
import { ArrowLeft, Send } from 'lucide-react'
import { usePinliaoStore } from '@/store/pinliao'
import { PageShell } from './JobDetailPage'
import { AvatarBadge } from './AvatarBadge'
import { jobById } from '@/api/mock/jobs'
import { sendMessage, onTyping } from '@/api/conversations'
import { MessageFromEnum, MessageTypeEnum } from '@/types/api'
import type { Message } from '@/types/api'
import { cn } from '@/lib/utils'

interface Props {
  jobId: string
  onBack: () => void
}

const QUICK_REPLIES = ['发送简历', '请问薪资待遇？', '想约个面试']

export function ChatPage({ jobId, onBack }: Props) {
  const conversations = usePinliaoStore(s => s.conversations)
  const messagesByConv = usePinliaoStore(s => s.messagesByConv)
  const mutateConversations = usePinliaoStore(s => s.mutateConversations)
  const markConversationRead = usePinliaoStore(s => s.markConversationRead)
  const typingConvIds = usePinliaoStore(s => s.typingConvIds)
  const setTyping = usePinliaoStore(s => s.setTyping)
  const setTab = usePinliaoStore(s => s.setTab)

  const [input, setInput] = useState('')
  const scrollRef = useRef<HTMLDivElement>(null)

  const job = jobById(jobId)
  const conv = conversations.find(c => c.jobId === jobId)
  const msgs = conv ? (messagesByConv[conv.id] ?? []) : []
  const isTyping = conv ? typingConvIds.has(conv.id) : false

  // 监听 HR 正在输入
  useEffect(() => {
    const off = onTyping((convId, isT) => {
      if (conv && convId === conv.id) setTyping(convId, isT)
    })
    return off
     
  }, [conv?.id])

  // 进入会话或消息变化时标记已读（用户在 ChatPage 中，新到达的 HR 消息不算未读）
  useEffect(() => {
    if (conv) markConversationRead(conv.id)
  }, [conv?.id, conv?.userUnreadCount, msgs.length])

  // 新消息时滚动到底部
  useEffect(() => {
    scrollRef.current?.scrollTo({ top: scrollRef.current.scrollHeight, behavior: 'auto' })
  }, [msgs.length, isTyping])

  if (!job || !conv) {
    return (
      <PageShell title="沟通" onBack={onBack}>
        <div className="flex h-full items-center justify-center text-sm text-pl-sub">
          会话不存在
        </div>
      </PageShell>
    )
  }

  const send = async (text: string) => {
    const t = text.trim()
    if (!t) return
    if (t === '发送简历') {
      await sendMessage(
        conv.id,
        { type: MessageTypeEnum.RESUME, content: '在线简历' },
        { conversations, messagesByConv: usePinliaoStore.getState().messagesByConv },
        mutateConversations,
      )
    } else {
      await sendMessage(
        conv.id,
        { type: MessageTypeEnum.TEXT, content: t },
        { conversations, messagesByConv: usePinliaoStore.getState().messagesByConv },
        mutateConversations,
      )
    }
    setInput('')
  }

  return (
    <div className="flex h-full flex-col bg-background">
      {/* 顶栏 */}
      <header className="flex h-12 items-center gap-2 border-b border-border bg-card px-2 pl-safe-top">
        <button
          onClick={onBack}
          aria-label="返回"
          className="flex h-9 w-9 items-center justify-center rounded-md text-foreground hover:bg-accent"
        >
          <ArrowLeft className="h-5 w-5" />
        </button>
        <div className="flex-1 min-w-0">
          <div className="pl-line-clamp-1 text-sm font-medium text-foreground">
            {job.hr.name}
          </div>
          <div className="pl-line-clamp-1 text-[11px] text-pl-sub">
            {isTyping ? (
              <span className="text-primary">对方正在输入…</span>
            ) : (
              `${job.company.name} · ${job.hr.title}`
            )}
          </div>
        </div>
      </header>

      {/* 职位条 */}
      <div className="flex items-center gap-2 border-b border-border bg-card px-4 py-2">
        <span className="pl-line-clamp-1 flex-1 text-[13px] text-foreground/80">{job.title}</span>
        <span className="shrink-0 text-[13px] font-medium text-pl-sal">{job.salary}</span>
        <span className="shrink-0 text-[11px] text-pl-sub">{job.city}</span>
      </div>

      {/* 消息列表 */}
      <div ref={scrollRef} className="pl-scroll flex-1 overflow-y-auto px-3 py-3">
        <div className="mx-auto max-w-md space-y-2.5">
          {msgs.map(m => (
            <MessageBubble key={m.id} m={m} hrName={job.hr.name} />
          ))}
          {isTyping && (
            <div className="flex justify-start">
              <div className="flex items-center gap-1 rounded-2xl bg-card px-3 py-2 text-pl-sub">
                <span className="h-1.5 w-1.5 animate-bounce rounded-full bg-pl-sub [animation-delay:-300ms]" />
                <span className="h-1.5 w-1.5 animate-bounce rounded-full bg-pl-sub [animation-delay:-150ms]" />
                <span className="h-1.5 w-1.5 animate-bounce rounded-full bg-pl-sub" />
              </div>
            </div>
          )}
        </div>
      </div>

      {/* 快捷回复 */}
      <div className="pl-scroll flex gap-1.5 overflow-x-auto border-t border-border bg-card px-3 py-2">
        {QUICK_REPLIES.map(q => (
          <button
            key={q}
            onClick={() => send(q)}
            className="shrink-0 whitespace-nowrap rounded-full bg-secondary px-3 py-1 text-xs text-secondary-foreground"
          >
            {q}
          </button>
        ))}
      </div>

      {/* 输入区 */}
      <div className="flex items-center gap-2 border-t border-border bg-card px-3 py-2 pl-safe-bottom">
        <input
          type="text"
          value={input}
          onChange={e => setInput(e.target.value)}
          onKeyDown={e => {
            if (e.key === 'Enter' && !e.nativeEvent.isComposing) {
              e.preventDefault()
              send(input)
            }
          }}
          placeholder="输入消息"
          aria-label="输入消息"
          autoComplete="off"
          className="flex-1 rounded-full bg-muted px-3 py-2 text-sm outline-none placeholder:text-pl-sub"
        />
        <button
          onClick={() => send(input)}
          disabled={!input.trim()}
          aria-label="发送"
          className={cn(
            'flex h-9 w-9 items-center justify-center rounded-full',
            input.trim()
              ? 'bg-primary text-primary-foreground'
              : 'bg-muted text-pl-sub',
          )}
        >
          <Send className="h-4 w-4" />
        </button>
      </div>
    </div>
  )
}

function MessageBubble({ m, hrName }: { m: Message; hrName: string }) {
  if (m.fromType === MessageFromEnum.SYSTEM) {
    return (
      <div className="pl-msg my-2 text-center">
        <span className="inline-block rounded-full bg-muted px-3 py-1 text-[11px] text-pl-sub">
          {m.content}
        </span>
      </div>
    )
  }
  const isMe = m.fromType === MessageFromEnum.USER
  return (
    <div className={cn('pl-msg flex', isMe ? 'justify-end' : 'justify-start')}>
      <div
        className={cn(
          'flex max-w-[78%] items-end gap-1.5',
          isMe && 'flex-row-reverse',
        )}
      >
        {!isMe && <AvatarBadge name={hrName} size={28} round />}
        <div
          className={cn(
            'rounded-2xl px-3 py-2 text-[13px]',
            isMe
              ? 'rounded-br-sm bg-primary text-primary-foreground'
              : 'rounded-bl-sm bg-card text-foreground',
          )}
        >
          {m.type === MessageTypeEnum.RESUME ? (
            <div className="flex items-center gap-2">
              <AvatarBadge name="简" size={24} />
              <div>
                <div className="text-[13px] font-medium">在线简历</div>
                <div className="text-[11px] opacity-80">点击查看</div>
              </div>
            </div>
          ) : (
            m.content
          )}
        </div>
      </div>
    </div>
  )
}
