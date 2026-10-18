/**
 * 聘聊 · Step 3 T1 种子 SQL 生成器
 * =====================================================================
 * 为什么存在：
 *   演示种子（51 条职位 / 17+67 分类 / 7 份简历 / 9 条投递 / 8 个会话 …）的手抄量极大且
 *   极易与前端 mock 脱节。本脚本直接 import `src/api/mock/*`（运行时的真实数据源），
 *   一键生成 V4~V8 五个 Flyway 迁移，保证「后端种子 ≡ mock 语义」。
 *
 * 运行方式（仓库根目录，Node >= 22.7）：
 *   node --experimental-transform-types \
 *     --import "file:///<仓库根>/backend/scripts/seed-gen/register.mjs" \
 *     backend/scripts/seed-gen/generate.mjs
 *   （Windows PowerShell 下 --import 需用 file:/// 绝对路径）
 *
 * 约定：
 *   - 生成结果写入 backend/src/main/resources/db/migration/，文件头会注明「勿手改」；
 *   - 时间策略与 mock 对齐：职位/投递/面试相对 NOW(3) 生成（演示新鲜度）；
 *     HR 会话/消息沿用 mock 的固定基准 2026-09-21T10:00:00Z（mock 为 SSR 稳定性故意固定）；
 *   - 新增/调整演示数据后，改 mock 源文件并重跑本脚本，再重建本地库验证。
 * =====================================================================
 */
import { writeFileSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'

const REPO_ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..', '..')
const MIGRATION_DIR = path.join(REPO_ROOT, 'backend', 'src', 'main', 'resources', 'db', 'migration')
const mockUrl = (p) => pathToFileURL(path.join(REPO_ROOT, 'src', 'api', 'mock', p)).href

// ---------------------------------------------------------------- 基础工具

/** SQL 字符串字面量（MySQL 默认 sql_mode 下反斜杠为转义符，需双写；单引号双写转义） */
const sqlStr = (s) => (s == null ? 'NULL' : `'${String(s).replace(/\\/g, '\\\\').replace(/'/g, "''")}'`)
/** JSON 列：传对象/数组；null / undefined → NULL */
const sqlJson = (v) => (v == null ? 'NULL' : sqlStr(JSON.stringify(v)))
/** ISO 时间串 → 'YYYY-MM-DD HH:mm:ss.SSS'（UTC 字面量） */
const sqlTs = (iso) => (iso == null ? 'NULL' : sqlStr(iso.replace('T', ' ').replace('Z', '')))
/** 相对现在的时间表达式：分钟/小时/天的偏移（正数＝过去，负数＝未来） */
const ago = (n, unit) => `DATE_SUB(NOW(3), INTERVAL ${n} ${unit})`
const later = (n, unit) => `DATE_ADD(NOW(3), INTERVAL ${n} ${unit})`
const stableHash = (s) => { let h = 0; for (let i = 0; i < s.length; i++) h = (h * 31 + s.charCodeAt(i)) >>> 0; return h }

const BANNER = (title, note) =>
  `-- =====================================================================\n` +
  `-- ${title}\n` +
  `-- ⚠ 本文件由 backend/scripts/seed-gen/generate.mjs 生成，请勿手改；\n` +
  `--   改数据请改 src/api/mock/* 后重跑生成脚本（见脚本头部说明）。\n` +
  `${note}\n` +
  `-- =====================================================================\n\n`

// ---------------------------------------------------------------- 加载 mock 数据源

await import(mockUrl('bootstrap.ts')) // 初始化 hrJobRecords（求职者可见职位合成依赖）
const { jobs } = await import(mockUrl('jobs.ts'))
const { companies } = await import(mockUrl('companies.ts'))
const { cities } = await import(mockUrl('dict.ts'))
const catMod = await import(mockUrl('categories.ts'))
const { CANDIDATES } = await import(mockUrl('candidates.ts'))
const resumeMod = await import(mockUrl('resumes.ts'))
const appMod = await import(mockUrl('applications.ts'))
const hrConvMod = await import(mockUrl('hrConversations.ts'))

// 触发投递/面试/HR 会话的惰性种子（与前端首屏语义一致）
appMod.seekerApplicationViews('u-seeker-1')
appMod.companyApplicationViews('co-acgiw7')
hrConvMod.seedHrConversations()

// ---------------------------------------------------------------- 常量（与 mock 对齐）

const YUNSHAN_ID = 'co-acgiw7'
const HR_CHEN_ID = 'hr-x2270n'
/** 与 V2 种子 / PasswordHashTest.SEED_HASH 一致（演示密码 123456） */
const SEED_PASSWORD_HASH = '$2a$10$.az91kSHYTOcUZerCHPwoe0jN3eUX8oQ.25Hps9C2jX0bZ1/8tr4m'
const DEFAULT_WELFARE = ['五险一金', '带薪年假', '年度体检', '弹性工作']
const DEFAULT_RESP = `1. 负责业务系统的设计、开发与迭代维护；
2. 参与技术方案评审，保障代码质量与交付进度；
3. 与产品、设计、测试紧密协作，持续优化用户体验。`
const DEFAULT_REQ = `1. 本科及以上学历，计算机相关专业优先；
2. 具备扎实的编程基础与良好的工程习惯；
3. 有较强的责任心与团队协作意识。`
const companyJobs = jobs.filter((j) => j.company.id === YUNSHAN_ID)

const written = []

// ---------------------------------------------------------------- V4 公司字典 / 城市 / 分类

{
  const rows = companies
    .filter((c) => c.id !== YUNSHAN_ID) // 云杉科技已在 V2 种子
    .map((c) =>
      `(${sqlStr(c.id)}, ${sqlStr(c.name)}, NULL, NULL, ${sqlStr(c.industry)}, ${sqlStr(c.stage)}, ` +
      `${sqlStr(c.size)}, NULL, ${c.verified ? 1 : 0}, ${sqlStr(c.status)}, '2026-09-01 10:00:00.000', '2026-09-01 10:00:00.000')`)
  const companiesSql =
    `INSERT INTO companies (id, name, logo_url, intro, industry, stage, size, website, verified, status, created_at, updated_at) VALUES\n` +
    rows.join(',\n') + ';\n\n'

  const cityRows = cities.map(
    (c) => `(${sqlStr(c.id)}, ${sqlStr(c.code)}, ${sqlStr(c.name)}, NULL, ${c.hot ? 1 : 0}, ${c.sort})`)
  const citiesSql = `INSERT INTO cities (id, code, name, province, hot, sort) VALUES\n` + cityRows.join(',\n') + ';\n\n'

  const catRows = []
  catMod.categoryTree.forEach((cat) => {
    catRows.push(
      `(${sqlStr(cat.id)}, NULL, ${sqlStr(cat.name)}, ${cat.sort}, NULL, ${sqlStr((catMod.RESP_TEMPLATE[cat.name] ?? []).join('\n'))})`)
    cat.children.forEach((sub) => {
      catRows.push(`(${sqlStr(sub.id)}, ${sqlStr(cat.id)}, ${sqlStr(sub.name)}, ${sub.sort}, NULL, NULL)`)
    })
  })
  const categoriesSql =
    `INSERT INTO categories (id, parent_id, name, sort, icon, responsibilities_template) VALUES\n` +
    catRows.join(',\n') + ';\n'

  writeFileSync(path.join(MIGRATION_DIR, 'V4__seed_dicts_companies.sql'),
    BANNER('V4 种子：公司字典 / 城市 / 分类树',
      `-- 来源：src/api/mock/companies.ts（16 家；云杉科技由 V2 落库）、dict.ts（9 城）、categories.ts（17 一级 + 67 二级）`) +
    companiesSql + citiesSql + categoriesSql)
  written.push(`V4__seed_dicts_companies.sql（companies ${rows.length} / cities ${cityRows.length} / categories ${catRows.length}）`)
}

// ---------------------------------------------------------------- V5 职位（51 条；13 条初始 CLOSED，求职者可见 38）

{
  const rows = jobs.map((j) => {
    const closed = stableHash(j.id) % 4 === 0
    const status = closed ? 'CLOSED' : 'OPEN'
    const pub = ago(j.days, 'DAY')
    return (
      `(${sqlStr(j.id)}, ${sqlStr(j.company.id)}, ${sqlStr(j.hr.id)}, ${sqlStr(j.hr.name)}, ${sqlStr(j.hr.title)}, ` +
      `${sqlStr(j.title)}, ${j.salaryMin}, ${j.salaryMax}, ${sqlStr(j.city)}, ${sqlStr(j.area)}, ${sqlStr(j.exp)}, ${sqlStr(j.edu)}, ` +
      `${sqlStr(j.cat)}, ${sqlStr(j.sub)}, ${sqlJson(j.tags)}, ${sqlStr(DEFAULT_RESP)}, ${sqlStr(DEFAULT_REQ)}, ${sqlJson(DEFAULT_WELFARE)}, ` +
      `${sqlStr(status)}, ${pub}, ${j.viewCount}, ${j.applyCount}, ${pub}, ${pub})`
    )
  })
  const sql =
    `INSERT INTO jobs (id, company_id, hr_id, hr_name, hr_title, title, salary_min, salary_max, city, area, exp, edu,\n` +
    `                 cat, sub, tags, responsibilities, requirements, welfare, status, published_at, view_count, apply_count, created_at, updated_at) VALUES\n` +
    rows.join(',\n') + ';\n'
  writeFileSync(path.join(MIGRATION_DIR, 'V5__seed_jobs.sql'),
    BANNER('V5 种子：职位 51 条',
      `-- 来源：src/api/mock/jobs.ts（JOBS_RAW→jobs）；状态与 mock initHrJobs 相同（stableHash(id)%4==0 → CLOSED，
-- 即求职者可见 38 / 云杉 7）；published_at = NOW - mock days 天；浏览/投递数为 mock 的确定性公式值`) + sql)
  written.push(`V5__seed_jobs.sql（jobs ${rows.length}，可见 ${jobs.filter((j) => stableHash(j.id) % 4 !== 0).length}）`)
}

// ---------------------------------------------------------------- V6 候选人账号与简历

{
  const findCatOfSub = (sub) =>
    Object.entries(catMod.CATEGORY_TREE_RAW).find(([, subs]) => subs.includes(sub))?.[0] ?? null
  const resumeOf = (userId) => resumeMod.MOCK_RESUMES.find((r) => r.userId === userId)

  const userRows = CANDIDATES.map((c, i) => {
    const phone = resumeOf(c.userId)?.content?.basics?.phone ?? `1380000110${i + 1}`
    return `(${sqlStr(c.userId)}, ${sqlStr(phone)}, NULL, ${sqlStr(SEED_PASSWORD_HASH)}, 'JOB_SEEKER', 'ACTIVE', NULL, '2026-09-04 10:00:00.000', '2026-09-04 10:00:00.000')`
  })
  const profileRows = CANDIDATES.map((c, i) =>
    `('up-cand-${i + 1}', ${sqlStr(c.userId)}, ${sqlStr(c.name)}, NULL, NULL, NULL, ${c.workYears}, ${sqlStr(c.edu)}, ` +
    `${sqlStr(findCatOfSub(c.expect))}, ${sqlStr(c.expect)}, ${sqlStr(c.city)}, NULL, NULL, 'ACTIVE', ${sqlStr(`resume-${c.userId}`)}, ` +
    `'2026-09-04 10:00:00.000', '2026-09-04 10:00:00.000')`)
  const resumeRows = resumeMod.MOCK_RESUMES.map((r) =>
    `(${sqlStr(r.id)}, ${sqlStr(r.userId)}, ${sqlStr(r.name)}, ${sqlJson(r.content)}, NULL, ${r.isDefault ? 1 : 0}, ` +
    `${sqlTs(r.createdAt) ?? "'2026-09-01 09:00:00.000'"}, ${sqlTs(r.updatedAt) ?? "'2026-09-07 09:00:00.000'"})`)

  const sql =
    `INSERT INTO users (id, phone, email, password_hash, role, status, last_login_at, created_at, updated_at) VALUES\n` +
    userRows.join(',\n') + ';\n\n' +
    `INSERT INTO user_profiles (id, user_id, name, avatar_url, gender, birth_date, work_years, edu, expect_cat, expect_sub, expect_city, expect_salary_min, expect_salary_max, seeking_status, default_resume_id, created_at, updated_at) VALUES\n` +
    profileRows.join(',\n') + ';\n\n' +
    `INSERT INTO resumes (id, user_id, name, content, attachment_url, is_default, created_at, updated_at) VALUES\n` +
    resumeRows.join(',\n') + ';\n'
  writeFileSync(path.join(MIGRATION_DIR, 'V6__seed_candidates_resumes.sql'),
    BANNER('V6 种子：候选人账号（6）与简历（7）',
      `-- 来源：src/api/mock/candidates.ts + resumes.ts；手机号取自简历 basics.phone；
-- 简历 content JSON 原文照搬（人才筛选 §4.5/§4.6 的断言数据源：React→林小满、自动化测试→沈禾、COBOL→空）；
-- 候选人默认密码同为 123456（哈希与 V2 一致，可登录但非演示主账号）`) + sql)
  written.push(`V6__seed_candidates_resumes.sql（users +${userRows.length}、profiles ${profileRows.length}、resumes ${resumeRows.length}）`)
}

// ---------------------------------------------------------------- V7 投递与面试

{
  const HR_STATUSES = ['SUBMITTED', 'VIEWED', 'SUBMITTED', 'INTERVIEW', 'REJECTED', 'OFFER']
  const appRows = []
  CANDIDATES.forEach((cand, i) => {
    const job = companyJobs[i % companyJobs.length]
    const cover = i === 0 ? sqlStr('您好，我对该岗位非常感兴趣，期待与您沟通。') : 'NULL'
    appRows.push(
      `('app-hr-0${i + 1}', ${sqlStr(cand.userId)}, ${sqlStr(job.id)}, ${sqlStr(YUNSHAN_ID)}, ${sqlStr(`resume-${cand.userId}`)}, ` +
      `${sqlStr(HR_STATUSES[i])}, ${cover}, ${ago((i + 1) * 8, 'HOUR')}, ${ago((i + 1) * 4, 'HOUR')})`)
  })
  // 求职者（u-seeker-1）3 条，均非云杉（与 mock applications.ensureSeed 的 pick 结果一致：job-9 / job-18 / job-10)
  const seekerApps = [
    { jobId: 'job-9', status: 'INTERVIEW', companyId: jobs.find((j) => j.id === 'job-9').company.id },
    { jobId: 'job-18', status: 'SUBMITTED', companyId: jobs.find((j) => j.id === 'job-18').company.id },
    { jobId: 'job-10', status: 'VIEWED', companyId: jobs.find((j) => j.id === 'job-10').company.id },
  ]
  seekerApps.forEach((s, i) => {
    appRows.push(
      `('app-seek-0${i + 1}', 'u-seeker-1', ${sqlStr(s.jobId)}, ${sqlStr(s.companyId)}, 'resume-seeker', ${sqlStr(s.status)}, NULL, ` +
      `${ago((i + 2) * 24, 'HOUR')}, ${ago((i + 1) * 6, 'HOUR')})`)
  })

  const intRows = [
    `('int-hr-01', 'app-hr-04', ${sqlStr(YUNSHAN_ID)}, ${later(2, 'DAY')}, 'VIDEO', NULL, 'https://meeting.pinliao.dev/demo-room', ` +
      `${sqlStr('请提前 5 分钟进入会议，准备好项目介绍。')}, 'PENDING', ${ago(2, 'HOUR')}, ${ago(2, 'HOUR')})`,
    `('int-hr-02', 'app-hr-06', ${sqlStr(YUNSHAN_ID)}, ${ago(3, 'DAY')}, 'ONSITE', ${sqlStr('上海市浦东新区云杉大厦 12F 会议室 A')}, NULL, NULL, ` +
      `'COMPLETED', ${ago(6, 'DAY')}, ${ago(3, 'DAY')})`,
    `('int-seek-01', 'app-seek-01', ${sqlStr(seekerApps[0].companyId)}, ${later(74, 'HOUR')}, 'VIDEO', NULL, 'https://meeting.pinliao.dev/room-6088', ` +
      `${sqlStr('一面预计 45 分钟，请准备一段自我介绍。')}, 'PENDING', ${ago(6, 'HOUR')}, ${ago(6, 'HOUR')})`,
  ]

  const sql =
    `INSERT INTO applications (id, user_id, job_id, company_id, resume_id, status, cover_letter, created_at, updated_at) VALUES\n` +
    appRows.join(',\n') + ';\n\n' +
    `INSERT INTO interviews (id, application_id, company_id, scheduled_at, format, location, online_url, note, status, created_at, updated_at) VALUES\n` +
    intRows.join(',\n') + ';\n'
  writeFileSync(path.join(MIGRATION_DIR, 'V7__seed_applications_interviews.sql'),
    BANNER('V7 种子：投递（9）与面试（3）',
      `-- 来源：src/api/mock/applications.ts（ensureSeed）
-- HR 侧 6 条覆盖全部状态枚举；求职者 3 条（job-9 / job-18 / job-10，均非云杉）；
-- 面试：int-hr-01（赵之衡·待确认 VIDEO）/ int-hr-02（何知遥·已完成 ONSITE）/ int-seek-01（林晓·待确认 VIDEO）`) + sql)
  written.push(`V7__seed_applications_interviews.sql（applications ${appRows.length}、interviews ${intRows.length}）`)
}

// ---------------------------------------------------------------- V8 会话与消息

{
  const convRows = []
  const msgRows = []

  // HR 侧 6 会话：直接取 mock 运行结果（seedHrConversations 后），保证 id 序列 / 未读分布 / 文案逐字一致
  for (const c of hrConvMod.hrConversations) {
    convRows.push(
      `(${sqlStr(c.id)}, ${sqlStr(c.jobId)}, ${sqlStr(c.userId)}, ${sqlStr(c.hrId)}, ${sqlStr(c.companyId)}, ` +
      `${sqlTs(c.lastMessageAt)}, ${c.userUnreadCount}, ${c.hrUnreadCount}, ${c.closedByHr ? 1 : 0}, ${sqlTs(c.createdAt)})`)
  }
  for (const msgs of Object.values(hrConvMod.hrMessagesByConv)) {
    for (const m of msgs) {
      msgRows.push(
        `(${sqlStr(m.id)}, ${sqlStr(m.conversationId)}, ${sqlStr(YUNSHAN_ID)}, ${sqlStr(m.fromType)}, ` +
        `${m.fromUserId ? sqlStr(m.fromUserId) : 'NULL'}, ${m.fromHrId ? sqlStr(m.fromHrId) : 'NULL'}, ${sqlStr(m.type)}, ` +
        `${sqlStr(m.content)}, ${sqlTs(m.createdAt)}, ${m.readByUser ? 1 : 0}, ${m.readByHr ? 1 : 0})`)
    }
  }

  // 求职者侧 2 会话（mock conversations.seedConversations；company/hr 用真实哈希 id）
  const seekerConv = [
    {
      id: 'conv-seed-003', jobId: 'job-3', hrName: '李女士', companyId: 'co-fm70w', hrId: 'hr-2o4rqu',
      last: ago(25, 'MINUTE'),
      msgs: [
        { id: 'msg-3-1', type: 'SYSTEM', fromType: 'SYSTEM', content: '你已向 李女士 发起沟通', at: ago(25, 'MINUTE'), readByUser: 1 },
        { id: 'msg-3-2', type: 'TEXT', fromType: 'USER', content: '您好，我对「产品经理（B端）」很感兴趣，方便聊聊吗？', at: ago(24, 'MINUTE'), readByUser: 1 },
        { id: 'msg-3-3', type: 'TEXT', fromType: 'HR', content: '您好！看了您的简历，B 端经验很匹配，方便这周找个时间电话聊聊吗？', at: ago(23, 'MINUTE'), readByUser: 0 },
      ],
    },
    {
      id: 'conv-seed-008', jobId: 'job-8', hrName: '孙先生', companyId: 'co-dekwg9', hrId: 'hr-1f2x4s9',
      last: ago(3, 'HOUR'),
      msgs: [
        { id: 'msg-8-1', type: 'TEXT', fromType: 'HR', content: '您好，我是极光量子的孙先生，看到您的简历，想和您聊聊 Go 后端的机会。', at: ago(3, 'HOUR'), readByUser: 0 },
      ],
    },
  ]
  for (const sc of seekerConv) {
    convRows.push(
      `(${sqlStr(sc.id)}, ${sqlStr(sc.jobId)}, 'u-seeker-1', ${sqlStr(sc.hrId)}, ${sqlStr(sc.companyId)}, ` +
      `${sc.last}, 1, 0, 0, ${sc.last})`)
    for (const m of sc.msgs) {
      msgRows.push(
        `(${sqlStr(m.id)}, ${sqlStr(sc.id)}, ${sqlStr(sc.companyId)}, ${sqlStr(m.fromType)}, ` +
        `${m.fromType === 'USER' ? "'u-seeker-1'" : 'NULL'}, ${m.fromType === 'HR' ? sqlStr(sc.hrId) : 'NULL'}, ${sqlStr(m.type)}, ` +
        `${sqlStr(m.content)}, ${m.at}, ${m.readByUser}, 1)`)
    }
  }

  const sql =
    `INSERT INTO conversations (id, job_id, user_id, hr_id, company_id, last_message_at, user_unread_count, hr_unread_count, closed_by_hr, created_at) VALUES\n` +
    convRows.join(',\n') + ';\n\n' +
    `INSERT INTO messages (id, conversation_id, company_id, from_type, from_user_id, from_hr_id, type, content, created_at, read_by_user, read_by_hr) VALUES\n` +
    msgRows.join(',\n') + ';\n'
  writeFileSync(path.join(MIGRATION_DIR, 'V8__seed_conversations_messages.sql'),
    BANNER('V8 种子：会话（8）与消息（16）',
      `-- 来源：src/api/mock/hrConversations.ts（HR 侧 6 会话 / 未读 4：2+1+1；id 序列与 mock 一致）
--       + conversations.ts（求职者侧 2 会话；companyId / hrId 由 mock 占位值替换为稳定哈希 id）
-- 时间：HR 侧沿用 mock 固定基准 2026-09-21T10:00:00Z；求职者侧相对 NOW(3)`) + sql)
  written.push(`V8__seed_conversations_messages.sql（conversations ${convRows.length}、messages ${msgRows.length}）`)
}

console.log('生成完成：')
for (const w of written) console.log(' -', w)
