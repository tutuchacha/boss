/**
 * 聘聊 · 唯一可见路由
 * =====================================================================
 * 渲染 RootGate。RootGate 根据登录状态与角色分发到：
 *  - 登录页
 *  - 求职者端 AppShell
 *  - HR 工作台 HrShell
 * 所有视图通过各自 store 的 tab/pageStack/sheet 切换，不走路由。
 * =====================================================================
 */
'use client'

import { RootGate } from '@/components/pinliao/RootGate'

export default function Home() {
  return <RootGate />
}
