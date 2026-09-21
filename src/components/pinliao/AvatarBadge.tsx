/**
 * 聘聊 · 通用头像色块
 * =====================================================================
 * 与 HTML 原型 avatar() 一致：取首字 + 哈希色块背景。
 * 不依赖真实图片资源，避免 mock 阶段外网图片加载失败。
 * =====================================================================
 */
'use client'

import { cn } from '@/lib/utils'

const PALETTE = [
  '#00857F', '#3D6FE0', '#B4531F', '#7A4CC2', '#1F7A4D', '#C23B6B',
]

function hashCode(s: string): number {
  let h = 0
  for (let i = 0; i < s.length; i++) h = (h + s.charCodeAt(i)) >>> 0
  return h
}

interface Props {
  name: string
  size?: number
  round?: boolean
  className?: string
}

export function AvatarBadge({ name, size = 40, round = false, className }: Props) {
  const bg = PALETTE[hashCode(name) % PALETTE.length]
  return (
    <span
      aria-hidden="true"
      className={cn(
        'inline-flex shrink-0 select-none items-center justify-center font-medium text-white',
        round ? 'rounded-full' : 'rounded-md',
        className,
      )}
      style={{
        width: size,
        height: size,
        background: bg,
        fontSize: Math.round(size * 0.42),
      }}
    >
      {name?.[0] ?? '?'}
    </span>
  )
}
