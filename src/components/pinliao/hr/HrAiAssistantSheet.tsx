/**
 * 聘聊 · HR 候选人详情 - AI 简历助手抽屉（Step 4 T-AI2 问答 / T-AI3 流式）
 * =====================================================================
 * 入口：候选人详情「AI 简历助手」卡片（仅 /ai/status.enabled=true 时展示）。
 * 能力：就该候选人的简历提问，回答边生成边渲染（打字机），带 [n] 引用；
 *      无依据时后端返回「简历中没有相关信息。」且 citations 为空（不展示来源列表）。
 * 引用定位：点来源卡片滚到正文中的 [n]，点 [n] 滚回来源卡片，命中处高亮 1.2s。
 *
 * 说明：
 *  - 取数走 service 层 askResumeQuestionStream（USE_MOCK 双分支），组件不直接 fetch；
 *  - 会话状态是本地临时态（刷新即清空），不落库、不影响业务数据；
 *  - 完成后以返回值覆盖已累积文本：模型漏标引用时后端会追加「来源：[1]」兜底。
 * =====================================================================
 */

'use client'

import { useEffect, useRef, useState } from 'react'
import { Loader2, Send, Sparkles } from 'lucide-react'
import type { AiCitation } from '@/types/api'
import { askResumeQuestionStream } from '@/api/ai'
import { ApiError } from '@/api/client'
import { cn } from '@/lib/utils'
import { BottomSheet } from '../BottomSheet'

interface Props {
  applicationId: string
  onClose: () => void
}

interface ChatTurn {
  id: number
  role: 'user' | 'assistant'
  text: string
  citations: AiCitation[]
  grounded?: boolean
  /** 正在流式接收（渲染光标、完成前不渲染空气泡） */
  streaming?: boolean
}

/** 首屏快捷问题（覆盖 5 类段落中最常用的 3 类） */
const QUICK_QUESTIONS = ['候选人的核心技能是什么？', '工作经历有哪些？', '项目中最有代表性的成果？']

const SECTION_TEXT: Record<string, string> = {
  basics: '基本信息',
  educations: '教育经历',
  experiences: '工作经历',
  projects: '项目经历',
  skills: '技能',
}

const markDomId = (turnId: number, index: number) => `ai-mark-${turnId}-${index}`
const citeDomId = (turnId: number, index: number) => `ai-cite-${turnId}-${index}`

export function HrAiAssistantSheet({ applicationId, onClose }: Props) {
  const [input, setInput] = useState('')
  const [turns, setTurns] = useState<ChatTurn[]>([])
  const [pending, setPending] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [flash, setFlash] = useState<string | null>(null)
  const scrollRef = useRef<HTMLDivElement>(null)
  const nextId = useRef(1)
  const flashTimer = useRef<number | null>(null)

  useEffect(() => {
    const el = scrollRef.current
    if (el) el.scrollTop = el.scrollHeight
  }, [turns, pending])

  useEffect(
    () => () => {
      if (flashTimer.current) window.clearTimeout(flashTimer.current)
    },
    [],
  )

  /** 引用 ←→ 正文互相定位（元素在同一滚动容器内，scrollIntoView 即可） */
  const jumpTo = (domId: string) => {
    const el = document.getElementById(domId)
    if (!el) return
    el.scrollIntoView({ block: 'center', behavior: 'smooth' })
    setFlash(domId)
    if (flashTimer.current) window.clearTimeout(flashTimer.current)
    flashTimer.current = window.setTimeout(() => setFlash(null), 1200)
  }

  const ask = async (raw: string) => {
    const question = raw.trim()
    if (!question || pending) return
    setError(null)
    setInput('')
    const userTurnId = nextId.current++
    const aiTurnId = nextId.current++
    setTurns(prev => [
      ...prev,
      { id: userTurnId, role: 'user', text: question, citations: [] },
      { id: aiTurnId, role: 'assistant', text: '', citations: [], streaming: true },
    ])
    setPending(true)
    try {
      const answer = await askResumeQuestionStream(applicationId, question, {
        onDelta: text =>
          setTurns(prev =>
            prev.map(turn => (turn.id === aiTurnId ? { ...turn, text: turn.text + text } : turn)),
          ),
        onCitation: citation =>
          setTurns(prev =>
            prev.map(turn =>
              turn.id === aiTurnId ? { ...turn, citations: [...turn.citations, citation] } : turn,
            ),
          ),
      })
      setTurns(prev =>
        prev.map(turn =>
          turn.id === aiTurnId
            ? {
                ...turn,
                text: answer.answer,
                citations: answer.citations,
                grounded: answer.grounded,
                streaming: false,
              }
            : turn,
        ),
      )
    } catch (err) {
      // 空占位气泡直接移除；已经打出部分文字则保留，避免「回答凭空消失」
      setTurns(prev =>
        prev.flatMap(turn => {
          if (turn.id !== aiTurnId) return [turn]
          return turn.text ? [{ ...turn, streaming: false }] : []
        }),
      )
      setError(err instanceof ApiError ? err.message : 'AI 服务暂时不可用，请稍后重试')
    } finally {
      setPending(false)
    }
  }

  /** 把正文里的 [n] 渲染成可点击引用；流式过程中未闭合的标记按普通文本显示 */
  const renderAnswer = (turn: ChatTurn) =>
    turn.text.split(/(\[\d{1,2}\])/).map((part, i) => {
      const matched = /^\[(\d{1,2})\]$/.exec(part)
      if (!matched) return <span key={i}>{part}</span>
      const index = Number(matched[1])
      const domId = markDomId(turn.id, index)
      return (
        <button
          key={i}
          type="button"
          id={domId}
          aria-label={`查看引用 ${index}`}
          onClick={() => jumpTo(citeDomId(turn.id, index))}
          className={cn(
            'mx-0.5 inline-flex h-4 items-center rounded bg-primary/10 px-1 align-baseline text-[10px] font-medium text-primary transition-colors hover:bg-primary/20',
            flash === domId && 'ring-2 ring-primary',
          )}
        >
          [{index}]
        </button>
      )
    })

  return (
    <BottomSheet title="AI 简历助手" onClose={onClose}>
      <div className="flex h-[62vh] flex-col">
        <div ref={scrollRef} className="flex-1 overflow-y-auto px-4 py-3 pl-scroll">
          <div className="flex items-start gap-2 rounded-lg bg-muted px-3 py-2.5">
            <Sparkles className="mt-0.5 h-3.5 w-3.5 shrink-0 text-primary" />
            <p className="text-[12px] leading-relaxed text-pl-sub">
              AI 只基于这位候选人的在线简历回答，结论会附上原文出处；
              简历里没有的信息会直接说明「没有相关信息」，不会猜测。
            </p>
          </div>

          {turns.length === 0 && (
            <div className="mt-3 flex flex-wrap gap-2">
              {QUICK_QUESTIONS.map(q => (
                <button
                  key={q}
                  type="button"
                  onClick={() => void ask(q)}
                  className="rounded-full border border-border px-3 py-1.5 text-[12px] text-foreground transition-colors hover:bg-accent/40"
                >
                  {q}
                </button>
              ))}
            </div>
          )}

          <div className="mt-3 space-y-3">
            {turns.map(turn =>
              turn.role === 'user' ? (
                <div key={turn.id} className="flex justify-end">
                  <p className="max-w-[85%] rounded-lg rounded-br-sm bg-primary px-3 py-2 text-[13px] leading-relaxed text-primary-foreground">
                    {turn.text}
                  </p>
                </div>
              ) : (
                <div key={turn.id} className="flex flex-col items-start gap-1.5">
                  {turn.text && (
                    <p className="max-w-[92%] whitespace-pre-wrap rounded-lg rounded-bl-sm bg-muted px-3 py-2 text-[13px] leading-relaxed text-foreground">
                      {renderAnswer(turn)}
                      {turn.streaming && (
                        <span
                          aria-hidden
                          className="ml-0.5 inline-block h-3 w-[2px] animate-pulse bg-primary align-middle"
                        />
                      )}
                    </p>
                  )}
                  {turn.citations.length > 0 && (
                    <ul className="w-full space-y-1.5">
                      {turn.citations.map(c => {
                        const domId = citeDomId(turn.id, c.index)
                        return (
                          <li key={`${turn.id}-${c.chunkId}`}>
                            <button
                              type="button"
                              id={domId}
                              onClick={() => jumpTo(markDomId(turn.id, c.index))}
                              className={cn(
                                'w-full rounded-md border border-border bg-card px-2.5 py-1.5 text-left transition-colors hover:border-primary/50',
                                flash === domId && 'ring-2 ring-primary/40',
                              )}
                            >
                              <span className="flex items-center gap-1.5 text-[11px] font-medium text-foreground">
                                <span className="rounded bg-primary/10 px-1 text-[10px] text-primary">
                                  [{c.index}]
                                </span>
                                {c.title}
                                <span className="ml-auto font-normal text-pl-sub">
                                  {SECTION_TEXT[c.section] ?? c.section} · {Math.round(c.score * 100)}%
                                </span>
                              </span>
                              <span className="pl-line-clamp-2 mt-0.5 block text-[11px] leading-relaxed text-pl-sub">
                                {c.snippet}
                              </span>
                            </button>
                          </li>
                        )
                      })}
                    </ul>
                  )}
                </div>
              ),
            )}

            {pending && (
              <p className="flex items-center gap-1.5 text-[12px] text-pl-sub">
                <Loader2 className="h-3.5 w-3.5 animate-spin" /> 正在阅读简历…
              </p>
            )}

            {error && (
              <p role="alert" className="rounded-md bg-destructive/10 px-2.5 py-1.5 text-[12px] text-destructive">
                {error}
              </p>
            )}
          </div>
        </div>

        <form
          className="shrink-0 border-t border-border px-3 py-2.5 pl-safe-bottom"
          onSubmit={e => {
            e.preventDefault()
            void ask(input)
          }}
        >
          <div className="flex items-end gap-2">
            <input
              aria-label="向 AI 提问"
              value={input}
              maxLength={500}
              onChange={e => setInput(e.target.value)}
              placeholder="例如：候选人的核心技术栈是什么？"
              className="h-9 flex-1 rounded-lg border border-border bg-background px-3 text-[13px] text-foreground placeholder:text-pl-sub focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
            />
            <button
              type="submit"
              disabled={pending || !input.trim()}
              className="flex h-9 items-center gap-1 rounded-lg bg-primary px-3 text-[13px] font-medium text-primary-foreground transition-opacity hover:opacity-90 disabled:opacity-50"
            >
              {pending ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <Send className="h-3.5 w-3.5" />}
              提问
            </button>
          </div>
          <p className="mt-1.5 text-[10px] text-pl-sub">AI 生成内容仅供参考，请以简历原文为准。</p>
        </form>
      </div>
    </BottomSheet>
  )
}
