/**
 * 聘聊 · 人才筛选 service（HR 按技能需求匹配候选人）
 * =====================================================================
 * 与 docs/api-endpoints.md 对应：
 *  - POST /api/v1/hr/candidates/match        §4.5 候选人匹配
 *  - GET  /api/v1/hr/candidates/match/tags   §4.6 技能标签词表
 *
 * 演示版为关键词匹配（mock/talentMatch.ts）；联调阶段切到 request()，
 * 后续服务端可升级为语义检索（embedding），接口与出参结构不变。
 * =====================================================================
 */

import type { CandidateMatchInput, CandidateMatchView } from '@/types/api'
import { USE_MOCK } from '@/config/env'
import { ApiError, request } from './client'
import { matchCandidatesForCompany, TALENT_MATCH_TAGS } from './mock/talentMatch'

/** 匹配结果查询键前缀（供 TanStack Query 使用） */
export const HR_TALENT_MATCH_KEY = ['hr', 'talent-match'] as const
/** 标签词表查询键 */
export const HR_TALENT_MATCH_TAGS_KEY = ['hr', 'talent-match-tags'] as const

/**
 * §4.5 按技能需求筛选候选人。
 * 入参：自然语言需求（query）与 / 或技能标签（tags）——至少一个非空（42210）。
 * 出参：按匹配度倒序的候选人列表（仅含命中的候选人，附证据片段）。
 */
export async function matchTalentCandidates(
  companyId: string,
  input: CandidateMatchInput,
): Promise<CandidateMatchView[]> {
  const query = (input.query ?? '').trim()
  const tags = input.tags ?? []

  if (USE_MOCK) {
    if (!query && tags.length === 0) {
      throw new ApiError({ code: 42210, message: '请先输入技能需求或勾选技能标签', status: 422 })
    }
    return matchCandidatesForCompany(companyId, query, tags)
  }

  return request<CandidateMatchView[]>('/hr/candidates/match', {
    method: 'POST',
    body: { query, tags },
  })
}

/** §4.6 技能标签词表（筛选页 chips） */
export async function listTalentMatchTags(): Promise<string[]> {
  if (USE_MOCK) return [...TALENT_MATCH_TAGS]
  return request<string[]>('/hr/candidates/match/tags')
}
