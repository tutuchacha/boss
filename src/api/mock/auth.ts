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
import { companyByName } from './companies'
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
