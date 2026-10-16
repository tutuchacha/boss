/**
 * 聘聊 · HR 工作台 - 人才筛选（按技能需求匹配候选人）
 * =====================================================================
 * 对应接口：
 *  - GET  /api/v1/hr/candidates/match/tags   技能标签词表（§4.6）
 *  - POST /api/v1/hr/candidates/match        候选人匹配（§4.5）
 *
 * 演示版（无 AI）：需求文本 + 标签 → 关键词匹配投递候选人的在线简历，
 * 展示命中关键词与关联证据片段（简历原文高亮）。
 * 后续升级为语义检索（embedding）时仅替换服务端实现，页面与接口不变。
 *
 * 取数：TanStack Query——标签词表一次拉取；结果按「已提交的筛选条件」
 * 作为 queryKey（输入过程中不触发请求，点「开始筛选」才提交）。
 * =====================================================================
 */

'use client'

import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { ArrowLeft, ChevronRight, SearchCheck, Target } from 'lucide-react'
import type { CandidateMatchView } from '@/types/api'
import {
  HR_TALENT_MATCH_KEY,
  HR_TALENT_MATCH_TAGS_KEY,
  listTalentMatchTags,
  matchTalentCandidates,
} from '@/api/hrTalentMatch'
import { useAuthStore } from '@/store/auth'
import { useHrStore } from '@/store/hr'
import { cn } from '@/lib/utils'
import { AvatarBadge } from '../AvatarBadge'

/** 已提交的筛选条件快照（输入过程不触发匹配） */
interface SubmittedCriteria {
  q: string
  tags: string[]
}

export function HrTalentMatchPage() {
  const company = useAuthStore(s => s.company)
  const popPage = useHrStore(s => s.popPage)
  const openCandidate = useHrStore(s => s.openCandidate)

  const [q, setQ] = useState('')
  const [tags, setTags] = useState<string[]>([])
  const [submitted, setSubmitted] = useState<SubmittedCriteria | null>(null)

  const companyId = company?.id ?? ''

  const { data: tagOptions = [] } = useQuery({
    queryKey: HR_TALENT_MATCH_TAGS_KEY,
    queryFn: listTalentMatchTags,
  })

  const submittedQ = submitted?.q.trim() ?? ''
  const submittedTags = submitted?.tags ?? []
  const hasCriteria = submittedQ !== '' || submittedTags.length > 0

  const { data: results = [], isFetching } = useQuery({
    queryKey: [...HR_TALENT_MATCH_KEY, companyId, submittedQ, submittedTags.join('|')],
    queryFn: () => matchTalentCandidates(companyId, { query: submittedQ, tags: submittedTags }),
    enabled: Boolean(companyId) && hasCriteria,
  })

  const canRun = q.trim() !== '' || tags.length > 0

  const run = (): void => {
    if (!canRun) return
    setSubmitted({ q, tags })
  }

  const toggleTag = (t: string): void => {
    setTags(prev => (prev.includes(t) ? prev.filter(x => x !== t) : [...prev, t]))
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
        <span className="text-[14px] font-medium text-foreground">人才筛选</span>
        <span className="ml-auto text-[11px] text-pl-sub">按技能需求匹配收到的简历</span>
      </div>

      <div className="pl-scroll flex-1 overflow-y-auto">
        {/* 筛选条件 */}
        <section className="border-b border-border bg-card px-4 py-3">
          <label htmlFor="talent-query" className="text-[13px] font-medium text-foreground">
            技能需求
          </label>
          <p className="mt-0.5 text-[11px] text-pl-sub">
            支持自然语言描述 + 技能标签组合（演示版为关键词匹配）
          </p>
          <input
            id="talent-query"
            type="text"
            value={q}
            onChange={e => setQ(e.target.value)}
            onKeyDown={e => {
              if (e.key === 'Enter') run()
            }}
            aria-label="技能需求"
            placeholder="如：3 年以上 React 前端 / 高并发后端"
            className="mt-2 h-9 w-full rounded-lg border border-border bg-background px-3 text-[13px] text-foreground placeholder:text-muted-foreground/70 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
          />

          <div className="mt-2 flex flex-wrap gap-1.5">
            {tagOptions.map(t => {
              const active = tags.includes(t)
              return (
                <button
                  key={t}
                  type="button"
                  onClick={() => toggleTag(t)}
                  aria-pressed={active}
                  className={cn(
                    'rounded-full px-2.5 py-1 text-[12px] transition-colors',
                    active
                      ? 'bg-primary text-primary-foreground'
                      : 'bg-secondary text-secondary-foreground hover:bg-accent',
                  )}
                >
                  {t}
                </button>
              )
            })}
          </div>

          <button
            type="button"
            onClick={run}
            disabled={!canRun}
            className={cn(
              'mt-2.5 flex h-9 w-full items-center justify-center gap-1.5 rounded-lg bg-primary text-[13px] font-medium text-primary-foreground transition-opacity hover:opacity-90 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring',
              !canRun && 'cursor-not-allowed opacity-50',
            )}
          >
            <SearchCheck className="h-4 w-4" />
            开始筛选
          </button>
        </section>

        {/* 结果区 */}
        {submitted === null && (
          <div className="flex flex-col items-center px-6 py-14 text-center">
            <span className="flex h-12 w-12 items-center justify-center rounded-full bg-secondary">
              <Target className="h-5 w-5 text-muted-foreground" />
            </span>
            <p className="mt-3 text-[14px] font-medium text-foreground">
              输入技能需求，找出简历相关的候选人
            </p>
            <p className="mt-1 text-[12px] text-pl-sub">
              可以打字描述（如「React 前端」），也可以直接勾选上方标签
            </p>
          </div>
        )}

        {submitted !== null && isFetching && (
          <p className="px-4 py-8 text-center text-[13px] text-pl-sub">匹配中…</p>
        )}

        {submitted !== null && !isFetching && hasCriteria && results.length === 0 && (
          <div className="flex flex-col items-center px-6 py-14 text-center">
            <span className="flex h-12 w-12 items-center justify-center rounded-full bg-secondary">
              <Target className="h-5 w-5 text-muted-foreground" />
            </span>
            <p className="mt-3 text-[14px] font-medium text-foreground">没有匹配的候选人</p>
            <p className="mt-1 text-[12px] text-pl-sub">换个关键词或标签再试试</p>
          </div>
        )}

        {submitted !== null && !isFetching && results.length > 0 && (
          <div>
            <p className="px-4 py-2.5 text-[11px] text-pl-sub">
              找到 {results.length} 位相关候选人（按匹配度排序）
            </p>
            {results.map(r => (
              <CandidateMatchCard
                key={r.applicationId}
                view={r}
                onOpen={() => openCandidate(r.applicationId)}
              />
            ))}
          </div>
        )}

        <div className="h-4" />
      </div>
    </div>
  )
}

/** 单个匹配结果卡片（点击进入候选人详情） */
function CandidateMatchCard({ view, onOpen }: { view: CandidateMatchView; onOpen: () => void }) {
  const { candidate, jobTitle, hitTokens, evidences } = view

  return (
    <button
      type="button"
      onClick={onOpen}
      className="block w-full border-b border-border bg-card px-4 py-3 text-left transition-colors hover:bg-accent/40 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
    >
      <span className="flex items-center gap-2.5">
        <AvatarBadge name={candidate.name} size={40} />
        <span className="min-w-0 flex-1">
          <span className="flex items-baseline gap-2">
            <span className="pl-line-clamp-1 text-[14px] font-medium text-foreground">
              {candidate.name}
            </span>
            <span className="pl-line-clamp-1 shrink-0 text-[11px] text-pl-sub">
              {candidate.expect} · {candidate.workYears}年 · {candidate.edu}
            </span>
          </span>
          <span className="mt-0.5 block pl-line-clamp-1 text-[11px] text-primary/90">
            应聘：{jobTitle}
          </span>
        </span>
        <ChevronRight className="h-4 w-4 shrink-0 text-muted-foreground" />
      </span>

      <span className="mt-2 flex flex-wrap items-center gap-1.5">
        <span className="text-[10px] text-pl-sub">命中</span>
        {hitTokens.map(t => (
          <span key={t} className="rounded bg-primary/10 px-1.5 py-0.5 text-[10px] text-primary">
            {t}
          </span>
        ))}
      </span>

      <span className="mt-2 block space-y-1.5">
        {evidences.map((e, i) => (
          <span
            key={`${e.token}-${e.field}-${i}`}
            className="block rounded-md bg-muted px-2.5 py-1.5"
          >
            <span className="block text-[10px] text-pl-sub">{e.field}</span>
            <span className="mt-0.5 block text-[12px] leading-relaxed text-foreground/90">
              <HighlightText text={e.snippet} tokens={[e.token]} />
            </span>
          </span>
        ))}
      </span>
    </button>
  )
}

/** 转义正则特殊字符（关键词可能含 C++ / . 等符号） */
function escapeRegExp(s: string): string {
  return s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

/** 命中词高亮：按关键词拆分文本，命中段用主题色 mark 样式 */
function HighlightText({ text, tokens }: { text: string; tokens: string[] }) {
  const valid = tokens.map(t => t.trim()).filter(Boolean)
  if (valid.length === 0) return <>{text}</>

  const pattern = valid.map(escapeRegExp).join('|')
  const parts = text.split(new RegExp(`(${pattern})`, 'gi'))
  const hitRe = new RegExp(`^(?:${pattern})$`, 'i')

  return (
    <>
      {parts.map((part, i) =>
        part && hitRe.test(part) ? (
          <mark key={i} className="rounded bg-primary/15 px-0.5 text-primary">
            {part}
          </mark>
        ) : (
          <span key={i}>{part}</span>
        ),
      )}
    </>
  )
}
