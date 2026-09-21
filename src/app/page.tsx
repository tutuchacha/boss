/**
 * 聘聊 · 唯一可见路由
 * =====================================================================
 * 渲染 AppShell。所有视图通过 store 中的 tab/pageStack/sheet 切换，不走路由。
 * =====================================================================
 */
'use client'

import { AppShell } from '@/components/pinliao/AppShell'

export default function Home() {
  return <AppShell />
}
