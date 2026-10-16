/**
 * 聘聊 · Mock 数据 - 候选人种子（HR 端演示数据）
 * =====================================================================
 * 为什么单独成文件：
 *   HR 端的「沟通」会话与「候选人管理」投递列表需要**同一批候选人**
 *   （否则同一个人在两个页面里的姓名/画像会不一致）。
 *   原先候选人名单内联在 mock/hrConversations.ts 中，本模块将其抽出，
 *   两个数据模块共同引用，保证跨页面数据自洽。
 *
 * 依赖方向：本模块不 import 任何其它数据模块（纯数据），避免循环依赖。
 * Step 3 接后端后本文件整体废弃。
 * =====================================================================
 */

/** 候选人（求职者）演示画像 */
export interface CandidateSeed {
  userId: string
  name: string
  expect: string
  city: string
  workYears: number
  edu: string
  resumeCompleteness: number
}

export const CANDIDATES: CandidateSeed[] = [
  { userId: 'u-cand-1', name: '林小满', expect: '前端开发', city: '上海', workYears: 3, edu: '本科', resumeCompleteness: 85 },
  { userId: 'u-cand-2', name: '周叙白', expect: '后端开发', city: '上海', workYears: 6, edu: '硕士', resumeCompleteness: 92 },
  { userId: 'u-cand-3', name: '沈禾', expect: '测试', city: '上海', workYears: 4, edu: '本科', resumeCompleteness: 78 },
  { userId: 'u-cand-4', name: '赵之衡', expect: '运维/安全', city: '上海', workYears: 5, edu: '本科', resumeCompleteness: 88 },
  { userId: 'u-cand-5', name: '苏晚', expect: '产品经理', city: '上海', workYears: 4, edu: '硕士', resumeCompleteness: 81 },
  { userId: 'u-cand-6', name: '何知遥', expect: 'UI/UX设计', city: '上海', workYears: 3, edu: '本科', resumeCompleteness: 74 },
]

/** 按 userId 查候选人画像 */
export const candidateById = (userId: string): CandidateSeed | undefined =>
  CANDIDATES.find(c => c.userId === userId)
