/**
 * 聘聊 · Mock 数据 - AI 简历问答（Step 4 T-AI2）
 * =====================================================================
 * 对应接口：
 *  - GET  /api/v1/ai/status
 *  - POST /api/v1/hr/applications/{id}/ai/qa
 *
 * 演示口径（与后端 provider=fake 对齐）：
 *   - 不做语义理解，用「段落意图关键词 + 简历原文命中」定位分块；
 *   - 命中即返回带 [n] 编号 + citations 的回答；
 *   - 未命中时如实回答「简历中没有相关信息。」，不编造引用。
 *
 * 依赖方向：只读 applications / resumes 两个 mock 模块，不修改业务数据。
 * =====================================================================
 */

import type { AiCitation, AiQaAnswer, AiStatus, ResumeContent } from '@/types/api'
import { ApiError } from '../client'
import { applicationById } from './applications'
import { resumeContentOfUser } from './resumes'
import { extractRequirementTokens } from './talentMatch'

export const MOCK_AI_STATUS: AiStatus = {
  enabled: true,
  provider: 'mock',
  chatModel: 'mock-rule-v1',
  embeddingModel: 'mock-rule-v1',
}

/** 无依据时的固定回答（与后端 PromptBuilder.NO_EVIDENCE_ANSWER 保持一致） */
export const MOCK_NO_EVIDENCE_ANSWER = '简历中没有相关信息。'

interface MockChunk {
  chunkId: string
  section: string
  title: string
  text: string
}

/** 段落意图关键词：问题命中即给对应段落加权，保证「问技能答技能」 */
const SECTION_KEYWORDS: Record<string, string[]> = {
  basics: ['介绍', '简介', '概况', '个人', '城市', '几年', '背景'],
  educations: ['学历', '教育', '学校', '大学', '专业', '毕业', '硕士', '本科', '博士'],
  experiences: ['工作经历', '经历', '公司', '任职', '职位', '负责', '在职'],
  projects: ['项目', '作品', '成果', '重构', '搭建'],
  skills: ['技能', '技术栈', '技术', '擅长', '掌握', '会什么', '能力'],
}

/** 把简历正文拆成与后端 ResumeChunker 同构的 5 类分块 */
function buildChunks(resumeId: string, content: ResumeContent): MockChunk[] {
  const chunks: MockChunk[] = []
  const push = (section: string, ordinal: number, title: string, text: string): void => {
    chunks.push({ chunkId: `mock-${resumeId}-${section}-${ordinal}`, section, title, text })
  }

  const basics = content.basics
  push(
    'basics',
    0,
    '基本信息',
    [basics.name, basics.city, basics.summary].filter(Boolean).join('；'),
  )

  content.educations.forEach((edu, i) => {
    push(
      'educations',
      i,
      `教育经历 · ${edu.school}`,
      `${edu.school}；${edu.major}；${edu.degree}；${edu.startDate}～${edu.endDate}`,
    )
  })

  content.experiences.forEach((exp, i) => {
    push(
      'experiences',
      i,
      `工作经历 · ${exp.company}`,
      [exp.company, exp.title, exp.description, `${exp.startDate}～${exp.endDate}`]
        .filter(Boolean)
        .join('；'),
    )
  })

  content.projects.forEach((pr, i) => {
    push(
      'projects',
      i,
      `项目经历 · ${pr.name}`,
      [pr.name, pr.role, pr.description, `${pr.startDate}～${pr.endDate}`]
        .filter(Boolean)
        .join('；'),
    )
  })

  push('skills', 0, '技能', `技能：${content.skills.join('、')}`)
  return chunks
}

/** 问题 → 关键词（英文技术词走 talentMatch 的同一套提取，中文走段落意图） */
function questionTokens(question: string): string[] {
  const tokens = extractRequirementTokens(question, [])
  const lower = question.toLowerCase()
  for (const words of Object.values(SECTION_KEYWORDS)) {
    for (const word of words) {
      if (lower.includes(word.toLowerCase()) && !tokens.includes(word)) tokens.push(word)
    }
  }
  return tokens
}

/** 单段落打分：意图命中 > 技能/技术词命中 > 普通关键词包含 */
function scoreOf(chunk: MockChunk, question: string, tokens: string[]): number {
  const lowerQuestion = question.toLowerCase()
  const hay = chunk.text.toLowerCase()
  let score = 0

  for (const word of SECTION_KEYWORDS[chunk.section] ?? []) {
    if (lowerQuestion.includes(word.toLowerCase())) score += 3
  }
  for (const token of tokens) {
    const tk = token.toLowerCase()
    if (tk.length < 2 || !hay.includes(tk)) continue
    score += /[a-z]/i.test(tk) ? 2 : 1
  }
  return score
}

/** 按段落生成一句可读回答（句尾带引用编号） */
function sentenceOf(chunk: MockChunk, index: number, content: ResumeContent): string {
  const mark = `[${index}]`
  if (chunk.section === 'basics') {
    const { name, city, summary } = content.basics
    return `候选人 ${name}，现居${city}。${summary ?? ''}${mark}`
  }
  if (chunk.section === 'educations') {
    const edu = content.educations[0]
    return `教育背景：${edu.school} · ${edu.major} · ${edu.degree}（${edu.startDate}～${edu.endDate}）。${mark}`
  }
  if (chunk.section === 'experiences') {
    const exp = content.experiences[0]
    return `工作经历：${exp.company} · ${exp.title}（${exp.startDate}～${exp.endDate}），${exp.description ?? ''}${mark}`
  }
  if (chunk.section === 'projects') {
    const pr = content.projects[0]
    return `项目经历：${pr.name}（${pr.role}），${pr.description ?? ''}${mark}`
  }
  return `技能：${content.skills.join('、')}。${mark}`
}

/** 片段截断（与后端 CitationAssembler 的可读长度接近） */
function snippetOf(text: string, max = 80): string {
  return text.length <= max ? text : `${text.slice(0, max)}…`
}

/**
 * Mock 版单简历问答。
 * 越权 / 不存在统一抛 40401（不泄露存在性，与后端口径一致）。
 */
export function mockAskResumeQuestion(applicationId: string, question: string): AiQaAnswer {
  const trimmed = question.trim()
  if (!trimmed) {
    throw new ApiError({ code: 42201, message: '问题不能为空', status: 422 })
  }
  if (trimmed.length > 500) {
    throw new ApiError({ code: 42201, message: '问题过长（最多 500 字）', status: 422 })
  }

  const application = applicationById(applicationId)
  if (!application) {
    throw new ApiError({ code: 40401, message: '投递不存在', status: 404 })
  }
  const content = resumeContentOfUser(application.userId)
  if (!content) {
    throw new ApiError({ code: 42221, message: '简历索引未就绪，请稍后重试', status: 422 })
  }

  const chunks = buildChunks(application.resumeId, content)
  const tokens = questionTokens(trimmed)
  const ranked = chunks
    .map(chunk => ({ chunk, score: scoreOf(chunk, trimmed, tokens) }))
    .filter(item => item.score > 0)
    .sort((a, b) => b.score - a.score)

  if (ranked.length === 0) {
    return {
      answer: MOCK_NO_EVIDENCE_ANSWER,
      grounded: false,
      citations: [],
      chatModel: MOCK_AI_STATUS.chatModel,
      elapsedMs: 36,
      totalTokens: null,
      retrieved: 0,
    }
  }

  const hits = ranked.slice(0, 2)
  const citations: AiCitation[] = hits.map((item, i) => ({
    index: i + 1,
    chunkId: item.chunk.chunkId,
    resumeId: application.resumeId,
    section: item.chunk.section,
    title: item.chunk.title,
    snippet: snippetOf(item.chunk.text),
    score: Number(Math.min(0.95, 0.5 + item.score * 0.05).toFixed(4)),
  }))

  return {
    answer: hits.map((item, i) => sentenceOf(item.chunk, i + 1, content)).join('\n'),
    grounded: true,
    citations,
    chatModel: MOCK_AI_STATUS.chatModel,
    elapsedMs: 36,
    totalTokens: null,
    retrieved: ranked.length,
  }
}

// ============================================================
// 流式（T-AI3）
// ============================================================

/** 假流式：每段字数与段间停顿（总时长 < 1s，e2e 的 1.5s 等待窗口内可完成） */
const STREAM_DELTA_CHARS = 4
const STREAM_DELAY_MS = 18

/**
 * Mock 版流式问答：先复用规则回答（校验与引用口径完全一致），再把正文按段回放。
 *
 * <p>与后端 provider=fake 的行为对齐：若干 delta → 若干 citation → 返回完整回答。
 * 引用在文本结束后才回调，避免前端渲染到一半就去匹配还没出现的 `[n]`。</p>
 */
export async function mockAskResumeQuestionStream(
  applicationId: string,
  question: string,
  handlers: { onDelta?: (text: string) => void; onCitation?: (citation: AiCitation) => void } = {},
  signal?: AbortSignal,
): Promise<AiQaAnswer> {
  const result = mockAskResumeQuestion(applicationId, question)
  for (let i = 0; i < result.answer.length; i += STREAM_DELTA_CHARS) {
    if (signal?.aborted) break
    await new Promise(resolve => setTimeout(resolve, STREAM_DELAY_MS))
    handlers.onDelta?.(result.answer.slice(i, i + STREAM_DELTA_CHARS))
  }
  result.citations.forEach(citation => handlers.onCitation?.(citation))
  return result
}
