/**
 * 聘聊 · Mock 数据 - 公司资料与企业认证（可变，内存态）
 * =====================================================================
 * 对应契约：docs/api-endpoints.md §5.3 / §5.4 / §5.6
 *  - PATCH /api/v1/company/me        更新公司信息
 *  - POST  /api/v1/company/me/verify 企业认证提交（Mock 模拟审核）
 *
 * 设计说明：
 *  - 公司对象在 companies.ts 中原地共享；jobs / hrJobRecords 里保存的是
 *    Pick<Company>（name/stage/size）快照，改名后必须同步刷新这些快照，
 *    否则求职者端仍显示旧公司名。
 *  - 认证提交记入 verifyRecords，仅 Mock 展示用；审核通过由 service 层
 *    定时调用（约 1.2s），模拟「人工审核」。
 * =====================================================================
 */

import type { Company, CompanyVerifyInput } from '@/types/api'
import { companyById } from './companies'
import { jobs } from './jobs'
import { hrJobRecords } from './hrJobs'

/** 可编辑的公司字段（对应契约 PATCH /company/me） */
export interface CompanyPatchInput {
  name?: string
  intro?: string
  industry?: string
  stage?: string
  size?: string
  website?: string
}

/** 更新公司资料（原地修改，并把名称/阶段/规模同步到职位快照） */
export function applyCompanyPatch(
  companyId: string,
  patch: CompanyPatchInput,
): Company | undefined {
  const co = companyById(companyId)
  if (!co) return undefined
  if (patch.name !== undefined) co.name = patch.name
  if (patch.intro !== undefined) co.intro = patch.intro
  if (patch.industry !== undefined) co.industry = patch.industry
  if (patch.stage !== undefined) co.stage = patch.stage
  if (patch.size !== undefined) co.size = patch.size
  if (patch.website !== undefined) co.website = patch.website
  syncCompanySnapshots(co)
  return co
}

/** 把公司最新名称/阶段/规模写回所有职位（基础职位 + HR 可变记录） */
function syncCompanySnapshots(co: Company): void {
  for (const j of jobs) {
    if (j.company.id !== co.id) continue
    j.company.name = co.name
    j.company.stage = co.stage ?? ''
    j.company.size = co.size ?? ''
  }
  for (const r of hrJobRecords) {
    if (r.company.id !== co.id) continue
    r.company.name = co.name
    r.company.stage = co.stage ?? ''
    r.company.size = co.size ?? ''
  }
}

/** 企业认证提交记录（Mock 展示用） */
export interface CompanyVerifyRecord extends CompanyVerifyInput {
  submittedAt: string
  approvedAt?: string
}

const verifyRecords = new Map<string, CompanyVerifyRecord>()

/** 提交企业认证：置为「审核中」（PENDING、verified=false） */
export function submitCompanyVerificationRecord(
  companyId: string,
  input: CompanyVerifyInput,
): Company | undefined {
  const co = companyById(companyId)
  if (!co) return undefined
  co.verified = false
  co.status = 'PENDING'
  verifyRecords.set(companyId, { ...input, submittedAt: new Date().toISOString() })
  return co
}

/** 审核通过（Mock：由 service 定时调用）：verified=true、status=ACTIVE */
export function approveCompanyVerification(companyId: string): Company | undefined {
  const co = companyById(companyId)
  if (!co) return undefined
  co.verified = true
  co.status = 'ACTIVE'
  const rec = verifyRecords.get(companyId)
  if (rec && !rec.approvedAt) rec.approvedAt = new Date().toISOString()
  return co
}

/** 最近一次认证提交记录（未提交过则 undefined） */
export const latestVerifyRecord = (companyId: string): CompanyVerifyRecord | undefined =>
  verifyRecords.get(companyId)
