/**
 * 聘聊 · 持久化状态水合（hydration）守卫
 * =====================================================================
 * 背景：
 *  - store 使用 zustand `persist`，数据存于 localStorage['pinliao.v3']。
 *  - 服务端渲染（SSR）时读不到 localStorage，只能用 seed 数据渲染 HTML；
 *    浏览器挂载后再从 localStorage 恢复，两份数据可能不同（例如未读数
 *    服务端为 2、本地已读后为 1），React 会报 `Hydration failed` 并
 *    丢弃服务端 HTML 整棵树重建。
 *
 * 方案：
 *  - store 配置 `skipHydration: true`，不再在模块初始化时自动恢复；
 *  - 首屏客户端也先用 seed 数据渲染，与服务端 HTML 完全一致 → 不报错；
 *  - 挂载完成后由本 hook 手动 rehydrate，切到真实持久化数据（一次普通重渲染）。
 *
 * 用法：在应用根组件（AppShell）调用一次即可。
 * =====================================================================
 */

'use client'

import { useEffect, useSyncExternalStore } from 'react'
import { usePinliaoStore } from '@/store/pinliao'

/** 空订阅（服务端快照恒为 false，不存在"变更"语义） */
const subscribeNoop = () => () => {}
/** 服务端快照：首屏一律视为"未水合" */
const getServerSnapshot = () => false
/** 客户端快照：由 React 保证水合完成后才返回 true */
const getClientSnapshot = () => true

/**
 * 返回持久化状态是否已水合完成。
 * 服务端与客户端首帧均返回 false（避免不一致），水合后返回 true。
 */
export function useStoreHydrated(): boolean {
  return useSyncExternalStore(subscribeNoop, getClientSnapshot, getServerSnapshot)
}

/**
 * 在水合完成后手动触发 persist 恢复。
 * 必须在应用根组件调用，且只调用一次。
 */
export function useRehydrateStore(): void {
  useEffect(() => {
    // persist 未启用时（理论上不会）直接退出
    if (!usePinliaoStore.persist) return
    void usePinliaoStore.persist.rehydrate()
  }, [])
}
