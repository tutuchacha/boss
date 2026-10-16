/**
 * 聘聊 · HR 工作台状态（Zustand）
 * =====================================================================
 * 职责：
 *  - HR 工作台的 Tab、页面栈（职位列表 / 职位编辑 / 我的）
 *  - 职位列表的筛选条件（状态 / 关键字 / 仅看我发布的）
 *  - dataVersion：Mock 数据在模块级数组中原地增删改，用版本号驱动列表刷新
 *
 * 不持久化：HR 工作台是操作型界面，刷新回到默认视图更符合预期。
 * =====================================================================
 */

'use client'

import { create } from 'zustand'
import { JobStatusEnum } from '@/types/api'
import type { JobFormDraft } from '@/api/hrJobs'
import { EMPTY_JOB_FORM } from '@/api/hrJobs'

export type HrTabKey = 'jobs' | 'candidates' | 'messages' | 'me'

/** HR 端页面栈元素。根视图（job-list / candidate-list / messages-root）不进入栈，由 tab 决定 */
export type HrPage =
  | { type: 'job-list' }
  | { type: 'candidate-list' }
  | { type: 'messages-root' }
  | { type: 'job-editor'; jobId?: string }
  | { type: 'hr-chat'; conversationId: string }
  | { type: 'candidate-detail'; applicationId: string }
  | { type: 'interview-invite'; applicationId: string }
  | { type: 'company-edit' }
  | { type: 'company-verify' }

export interface HrFilterState {
  status: JobStatusEnum | 'ALL'
  q: string
  /** 仅显示当前 HR 自己发布的职位 */
  onlyMine: boolean
}

interface HrState {
  tab: HrTabKey
  /** 页面栈；空栈 = 根视图（按 tab 渲染） */
  pageStack: HrPage[]
  filters: HrFilterState
  /** 编辑中的表单草稿（进入编辑器时初始化） */
  draft: JobFormDraft
  /** 数据版本号：Mock 数据变更后自增，触发列表重新读取 */
  dataVersion: number
  /** HR 工作台自己的轻提示文案（与求职者端的 toast 相互独立） */
  toastText: string | null
}

interface HrActions {
  setTab: (t: HrTabKey) => void
  pushPage: (p: HrPage) => void
  popPage: () => void
  clearPageStack: () => void

  setFilterStatus: (s: HrFilterState['status']) => void
  setFilterQ: (q: string) => void
  setOnlyMine: (v: boolean) => void
  resetFilters: () => void

  /** 打开编辑器：传 jobId 为编辑，不传为新建 */
  openEditor: (jobId?: string, initial?: JobFormDraft) => void
  /** 打开某个候选人的聊天页 */
  openChat: (conversationId: string) => void
  /** 打开候选人详情（按投递 id） */
  openCandidate: (applicationId: string) => void
  /** 打开「发起面试邀请」表单 */
  openInterviewInvite: (applicationId: string) => void
  /** 打开公司资料编辑 */
  openCompanyEdit: () => void
  /** 打开企业认证 */
  openCompanyVerify: () => void
  /** 更新草稿字段 */
  patchDraft: (patch: Partial<JobFormDraft>) => void
  /** 表单字段级错误 */
  setDraftErrors: (errors: Partial<Record<keyof JobFormDraft, string>>) => void
  clearDraftErrors: () => void
  /** Mock 数据变更后调用，令列表刷新 */
  bumpDataVersion: () => void

  /** 轻提示 */
  toast: (text: string) => void
  clearToast: () => void
}

export type HrStore = HrState & HrActions & {
  draftErrors: Partial<Record<keyof JobFormDraft, string>>
}

const DEFAULT_FILTERS: HrFilterState = {
  status: 'ALL',
  q: '',
  onlyMine: false,
}

export const useHrStore = create<HrStore>()(set => ({
  tab: 'jobs',
  // 空栈 = 根视图，根视图由 tab 决定（见 hrCurrentPage）
  pageStack: [],
  filters: { ...DEFAULT_FILTERS },
  draft: { ...EMPTY_JOB_FORM },
  draftErrors: {},
  dataVersion: 0,
  toastText: null,

  setTab: t => set({ tab: t, pageStack: [] }),

  pushPage: p => set(s => ({ pageStack: [...s.pageStack, p] })),

  popPage: () => set(s => ({ pageStack: s.pageStack.slice(0, -1) })),

  clearPageStack: () => set({ pageStack: [] }),

  setFilterStatus: status =>
    set(s => ({ filters: { ...s.filters, status } })),
  setFilterQ: q => set(s => ({ filters: { ...s.filters, q } })),
  setOnlyMine: onlyMine => set(s => ({ filters: { ...s.filters, onlyMine } })),
  resetFilters: () => set({ filters: { ...DEFAULT_FILTERS } }),

  openEditor: (jobId, initial) =>
    set(s => ({
      pageStack: [...s.pageStack, { type: 'job-editor', jobId }],
      draft: initial ? { ...initial } : { ...EMPTY_JOB_FORM },
      draftErrors: {},
    })),

  openChat: conversationId =>
    set(s => ({ pageStack: [...s.pageStack, { type: 'hr-chat', conversationId }] })),

  openCandidate: applicationId =>
    set(s => ({ pageStack: [...s.pageStack, { type: 'candidate-detail', applicationId }] })),

  openInterviewInvite: applicationId =>
    set(s => ({ pageStack: [...s.pageStack, { type: 'interview-invite', applicationId }] })),

  openCompanyEdit: () => set(s => ({ pageStack: [...s.pageStack, { type: 'company-edit' }] })),

  openCompanyVerify: () =>
    set(s => ({ pageStack: [...s.pageStack, { type: 'company-verify' }] })),

  patchDraft: patch =>
    set(s => {
      // 修改某字段时清掉该字段的错误，提升填写体验
      const nextErrors = { ...s.draftErrors }
      for (const k of Object.keys(patch) as (keyof JobFormDraft)[]) {
        delete nextErrors[k]
      }
      return { draft: { ...s.draft, ...patch }, draftErrors: nextErrors }
    }),

  setDraftErrors: errors => set({ draftErrors: errors }),
  clearDraftErrors: () => set({ draftErrors: {} }),

  bumpDataVersion: () => set(s => ({ dataVersion: s.dataVersion + 1 })),

  toast: (text: string) => set({ toastText: text }),
  clearToast: () => set({ toastText: null }),
}))

/** 稳定的根视图常量：选择器返回它们可保证引用不变 */
const JOB_LIST_ROOT: HrPage = { type: 'job-list' }
const CANDIDATE_LIST_ROOT: HrPage = { type: 'candidate-list' }
const MESSAGES_ROOT: HrPage = { type: 'messages-root' }

/**
 * 当前页面（非 hook 环境用）。
 *
 * ⚠️ 不要把它直接当 zustand 选择器传进 useHrStore：
 *   `?? { type: 'job-list' }` 每次调用都会新建对象，zustand 用 Object.is
 *   比较快照，会判定每次都变化 → 触发无限重渲染（React 报
 *   "The result of getSnapshot should be cached" 与 Maximum update depth）。
 *   组件里请改用 useHrCurrentPage()。
 */
export const hrCurrentPage = (s: HrStore): HrPage =>
  s.pageStack[s.pageStack.length - 1] ??
  (s.tab === 'messages'
    ? MESSAGES_ROOT
    : s.tab === 'candidates'
      ? CANDIDATE_LIST_ROOT
      : JOB_LIST_ROOT)

/**
 * 组件内获取当前页面。
 * 选择器只读取稳定值（栈为空时返回同一常量引用），因此不会造成无限重渲染。
 */
export function useHrCurrentPage(): HrPage {
  const top = useHrStore(s => s.pageStack[s.pageStack.length - 1])
  const tab = useHrStore(s => s.tab)
  if (top) return top
  return tab === 'messages'
    ? MESSAGES_ROOT
    : tab === 'candidates'
      ? CANDIDATE_LIST_ROOT
      : JOB_LIST_ROOT
}
