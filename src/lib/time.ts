/**
 * 聘聊 · 时间格式化工具
 * =====================================================================
 */
import type { Message } from '@/types/api'

/** 距今天数 → 文案 */
export function whenText(days: number): string {
  if (days <= 0) return '今天'
  if (days === 1) return '昨天'
  return `${days}天前`
}

/** ISO 时间 → 会话列表显示文案 */
export function convTimeText(iso: string): string {
  const d = new Date(iso)
  const now = new Date()
  const sameDay = d.toDateString() === now.toDateString()
  if (sameDay) {
    return d.toTimeString().slice(0, 5)
  }
  const yesterday = new Date(now.getTime() - 86400000)
  if (d.toDateString() === yesterday.toDateString()) return '昨天'
  return `${d.getMonth() + 1}月${d.getDate()}日`
}

/** 聊天消息列表中"上一条"文案（用于会话列表） */
export function lastMessageText(m: Message | undefined): string {
  if (!m) return ''
  if (m.fromType === 'SYSTEM') return m.content
  if (m.type === 'RESUME') return '[简历] 已发送在线简历'
  if (m.type === 'INTERVIEW_INVITE') return '[面试邀请]'
  if (m.type === 'JOB_CARD') return '[职位卡片]'
  const prefix = m.fromType === 'USER' ? '我：' : ''
  return prefix + m.content
}
