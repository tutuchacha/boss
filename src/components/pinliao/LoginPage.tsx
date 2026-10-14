/**
 * 聘聊 · 登录页
 * =====================================================================
 * 对应契约：POST /api/v1/auth/login/password
 *
 * 交互设计：
 *  - 顶部品牌区（与求职者端共用青绿主题）
 *  - 身份切换（求职者 / 公司代表 HR）：决定下方默认填入的演示账号，
 *    并在账号身份不匹配时给出可一键修正的提示
 *  - 密码登录表单 + 字段级校验 + 业务错误提示
 *  - 演示账号一键填入（Mock 阶段专用，联调后由 USE_MOCK 控制隐藏）
 *
 *  hydration 安全：组件不依赖时间/随机数，首帧渲染内容由固定常量决定。
 * =====================================================================
 */

'use client'

import { useState } from 'react'
import { AlertCircle, Briefcase, Building2, CheckCircle2, Eye, EyeOff, Zap } from 'lucide-react'
import { RoleEnum } from '@/types/api'
import { DEMO_ACCOUNTS } from '@/api/mock/auth'
import { useAuthStore } from '@/store/auth'
import { useHrStore } from '@/store/hr'
import { RoleMismatchError } from '@/api/auth'
import { ApiError } from '@/api/client'
import { USE_MOCK } from '@/config/env'
import { cn } from '@/lib/utils'

type LoginRole = typeof RoleEnum.JOB_SEEKER | typeof RoleEnum.HR

/** 身份切换项 */
const ROLE_TABS: {
  key: LoginRole
  label: string
  desc: string
  Icon: typeof Briefcase
}[] = [
  {
    key: RoleEnum.JOB_SEEKER,
    label: '我要找工作',
    desc: '浏览职位、投递简历、与 HR 沟通',
    Icon: Briefcase,
  },
  {
    key: RoleEnum.HR,
    label: '我是公司代表',
    desc: '发布职位、管理招聘、查看候选人',
    Icon: Building2,
  },
]

export function LoginPage() {
  const login = useAuthStore(s => s.login)
  const pending = useAuthStore(s => s.pending)

  const [role, setRole] = useState<LoginRole>(RoleEnum.JOB_SEEKER)
  const [account, setAccount] = useState('')
  const [password, setPassword] = useState('')
  const [showPwd, setShowPwd] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [fieldErr, setFieldErr] = useState<{ account?: string; password?: string }>({})
  /** 账号真实身份与所选不符时的提示 */
  const [mismatch, setMismatch] = useState<{ actual: LoginRole } | null>(null)
  const activeTab = ROLE_TABS.find(t => t.key === role)!

  /** 演示账号一键填入 */
  const fillDemo = (demoRole: LoginRole) => {
    const demo = DEMO_ACCOUNTS.find(d => d.role === demoRole)
    if (!demo) return
    setRole(demo.role === RoleEnum.HR ? RoleEnum.HR : RoleEnum.JOB_SEEKER)
    setAccount(demo.account)
    setPassword(demo.password)
    setError(null)
    setFieldErr({})
    setMismatch(null)
  }

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault()

    // 字段级校验
    const fe: typeof fieldErr = {}
    if (!account.trim()) fe.account = '请输入账号'
    if (!password) fe.password = '请输入密码'
    setFieldErr(fe)
    if (Object.keys(fe).length > 0) return

    setError(null)
    setMismatch(null)

    try {
      // 期望身份随请求下发：不匹配时 auth service 直接抛错，不写入登录态，
      // 因此 LoginPage 不会被 RootGate 卸载，提示才能正常显示。
      await login(account.trim(), password, role)

      // 进入工作台前重置 HR 页面栈，保证落地在职位列表
      useHrStore.getState().clearPageStack()
      useHrStore.getState().setTab('jobs')
    } catch (err) {
      if (err instanceof RoleMismatchError) {
        // 契约 Roles 中还有 COMPANY_ADMIN / PLATFORM_ADMIN，本页只区分两类
        const actual: LoginRole =
          err.actualRole === RoleEnum.HR ? RoleEnum.HR : RoleEnum.JOB_SEEKER
        setMismatch({ actual })
      } else if (err instanceof ApiError) {
        setError(err.message)
      } else {
        setError('登录失败，请稍后重试')
      }
    }
  }

  return (
    <div className="flex min-h-screen flex-col bg-pl-desk">
      <div className="mx-auto flex w-full max-w-[430px] flex-1 flex-col bg-background">
        {/* 品牌区 */}
        <header className="px-6 pt-14 pb-7">
          <div className="flex items-center gap-2.5">
            <span className="flex h-11 w-11 items-center justify-center rounded-xl bg-primary text-primary-foreground">
              <Zap className="h-6 w-6" strokeWidth={2.2} />
            </span>
            <div>
              <h1 className="text-xl font-semibold tracking-tight text-foreground">聘聊</h1>
              <p className="text-[11px] text-pl-sub">求职者与招聘者高效沟通平台</p>
            </div>
          </div>
        </header>

        {/* 身份切换 */}
        <div className="px-6">
          <div className="grid grid-cols-2 gap-2" role="tablist" aria-label="选择身份">
            {ROLE_TABS.map(({ key, label, Icon }) => {
              const active = role === key
              return (
                <button
                  key={key}
                  type="button"
                  role="tab"
                  aria-selected={active}
                  onClick={() => {
                    setRole(key)
                    setError(null)
                    setMismatch(null)
                  }}
                  className={cn(
                    'flex flex-col items-start gap-1 rounded-xl border px-3.5 py-3 text-left transition-colors',
                    active
                      ? 'border-primary bg-primary/5'
                      : 'border-border bg-card hover:bg-accent/40',
                  )}
                >
                  <span className="flex items-center gap-1.5">
                    <Icon
                      className={cn('h-4 w-4', active ? 'text-primary' : 'text-muted-foreground')}
                      strokeWidth={2.2}
                    />
                    <span
                      className={cn(
                        'text-[13px] font-medium',
                        active ? 'text-primary' : 'text-foreground',
                      )}
                    >
                      {label}
                    </span>
                  </span>
                </button>
              )
            })}
          </div>
          <p className="mt-2.5 px-0.5 text-[11px] text-pl-sub">{activeTab.desc}</p>
        </div>

        {/* 表单 */}
        <form onSubmit={onSubmit} className="flex flex-1 flex-col px-6 pt-6" noValidate>
          <label className="text-[13px] font-medium text-foreground" htmlFor="login-account">
            账号
          </label>
          <input
            id="login-account"
            name="account"
            type="text"
            autoComplete="username"
            inputMode="tel"
            value={account}
            onChange={e => {
              setAccount(e.target.value)
              if (fieldErr.account) setFieldErr(p => ({ ...p, account: undefined }))
            }}
            placeholder="请输入手机号"
            className={cn(
              'mt-1.5 h-11 w-full rounded-lg border bg-card px-3 text-[15px] text-foreground',
              'placeholder:text-muted-foreground/70 focus-visible:outline-none focus-visible:ring-2',
              fieldErr.account
                ? 'border-destructive focus-visible:ring-destructive/40'
                : 'border-border focus-visible:ring-ring',
            )}
          />
          {fieldErr.account && (
            <p className="mt-1 text-[11px] text-destructive">{fieldErr.account}</p>
          )}

          <label
            className="mt-4 text-[13px] font-medium text-foreground"
            htmlFor="login-password"
          >
            密码
          </label>
          <div className="relative mt-1.5">
            <input
              id="login-password"
              name="password"
              type={showPwd ? 'text' : 'password'}
              autoComplete="current-password"
              value={password}
              onChange={e => {
                setPassword(e.target.value)
                if (fieldErr.password) setFieldErr(p => ({ ...p, password: undefined }))
              }}
              placeholder="请输入密码"
              className={cn(
                'h-11 w-full rounded-lg border bg-card pl-3 pr-11 text-[15px] text-foreground',
                'placeholder:text-muted-foreground/70 focus-visible:outline-none focus-visible:ring-2',
                fieldErr.password
                  ? 'border-destructive focus-visible:ring-destructive/40'
                  : 'border-border focus-visible:ring-ring',
              )}
            />
            <button
              type="button"
              onClick={() => setShowPwd(v => !v)}
              aria-label={showPwd ? '隐藏密码' : '显示密码'}
              className="absolute right-1 top-1 flex h-9 w-9 items-center justify-center rounded-md text-muted-foreground hover:bg-accent/60 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
            >
              {showPwd ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
            </button>
          </div>
          {fieldErr.password && (
            <p className="mt-1 text-[11px] text-destructive">{fieldErr.password}</p>
          )}

          {/* 业务错误 */}
          {error && (
            <div
              role="alert"
              className="mt-4 flex items-start gap-2 rounded-lg border border-destructive/30 bg-destructive/5 px-3 py-2.5"
            >
              <AlertCircle className="mt-0.5 h-4 w-4 shrink-0 text-destructive" />
              <span className="text-[12px] text-destructive">{error}</span>
            </div>
          )}

          {/* 身份不匹配提示 */}
          {mismatch && (
            <div
              role="alert"
              className="mt-4 rounded-lg border border-amber-500/35 bg-amber-500/5 px-3 py-2.5"
            >
              <p className="text-[12px] text-amber-700 dark:text-amber-400">
                该账号是
                <strong className="font-semibold">
                  {mismatch.actual === 'HR' ? '公司代表 HR' : '求职者'}
                </strong>
                ，请切换到对应身份后登录。
              </p>
              <button
                type="button"
                onClick={() => fillDemo(mismatch.actual)}
                className="mt-2 rounded-md border border-amber-500/40 px-2.5 py-1 text-[11px] font-medium text-amber-700 hover:bg-amber-500/10 dark:text-amber-400"
              >
                切换到{mismatch.actual === 'HR' ? '公司代表' : '求职者'}并填入账号
              </button>
            </div>
          )}

          <button
            type="submit"
            disabled={pending}
            className={cn(
              'mt-5 h-11 w-full rounded-lg bg-primary text-[15px] font-medium text-primary-foreground',
              'transition-opacity focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring',
              pending ? 'opacity-60' : 'hover:opacity-90 active:opacity-85',
            )}
          >
            {pending ? '登录中…' : '登录'}
          </button>

          <div className="flex-1" />

          {/* 演示账号（Mock 阶段） */}
          {USE_MOCK && (
            <section className="mb-6 rounded-xl border border-dashed border-border bg-secondary/40 p-3.5">
              <div className="flex items-center gap-1.5">
                <CheckCircle2 className="h-3.5 w-3.5 text-primary" />
                <h2 className="text-[12px] font-medium text-foreground">演示账号（一键填入）</h2>
              </div>
              <div className="mt-2.5 space-y-2">
                {DEMO_ACCOUNTS.map(d => {
                  const isHr = d.role === 'HR'
                  return (
                    <button
                      key={d.account}
                      type="button"
                      onClick={() => fillDemo(d.role)}
                      className="flex w-full items-center gap-2.5 rounded-lg border border-border bg-card px-3 py-2.5 text-left transition-colors hover:bg-accent/40 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                    >
                      <span
                        className={cn(
                          'flex h-7 w-7 shrink-0 items-center justify-center rounded-full',
                          isHr ? 'bg-primary/10 text-primary' : 'bg-secondary text-foreground',
                        )}
                      >
                        {isHr ? (
                          <Building2 className="h-3.5 w-3.5" />
                        ) : (
                          <Briefcase className="h-3.5 w-3.5" />
                        )}
                      </span>
                      <span className="min-w-0 flex-1">
                        <span className="block text-[13px] font-medium text-foreground">
                          {d.label}
                        </span>
                        <span className="block pl-line-clamp-1 text-[11px] text-pl-sub">
                          {d.note}
                        </span>
                      </span>
                      <span className="shrink-0 text-right">
                        <span className="block font-mono text-[12px] text-foreground">
                          {d.account}
                        </span>
                        <span className="block font-mono text-[11px] text-pl-sub">
                          {d.password}
                        </span>
                      </span>
                    </button>
                  )
                })}
              </div>
              <p className="mt-2.5 text-[11px] leading-relaxed text-pl-sub">
                密码均为 <span className="font-mono">123456</span>。登录状态会保存在本机，
                刷新后仍保持登录。
              </p>
            </section>
          )}
        </form>
      </div>
    </div>
  )
}
