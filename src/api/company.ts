/**
 * 聘聊 · 公司资料 / 企业认证 service
 * =====================================================================
 * 与 docs/api-endpoints.md 对应：
 *  - GET   /api/v1/company/me         §5.3 我所在公司（招聘者视角）
 *  - PATCH /api/v1/company/me         §5.4 更新公司信息
 *  - POST  /api/v1/company/me/verify  §5.6 企业认证提交（本次新增）
 *
 * Mock 阶段直接操作 src/api/mock/companyOps.ts 的内存数据；
 * 认证提交后约 1.2s 自动「审核通过」，页面轮询即可看到状态变化。
 * =====================================================================
 */

import type { Company, CompanyVerifyInput } from '@/types/api'
import { USE_MOCK } from '@/config/env'
import { ApiError, request } from './client'
import { companyById } from './mock/companies'
import type { CompanyPatchInput, CompanyVerifyRecord } from './mock/companyOps'
import {
  applyCompanyPatch,
  approveCompanyVerification,
  submitCompanyVerificationRecord,
} from './mock/companyOps'

export type { CompanyPatchInput, CompanyVerifyRecord }

/** Mock 审核通过延迟（ms）：提交认证后约 1.2s 自动通过 */
export const MOCK_VERIFY_REVIEW_DELAY_MS = 1200

const sleep = (ms: number) => new Promise(r => setTimeout(r, ms))

/** §5.3 我所在公司 */
export async function getMyCompany(companyId: string): Promise<Company> {
  if (USE_MOCK) {
    await sleep(160)
    const co = companyById(companyId)
    if (!co) {
      throw new ApiError({ code: 40410, message: '公司不存在', status: 404 })
    }
    return { ...co }
  }
  return request<Company>('/company/me')
}

/** §5.4 更新公司信息（仅本公司管理员） */
export async function updateMyCompany(
  companyId: string,
  patch: CompanyPatchInput,
): Promise<Company> {
  if (USE_MOCK) {
    await sleep(280)
    const next: CompanyPatchInput = { ...patch }
    if (patch.name !== undefined) {
      const name = patch.name.trim()
      if (name.length < 2) {
        throw new ApiError({ code: 42210, message: '公司名称至少 2 个字', status: 422 })
      }
      if (name.length > 30) {
        throw new ApiError({ code: 42210, message: '公司名称不超过 30 个字', status: 422 })
      }
      next.name = name
    }
    if (patch.intro !== undefined && patch.intro.length > 500) {
      throw new ApiError({ code: 42210, message: '公司简介不超过 500 字', status: 422 })
    }
    const updated = applyCompanyPatch(companyId, next)
    if (!updated) {
      throw new ApiError({ code: 40410, message: '公司不存在', status: 404 })
    }
    return { ...updated }
  }
  return request<Company>('/company/me', { method: 'PATCH', body: patch })
}

/**
 * §5.6 提交企业认证。
 * 校验后置为「审核中」（status=PENDING），Mock 约 1.2s 后自动通过。
 * 已通过认证（verified=true）时拒绝重复提交（40916）。
 */
export async function submitCompanyVerification(
  companyId: string,
  input: CompanyVerifyInput,
): Promise<Company> {
  if (USE_MOCK) {
    await sleep(300)
    const co = companyById(companyId)
    if (!co) {
      throw new ApiError({ code: 40410, message: '公司不存在', status: 404 })
    }
    if (co.verified) {
      throw new ApiError({ code: 40916, message: '该公司已完成企业认证，无需重复提交', status: 409 })
    }
    assertVerifyInput(input)
    const updated = submitCompanyVerificationRecord(companyId, input)
    if (!updated) {
      throw new ApiError({ code: 40410, message: '公司不存在', status: 404 })
    }
    // 模拟人工审核：延迟自动通过（页面在 PENDING 期间轮询会看到结果）
    setTimeout(() => approveCompanyVerification(companyId), MOCK_VERIFY_REVIEW_DELAY_MS)
    return { ...updated }
  }
  return request<Company>('/company/me/verify', { method: 'POST', body: input })
}

/** 认证表单校验（与契约字段约束对齐） */
function assertVerifyInput(input: CompanyVerifyInput): void {
  if (input.companyName.trim().length < 2) {
    throw new ApiError({ code: 42210, message: '请填写完整的企业名称', status: 422 })
  }
  const license = input.licenseNo.trim().toUpperCase()
  if (!/^[0-9A-Z]{18}$/.test(license)) {
    throw new ApiError({ code: 42210, message: '统一社会信用代码需为 18 位大写字母或数字', status: 422 })
  }
  if (!input.contactName.trim()) {
    throw new ApiError({ code: 42210, message: '请填写联系人姓名', status: 422 })
  }
  if (!/^1\d{10}$/.test(input.contactPhone.trim())) {
    throw new ApiError({ code: 42210, message: '请填写正确的联系电话', status: 422 })
  }
}
