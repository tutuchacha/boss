/**
 * 聘聊 · 认证状态（Zustand）
 * =====================================================================
 * 职责：
 *  - 保存当前登录用户、角色、画像与（HR 的）公司归属
 *  - 提供登录 / 退出动作
 *
 * 为什么独立于 pinliao.ts：
 *  - 求职者端的 store 已通过验收，认证是横切关注点，独立存放可避免
 *    在已验证的 store 上做侵入式改动（降低回归风险）。
 *
 * 水合策略（与 pinliao.ts 一致，避免 SSR/CSR mismatatch）：
 *  - skipHydration: true，首屏客户端也用初始值渲染，与服务端一致；
 *  - 由 RootGate 挂载后手动 rehydrate()。
 *
 * 安全说明：
 *  - 仅持久化「当前登录用户」用于免密回访；token 由 api/auth.ts 单独
 *    存于 pinliao.accessToken，本 store 不重复持有。
 *  - Mock 阶段的凭据是假数据，不构成真实凭据泄露。
 * =====================================================================
 */

'use client'

import { create } from 'zustand'
import { persist, createJSONStorage } from 'zustand/middleware'
import type {
  Company,
  CompanyMember,
  HrProfile,
  RegisterInput,
  User,
  UserProfile,
} from '@/types/api'
import { RoleEnum } from '@/types/api'
import {
  loginWithPassword,
  loginWithSms as apiLoginSms,
  logout as apiLogout,
  register as apiRegister,
  type LoginResult,
} from '@/api/auth'

export interface AuthState {
  /** 当前登录用户；null 表示未登录 */
  user: User | null
  /** 求职者画像（JOB_SEEKER 时存在） */
  seekerProfile: UserProfile | null
  /** HR 画像（HR / COMPANY_ADMIN 时存在） */
  hrProfile: HrProfile | null
  /** HR 所属公司 */
  company: Company | null
  /** 公司成员关系（含角色 ADMIN / HR） */
  member: CompanyMember | null
  /** 是否正在登录 */
  pending: boolean
}

interface AuthActions {
  /**
   * 密码登录；失败抛出 ApiError（含业务码与提示）。
   * @param expectRole 登录页所选的期望身份。与账号真实角色不符时抛
   *   RoleMismatchError，此时不会写入登录态（登录页可提示切换身份）。
   */
  login: (account: string, password: string, expectRole?: RoleEnum) => Promise<User>
  /** 手机验证码登录（未注册手机号会抛 40110，可引导去注册） */
  loginSms: (phone: string, code: string, expectRole?: RoleEnum) => Promise<User>
  /** 注册并登录（HR 注册会自动创建「未认证」新公司，联动企业认证） */
  register: (input: RegisterInput) => Promise<User>
  /** 公司资料编辑 / 企业认证后同步公司信息，驱动界面即时刷新 */
  updateCompany: (company: Company) => void
  /** 退出登录并清理 token */
  logout: () => Promise<void>
  /** 是否具备招聘者权限（HR 或企业管理员） */
  isRecruiter: () => boolean
  /** 当前用户展示名 */
  displayName: () => string
}

export type AuthStore = AuthState & AuthActions

/** 初始值（服务端与客户端首帧一致） */
const INITIAL: AuthState = {
  user: null,
  seekerProfile: null,
  hrProfile: null,
  company: null,
  member: null,
  pending: false,
}

/** 登录结果 → store 身份状态（密码 / 验证码 / 注册三路共用） */
function toAuthState(
  result: LoginResult,
): Pick<AuthState, 'user' | 'seekerProfile' | 'hrProfile' | 'company' | 'member'> {
  const isHr = result.user.role === RoleEnum.HR
  return {
    user: result.user,
    seekerProfile: isHr ? null : ((result.profile as UserProfile) ?? null),
    hrProfile: isHr ? ((result.profile as HrProfile) ?? null) : null,
    company: isHr ? (result.company ?? null) : null,
    member: isHr ? (result.member ?? null) : null,
  }
}

export const useAuthStore = create<AuthStore>()(
  persist(
    (set, get) => ({
      ...INITIAL,

      login: async (account, password, expectRole) => {
        set({ pending: true })
        try {
          const result = await loginWithPassword({ account, password }, expectRole)
          set({ ...toAuthState(result), pending: false })
          return result.user
        } catch (err) {
          set({ pending: false })
          throw err
        }
      },

      loginSms: async (phone, code, expectRole) => {
        set({ pending: true })
        try {
          const result = await apiLoginSms({ phone, code }, expectRole)
          set({ ...toAuthState(result), pending: false })
          return result.user
        } catch (err) {
          set({ pending: false })
          throw err
        }
      },

      register: async input => {
        set({ pending: true })
        try {
          const result = await apiRegister(input)
          set({ ...toAuthState(result), pending: false })
          return result.user
        } catch (err) {
          set({ pending: false })
          throw err
        }
      },

      updateCompany: company => set({ company }),

      logout: async () => {
        await apiLogout()
        set({ ...INITIAL })
      },

      isRecruiter: () => {
        const role = get().user?.role
        return role === RoleEnum.HR || role === RoleEnum.COMPANY_ADMIN
      },

      displayName: () => {
        const s = get()
        return s.hrProfile?.name ?? s.seekerProfile?.name ?? s.user?.phone ?? ''
      },
    }),
    {
      name: 'pinliao.auth.v1',
      storage: createJSONStorage(() => localStorage),
      // 首屏不读 localStorage，避免与服务端 HTML 不一致
      skipHydration: true,
      version: 0,
      migrate: persisted => (persisted ?? {}) as AuthState,
      // 只持久化身份信息，不持久化 pending 等运行时态
      partialize: s => ({
        user: s.user,
        seekerProfile: s.seekerProfile,
        hrProfile: s.hrProfile,
        company: s.company,
        member: s.member,
      }),
    },
  ),
)

// ============================================================
// 说明：公司/成员上下文由登录结果（LoginResult.company / member）注入，
// 联调阶段（USE_MOCK=false）改由 /auth/me 提供，store 逻辑无需改动。
// ============================================================
