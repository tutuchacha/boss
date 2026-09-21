/**
 * 聘聊 · 全局轻量 Toast
 * =====================================================================
 * 从 store 读 toastText 显示，1.8s 自动消失。
 * 与 HTML 原型 toast() 行为一致；不依赖 sonner/toaster 以保持极简。
 * =====================================================================
 */
'use client'

import { useEffect } from 'react'
import { usePinliaoStore } from '@/store/pinliao'

export function PinliaoToast() {
  const toastText = usePinliaoStore(s => s.toastText)
  const clearToast = usePinliaoStore(s => s.clearToast)

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
