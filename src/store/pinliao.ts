/**
 * 聘聊 · 全局状态（Zustand）
 * =====================================================================
 * 设计要点：
 *  - 单 store，按切片组织（tab/筛选/收藏/会话/主题/期望/页面栈/Sheet）
 *  - 持久化键 `pinliao.v3`，仅持久化跨会话需要保留的部分
 *  - 页面栈 + Sheet 通过 store 状态切换（SPA，无路由跳转）
 *  - 与 HTML 原型 S 全局状态对齐，扩展到契约字段
 * =====================================================================
 */

'use client'

import { create } from 'zustand'
import { persist, createJSONStorage } from 'zustand/middleware'
import type {
  Conversation,
  Message,
} from '@/types/api'
import type { ThemePref } from '@/types/api'
import {
  CATEGORY_NAMES,
  CATEGORY_TREE_RAW,
} from '@/api/mock/categories'
import {
  currentUserProfile,
  USER_STATUS_TEXT,
} from '@/api/mock/conversations'
import { getSeedConversations } from '@/api/conversations'

// ============================================================
// 类型
// ============================================================

export type TabKey = 'jobs' | 'msgs' | 'me'

/** 页面栈元素 */
export interface PageEntry {
  /** 页面类型 */
  type: 'job' | 'chat' | 'favorites' | 'resume' | 'wish'
  /** 关联资源 id（如 job id / conversation job-id） */
  id?: string
}

/** Sheet 类型 */
export type SheetKind = 'city' | 'filter' | 'wish' | null

/** 排序键（与契约 JobSortKey 对齐） */
export type SortKey = 'recommend' | 'latest'

export interface ProfileState {
  name: string
  statusText: string
  expectCat: string
  expectSub: string
  expectCity: string
  expectSalary: string
}

export interface FiltersState {
  city: string
  q: string
  cat: string // '全部' 或一级分类名
  sub: string // '全部' 或二级岗位名
  sort: SortKey
  exp: string
  sal: string
}

// ============================================================
// store
// ============================================================

interface PinliaoState {
  // ---- 视图 ----
  tab: TabKey
  pageStack: PageEntry[]
  sheet: SheetKind
  /** 筛选 sheet 临时状态（确认前不污染主筛选） */
  filterDraft: Pick<FiltersState, 'sort' | 'exp' | 'sal'>
  /** 求职期望 sheet 临时状态 */
  wishDraft: ProfileState | null
  /** 聊天正在输入的会话 id 集合 */
  typingConvIds: Set<string>

  // ---- 列表筛选 ----
  filters: FiltersState

  // ---- 收藏（jobId 集合） ----
  favorites: string[]

  // ---- 会话与消息 ----
  conversations: Conversation[]
  messagesByConv: Record<string, Message[]>
  /** 当前打开的聊天会话 id（在 pageStack 顶层 chat 时） */
  activeConversationId: string | null

  // ---- 求职者画像（简化版，对齐 HTML 原型 profile） ----
  profile: ProfileState

  // ---- 临时 toast ----
  toastText: string | null
}

interface PinliaoActions {
  // 视图
  setTab: (t: TabKey) => void
  pushPage: (entry: PageEntry) => void
  popPage: () => void
  clearPageStack: () => void
  openSheet: (k: Exclude<SheetKind, null>) => void
  closeSheet: () => void
  setFilterDraft: (d: Partial<PinliaoState['filterDraft']>) => void
  applyFilterDraft: () => void
  resetFilter: () => void
  setWishDraft: () => void
  setWishDraftField: (patch: Partial<ProfileState>) => void
  applyWishDraft: () => void
  setTyping: (convId: string, isTyping: boolean) => void
  setActiveConversation: (id: string | null) => void

  // 列表筛选
  setCity: (c: string) => void
  setQ: (q: string) => void
  setCat: (c: string) => void
  setSub: (s: string) => void

  // 收藏
  toggleFavorite: (jobId: string) => void
  isFavorite: (jobId: string) => boolean

  // 会话
  mutateConversations: (fn: (ctx: { conversations: Conversation[]; messagesByConv: Record<string, Message[]> }) => void) => void
  markConversationRead: (convId: string) => void
  unreadTotal: () => number
  getMessages: (convId: string) => Message[]
  hasConversationForJob: (jobId: string) => boolean

  // toast
  toast: (text: string) => void
  clearToast: () => void

  // 期望编辑
  setProfile: (patch: Partial<ProfileState>) => void
}

export type PinliaoStore = PinliaoState & PinliaoActions

// ============================================================
// 默认值
// ============================================================

const DEFAULT_FILTERS: FiltersState = {
  city: '全国',
  q: '',
  cat: '全部',
  sub: '全部',
  sort: 'recommend',
  exp: '不限',
  sal: '不限',
}

const DEFAULT_PROFILE: ProfileState = {
  name: currentUserProfile.name,
  statusText: USER_STATUS_TEXT,
  expectCat: currentUserProfile.expectCat ?? '互联网',
  expectSub: currentUserProfile.expectSub ?? '前端开发',
  expectCity: currentUserProfile.expectCity ?? '上海',
  expectSalary: '20-30K',
}

const seed = getSeedConversations()

// ============================================================
// 创建 store
// ============================================================

export const usePinliaoStore = create<PinliaoStore>()(
  persist(
    (set, get) => ({
      // ---- 视图 ----
      tab: 'jobs',
      pageStack: [],
      sheet: null,
      filterDraft: { sort: 'recommend', exp: '不限', sal: '不限' },
      wishDraft: null,
      typingConvIds: new Set(),

      // ---- 列表筛选 ----
      filters: { ...DEFAULT_FILTERS },

      // ---- 收藏 ----
      favorites: [],

      // ---- 会话 ----
      conversations: seed.conversations,
      messagesByConv: seed.messagesByConv,
      activeConversationId: null,

      // ---- 画像 ----
      profile: { ...DEFAULT_PROFILE },

      // ---- toast ----
      toastText: null,

      // ============ actions ============

      setTab: t =>
        set({
          tab: t,
          pageStack: [],
          sheet: null,
          activeConversationId: null,
        }),

      pushPage: entry => set(s => ({ pageStack: [...s.pageStack, entry] })),

      popPage: () =>
        set(s => {
          const next = s.pageStack.slice(0, -1)
          const top = next[next.length - 1]
          return {
            pageStack: next,
            activeConversationId: top?.type === 'chat' ? top.id ?? null : null,
          }
        }),

      clearPageStack: () => set({ pageStack: [], activeConversationId: null }),

      openSheet: k =>
        set(s => {
          if (k === 'filter') {
            return {
              sheet: 'filter',
              filterDraft: {
                sort: s.filters.sort,
                exp: s.filters.exp,
                sal: s.filters.sal,
              },
            }
          }
          if (k === 'wish') {
            return { sheet: 'wish', wishDraft: { ...s.profile } }
          }
          return { sheet: k }
        }),

      closeSheet: () => set({ sheet: null, wishDraft: null }),

      setFilterDraft: d =>
        set(s => ({ filterDraft: { ...s.filterDraft, ...d } })),

      applyFilterDraft: () =>
        set(s => ({
          filters: {
            ...s.filters,
            sort: s.filterDraft.sort,
            exp: s.filterDraft.exp,
            sal: s.filterDraft.sal,
          },
          sheet: null,
        })),

      resetFilter: () =>
        set({
          filterDraft: { sort: 'recommend', exp: '不限', sal: '不限' },
        }),

      setWishDraft: () => set(s => ({ wishDraft: { ...s.profile } })),

      setWishDraftField: patch =>
        set(s => ({
          wishDraft: s.wishDraft ? { ...s.wishDraft, ...patch } : null,
        })),

      applyWishDraft: () =>
        set(s => ({
          profile: s.wishDraft ?? s.profile,
          sheet: null,
          wishDraft: null,
        })),

      setTyping: (convId, isTyping) =>
        set(s => {
          const next = new Set(s.typingConvIds)
          if (isTyping) next.add(convId)
          else next.delete(convId)
          return { typingConvIds: next }
        }),

      setActiveConversation: id => set({ activeConversationId: id }),

      // ---- 列表筛选 ----
      setCity: c => set(s => ({ filters: { ...s.filters, city: c } })),
      setQ: q => set(s => ({ filters: { ...s.filters, q } })),
      setCat: c =>
        set(s => ({ filters: { ...s.filters, cat: c, sub: '全部' } })),
      setSub: sub => set(s => ({ filters: { ...s.filters, sub } })),

      // ---- 收藏 ----
      toggleFavorite: jobId =>
        set(s => {
          const exists = s.favorites.includes(jobId)
          return {
            favorites: exists
              ? s.favorites.filter(x => x !== jobId)
              : [...s.favorites, jobId],
          }
        }),
      isFavorite: jobId => get().favorites.includes(jobId),

      // ---- 会话 ----
      mutateConversations: fn =>
        set(s => {
          const draft = {
            conversations: s.conversations.slice(),
            messagesByConv: { ...s.messagesByConv },
          }
          // 深拷贝消息数组，避免外部修改影响 store
          for (const k of Object.keys(draft.messagesByConv)) {
            draft.messagesByConv[k] = draft.messagesByConv[k].slice()
          }
          fn(draft)
          return {
            conversations: draft.conversations,
            messagesByConv: draft.messagesByConv,
          }
        }),

      markConversationRead: convId =>
        set(s => {
          const conversations = s.conversations.map(c =>
            c.id === convId ? { ...c, userUnreadCount: 0 } : c,
          )
          const msgs = (s.messagesByConv[convId] ?? []).map(m =>
            m.fromType !== 'USER' ? { ...m, readByUser: true } : m,
          )
          return {
            conversations,
            messagesByConv: { ...s.messagesByConv, [convId]: msgs },
          }
        }),

      unreadTotal: () =>
        get().conversations.reduce((a, c) => a + c.userUnreadCount, 0),

      getMessages: convId => get().messagesByConv[convId] ?? [],

      hasConversationForJob: jobId =>
        get().conversations.some(c => c.jobId === jobId),

      // ---- toast ----
      toast: text =>
        set({ toastText: text }),
      clearToast: () => set({ toastText: null }),

      // ---- 期望 ----
      setProfile: patch => set(s => ({ profile: { ...s.profile, ...patch } })),
    }),
    {
      name: 'pinliao.v3',
      storage: createJSONStorage(() => localStorage),
      // 仅持久化跨会话需要保留的部分
      partialize: s => ({
        favorites: s.favorites,
        conversations: s.conversations,
        messagesByConv: s.messagesByConv,
        profile: s.profile,
        filters: {
          ...s.filters,
          q: '', // 进入时不恢复搜索词，避免误导
        },
      }),
      // 恢复时合并默认值，避免字段缺失
      merge: (persisted, current) => {
        const p = (persisted ?? {}) as Partial<PinliaoState>
        return {
          ...current,
          ...p,
          filters: { ...current.filters, ...(p.filters ?? {}) },
          profile: { ...current.profile, ...(p.profile ?? {}) },
          typingConvIds: new Set(), // 不持久化运行时态
          pageStack: [], // 不持久化页面栈
          sheet: null,
          toastText: null,
        }
      },
    },
  ),
)

// ============================================================
// 选择器 / 工具
// ============================================================

export const CATEGORY_LIST = ['全部', ...CATEGORY_NAMES]
export const SUB_LIST_OF = (cat: string): string[] =>
  cat === '全部' ? [] : ['全部', ...(CATEGORY_TREE_RAW[cat] ?? [])]

/** 计算当前激活的筛选数量（用于筛选按钮角标） */
export const activeFilterCount = (s: PinliaoState): number =>
  (s.filters.exp !== '不限' ? 1 : 0) +
  (s.filters.sal !== '不限' ? 1 : 0) +
  (s.filters.sort !== 'recommend' ? 1 : 0)

/** 主题文案 */
export const THEME_TEXT: Record<ThemePref, string> = {
  auto: '跟随系统',
  light: '浅色',
  dark: '深色',
}
