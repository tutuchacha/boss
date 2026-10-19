/**
 * 聘聊 · 登录页（密码 / 验证码 / 注册 三模式）
 * =====================================================================
 * 对应契约：
 *  - POST /api/v1/auth/login/password   §1.4 密码登录
 *  - POST /api/v1/auth/sms/code         §1.1 发送短信验证码
 *  - POST /api/v1/auth/login/sms        §1.3 手机验证码登录
 *  - POST /api/v1/auth/register         §1.2 手机号注册（注册成功即登录）
 *
 * 交互设计：
 *  - 顶部品牌区 + 登录方式切换（密码 / 验证码 / 注册）
 *  - 身份切换（求职者 / 公司代表）对三种方式都生效：
 *    密码 / 验证码登录用于「身份不匹配」校验；注册用于决定新账号角色
 *  - 字段级校验 + 业务错误提示 + 身份不匹配一键切换
 *  - 验证码 60 秒倒计时（与 Mock 限流 42901 对齐）
 *  - 演示账号一键填入（原型演示专用；Mock 与真实后端联调都保留）
 *
 * hydration 安全：组件不依赖时间/随机数，首帧渲染内容由固定常量决定。
 * =====================================================================
 */

'use client'

import { useEffect, useState } from 'react'
import {
  AlertCircle,
  Briefcase,
  Building2,
  CheckCircle2,
  Eye,
  EyeOff,
  Zap,
} from 'lucide-react'
import { RoleEnum } from '@/types/api'
import { DEMO_ACCOUNTS } from '@/api/mock/auth'
import { sendSmsCode, RoleMismatchError } from '@/api/auth'
import { useAuthStore } from '@/store/auth'
import { useHrStore } from '@/store/hr'
import { usePinliaoStore } from '@/store/pinliao'
import { ApiError } from '@/api/client'
import { cn } from '@/lib/utils'

type LoginRole = typeof RoleEnum.JOB_SEEKER | typeof RoleEnum.HR
type ModeKey = 'password' | 'sms' | 'register'

/** 登录方式切换项 */
const MODE_TABS: { key: ModeKey; label: string }[] = [
  { key: 'password', label: '密码登录' },
  { key: 'sms', label: '验证码登录' },
  { key: 'register', label: '注册' },
]

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

const PHONE_RE = /^1\d{10}$/

/** 简化倒计时：setTimeout 链式递减，卸载即清理 */
function useCountdown(): [number, (n: number) => void] {
  const [left, setLeft] = useState(0)
  useEffect(() => {
    if (left <= 0) return
    const timer = setTimeout(() => setLeft(left - 1), 1000)
    return () => clearTimeout(timer)
  }, [left])
  return [left, setLeft]
}

export function LoginPage() {
  const login = useAuthStore(s => s.login)
  const loginSms = useAuthStore(s => s.loginSms)
  const register = useAuthStore(s => s.register)
  const pending = useAuthStore(s => s.pending)

  const [mode, setMode] = useState<ModeKey>('password')
  const [role, setRole] = useState<LoginRole>(RoleEnum.JOB_SEEKER)

  // 密码 / 验证码登录共用账号输入（验证码场景下即手机号）
  const [account, setAccount] = useState('')
  const [password, setPassword] = useState('')
  const [code, setCode] = useState('')
  const [showPwd, setShowPwd] = useState(false)

  // 注册专用字段（与登录表单隔离，切换模式不串数据）
  const [regPhone, setRegPhone] = useState('')
  const [regCode, setRegCode] = useState('')
  const [regPassword, setRegPassword] = useState('')

  const [error, setError] = useState<string | null>(null)
  const [fieldErr, setFieldErr] = useState<Record<string, string>>({})
  /** 账号真实身份与所选不符时的提示 */
  const [mismatch, setMismatch] = useState<{ actual: LoginRole } | null>(null)
  const [sendingCode, setSendingCode] = useState(false)
  const [smsLeft, setSmsLeft] = useCountdown()
  const [regLeft, setRegLeft] = useCountdown()

  const activeTab = ROLE_TABS.find(t => t.key === role)!

  const switchMode = (m: ModeKey) => {
    setMode(m)
    setError(null)
    setMismatch(null)
    setFieldErr({})
  }

  const switchRole = (r: LoginRole) => {
    setRole(r)
    setError(null)
    setMismatch(null)
  }

  const clearFieldErr = (key: string) => {
    setFieldErr(p => {
      if (!p[key]) return p
      const next = { ...p }
      delete next[key]
      return next
    })
  }

  /** 演示账号一键填入（密码 / 验证码模式可用） */
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

  /** 发送验证码（验证码登录 / 注册共用） */
  const onSendCode = async (
    scene: 'LOGIN' | 'REGISTER',
    phoneRaw: string,
    markLeft: (n: number) => void,
  ) => {
    const phone = phoneRaw.trim()
    if (!PHONE_RE.test(phone)) {
      setError('请输入正确的手机号')
      return
    }
    setError(null)
    setSendingCode(true)
    try {
      const res = await sendSmsCode({ phone, scene })
      markLeft(res.nextRetryIn)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '验证码发送失败，请稍后重试')
    } finally {
      setSendingCode(false)
    }
  }

  /**
   * 登录成功后的公共收尾。
   * 两个 store 都在同一页面会话内保留上次的 tab / 页面栈，
   * 若不在登录时重置，重新登录会落在上次停留的页签（如「我的」），
   * 而验收与真实用户都预期落地在首屏（职位列表）。
   */
  const afterAuthSuccess = () => {
    usePinliaoStore.getState().setTab('jobs')
    useHrStore.getState().clearPageStack()
    useHrStore.getState().setTab('jobs')
  }

  /** 密码登录 */
  const onSubmitPassword = async (e: React.FormEvent) => {
    e.preventDefault()

    const fe: Record<string, string> = {}
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
      afterAuthSuccess()
    } catch (err) {
      if (err instanceof RoleMismatchError) {
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

  /** 验证码登录 */
  const onSubmitSms = async (e: React.FormEvent) => {
    e.preventDefault()

    const fe: Record<string, string> = {}
    if (!PHONE_RE.test(account.trim())) fe.account = '请输入正确的手机号'
    if (!code.trim()) fe.code = '请输入验证码'
    setFieldErr(fe)
    if (Object.keys(fe).length > 0) return

    setError(null)
    setMismatch(null)
    try {
      await loginSms(account.trim(), code.trim(), role)
      afterAuthSuccess()
    } catch (err) {
      if (err instanceof RoleMismatchError) {
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

  /** 注册（成功后即登录：HR 会自动创建未认证新公司） */
  const onSubmitRegister = async (e: React.FormEvent) => {
    e.preventDefault()

    const fe: Record<string, string> = {}
    if (!PHONE_RE.test(regPhone.trim())) fe.regPhone = '请输入正确的手机号'
    if (!regCode.trim()) fe.regCode = '请输入验证码'
    if (regPassword.length < 6) fe.regPassword = '密码至少 6 位'
    setFieldErr(fe)
    if (Object.keys(fe).length > 0) return

    setError(null)
    setMismatch(null)
    try {
      await register({
        phone: regPhone.trim(),
        code: regCode.trim(),
        password: regPassword,
        role: role === RoleEnum.HR ? RoleEnum.HR : RoleEnum.JOB_SEEKER,
      })
      afterAuthSuccess()
    } catch (err) {
      if (err instanceof ApiError) {
        setError(err.message)
      } else {
        setError('注册失败，请稍后重试')
      }
    }
  }

  /** 公共：业务错误 + 身份不匹配提示 */
  const feedback = (
    <>
      {error && (
        <div
          role="alert"
          className="mt-4 flex items-start gap-2 rounded-lg border border-destructive/30 bg-destructive/5 px-3 py-2.5"
        >
          <AlertCircle className="mt-0.5 h-4 w-4 shrink-0 text-destructive" />
          <span className="text-[12px] text-destructive">{error}</span>
        </div>
      )}

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
    </>
  )

  /** 公共：输入框样式 */
  const inputCls = (bad?: string) =>
    cn(
      'mt-1.5 h-11 w-full rounded-lg border bg-card px-3 text-[15px] text-foreground',
      'placeholder:text-muted-foreground/70 focus-visible:outline-none focus-visible:ring-2',
      bad
        ? 'border-destructive focus-visible:ring-destructive/40'
        : 'border-border focus-visible:ring-ring',
    )

  /** 公共：提交按钮 */
  const submitCls = cn(
    'mt-5 h-11 w-full rounded-lg bg-primary text-[15px] font-medium text-primary-foreground',
    'transition-opacity focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring',
    pending ? 'opacity-60' : 'hover:opacity-90 active:opacity-85',
  )

  return (
    <div className="flex min-h-screen flex-col bg-pl-desk">
      <div className="mx-auto flex w-full max-w-[430px] flex-1 flex-col bg-background">
        {/* 品牌区 */}
        <header className="px-6 pt-10 pb-5">
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

        {/* 登录方式切换 */}
        <div className="px-6">
          <div
            className="grid grid-cols-3 gap-1 rounded-lg bg-secondary p-1"
            role="tablist"
            aria-label="登录方式"
          >
            {MODE_TABS.map(t => {
              const active = mode === t.key
              return (
                <button
                  key={t.key}
                  type="button"
                  role="tab"
                  aria-selected={active}
                  onClick={() => switchMode(t.key)}
                  className={cn(
                    'rounded-md py-2 text-[13px] font-medium transition-colors',
                    active
                      ? 'bg-card text-foreground shadow-sm'
                      : 'text-pl-sub hover:text-foreground',
                  )}
                >
                  {t.label}
                </button>
              )
            })}
          </div>
        </div>

        {/* 身份切换 */}
        <div className="px-6 pt-4">
          <div className="grid grid-cols-2 gap-2" role="tablist" aria-label="选择身份">
            {ROLE_TABS.map(({ key, label, Icon }) => {
              const active = role === key
              return (
                <button
                  key={key}
                  type="button"
                  role="tab"
                  aria-selected={active}
                  onClick={() => switchRole(key)}
                  className={cn(
                    'flex items-center gap-1.5 rounded-xl border px-3.5 py-2.5 text-left transition-colors',
                    active
                      ? 'border-primary bg-primary/5'
                      : 'border-border bg-card hover:bg-accent/40',
                  )}
                >
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
                </button>
              )
            })}
          </div>
          <p className="mt-2 px-0.5 text-[11px] text-pl-sub">
            {mode === 'register' ? '注册身份决定新账号的默认角色，注册后可完善资料' : activeTab.desc}
          </p>
        </div>

        {/* ============ 密码登录 ============ */}
        {mode === 'password' && (
          <form onSubmit={onSubmitPassword} className="flex flex-1 flex-col px-6 pt-5" noValidate>
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
                clearFieldErr('account')
              }}
              placeholder="请输入手机号"
              className={inputCls(fieldErr.account)}
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
                  clearFieldErr('password')
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

            {feedback}

            <button type="submit" disabled={pending} className={submitCls}>
              {pending ? '登录中…' : '登录'}
            </button>

            <DemoAccountsSection onFill={fillDemo} />
          </form>
        )}

        {/* ============ 验证码登录 ============ */}
        {mode === 'sms' && (
          <form onSubmit={onSubmitSms} className="flex flex-1 flex-col px-6 pt-5" noValidate>
            <label className="text-[13px] font-medium text-foreground" htmlFor="sms-phone">
              手机号
            </label>
            <input
              id="sms-phone"
              name="phone"
              type="tel"
              autoComplete="tel"
              inputMode="tel"
              value={account}
              onChange={e => {
                setAccount(e.target.value)
                clearFieldErr('account')
              }}
              placeholder="请输入手机号"
              className={inputCls(fieldErr.account)}
            />
            {fieldErr.account && (
              <p className="mt-1 text-[11px] text-destructive">{fieldErr.account}</p>
            )}

            <label className="mt-4 text-[13px] font-medium text-foreground" htmlFor="sms-code">
              验证码
            </label>
            <div className="mt-1.5 flex gap-2">
              <input
                id="sms-code"
                name="code"
                type="text"
                inputMode="numeric"
                autoComplete="one-time-code"
                maxLength={6}
                value={code}
                onChange={e => {
                  setCode(e.target.value)
                  clearFieldErr('code')
                }}
                placeholder="6 位验证码"
                className={cn(
                  'h-11 min-w-0 flex-1 rounded-lg border bg-card px-3 text-[15px] text-foreground',
                  'placeholder:text-muted-foreground/70 focus-visible:outline-none focus-visible:ring-2',
                  fieldErr.code
                    ? 'border-destructive focus-visible:ring-destructive/40'
                    : 'border-border focus-visible:ring-ring',
                )}
              />
              <button
                type="button"
                disabled={sendingCode || smsLeft > 0}
                onClick={() => void onSendCode('LOGIN', account, setSmsLeft)}
                className={cn(
                  'h-11 shrink-0 rounded-lg border px-3 text-[13px] font-medium transition-colors',
                  smsLeft > 0
                    ? 'border-border text-pl-sub'
                    : 'border-primary text-primary hover:bg-primary/5',
                  sendingCode && 'opacity-60',
                )}
              >
                {smsLeft > 0 ? `${smsLeft}s 后重发` : sendingCode ? '发送中…' : '获取验证码'}
              </button>
            </div>
            {fieldErr.code && <p className="mt-1 text-[11px] text-destructive">{fieldErr.code}</p>}
            <p className="mt-1.5 text-[11px] text-pl-sub">
              演示环境验证码固定为 <span className="font-mono">123456</span>
            </p>

            {feedback}

            <button type="submit" disabled={pending} className={submitCls}>
              {pending ? '登录中…' : '登录'}
            </button>

            <p className="mt-2.5 text-center text-[11px] text-pl-sub">
              未注册的手机号请先
              <button
                type="button"
                onClick={() => switchMode('register')}
                className="mx-0.5 font-medium text-primary hover:underline"
              >
                注册
              </button>
            </p>

            <DemoAccountsSection onFill={fillDemo} />
          </form>
        )}

        {/* ============ 注册 ============ */}
        {mode === 'register' && (
          <form onSubmit={onSubmitRegister} className="flex flex-1 flex-col px-6 pt-5" noValidate>
            <label className="text-[13px] font-medium text-foreground" htmlFor="reg-phone">
              手机号
            </label>
            <input
              id="reg-phone"
              name="phone"
              type="tel"
              autoComplete="tel"
              inputMode="tel"
              value={regPhone}
              onChange={e => {
                setRegPhone(e.target.value)
                clearFieldErr('regPhone')
              }}
              placeholder="用于登录与找回密码"
              className={inputCls(fieldErr.regPhone)}
            />
            {fieldErr.regPhone && (
              <p className="mt-1 text-[11px] text-destructive">{fieldErr.regPhone}</p>
            )}

            <label className="mt-4 text-[13px] font-medium text-foreground" htmlFor="reg-code">
              验证码
            </label>
            <div className="mt-1.5 flex gap-2">
              <input
                id="reg-code"
                name="code"
                type="text"
                inputMode="numeric"
                autoComplete="one-time-code"
                maxLength={6}
                value={regCode}
                onChange={e => {
                  setRegCode(e.target.value)
                  clearFieldErr('regCode')
                }}
                placeholder="6 位验证码"
                className={cn(
                  'h-11 min-w-0 flex-1 rounded-lg border bg-card px-3 text-[15px] text-foreground',
                  'placeholder:text-muted-foreground/70 focus-visible:outline-none focus-visible:ring-2',
                  fieldErr.regCode
                    ? 'border-destructive focus-visible:ring-destructive/40'
                    : 'border-border focus-visible:ring-ring',
                )}
              />
              <button
                type="button"
                disabled={sendingCode || regLeft > 0}
                onClick={() => void onSendCode('REGISTER', regPhone, setRegLeft)}
                className={cn(
                  'h-11 shrink-0 rounded-lg border px-3 text-[13px] font-medium transition-colors',
                  regLeft > 0
                    ? 'border-border text-pl-sub'
                    : 'border-primary text-primary hover:bg-primary/5',
                  sendingCode && 'opacity-60',
                )}
              >
                {regLeft > 0 ? `${regLeft}s 后重发` : sendingCode ? '发送中…' : '获取验证码'}
              </button>
            </div>
            {fieldErr.regCode && (
              <p className="mt-1 text-[11px] text-destructive">{fieldErr.regCode}</p>
            )}

            <label
              className="mt-4 text-[13px] font-medium text-foreground"
              htmlFor="reg-password"
            >
              设置密码
            </label>
            <input
              id="reg-password"
              name="password"
              type="password"
              autoComplete="new-password"
              value={regPassword}
              onChange={e => {
                setRegPassword(e.target.value)
                clearFieldErr('regPassword')
              }}
              placeholder="至少 6 位"
              className={inputCls(fieldErr.regPassword)}
            />
            {fieldErr.regPassword && (
              <p className="mt-1 text-[11px] text-destructive">{fieldErr.regPassword}</p>
            )}

            <p className="mt-2 text-[11px] text-pl-sub">
              演示环境验证码固定为 <span className="font-mono">123456</span>
              ；以「公司代表」身份注册将自动创建未认证公司，可体验企业认证流程。
            </p>

            {feedback}

            <button type="submit" disabled={pending} className={submitCls}>
              {pending ? '注册中…' : '注册并登录'}
            </button>

            <p className="mt-2.5 text-center text-[11px] text-pl-sub">
              已有账号？
              <button
                type="button"
                onClick={() => switchMode('password')}
                className="mx-0.5 font-medium text-primary hover:underline"
              >
                去登录
              </button>
            </p>
          </form>
        )}
      </div>
    </div>
  )
}

/** 演示账号一键填入（密码 / 验证码模式展示；原型演示需要，真实后端联调时同样保留） */
function DemoAccountsSection({ onFill }: { onFill: (role: LoginRole) => void }) {
  return (
    <section className="mb-6 mt-6 rounded-xl border border-dashed border-border bg-secondary/40 p-3.5">
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
              onClick={() => onFill(d.role)}
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
                <span className="block text-[13px] font-medium text-foreground">{d.label}</span>
                <span className="block pl-line-clamp-1 text-[11px] text-pl-sub">{d.note}</span>
              </span>
              <span className="shrink-0 text-right">
                <span className="block font-mono text-[12px] text-foreground">{d.account}</span>
                <span className="block font-mono text-[11px] text-pl-sub">{d.password}</span>
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
  )
}
