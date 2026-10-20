# 踩坑 · 前端（§6.1~§6.7）

> 摘自 `docs/AI-CONTINUE.md` §6（2026-10-20 拆分，编号未变）。索引见 [`../AI-CONTINUE.md`](../AI-CONTINUE.md) §6。

---
### 6.1 循环依赖：`jobs.ts` ←→ `hrJobs.ts`

**现象**：页面 HTTP 500，报 `Cannot access 'jobs' before initialization`
或 `baseJobs is not defined`。

**原因**：`jobs.ts` 需要 `hrJobRecords` 来合成「求职者可见职位」，
而 `hrJobs.ts` 又需要 `jobs.ts` 的基础职位做初始数据 —— 直接互相 import 成环，
模块初始化期取到 `undefined`。

**已采用的解法**（唯一同时满足「无环 + 无 require + 初始化顺序确定」）：
1. `hrJobs.ts` **不 import** `jobs.ts`，导出 `initHrJobs(baseJobs)` 惰性填充；
2. 新增 `src/api/mock/bootstrap.ts`，由它按序调用 `initHrJobs(jobs)`；
3. `api/jobs.ts` 顶部 `import './mock/bootstrap'` 触发装配（此处 `jobs.ts` 已求值完成，
   不会形成再入）。

**已否决的两种写法**（不要再试）：
- 在 `jobs.ts` 里 `require('./seekerJobs')` 延迟加载 → 被 ESLint
  `@typescript-eslint/no-require-imports` 拦下（本项目设为 error）。
- 从 `jobs.ts` 顶部 import bootstrap → 造成 bootstrap 再入 jobs，`baseJobs` 仍是 `undefined`。

### 6.2 Zustand 选择器返回新对象 → 无限重渲染

**现象**：页面崩成 500；控制台报
`The result of getSnapshot should be cached to avoid an infinite loop`
与 `Maximum update depth exceeded`。

**原因**：把返回**新建对象**的函数直接当选择器：
```ts
const current = useHrStore(s => s.pageStack.at(-1) ?? { type: 'job-list' })  // ❌
```
zustand 用 `Object.is` 比较快照，每次都是新对象 → 判定每次都变 → 无限重渲染。

**解法**：
```ts
// store 里导出「栈为空时返回同一模块级常量引用」的 hook
export function useHrCurrentPage(): HrPage {
  const top = useHrStore(s => s.pageStack[s.pageStack.length - 1])
  const tab = useHrStore(s => s.tab)
  if (top) return top
  return tab === 'messages' ? MESSAGES_ROOT : JOB_LIST_ROOT  // 常量
}
```
**规则**：选择器只允许返回**稳定引用或原始值**；需要派生对象时用 `useMemo` 或在组件里组装。

### 6.3 误杀用户浏览器（严重，已根治）

**曾经的做法**（❌ 严禁再用）：
```powershell
Get-Process chrome -ErrorAction SilentlyContinue | Stop-Process -Force
```
这是**按进程名全杀**，会把用户正在使用的 Chrome 全部标签页杀掉，
用户会看到标签页崩溃报错 —— 而且很容易被误判成「代码坏了」。

**正确做法**：只终止自己 spawn 的进程树。
```js
const { killChromeTree } = require('./.zscripts/kill-chrome-safe.cjs')
const chrome = spawn(CHROME, [...], { stdio: 'ignore' })
// ...
killChromeTree(chrome.pid)   // taskkill /T /F，仅该 PID 树
```

### 6.4 身份校验必须在写入登录态之前

**现象**：用户以「公司代表」身份登录求职者账号，结果**直接进了求职者端**，
预期的「身份不匹配」提示永远看不到。

**原因**：先 `login()` 写入 auth store，再由页面判断角色不符并 `setMismatch` ——
但 store 一变，`RootGate` 立刻卸载登录页去渲染工作台，提示来不及渲染。

**解法**：把期望身份下沉到 service 层，**不匹配就抛错、不写任何登录态**：
```ts
await login(account, password, expectRole)   // 不符时抛 RoleMismatchError
```
`RoleMismatchError` 定义在 `src/api/auth.ts`。**规则**：任何「登录后校验」都要前置到写入状态之前。

### 6.5 手写 `useEffect + setState` 取数会触发 React Compiler 告警

本项目开启 React Compiler，`react-hooks/set-state-in-effect` 是 **error** 级。
用 effect 手写取数（`useEffect(() => { reload() }, [...])`）会报错。

**解法**：用 **TanStack Query**（Provider 已就位）：
```ts
const { data, isPending } = useQuery({
  queryKey: ['hr', 'jobs', companyId, filters.status, dataVersion],
  queryFn: () => listMyJobs({ companyId, status: filters.status }),
  enabled: Boolean(companyId),
})
// 变更后：queryClient.invalidateQueries({ queryKey: ['hr', 'jobs'] })
```
需要「取数 + loading」时不要手工维护 `loading` 状态，交给 `isPending`。
`dataVersion`（store 里的自增计数）用于让 Mock 数据变更后触发重取。

### 6.6 同一组件既取数又 setState 会被告警 → 拆组件

编辑页需要「先取原职位、回填表单」。若在同一个组件里 `useEffect` 取数并
`setState` 回填，仍会触发 §6.5 的告警。
**解法**：拆成 `HrJobEditorPage`（只负责取数）+ `EditorForm`（只负责渲染表单）。

### 6.7 其他小坑

- **Mock 数据在内存**，页面刷新即重置。跨端联动测试**必须用应用内退出登录切换账号**，
  不能 `Page.navigate` 刷新，否则新建的职位会丢。
- **新建职位 id 用递增计数**，不要用 `Date.now()` / `Math.random()` 生成 ——
  会造成 SSR/CSR 渲染不一致。
- **Mock 职位状态用稳定哈希**（`stableHash(id) % 4`）而非随机，同理。
- **HR 关闭某职位后，该职位对求职者端应隐藏**（`listSeekerJobs` 已实现）；
  修改职位内容也要覆盖到求职者端（即使状态未变），否则改标题/薪资后求职者端仍是旧内容。
- **重新登录会落回上次停留的页签**：`pinliao` / `hr` 两个 store 不随登出重置。
  登录成功处必须 `setTab('jobs')`（`LoginPage` 的 `afterAuthSuccess` 已统一处理）；
  新增登录入口（如新登录方式）时不要漏掉。
- **登录页有三个模式页签**（密码 / 验证码 / 注册），按文本找「登录」按钮会命中页签。
  自动化脚本用 `form button[type=submit]` 定位提交按钮（`tests/e2e-verify.cjs` 的
  `clickSubmit`）；覆盖层页面先点 `aria-label="返回"` 退出再操作底部 Tab
  （`clickAria`），否则点击会静默落空（历史上就是这么丢过断言覆盖的）。
- **HR 新建职位的公司归属**：`mock/hrJobs.ts` 已从硬编码云杉改为按登录上下文注入
  （注册新公司的 HR 发布职位会挂到自己公司名下）；改动相关逻辑时保持这一点。
- **Mock 验证码是内存态**：固定 `123456`、按手机号 60s 限流；刷新页面即重置。
- **覆盖层内做「不出现」断言要限定取数范围**：`document.body.innerText` 会包含被覆盖层
  遮挡的**根视图**文本（如候选人列表仍在 DOM 中），「某某不出现」类断言会被背景文本
  误判。人才筛选段落改用顶层 `.pl-page-enter` 的 `innerText` 取数
  （`tests/e2e-verify.cjs` 的 `matchOverlayText`），写新覆盖层断言时沿用此方式。
- **人才筛选（候选人匹配）依赖简历正文数据**：数据源为 `src/api/mock/resumes.ts`
  （按 userId 关联，简历 id 约定 `resume-{userId}`）。新增候选人 / 求职者投递若没有
  对应简历正文，不会出现在匹配结果中——这是预期行为（先有简历数据才有可筛内容）。
