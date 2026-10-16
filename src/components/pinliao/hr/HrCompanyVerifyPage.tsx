/**
 * 聘聊 · HR 工作台 - 企业认证
 * =====================================================================
 * 对应接口：POST /api/v1/company/me/verify（docs/api-endpoints.md §5.6）
 *
 * 状态机（Mock）：
 *  - 未认证：展示认证表单（预填公司名 / 联系人 / 电话）
 *  - 审核中（PENDING）：展示进度，页面按 refetchInterval 轮询；
 *    Mock 约 1.2s 自动「人工审核通过」（对应 service 层 MOCK_VERIFY_REVIEW_DELAY_MS）
 *  - 已认证：展示成功态
 *
 * 认证状态变化会同步回 auth store（我的页角标 / 顶栏即时可见）。
 * =====================================================================
 */

'use client'

import { useEffect, useRef, useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { ArrowLeft, BadgeCheck, Loader2, ShieldCheck } from 'lucide-react'
import { getMyCompany, submitCompanyVerification } from '@/api/company'
import { ApiError } from '@/api/client'
import { useAuthStore } from '@/store/auth'
import { useHrStore } from '@/store/hr'

/** 演示用统一社会信用代码（18 位） */
const DEMO_LICENSE_NO = '91310000MA1K35Q99X'

export function HrCompanyVerifyPage() {
  const queryClient = useQueryClient()
  const company = useAuthStore(s => s.company)
  const user = useAuthStore(s => s.user)
  const hrProfile = useAuthStore(s => s.hrProfile)
  const updateCompany = useAuthStore(s => s.updateCompany)
  const popPage = useHrStore(s => s.popPage)
  const toast = useHrStore(s => s.toast)

  const companyId = company?.id ?? ''

  const { data: live, isPending } = useQuery({
    queryKey: ['company', 'me', companyId],
    queryFn: () => getMyCompany(companyId),
    enabled: Boolean(companyId),
    // 审核中轮询：Mock 通过后自动切换到成功态
    refetchInterval: q => {
      const d = q.state.data
      return d && d.status === 'PENDING' && !d.verified ? 900 : false
    },
  })

  /**
   * 公司资料变化（认证状态 / 名称）同步回 auth store。
   * 用 key 去重，避免相同内容重复 set 触发不必要的重渲染。
   */
  const syncedKey = useRef('')
  useEffect(() => {
    if (!live) return
    const key = `${live.id}:${live.verified ? 1 : 0}:${live.name}`
    if (syncedKey.current === key) return
    syncedKey.current = key
    updateCompany(live)
  }, [live, updateCompany])

  const [companyName, setCompanyName] = useState(company?.name ?? '')
  const [licenseNo, setLicenseNo] = useState(DEMO_LICENSE_NO)
  const [contactName, setContactName] = useState(hrProfile?.name ?? '')
  const [contactPhone, setContactPhone] = useState(user?.phone ?? '')
  const [submitting, setSubmitting] = useState(false)

  const onSubmit = async () => {
    if (companyName.trim().length < 2) {
      toast('请填写完整的企业名称')
      return
    }
    if (!/^[0-9A-Za-z]{18}$/.test(licenseNo.trim())) {
      toast('统一社会信用代码需为 18 位字母或数字')
      return
    }
    if (!contactName.trim()) {
      toast('请填写联系人姓名')
      return
    }
    if (!/^1\d{10}$/.test(contactPhone.trim())) {
      toast('请填写正确的联系电话')
      return
    }

    setSubmitting(true)
    try {
      await submitCompanyVerification(companyId, {
        companyName: companyName.trim(),
        licenseNo: licenseNo.trim().toUpperCase(),
        contactName: contactName.trim(),
        contactPhone: contactPhone.trim(),
      })
      toast('认证资料已提交，审核中…')
      await queryClient.invalidateQueries({ queryKey: ['company', 'me', companyId] })
    } catch (err) {
      toast(err instanceof ApiError ? err.message : '提交失败，请重试')
    } finally {
      setSubmitting(false)
    }
  }

  const header = (
    <div className="flex shrink-0 items-center gap-2 border-b border-border bg-card px-3 py-2.5">
      <button
        type="button"
        onClick={popPage}
        aria-label="返回"
        className="flex h-8 w-8 items-center justify-center rounded-md text-foreground hover:bg-accent/50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
      >
        <ArrowLeft className="h-4 w-4" />
      </button>
      <span className="text-[14px] font-medium text-foreground">企业认证</span>
    </div>
  )

  if (isPending || !live) {
    return (
      <div className="flex flex-1 flex-col overflow-hidden">
        {header}
        <div className="flex flex-1 items-center justify-center gap-2 text-[13px] text-pl-sub">
          <Loader2 className="h-4 w-4 animate-spin" /> 加载中…
        </div>
      </div>
    )
  }

  // ---- 已认证 ----
  if (live.verified) {
    return (
      <div className="flex flex-1 flex-col overflow-hidden">
        {header}
        <div className="pl-scroll flex-1 overflow-y-auto bg-background">
          <div className="flex flex-col items-center px-6 py-12 text-center">
            <span className="flex h-16 w-16 items-center justify-center rounded-full bg-primary/10">
              <BadgeCheck className="h-8 w-8 text-primary" />
            </span>
            <p className="mt-4 text-[16px] font-medium text-foreground">企业认证已通过</p>
            <p className="mt-1 text-[12px] text-pl-sub">{live.name}</p>
            <p className="mt-3 text-[12px] leading-relaxed text-pl-sub">
              认证企业会在求职者端展示「已认证」标识，
              <br />
              有效提升候选人信任度与回复率。
            </p>
          </div>
        </div>
      </div>
    )
  }

  // ---- 审核中 ----
  if (live.status === 'PENDING') {
    return (
      <div className="flex flex-1 flex-col overflow-hidden">
        {header}
        <div className="pl-scroll flex-1 overflow-y-auto bg-background">
          <div className="flex flex-col items-center px-6 py-12 text-center">
            <span className="flex h-16 w-16 items-center justify-center rounded-full bg-amber-500/10">
              <Loader2 className="h-8 w-8 animate-spin text-amber-600 dark:text-amber-400" />
            </span>
            <p className="mt-4 text-[16px] font-medium text-foreground">认证审核中</p>
            <p className="mt-1 text-[12px] text-pl-sub">
              已收到「{companyName || live.name}」的认证资料
            </p>
            <p className="mt-3 text-[12px] leading-relaxed text-pl-sub">
              人工审核预计 1~3 分钟出结果，本页会自动刷新，
              <br />
              审核通过后即可在求职者端展示认证标识。
            </p>
          </div>
        </div>
      </div>
    )
  }

  // ---- 未认证：认证表单 ----
  return (
    <div className="flex flex-1 flex-col overflow-hidden">
      {header}
      <div className="pl-scroll flex-1 overflow-y-auto bg-background">
        <section className="bg-card px-4 py-4">
          <h3 className="flex items-center gap-1.5 text-[13px] font-medium text-foreground">
            <ShieldCheck className="h-3.5 w-3.5 text-primary" />
            认证资料
          </h3>
          <p className="mt-1 text-[11px] leading-relaxed text-pl-sub">
            请填写与营业执照一致的信息，仅用于企业认证，不会对外公开。
          </p>

          <label className="mt-3 block text-[12px] text-pl-sub" htmlFor="verify-company-name">
            企业全称 <span className="text-destructive">*</span>
          </label>
          <input
            id="verify-company-name"
            type="text"
            value={companyName}
            onChange={e => setCompanyName(e.target.value)}
            maxLength={30}
            placeholder="与营业执照一致的企业全称"
            className="mt-1.5 h-10 w-full rounded-lg border border-border bg-background px-3 text-[13px] text-foreground placeholder:text-muted-foreground/70 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
          />

          <label className="mt-3 block text-[12px] text-pl-sub" htmlFor="verify-license-no">
            统一社会信用代码 <span className="text-destructive">*</span>
          </label>
          <input
            id="verify-license-no"
            type="text"
            value={licenseNo}
            onChange={e => setLicenseNo(e.target.value.toUpperCase())}
            maxLength={18}
            placeholder="18 位字母或数字"
            className="mt-1.5 h-10 w-full rounded-lg border border-border bg-background px-3 font-mono text-[13px] text-foreground placeholder:text-muted-foreground/70 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
          />

          <div className="mt-3 grid grid-cols-2 gap-3">
            <div>
              <label className="block text-[12px] text-pl-sub" htmlFor="verify-contact-name">
                联系人姓名 <span className="text-destructive">*</span>
              </label>
              <input
                id="verify-contact-name"
                type="text"
                value={contactName}
                onChange={e => setContactName(e.target.value)}
                placeholder="姓名"
                className="mt-1.5 h-10 w-full rounded-lg border border-border bg-background px-3 text-[13px] text-foreground placeholder:text-muted-foreground/70 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
              />
            </div>
            <div>
              <label className="block text-[12px] text-pl-sub" htmlFor="verify-contact-phone">
                联系电话 <span className="text-destructive">*</span>
              </label>
              <input
                id="verify-contact-phone"
                type="tel"
                value={contactPhone}
                onChange={e => setContactPhone(e.target.value)}
                maxLength={11}
                placeholder="手机号"
                className="mt-1.5 h-10 w-full rounded-lg border border-border bg-background px-3 text-[13px] text-foreground placeholder:text-muted-foreground/70 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
              />
            </div>
          </div>
        </section>

        <p className="px-4 py-4 text-[11px] leading-relaxed text-pl-sub">
          演示环境将模拟人工审核：提交后约 1~3 秒自动通过，
          页面会自动刷新为「已认证」状态。
        </p>
        <div className="h-4" />
      </div>

      <div className="shrink-0 border-t border-border bg-card px-4 py-3 pl-safe-bottom">
        <button
          type="button"
          disabled={submitting}
          onClick={() => void onSubmit()}
          className="flex h-11 w-full items-center justify-center gap-2 rounded-lg bg-primary text-[15px] font-medium text-primary-foreground transition-opacity hover:opacity-90 disabled:opacity-60"
        >
          {submitting && <Loader2 className="h-4 w-4 animate-spin" />}
          {submitting ? '提交中…' : '提交认证资料'}
        </button>
      </div>
    </div>
  )
}
