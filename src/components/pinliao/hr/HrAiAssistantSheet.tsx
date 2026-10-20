/**
 * 聘聊 · HR 候选人详情 - AI 简历助手抽屉（Step 4 T-AI2）
 * =====================================================================
 * 入口：候选人详情「AI 简历助手」卡片（仅 /ai/status.enabled=true 时展示）。
 * 能力：就该候选人的简历提问，回答带 [n] 引用；无依据时后端返回
 *      「简历中没有相关信息。」且 citations 为空（前端不展示来源列表）。
 *
 * 说明：
 *  - 取数走 service 层 askResumeQuestion（USE_MOCK 双分支），组件不直接 fetch；
 *  - 会话状态是本地临时态（刷新即清空），不落库、不影响业务数据。
 * =====================================================================
 */

'use client'

import { useEffect, useRef, useState } from 'react'
import { Loader2, Send, Sparkles } from 'lucide-react'
import type { AiCitation } from '@/types/api'
import { askResumeQuestion } from '@/api/ai'
import { ApiError } from '@/api/client'
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

export function HrAiAssistantSheet({ applicationId, onClose }: Props) {
  const [input, setInput] = useState('')
  const [turns, setTurns] = useState<ChatTurn[]>([])
  const [pending, setPending] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const scrollRef = useRef<HTMLDivElement>(null)
  const nextId = useRef(1)

  useEffect(() => {
    const el = scrollRef.current
    if (el) el.scrollTop = el.scrollHeight
  }, [turns, pending])

  const ask = async (raw: string) => {
    const question = raw.trim()
    if (!question || pending) return
    setError(null)
    setTurns(prev => [...prev, { id: nextId.current++, role: 'user', text: question, citations: [] }])
    setInput('')
    setPending(true)
    try {
      const result = await askResumeQuestion(applicationId, question)
      setTurns(prev => [
        ...prev,
        {
          id: nextId.current++,
          role: 'assistant',
          text: result.answer,
          citations: result.citations,
          grounded: result.grounded,
        },
      ])
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'AI 服务暂时不可用，请稍后重试')
    } finally {
      setPending(false)
    }
  }

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
                  <p className="max-w-[92%] whitespace-pre-wrap rounded-lg rounded-bl-sm bg-muted px-3 py-2 text-[13px] leading-relaxed text-foreground">
                    {turn.text}
                  </p>
                  {turn.citations.length > 0 && (
                    <ul className="w-full space-y-1.5">
                      {turn.citations.map(c => (
                        <li
                          key={`${turn.id}-${c.chunkId}`}
                          className="rounded-md border border-border bg-card px-2.5 py-1.5"
                        >
                          <p className="flex items-center gap-1.5 text-[11px] font-medium text-foreground">
                            <span className="rounded bg-primary/10 px-1 text-[10px] text-primary">
                              [{c.index}]
                            </span>
                            {c.title}
                            <span className="ml-auto font-normal text-pl-sub">
                              {SECTION_TEXT[c.section] ?? c.section} · {Math.round(c.score * 100)}%
                            </span>
                          </p>
                          <p className="pl-line-clamp-2 mt-0.5 text-[11px] leading-relaxed text-pl-sub">
                            {c.snippet}
                          </p>
                        </li>
                      ))}
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
