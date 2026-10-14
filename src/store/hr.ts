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

export type HrTabKey = 'jobs' | 'candidates' | 'me'

/** HR 端页面栈元素 */
export type HrPage =
  | { type: 'job-list' }
  | { type: 'job-editor'; jobId?: string }

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
  pageStack: [{ type: 'job-list' }],
  filters: { ...DEFAULT_FILTERS },
  draft: { ...EMPTY_JOB_FORM },
  draftErrors: {},
  dataVersion: 0,
  toastText: null,

  setTab: t => set({ tab: t, pageStack: [{ type: 'job-list' }] }),

  pushPage: p => set(s => ({ pageStack: [...s.pageStack, p] })),

  popPage: () =>
    set(s => {
      const next = s.pageStack.slice(0, -1)
      return { pageStack: next.length ? next : [{ type: 'job-list' }] }
    }),

  clearPageStack: () => set({ pageStack: [{ type: 'job-list' }] }),

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

/** 当前是否处于编辑页 */
export const hrCurrentPage = (s: HrStore): HrPage =>
  s.pageStack[s.pageStack.length - 1] ?? { type: 'job-list' }
