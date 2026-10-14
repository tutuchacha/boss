/**
 * 聘聊 · HR 工作台 - 职位发布 / 编辑
 * =====================================================================
 * 对应接口：
 *  - POST  /api/v1/jobs           发布（§6.5）
 *  - PATCH /api/v1/jobs/{jobId}   更新（§6.6）
 *
 * 设计要点：
 *  - 表单状态存于 hr store 的 draft，中途返回再进入不丢失
 *  - 字段级校验（validateJobForm），错误就近展示
 *  - 一级分类联动二级岗位；切换分类时自动重置二级并预填职责模板
 *  - 职责/要求提供"按分类填入模板"，降低填写成本
 * =====================================================================
 */

'use client'

import { useEffect, useMemo, useState } from 'react'
import { ArrowLeft, Check, Loader2, Sparkles } from 'lucide-react'
import { JobStatusEnum } from '@/types/api'
import type { HrJobRecord } from '@/api/hrJobs'
import {
  HR_CITY_OPTIONS,
  HR_EDU_OPTIONS,
  HR_EXP_OPTIONS,
  HR_WELFARE_OPTIONS,
  createJob,
  draftToInput,
  getMyJob,
  parseTags,
  recordToDraft,
  updateJob,
  validateJobForm,
} from '@/api/hrJobs'
import { CATEGORY_NAMES, subsOf, responsibilitiesOf } from '@/api/mock/categories'
import { useAuthStore } from '@/store/auth'
import { useHrStore } from '@/store/hr'
import { cn } from '@/lib/utils'

interface Props {
  /** 有值 = 编辑模式 */
  jobId?: string
}

/** 统一的输入框样式 */
const inputCls = (invalid?: boolean) =>
  cn(
    'h-10 w-full rounded-lg border bg-card px-3 text-[14px] text-foreground',
    'placeholder:text-muted-foreground/70 focus-visible:outline-none focus-visible:ring-2',
    invalid
      ? 'border-destructive focus-visible:ring-destructive/40'
      : 'border-border focus-visible:ring-ring',
  )

const selectCls = (invalid?: boolean) =>
  cn(
    'h-10 w-full rounded-lg border bg-card px-2.5 text-[14px] text-foreground',
    'focus-visible:outline-none focus-visible:ring-2',
    invalid
      ? 'border-destructive focus-visible:ring-destructive/40'
      : 'border-border focus-visible:ring-ring',
  )

function Field({
  label,
  error,
  required,
  children,
  hint,
}: {
  label: string
  error?: string
  required?: boolean
  hint?: string
  children: React.ReactNode
}) {
  return (
    <div>
      <div className="flex items-baseline gap-1.5">
        <label className="text-[13px] font-medium text-foreground">
          {label}
          {required && <span className="ml-0.5 text-destructive">*</span>}
        </label>
        {hint && <span className="text-[11px] text-pl-sub">{hint}</span>}
      </div>
      <div className="mt-1.5">{children}</div>
      {error && <p className="mt-1 text-[11px] text-destructive">{error}</p>}
    </div>
  )
}

export function HrJobEditorPage({ jobId }: Props) {
  const [origin, setOrigin] = useState<HrJobRecord | null>(null)
  const [loading, setLoading] = useState(Boolean(jobId))

  /** 编辑模式：载入原职位并回填表单。
   *  放在本组件（仅负责取数）中，其子树 EditorForm 不参与取数，
   *  从而避免在同一个组件里既取数又同步 setState 的告警。 */
  useEffect(() => {
    if (!jobId) return
    let cancelled = false
    void (async () => {
      try {
        const rec = await getMyJob(jobId)
        if (cancelled) return
        setOrigin(rec)
        useHrStore.setState({ draft: recordToDraft(rec), draftErrors: {} })
      } catch {
        if (!cancelled) useHrStore.getState().toast('职位不存在或无权编辑')
      } finally {
        if (!cancelled) setLoading(false)
      }
    })()
    return () => {
      cancelled = true
    }
  }, [jobId])

  if (loading) {
    return (
      <div className="flex flex-1 items-center justify-center">
        <Loader2 className="h-5 w-5 animate-spin text-muted-foreground" />
      </div>
    )
  }

  return <EditorForm jobId={jobId} origin={origin} />
}

/** 表单主体：只依赖 store 中的 draft，不参与取数 */
function EditorForm({ jobId, origin }: { jobId?: string; origin: HrJobRecord | null }) {
  const company = useAuthStore(s => s.company)
  const hrProfile = useAuthStore(s => s.hrProfile)

  const draft = useHrStore(s => s.draft)
  const errors = useHrStore(s => s.draftErrors)
  const patchDraft = useHrStore(s => s.patchDraft)
  const setDraftErrors = useHrStore(s => s.setDraftErrors)
  const popPage = useHrStore(s => s.popPage)
  const bumpDataVersion = useHrStore(s => s.bumpDataVersion)
  const toast = useHrStore(s => s.toast)

  const [saving, setSaving] = useState(false)

  const isEdit = Boolean(jobId)
  const companyId = company?.id ?? ''

  const subOptions = useMemo(() => subsOf(draft.cat), [draft.cat])
  const tagList = useMemo(() => parseTags(draft.tagsText), [draft.tagsText])

  /** 切换一级分类：重置二级并预填职责模板（仅当职责为空） */
  const onCatChange = (cat: string) => {
    const subs = subsOf(cat)
    patchDraft({ cat, sub: subs[0] ?? '' })
  }

  const applyTemplate = () => {
    const tpl = responsibilitiesOf(draft.cat)
    if (tpl.length === 0) return
    patchDraft({ responsibilities: tpl.map((t, i) => `${i + 1}. ${t}；`).join('\n') })
  }

  const toggleWelfare = (w: string) => {
    const has = draft.welfare.includes(w)
    patchDraft({
      welfare: has ? draft.welfare.filter(x => x !== w) : [...draft.welfare, w],
    })
  }

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault()

    const errs = validateJobForm(draft)
    setDraftErrors(errs)
    if (Object.keys(errs).length > 0) {
      toast('请检查表单中标红的字段')
      return
    }
    if (!companyId) {
      toast('未绑定公司，无法发布职位')
      return
    }

    setSaving(true)
    try {
      const input = draftToInput(draft)
      if (isEdit && jobId) {
        await updateJob(jobId, input, companyId)
        toast('职位已更新')
      } else {
        await createJob(input)
        toast('职位已发布，求职者端可搜索到')
      }
      bumpDataVersion()
      popPage()
    } catch {
      toast(isEdit ? '更新失败，请重试' : '发布失败，请重试')
    } finally {
      setSaving(false)
    }
  }

  return (
    <form onSubmit={onSubmit} className="flex flex-1 flex-col overflow-hidden" noValidate>
      {/* 顶栏 */}
      <div className="flex items-center gap-2 border-b border-border bg-card px-3 py-2.5">
        <button
          type="button"
          onClick={popPage}
          aria-label="返回"
          className="flex h-8 w-8 items-center justify-center rounded-md text-foreground hover:bg-accent/50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
        >
          <ArrowLeft className="h-4 w-4" />
        </button>
        <div className="min-w-0 flex-1">
          <h2 className="text-[15px] font-medium text-foreground">
            {isEdit ? '编辑职位' : '发布职位'}
          </h2>
          {isEdit && origin && (
            <p className="text-[11px] text-pl-sub">
              当前状态：
              {origin.status === JobStatusEnum.OPEN ? '招聘中' : '已关闭'}
            </p>
          )}
        </div>
      </div>

      <div className="pl-scroll flex-1 space-y-4 overflow-y-auto px-4 py-4">
        {/* 基本信息 */}
        <section className="rounded-xl border border-border bg-card p-3.5">
          <h3 className="mb-3 text-[13px] font-medium text-foreground">基本信息</h3>
          <div className="space-y-3.5">
            <Field label="职位名称" required error={errors.title} hint="2-30 字">
              <input
                type="text"
                value={draft.title}
                onChange={e => patchDraft({ title: e.target.value })}
                placeholder="如：前端开发工程师"
                maxLength={40}
                className={inputCls(Boolean(errors.title))}
              />
            </Field>

            <div className="grid grid-cols-2 gap-3">
              <Field label="一级分类" required error={errors.cat}>
                <select
                  value={draft.cat}
                  onChange={e => onCatChange(e.target.value)}
                  className={selectCls(Boolean(errors.cat))}
                >
                  {CATEGORY_NAMES.map(c => (
                    <option key={c} value={c}>
                      {c}
                    </option>
                  ))}
                </select>
              </Field>
              <Field label="二级岗位" required error={errors.sub}>
                <select
                  value={draft.sub}
                  onChange={e => patchDraft({ sub: e.target.value })}
                  className={selectCls(Boolean(errors.sub))}
                >
                  {subOptions.map(s => (
                    <option key={s} value={s}>
                      {s}
                    </option>
                  ))}
                </select>
              </Field>
            </div>

            <div className="grid grid-cols-2 gap-3">
              <Field label="工作城市" required error={errors.city}>
                <select
                  value={draft.city}
                  onChange={e => patchDraft({ city: e.target.value })}
                  className={selectCls(Boolean(errors.city))}
                >
                  {HR_CITY_OPTIONS.map(c => (
                    <option key={c} value={c}>
                      {c}
                    </option>
                  ))}
                </select>
              </Field>
              <Field label="区域" hint="选填">
                <input
                  type="text"
                  value={draft.area}
                  onChange={e => patchDraft({ area: e.target.value })}
                  placeholder="如：浦东新区"
                  maxLength={20}
                  className={inputCls()}
                />
              </Field>
            </div>

            <div className="grid grid-cols-2 gap-3">
              <Field label="经验要求">
                <select
                  value={draft.exp}
                  onChange={e => patchDraft({ exp: e.target.value })}
                  className={selectCls()}
                >
                  {HR_EXP_OPTIONS.map(o => (
                    <option key={o} value={o}>
                      {o}
                    </option>
                  ))}
                </select>
              </Field>
              <Field label="学历要求">
                <select
                  value={draft.edu}
                  onChange={e => patchDraft({ edu: e.target.value })}
                  className={selectCls()}
                >
                  {HR_EDU_OPTIONS.map(o => (
                    <option key={o} value={o}>
                      {o}
                    </option>
                  ))}
                </select>
              </Field>
            </div>

            <Field label="薪资范围" required error={errors.salaryMin || errors.salaryMax} hint="单位 K/月">
              <div className="flex items-center gap-2">
                <input
                  type="number"
                  inputMode="numeric"
                  value={draft.salaryMin}
                  onChange={e => patchDraft({ salaryMin: e.target.value })}
                  placeholder="最低"
                  min={1}
                  className={inputCls(Boolean(errors.salaryMin))}
                />
                <span className="shrink-0 text-[13px] text-pl-sub">—</span>
                <input
                  type="number"
                  inputMode="numeric"
                  value={draft.salaryMax}
                  onChange={e => patchDraft({ salaryMax: e.target.value })}
                  placeholder="最高"
                  min={1}
                  className={inputCls(Boolean(errors.salaryMax))}
                />
                <span className="shrink-0 text-[13px] text-pl-sub">K</span>
              </div>
            </Field>
          </div>
        </section>

        {/* 标签 */}
        <section className="rounded-xl border border-border bg-card p-3.5">
          <Field label="技能标签" error={errors.tagsText} hint="用、或逗号分隔，最多 8 个">
            <input
              type="text"
              value={draft.tagsText}
              onChange={e => patchDraft({ tagsText: e.target.value })}
              placeholder="如：Vue3、TypeScript、Vite"
              className={inputCls(Boolean(errors.tagsText))}
            />
          </Field>
          {tagList.length > 0 && (
            <div className="mt-2.5 flex flex-wrap gap-1.5">
              {tagList.map(t => (
                <span
                  key={t}
                  className="rounded bg-secondary px-2 py-0.5 text-[11px] text-secondary-foreground"
                >
                  {t}
                </span>
              ))}
            </div>
          )}
        </section>

        {/* 职位描述 */}
        <section className="rounded-xl border border-border bg-card p-3.5">
          <div className="mb-3 flex items-center justify-between">
            <h3 className="text-[13px] font-medium text-foreground">职位描述</h3>
            <button
              type="button"
              onClick={applyTemplate}
              className="flex items-center gap-1 rounded-md border border-border px-2 py-1 text-[11px] text-primary hover:bg-primary/5"
            >
              <Sparkles className="h-3 w-3" />
              按「{draft.cat}」填入模板
            </button>
          </div>

          <div className="space-y-3.5">
            <Field label="岗位职责" hint="选填">
              <textarea
                value={draft.responsibilities}
                onChange={e => patchDraft({ responsibilities: e.target.value })}
                rows={5}
                placeholder={'1. 负责……；\n2. 参与……；'}
                className="w-full resize-y rounded-lg border border-border bg-card px-3 py-2 text-[14px] leading-relaxed text-foreground placeholder:text-muted-foreground/70 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
              />
            </Field>

            <Field label="任职要求" hint="选填">
              <textarea
                value={draft.requirements}
                onChange={e => patchDraft({ requirements: e.target.value })}
                rows={5}
                placeholder={'1. 本科及以上学历……；\n2. 熟悉……；'}
                className="w-full resize-y rounded-lg border border-border bg-card px-3 py-2 text-[14px] leading-relaxed text-foreground placeholder:text-muted-foreground/70 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
              />
            </Field>
          </div>
        </section>

        {/* 福利 */}
        <section className="rounded-xl border border-border bg-card p-3.5">
          <h3 className="mb-1 text-[13px] font-medium text-foreground">福利待遇</h3>
          <p className="mb-2.5 text-[11px] text-pl-sub">
            已选 {draft.welfare.length} 项
          </p>
          <div className="flex flex-wrap gap-1.5">
            {HR_WELFARE_OPTIONS.map(w => {
              const active = draft.welfare.includes(w)
              return (
                <button
                  key={w}
                  type="button"
                  onClick={() => toggleWelfare(w)}
                  aria-pressed={active}
                  className={cn(
                    'flex items-center gap-1 rounded-full px-2.5 py-1 text-[12px] transition-colors',
                    active
                      ? 'bg-primary text-primary-foreground'
                      : 'bg-secondary text-secondary-foreground hover:bg-accent',
                  )}
                >
                  {active && <Check className="h-3 w-3" />}
                  {w}
                </button>
              )
            })}
          </div>
        </section>

        {/* 提交人提示 */}
        <p className="px-0.5 pb-2 text-[11px] leading-relaxed text-pl-sub">
          发布后将记入 <span className="text-foreground">{hrProfile?.name}</span>
          （{hrProfile?.title}）名下，归属公司{' '}
          <span className="text-foreground">{company?.name}</span>。
        </p>
      </div>

      {/* 底部提交 */}
      <div className="flex gap-2 border-t border-border bg-card px-4 py-3">
        <button
          type="button"
          onClick={popPage}
          className="h-10 flex-1 rounded-lg border border-border text-[14px] text-foreground hover:bg-accent/40"
        >
          取消
        </button>
        <button
          type="submit"
          disabled={saving}
          className={cn(
            'flex h-10 flex-[2] items-center justify-center gap-1.5 rounded-lg bg-primary text-[14px] font-medium text-primary-foreground',
            saving ? 'opacity-60' : 'hover:opacity-90',
          )}
        >
          {saving && <Loader2 className="h-3.5 w-3.5 animate-spin" />}
          {saving ? '保存中…' : isEdit ? '保存修改' : '发布职位'}
        </button>
      </div>
    </form>
  )
}
