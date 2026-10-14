/**
 * 聘聊 · 根路由分发
 * =====================================================================
 * 职责：根据登录状态与角色决定渲染哪个工作台。
 *
 * 分流规则：
 *  - 认证状态未水合 → 渲染中性过渡屏（不依赖 user，避免 hydration mismatch）
 *  - 未登录         → 登录页
 *  - 求职者         → 求职者端 AppShell（JobSeekerShell）
 *  - HR             → HR 工作台 HrShell
 *
 * 为什么在这里做分发而不是用路由：
 *  本项目是 SPA，全部视图靠 store 的 pageStack 切换（见 src/app/page.tsx 注释）。
 *  引入 next/navigation 会改变架构并让已验收的求职者端出现白屏风险。
 * =====================================================================
 */

'use client'

import { useAuthStore } from '@/store/auth'
import { RoleEnum } from '@/types/api'
import { useRehydrateAuth, useRehydrateStore, useStoreHydrated } from '@/hooks/useStoreHydration'
import { AppShell } from './AppShell'
import { LoginPage } from './LoginPage'
import { HrShell } from './hr/HrShell'

export function RootGate() {
  // 挂载后恢复两个持久化 store（只调用一次，位于应用最外层）
  useRehydrateAuth()
  useRehydrateStore()

  const hydrated = useStoreHydrated()
  const user = useAuthStore(s => s.user)

  // 认证状态未恢复前不渲染任何依赖 user 的界面
  if (!hydrated) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-pl-desk">
        <div className="mx-auto flex w-full max-w-[430px] flex-1 items-center justify-center">
          <span className="text-[13px] text-pl-sub">加载中…</span>
        </div>
      </div>
    )
  }

  if (!user) return <LoginPage />

  const isRecruiter =
    user.role === RoleEnum.HR || user.role === RoleEnum.COMPANY_ADMIN

  return isRecruiter ? <HrShell /> : <AppShell />
}
