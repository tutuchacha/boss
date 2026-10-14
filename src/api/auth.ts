/**
 * 聘聊 · 认证 service
 * =====================================================================
 * 与 docs/api-endpoints.md §1 对应：
 *  - POST /api/v1/auth/login/password   密码登录
 *  - POST /api/v1/auth/logout           退出登录
 *  - GET  /api/v1/auth/me               当前登录用户
 *  - POST /api/v1/auth/refresh          刷新 token
 *
 * Mock 阶段：校验 src/api/mock/auth.ts 中的预置测试账号。
 * 联调阶段：USE_MOCK=false，走 client.ts（自动带 Bearer 头）。
 * =====================================================================
 */

import type {
  AuthResult,
  Company,
  CompanyMember,
  HrProfile,
  LoginPasswordInput,
  User,
  UserProfile,
} from '@/types/api'
import { RoleEnum } from '@/types/api'
import { USE_MOCK } from '@/config/env'
import { ApiError, request } from './client'
import {
  MOCK_COMPANY,
  MOCK_COMPANY_MEMBER,
  MOCK_CREDENTIALS,
  MOCK_HR_PROFILE,
  MOCK_HR_USER,
  MOCK_SEEKER_PROFILE,
  MOCK_SEEKER_USER,
  MOCK_USERS,
} from './mock'

/** token 在 localStorage 的键（与 client.ts 的 getToken 保持一致） */
export const ACCESS_TOKEN_KEY = 'pinliao.accessToken'
export const REFRESH_TOKEN_KEY = 'pinliao.refreshToken'

/** Mock token（无需真实签名，仅用于演示与 client 头拼接） */
const mockToken = (kind: string, userId: string): string =>
  `mock.${kind}.${userId}.${Date.now().toString(36)}`

/**
 * 登录结果（在契约 AuthResult 之上补充公司上下文）。
 * 契约的 AuthResult 只含 user + profile，但 HR 端渲染「所属公司 / 公司职位数」
 * 需要 company 与 member，故在此扩展；联调时由 /auth/me 或登录响应一并返回。
 */
export interface LoginResult extends AuthResult {
  company?: Company
  member?: CompanyMember
}

/** 账号或密码错误（对应契约错误码 40110） */
export class InvalidCredentialsError extends ApiError {
  constructor(message = '账号或密码错误') {
    super({ code: 40110, message, status: 401 })
    this.name = 'InvalidCredentialsError'
  }
}

/**
 * 账号真实身份与所选身份不一致。
 * 抛出此错误时**不会**写入登录态，登录页可据此提示用户切换身份。
 */
export class RoleMismatchError extends ApiError {
  /** 账号的真实角色 */
  actualRole: RoleEnum

  constructor(actualRole: RoleEnum) {
    super({
      code: 40320,
      message: `该账号是${actualRole === RoleEnum.HR ? '公司代表' : '求职者'}，请切换身份后登录`,
      status: 403,
    })
    this.name = 'RoleMismatchError'
    this.actualRole = actualRole
  }
}

/**
 * 密码登录。
 * @param input      账号密码
 * @param expectRole 期望身份（登录页所选）。与账号真实角色不符时抛
 *                   RoleMismatchError，且不写入任何登录态。
 *
 * 注意：身份校验必须在写入 store 之前完成 —— 若先登录成功再校验，
 * RootGate 会立刻卸载登录页去渲染工作台，提示永远来不及显示。
 */
export async function loginWithPassword(
  input: LoginPasswordInput,
  expectRole?: RoleEnum,
): Promise<LoginResult> {
  if (USE_MOCK) {
    // 模拟网络延迟，让 loading 态可见
    await new Promise(r => setTimeout(r, 320))

    const account = input.account.trim()
    const expected = MOCK_CREDENTIALS[account]
    if (!expected || expected !== input.password) {
      throw new InvalidCredentialsError()
    }

    const user = MOCK_USERS.find(u => u.phone === account)
    if (!user) {
      throw new InvalidCredentialsError()
    }
    if (user.status !== 'ACTIVE') {
      throw new ApiError({ code: 40310, message: '账号已被禁用', status: 403 })
    }

    // 身份校验：必须在写入 token / store 之前完成
    const actualRole: RoleEnum =
      user.role === RoleEnum.HR ? RoleEnum.HR : RoleEnum.JOB_SEEKER
    if (expectRole && expectRole !== actualRole) {
      throw new RoleMismatchError(actualRole)
    }

    const profile: UserProfile | HrProfile =
      user.role === 'HR' ? MOCK_HR_PROFILE : MOCK_SEEKER_PROFILE

    const result: LoginResult = {
      accessToken: mockToken('at', user.id),
      refreshToken: mockToken('rt', user.id),
      user,
      profile,
      // HR 登录额外带上公司归属，供 HR 工作台使用
      company: user.role === 'HR' ? MOCK_COMPANY : undefined,
      member: user.role === 'HR' ? MOCK_COMPANY_MEMBER : undefined,
    }
    persistTokens(result)
    return result
  }

  const result = await request<LoginResult>('/auth/login/password', {
    method: 'POST',
    body: input,
    noAuth: true,
  })
  persistTokens(result)
  return result
}

/** 退出登录 */
export async function logout(): Promise<void> {
  if (USE_MOCK) {
    clearTokens()
    return
  }
  try {
    await request<void>('/auth/logout', { method: 'POST' })
  } finally {
    clearTokens()
  }
}

/** 当前登录用户（联调用；Mock 阶段由 store 持有） */
export async function fetchMe(): Promise<{
  user: User
  profile?: UserProfile | HrProfile
  company?: typeof MOCK_COMPANY
  member?: typeof MOCK_COMPANY_MEMBER
}> {
  if (USE_MOCK) {
    return {
      user: MOCK_HR_USER,
      profile: MOCK_HR_PROFILE,
      company: MOCK_COMPANY,
      member: MOCK_COMPANY_MEMBER,
    }
  }
  return request('/auth/me', { method: 'GET' })
}

// ============================================================
// token 持久化（仅浏览器）
// ============================================================

function persistTokens(result: AuthResult): void {
  if (typeof window === 'undefined') return
  try {
    window.localStorage.setItem(ACCESS_TOKEN_KEY, result.accessToken)
    window.localStorage.setItem(REFRESH_TOKEN_KEY, result.refreshToken)
  } catch {
    /* 隐私模式等场景忽略 */
  }
}

function clearTokens(): void {
  if (typeof window === 'undefined') return
  try {
    window.localStorage.removeItem(ACCESS_TOKEN_KEY)
    window.localStorage.removeItem(REFRESH_TOKEN_KEY)
  } catch {
    /* 忽略 */
  }
}

/** 登录页展示用：预置测试账号（求职者 / HR 各一个） */
export { DEMO_ACCOUNTS } from './mock'
export type { DemoAccountHint } from './mock'

/** 角色 → 展示文案 */
export const ROLE_TEXT: Record<RoleEnum, string> = {
  JOB_SEEKER: '求职者',
  HR: '公司代表 HR',
  COMPANY_ADMIN: '企业管理员',
  PLATFORM_ADMIN: '平台管理员',
} as Record<RoleEnum, string>
