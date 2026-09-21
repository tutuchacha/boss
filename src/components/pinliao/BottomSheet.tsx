/**
 * 聘聊 · 底部 Sheet 容器
 * =====================================================================
 * 通用底部弹层，遮罩 + 滑入动画，按 Esc 关闭，遮罩点击关闭。
 * 内部内容由调用方通过 children 提供。
 * =====================================================================
 */
'use client'

import { useEffect } from 'react'
import { X } from 'lucide-react'

interface Props {
  title: string
  onClose: () => void
  children: React.ReactNode
  /** 是否显示顶部关闭按钮 */
  showClose?: boolean
}

export function BottomSheet({ title, onClose, children, showClose = true }: Props) {
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose()
    }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [onClose])

  return (
    <div
      className="fixed inset-0 z-40 flex items-end justify-center"
      role="dialog"
      aria-modal="true"
      aria-label={title}
    >
      <div
        className="pl-mask absolute inset-0 bg-black/40"
        onClick={onClose}
      />
      <div className="pl-sheet relative z-10 w-full max-w-[430px] rounded-t-xl bg-card shadow-xl pl-safe-bottom">
        <header className="flex items-center justify-between border-b border-border px-4 py-3 pl-safe-top">
          <h2 className="text-sm font-semibold text-foreground">{title}</h2>
          {showClose && (
            <button
              onClick={onClose}
              aria-label="关闭"
              className="flex h-7 w-7 items-center justify-center rounded-md text-pl-sub hover:bg-accent"
            >
              <X className="h-4 w-4" />
            </button>
          )}
        </header>
        <div className="max-h-[70vh] overflow-y-auto pl-scroll">{children}</div>
      </div>
    </div>
  )
}
