/**
 * 聘聊 · Mock 数据 - 认证与账号（Step 1 验收 / Step 2 登录阶段）
 * =====================================================================
 * 对应契约：src/types/api.ts 的 RoleEnum / User / UserProfile / HrProfile /
 *           CompanyMember / LoginPasswordInput / AuthResult
 *
 * 设计说明：
 *  - 密码以明文常量存放，仅供前端 Mock 验收使用，Step 3 后端联调时整体废弃。
 *  - HR 账号绑定到「云杉科技」（companyId 与 companies.ts 的哈希一致），
 *    以便「我发布的职位」能命中 jobs.ts 中已存在的真实 Mock 职位数据。
 *  - hrProfile.id 必须与 jobs.ts 中 hrIdOf(hrName, companyName) 的算法一致，
 *    否则按 hrId 过滤职位会匹配不到。
 * =====================================================================
 */

import type {
  Company,
  CompanyMember,
  HrProfile,
  User,
  UserProfile,
} from '@/types/api'
import { RoleEnum, SeekingStatusEnum } from '@/types/api'
import { companyByName, registerCompany } from './companies'
import { jobs } from './jobs'

/** 测试账号密码（Mock 阶段统一） */
export const MOCK_PASSWORD = '123456'

/** 稳定的 ISO 时间（避免 SSR/CSR 不一致，不使用 Date.now()） */
const T = (day: number, hour = 10): string =>
  new Date(Date.UTC(2026, 8, 1 + day, hour, 0, 0)).toISOString()

// ============================================================
// 公司：与 jobs.ts 共享同一份哈希 id，保证跨模块一致
// ============================================================

const YUNSHAN = companyByName('云杉科技')!

// ============================================================
// 用户
// ============================================================

/** 求职者测试账号 */
export const MOCK_SEEKER_USER: User = {
  id: 'u-seeker-1',
  phone: '13800000001',
  email: 'seeker@pinliao.dev',
  role: RoleEnum.JOB_SEEKER,
  status: 'ACTIVE',
  lastLoginAt: T(20),
  createdAt: T(1),
  updatedAt: T(20),
}

/** HR 测试账号 */
export const MOCK_HR_USER: User = {
  id: 'u-hr-1',
  phone: '13800000002',
  email: 'hr@yunshan.dev',
  role: RoleEnum.HR,
  status: 'ACTIVE',
  lastLoginAt: T(20),
  createdAt: T(2),
  updatedAt: T(20),
}

/** 全部可登录账号 */
export const MOCK_USERS: User[] = [MOCK_SEEKER_USER, MOCK_HR_USER]

/** 账号 → 密码 */
export const MOCK_CREDENTIALS: Record<string, string> = {
  '13800000001': MOCK_PASSWORD,
  '13800000002': MOCK_PASSWORD,
}

// ============================================================
// 求职者画像
// ============================================================

export const MOCK_SEEKER_PROFILE: UserProfile = {
  id: 'up-seeker-1',
  userId: MOCK_SEEKER_USER.id,
  name: '林晓',
  gender: 'female',
  workYears: 4,
  edu: '本科',
  expectCat: '互联网',
  expectSub: '前端开发',
  expectCity: '上海',
  expectSalaryMin: 20,
  expectSalaryMax: 30,
  seekingStatus: SeekingStatusEnum.ACTIVE,
}

// ============================================================
// HR 画像
// 算法必须与 src/api/mock/jobs.ts 的 hrIdOf('陈女士', '云杉科技') 一致
// ============================================================

/** 与 jobs.ts 相同的哈希算法（companyName@hrName） */
const hrIdOf = (hrName: string, companyName: string): string => {
  let h = 0
  const s = `${companyName}@${hrName}`
  for (let i = 0; i < s.length; i++) h = (h * 31 + s.charCodeAt(i)) >>> 0
  return `hr-${h.toString(36)}`
}

/** HR 姓名（与 jobs.ts 中云杉科技的 HR 保持一致） */
export const MOCK_HR_NAME = '陈女士'
export const MOCK_HR_TITLE = '技术招聘经理'

export const MOCK_HR_PROFILE: HrProfile = {
  id: hrIdOf(MOCK_HR_NAME, YUNSHAN.name),
  userId: MOCK_HR_USER.id,
  companyId: YUNSHAN.id,
  name: MOCK_HR_NAME,
  title: MOCK_HR_TITLE,
  intro: '云杉科技技术招聘负责人，负责研发团队招聘。',
}

export const MOCK_COMPANY: Company = YUNSHAN

// ============================================================
// 公司成员关系
// ============================================================

export const MOCK_COMPANY_MEMBER: CompanyMember = {
  id: 'cm-1',
  userId: MOCK_HR_USER.id,
  companyId: YUNSHAN.id,
  role: 'ADMIN',
  joinedAt: T(2),
  user: MOCK_HR_USER,
  hrProfile: MOCK_HR_PROFILE,
}

// ============================================================
// 测试账号展示信息（登录页「一键填入」用）
// ============================================================

export interface DemoAccountHint {
  role: RoleEnum.JOB_SEEKER | RoleEnum.HR
  label: string
  account: string
  password: string
  /** 该账号归属说明，用于登录页提示文案 */
  note: string
}

export const DEMO_ACCOUNTS: DemoAccountHint[] = [
  {
    role: RoleEnum.JOB_SEEKER,
    label: '求职者',
    account: MOCK_SEEKER_USER.phone,
    password: MOCK_PASSWORD,
    note: '林晓 · 前端开发 · 上海',
  },
  {
    role: RoleEnum.HR,
    label: '公司代表 HR',
    account: MOCK_HR_USER.phone,
    password: MOCK_PASSWORD,
    note: `${YUNSHAN.name} · ${MOCK_HR_TITLE}`,
  },
]

/**
 * 该 HR 所在公司当前发布的职位数（登录页/工作台展示用）。
 * 注意：统计的是公司维度，非个人维度。
 */
export const companyJobCount = (companyId: string): number =>
  jobs.filter(j => j.company.id === companyId).length

// ============================================================
// 短信验证码（Mock）与注册账号登记
// ============================================================

/** 短信验证码固定值（演示环境不外发短信，登录页会给出提示） */
export const MOCK_SMS_CODE = '123456'
/** 验证码有效期（5 分钟） */
export const MOCK_SMS_TTL_MS = 5 * 60 * 1000
/** 同手机号重发间隔（秒），与契约 §1.1 的 60s 限流一致 */
export const MOCK_SMS_RESEND_SECONDS = 60

export interface MockSmsRecord {
  code: string
  expiresAt: number
}

/** `scene:phone` → 验证码记录（service 层读写，一次性使用） */
export const mockSmsRecords = new Map<string, MockSmsRecord>()
/** phone → 可再次发送的时间戳（60s 限流） */
export const mockSmsCooldownUntil = new Map<string, number>()

/** 注册账号的画像 / 公司 / 成员登记（键 userId；演示账号走专门分支，不在此表） */
const registeredProfiles = new Map<string, UserProfile | HrProfile>()
const registeredCompanies = new Map<string, Company>()
const registeredMembers = new Map<string, CompanyMember>()

let registerSeq = 0

/**
 * 注册新账号（Mock）：追加用户与登录凭据。
 * - 求职者：仅用户 + 空画像；
 * - HR：自动创建「未认证」新公司（ADMIN 成员），联动企业认证流程。
 */
export function registerMockAccount(input: {
  phone: string
  password: string
  role: RoleEnum.JOB_SEEKER | RoleEnum.HR
}): { user: User; profile: UserProfile | HrProfile; company?: Company; member?: CompanyMember } {
  registerSeq += 1
  const now = new Date().toISOString()
  const user: User = {
    id: `u-reg-${registerSeq}`,
    phone: input.phone,
    role: input.role,
    status: 'ACTIVE',
    lastLoginAt: now,
    createdAt: now,
    updatedAt: now,
  }
  MOCK_USERS.push(user)
  MOCK_CREDENTIALS[input.phone] = input.password

  if (input.role === RoleEnum.JOB_SEEKER) {
    const profile: UserProfile = {
      id: `up-reg-${registerSeq}`,
      userId: user.id,
      name: `求职者${input.phone.slice(-4)}`,
      seekingStatus: SeekingStatusEnum.ACTIVE,
    }
    registeredProfiles.set(user.id, profile)
    return { user, profile }
  }

  const company = registerCompany(`新注册企业-${input.phone.slice(-4)}`)
  const hrProfile: HrProfile = {
    id: `hr-reg-${registerSeq}`,
    userId: user.id,
    companyId: company.id,
    name: `招聘官${input.phone.slice(-4)}`,
    title: '招聘负责人',
  }
  const member: CompanyMember = {
    id: `cm-reg-${registerSeq}`,
    userId: user.id,
    companyId: company.id,
    role: 'ADMIN',
    joinedAt: now,
    user,
    hrProfile,
  }
  registeredProfiles.set(user.id, hrProfile)
  registeredCompanies.set(user.id, company)
  registeredMembers.set(user.id, member)
  return { user, profile: hrProfile, company, member }
}

/**
 * 登录后的会话上下文（画像 / 公司 / 成员）。
 * 演示账号与注册账号的统一出口，避免各处重复判断。
 */
export function mockSessionContextOf(user: User): {
  profile?: UserProfile | HrProfile
  company?: Company
  member?: CompanyMember
} {
  if (user.id === MOCK_SEEKER_USER.id) return { profile: MOCK_SEEKER_PROFILE }
  if (user.id === MOCK_HR_USER.id) {
    return { profile: MOCK_HR_PROFILE, company: MOCK_COMPANY, member: MOCK_COMPANY_MEMBER }
  }
  return {
    profile: registeredProfiles.get(user.id),
    company: registeredCompanies.get(user.id),
    member: registeredMembers.get(user.id),
  }
}
