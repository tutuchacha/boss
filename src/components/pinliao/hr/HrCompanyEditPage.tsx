/**
 * 聘聊 · HR 工作台 - 公司资料编辑
 * =====================================================================
 * 对应接口：PATCH /api/v1/company/me（docs/api-endpoints.md §5.4）
 *
 * 说明：
 *  - 保存成功后同步 auth store（顶栏公司名、我的页即时刷新）
 *  - Mock 层会同步把新名称/阶段/规模写回本司已发布职位的公司快照
 * =====================================================================
 */

'use client'

import { useState } from 'react'
import { ArrowLeft, Building2, Loader2 } from 'lucide-react'
import { updateMyCompany } from '@/api/company'
import { ApiError } from '@/api/client'
import { useAuthStore } from '@/store/auth'
import { useHrStore } from '@/store/hr'
import { cn } from '@/lib/utils'

/** 融资阶段选项（覆盖演示数据与常见值） */
const STAGE_OPTIONS = ['未融资', '天使轮', 'A轮', 'B轮', 'C轮', 'D轮及以上', '已上市', '不需要融资']

/** 公司规模选项 */
const SIZE_OPTIONS = ['1-19人', '20-99人', '100-499人', '500-999人', '1000-9999人', '10000人以上']

export function HrCompanyEditPage() {
  const company = useAuthStore(s => s.company)
  const updateCompany = useAuthStore(s => s.updateCompany)
  const popPage = useHrStore(s => s.popPage)
  const toast = useHrStore(s => s.toast)

  const [name, setName] = useState(company?.name ?? '')
  const [industry, setIndustry] = useState(company?.industry ?? '')
  const [stage, setStage] = useState(company?.stage ?? '')
  const [size, setSize] = useState(company?.size ?? '')
  const [website, setWebsite] = useState(company?.website ?? '')
  const [intro, setIntro] = useState(company?.intro ?? '')
  const [saving, setSaving] = useState(false)

  const onSave = async () => {
    if (!company) return
    if (name.trim().length < 2) {
      toast('公司名称至少 2 个字')
      return
    }

    setSaving(true)
    try {
      const updated = await updateMyCompany(company.id, {
        name: name.trim(),
        industry: industry.trim(),
        stage,
        size,
        website: website.trim(),
        intro: intro.trim(),
      })
      updateCompany(updated)
      toast('公司资料已更新')
      popPage()
    } catch (err) {
      toast(err instanceof ApiError ? err.message : '保存失败，请重试')
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="flex flex-1 flex-col overflow-hidden">
      {/* 顶栏 */}
      <div className="flex shrink-0 items-center gap-2 border-b border-border bg-card px-3 py-2.5">
        <button
          type="button"
          onClick={popPage}
          aria-label="返回"
          className="flex h-8 w-8 items-center justify-center rounded-md text-foreground hover:bg-accent/50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
        >
          <ArrowLeft className="h-4 w-4" />
        </button>
        <span className="text-[14px] font-medium text-foreground">公司资料</span>
      </div>

      {!company ? (
        <div className="flex flex-1 items-center justify-center text-[13px] text-pl-sub">
          未绑定公司
        </div>
      ) : (
        <>
          <div className="pl-scroll flex-1 overflow-y-auto bg-background">
            <section className="mt-2 bg-card px-4 py-4">
              <h3 className="flex items-center gap-1.5 text-[13px] font-medium text-foreground">
                <Building2 className="h-3.5 w-3.5 text-primary" />
                基本信息
              </h3>

              <label className="mt-3 block text-[12px] text-pl-sub" htmlFor="company-name">
                公司名称 <span className="text-destructive">*</span>
              </label>
              <input
                id="company-name"
                type="text"
                value={name}
                onChange={e => setName(e.target.value)}
                maxLength={30}
                placeholder="请输入公司全称"
                className="mt-1.5 h-10 w-full rounded-lg border border-border bg-background px-3 text-[13px] text-foreground placeholder:text-muted-foreground/70 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
              />

              <label className="mt-3 block text-[12px] text-pl-sub" htmlFor="company-industry">
                所属行业
              </label>
              <input
                id="company-industry"
                type="text"
                value={industry}
                onChange={e => setIndustry(e.target.value)}
                maxLength={20}
                placeholder="如：互联网 / 企业服务"
                className="mt-1.5 h-10 w-full rounded-lg border border-border bg-background px-3 text-[13px] text-foreground placeholder:text-muted-foreground/70 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
              />

              <label className="mt-3 block text-[12px] text-pl-sub" htmlFor="company-website">
                公司官网（选填）
              </label>
              <input
                id="company-website"
                type="text"
                value={website}
                onChange={e => setWebsite(e.target.value)}
                placeholder="https://"
                className="mt-1.5 h-10 w-full rounded-lg border border-border bg-background px-3 text-[13px] text-foreground placeholder:text-muted-foreground/70 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
              />
            </section>

            <section className="mt-2 bg-card px-4 py-4">
              <h3 className="text-[13px] font-medium text-foreground">融资阶段</h3>
              <div className="mt-2 flex flex-wrap gap-1.5">
                {STAGE_OPTIONS.map(s => {
                  const active = stage === s
                  return (
                    <button
                      key={s}
                      type="button"
                      aria-pressed={active}
                      onClick={() => setStage(s)}
                      className={cn(
                        'rounded-full px-2.5 py-1 text-[12px] transition-colors',
                        active
                          ? 'bg-primary text-primary-foreground'
                          : 'bg-secondary text-secondary-foreground hover:bg-accent',
                      )}
                    >
                      {s}
                    </button>
                  )
                })}
              </div>
            </section>

            <section className="mt-2 bg-card px-4 py-4">
              <h3 className="text-[13px] font-medium text-foreground">公司规模</h3>
              <div className="mt-2 flex flex-wrap gap-1.5">
                {SIZE_OPTIONS.map(s => {
                  const active = size === s
                  return (
                    <button
                      key={s}
                      type="button"
                      aria-pressed={active}
                      onClick={() => setSize(s)}
                      className={cn(
                        'rounded-full px-2.5 py-1 text-[12px] transition-colors',
                        active
                          ? 'bg-primary text-primary-foreground'
                          : 'bg-secondary text-secondary-foreground hover:bg-accent',
                      )}
                    >
                      {s}
                    </button>
                  )
                })}
              </div>
            </section>

            <section className="mt-2 bg-card px-4 py-4">
              <label className="text-[13px] font-medium text-foreground" htmlFor="company-intro">
                公司简介
              </label>
              <textarea
                id="company-intro"
                value={intro}
                onChange={e => setIntro(e.target.value)}
                rows={4}
                maxLength={500}
                placeholder="一句话介绍公司业务与团队亮点"
                className="mt-2 w-full resize-none rounded-lg border border-border bg-background px-3 py-2 text-[13px] text-foreground placeholder:text-muted-foreground/70 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
              />
              <p className="mt-1 text-right text-[11px] text-pl-sub">{intro.length}/500</p>
            </section>

            <p className="px-4 py-4 text-[11px] leading-relaxed text-pl-sub">
              公司名称、融资阶段与规模会同步展示在本司已发布职位的卡片上，
              保存后求职者端即可看到最新信息。
            </p>
            <div className="h-4" />
          </div>

          {/* 底部保存 */}
          <div className="shrink-0 border-t border-border bg-card px-4 py-3 pl-safe-bottom">
            <button
              type="button"
              disabled={saving}
              onClick={() => void onSave()}
              className="flex h-11 w-full items-center justify-center gap-2 rounded-lg bg-primary text-[15px] font-medium text-primary-foreground transition-opacity hover:opacity-90 disabled:opacity-60"
            >
              {saving && <Loader2 className="h-4 w-4 animate-spin" />}
              {saving ? '保存中…' : '保存修改'}
            </button>
          </div>
        </>
      )}
    </div>
  )
}
