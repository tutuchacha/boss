/**
 * 聘聊 · Mock 数据 - 人才筛选（HR：按技能需求匹配候选人）
 * =====================================================================
 * 对应契约：docs/api-endpoints.md §4.5 / §4.6
 *  - POST /api/v1/hr/candidates/match        候选人匹配
 *  - GET  /api/v1/hr/candidates/match/tags   技能标签词表
 *
 * 演示版匹配算法（无 AI、无外部依赖）：
 *   需求文本 + 标签 → 关键词提取（词表命中 / 英文技术词 / 中文去停用词）
 *   → 在投递候选人的在线简历（mock/resumes.ts）中检索
 *   → 输出命中关键词 + 关联证据片段（简历原文，供前端高亮）。
 *
 * 边界（诚实口径）：
 *   - 这是「关键词匹配」而非语义检索：同义词（如「React」与「前端框架」）
 *     不会被识别，后续接入 embedding 语义检索时本文件整体替换、接口不变。
 *   - 词典命中的匹配为包含式（大小写不敏感），对短词存在误报可能，
 *     演示数据已规避（如无 JavaScript 技能项，避免被「Java」误命中）。
 *
 * 依赖方向：只读取（applications 视图 + resumes 数据），不修改任何可变数据。
 * =====================================================================
 */

import type { CandidateMatchEvidence, CandidateMatchView, ResumeContent } from '@/types/api'
import { companyApplicationViews, type HrApplicationView } from './applications'
import { resumeContentOfUser } from './resumes'

/** 技能标签词表（筛选页 chips；同时作为关键词提取的主词典） */
export const TALENT_MATCH_TAGS: string[] = [
  '前端',
  'React',
  'TypeScript',
  'Vue',
  '后端',
  'Java',
  'Spring Boot',
  'MySQL',
  'Redis',
  '高并发',
  '测试',
  '自动化测试',
  '性能测试',
  '运维',
  'Kubernetes',
  'Docker',
  '产品',
  '数据分析',
  '设计',
  'Figma',
]

/**
 * 停用词：从中文片段中剔除，避免把「熟悉 / 以上 / 工程师」等
 * 无检索意义的词当成关键词。按长度倒序使用（先移除长词）。
 */
const STOPWORDS: string[] = [
  '工作经验',
  '相关经验',
  '年以上',
  '年以下',
  '经验',
  '熟悉',
  '掌握',
  '了解',
  '精通',
  '优先',
  '要求',
  '岗位',
  '负责',
  '具备',
  '能力',
  '不限',
  '相关',
  '开发',
  '工程师',
  '专员',
  '经理',
  '主管',
  '助理',
  '以及',
  '并且',
  '或者',
  '年',
  '的',
  '和',
  '与',
  '或',
  '及',
  '有',
]

/** 词典：标签词表，长词优先（保证「自动化测试」先于「测试」命中） */
const DICT: string[] = [...TALENT_MATCH_TAGS].sort((a, b) => b.length - a.length)
const STOPWORDS_SORTED: string[] = [...STOPWORDS].sort((a, b) => b.length - a.length)

/** 分值：技能精确命中 > 文本命中（简介 / 经历 / 项目 / 意向） */
const WEIGHT_SKILL = 3
const WEIGHT_TEXT = 2
/** 单个关键词最多展示几条证据；单候选人证据总数上限（避免卡片过长） */
const MAX_EVIDENCES_PER_TOKEN = 3
const MAX_EVIDENCES_PER_CANDIDATE = 6

/**
 * 从「需求文本 + 标签」提取关键词（演示版，非语义分词）：
 *  1) 词典命中：长词优先、大小写不敏感；命中后用空格占位避免子词重复计数；
 *  2) 英文 / 数字技术词：词表未覆盖的（如 Node.js、Go、C++）；
 *  3) 中文：去除停用词后剩余的连续中文片段（长度 ≥ 2）。
 */
export function extractRequirementTokens(query: string, tags: string[]): string[] {
  const raw = `${query ?? ''} ${tags.join(' ')}`.trim()
  if (!raw) return []

  const seen = new Set<string>()
  const tokens: string[] = []
  const push = (t: string): void => {
    const normalized = t.trim()
    if (!normalized) return
    const key = normalized.toLowerCase()
    if (seen.has(key)) return
    seen.add(key)
    tokens.push(normalized)
  }

  let working = raw

  // 1) 词典命中
  for (const term of DICT) {
    const idx = working.toLowerCase().indexOf(term.toLowerCase())
    if (idx < 0) continue
    push(term)
    working = working.slice(0, idx) + ' '.repeat(term.length) + working.slice(idx + term.length)
  }

  // 2) 英文 / 数字技术词（去掉结尾的 . 与 -，保留 C++ 之类的中间符号）
  for (const m of working.match(/[A-Za-z][A-Za-z0-9+#.-]*/g) ?? []) {
    push(m.replace(/[.-]+$/, ''))
  }

  // 3) 中文片段：去停用词后剩余连续中文（≥2 字）
  let zh = working
  for (const w of STOPWORDS_SORTED) zh = zh.split(w).join(' ')
  for (const chunk of zh.match(/[\u4e00-\u9fa5]{2,}/g) ?? []) {
    push(chunk)
  }

  return tokens
}

/** 取 token 在文本中首次出现处的上下文片段（供前端高亮） */
function snippetAround(text: string, token: string): string {
  const idx = text.toLowerCase().indexOf(token.toLowerCase())
  if (idx < 0) return text
  const start = Math.max(0, idx - 10)
  const end = Math.min(text.length, idx + token.length + 46)
  return `${start > 0 ? '…' : ''}${text.slice(start, end)}${end < text.length ? '…' : ''}`
}

interface TokenHit {
  token: string
  weight: number
  evidences: CandidateMatchEvidence[]
}

/** 单个关键词在某位候选人简历中的命中（含证据片段）；未命中返回 null */
function hitsOfToken(token: string, expect: string, content: ResumeContent): TokenHit | null {
  const tk = token.toLowerCase()
  const evidences: CandidateMatchEvidence[] = []
  let weight = 0

  const add = (w: number, field: string, snippet: string): void => {
    weight = Math.max(weight, w)
    if (evidences.length < MAX_EVIDENCES_PER_TOKEN) evidences.push({ token, field, snippet })
  }

  // 技能（精确命中权重最高）
  const skill = content.skills.find(s => s.toLowerCase().includes(tk))
  if (skill) add(WEIGHT_SKILL, '技能', skill)

  // 求职意向
  if (expect.toLowerCase().includes(tk)) add(WEIGHT_TEXT, '求职意向', expect)

  // 个人简介
  const summary = content.basics.summary
  if (summary && summary.toLowerCase().includes(tk)) {
    add(WEIGHT_TEXT, '个人简介', snippetAround(summary, token))
  }

  // 工作经历（合并为一行检索；命中即取首个匹配项）
  for (const exp of content.experiences) {
    const hay = [exp.company, exp.title, exp.description].filter(Boolean).join(' · ')
    if (hay.toLowerCase().includes(tk)) {
      add(WEIGHT_TEXT, `工作经历 · ${exp.company}`, snippetAround(hay, token))
      break
    }
  }

  // 项目经历
  for (const pr of content.projects) {
    const hay = [pr.name, pr.role, pr.description].filter(Boolean).join(' · ')
    if (hay.toLowerCase().includes(tk)) {
      add(WEIGHT_TEXT, `项目经历 · ${pr.name}`, snippetAround(hay, token))
      break
    }
  }

  return weight > 0 ? { token, weight, evidences } : null
}

/**
 * 匹配公司收到的投递候选人（按匹配度倒序；仅返回有命中的）。
 * 同一候选人多条投递时合并：取最近一条（views 已按更新时间倒序）。
 */
export function matchCandidatesForCompany(
  companyId: string,
  query: string,
  tags: string[],
): CandidateMatchView[] {
  const tokens = extractRequirementTokens(query, tags)
  if (tokens.length === 0) return []

  const byUser = new Map<string, HrApplicationView>()
  for (const v of companyApplicationViews(companyId)) {
    if (!byUser.has(v.candidate.userId)) byUser.set(v.candidate.userId, v)
  }

  const results: CandidateMatchView[] = []
  for (const v of byUser.values()) {
    const content = resumeContentOfUser(v.candidate.userId)
    if (!content) continue

    const tokenHits: TokenHit[] = []
    for (const token of tokens) {
      const hit = hitsOfToken(token, v.candidate.expect, content)
      if (hit) tokenHits.push(hit)
    }
    if (tokenHits.length === 0) continue

    tokenHits.sort((a, b) => b.weight - a.weight)
    results.push({
      applicationId: v.application.id,
      candidate: { ...v.candidate },
      jobTitle: v.job.title,
      score: tokenHits.reduce((sum, h) => sum + h.weight, 0),
      hitTokens: tokenHits.map(h => h.token),
      evidences: tokenHits.flatMap(h => h.evidences).slice(0, MAX_EVIDENCES_PER_CANDIDATE),
    })
  }

  return results.sort(
    (a, b) => b.score - a.score || b.candidate.resumeCompleteness - a.candidate.resumeCompleteness,
  )
}
