/**
 * 聘聊 · 全局轻量 Toast
 * =====================================================================
 * 从 store 读 toastText 显示，1.8s 自动消失。
 * 与 HTML 原型 toast() 行为一致；不依赖 sonner/toaster 以保持极简。
 *
 * 双端复用：默认读取求职者端 store（pinliao），HR 工作台通过 text/onClear
 * 传入自己的 store 状态，避免两端互相污染 toast 文案。
 * =====================================================================
 */
'use client'

import { useEffect } from 'react'
import { usePinliaoStore } from '@/store/pinliao'

interface Props {
  /** 覆盖 store 文案（HR 端传入自己的 toastText） */
  text?: string | null
  /** 覆盖清除动作 */
  onClear?: () => void
}

export function PinliaoToast({ text, onClear }: Props = {}) {
  const storeText = usePinliaoStore(s => s.toastText)
  const storeClear = usePinliaoStore(s => s.clearToast)

  const toastText = text !== undefined ? text : storeText
  const clearToast = onClear ?? storeClear

  useEffect(() => {
    if (!toastText) return
    const t = setTimeout(() => clearToast(), 1800)
    return () => clearTimeout(t)
  }, [toastText, clearToast])

  if (!toastText) return null
  return (
    <div
      role="status"
      aria-live="polite"
      className="pointer-events-none fixed inset-x-0 top-1/2 z-50 flex -translate-y-1/2 justify-center px-6"
    >
      <div className="pointer-events-auto rounded-lg bg-foreground/90 px-4 py-2.5 text-sm text-background shadow-lg">
        {toastText}
      </div>
    </div>
  )
}
