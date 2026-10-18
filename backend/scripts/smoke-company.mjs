// 聘聊 · 公司模块接口冒烟（Step 3 T5；契约 docs/api-endpoints.md §5.1~§5.6，约 24 项）
// 用法：后端启动后运行 `node scripts/smoke-company.mjs`（可用 PINLIAO_BASE 覆盖地址）
// 前置：Flyway V1~V8 已迁移；MySQL + Redis 运行中；演示账号 13800000001（求职者）/ 13800000002（HR），密码 123456
// 说明：
//  - 公开公司详情 / 公司职位用云杉科技（7 条可见职位）验证，不改种子公司资料；
//  - 资料编辑 / 企业认证用临时注册 HR 的新公司验证（co-reg-* / u-reg-* 已被 verify-seed 排除）；
//  - 临时发布的 job-new-* 在 finally 中软删，保证「游客可见 38 条」基线不被破坏。
const base = process.env.PINLIAO_BASE || 'http://localhost:8080';

let passed = 0;
let failed = 0;
function check(label, ok, detail = '') {
  if (ok) {
    passed += 1;
    console.log('  \u2714 ' + label + (detail ? ' | ' + detail : ''));
  } else {
    failed += 1;
    console.log('  \u2718 ' + label + (detail ? ' | ' + detail : ''));
  }
}

async function http(method, path, { token, headers = {}, body } = {}) {
  const h = { Accept: 'application/json', ...headers };
  if (token) h.Authorization = 'Bearer ' + token;
  if (body !== undefined) h['Content-Type'] = 'application/json';
  const res = await fetch(base + path, {
    method,
    headers: h,
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const text = await res.text();
  let json = null;
  try {
    json = JSON.parse(text);
  } catch {
    /* 空响应 */
  }
  return { status: res.status, headers: res.headers, json, text };
}
const GET = (path, opts) => http('GET', path, opts);
const POST = (path, opts) => http('POST', path, opts);
const PATCH = (path, opts) => http('PATCH', path, opts);
const DELETE = (path, opts) => http('DELETE', path, opts);
const dataOf = (r) => (r.json && typeof r.json === 'object' ? r.json.data : undefined) || {};
const codeOf = (r) => (r.json && typeof r.json === 'object' ? r.json.code : undefined);
const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

async function login(account) {
  const r = await POST('/api/v1/auth/login/password', {
    body: { account, password: '123456' },
  });
  return dataOf(r).accessToken;
}

(async () => {
  const seekerToken = await login('13800000001');
  const hrToken = await login('13800000002');
  check('演示账号登录（求职者 / HR）', typeof seekerToken === 'string' && typeof hrToken === 'string');

  // ============================== §5.1 / §5.2 公开读 ==============================
  console.log('== §5.1 / §5.2 公开读 ==');

  let r = await GET('/api/v1/companies/co-acgiw7');
  let d = dataOf(r);
  check('匿名公司详情 200 / 云杉科技 / 已认证',
    r.status === 200 && codeOf(r) === 0 && d.id === 'co-acgiw7' && d.name === '云杉科技'
    && d.verified === true && d.status === 'ACTIVE',
    `name=${d.name} verified=${d.verified}`);

  r = await GET('/api/v1/companies/not-exist-company');
  check('公司详情不存在 → 404 + 40410', r.status === 404 && codeOf(r) === 40410,
    `HTTP ${r.status} code=${codeOf(r)}`);

  r = await GET('/api/v1/companies/co-acgiw7/jobs?limit=100');
  d = dataOf(r);
  check('公司职位 7 条且均属于云杉科技',
    r.status === 200 && (d.list || []).length === 7
    && d.list.every((job) => job.company && job.company.id === 'co-acgiw7'),
    `count=${(d.list || []).length}`);

  const p1 = await GET('/api/v1/companies/co-acgiw7/jobs?limit=3');
  const cursor = dataOf(p1).pagination?.nextCursor;
  const p2 = await GET('/api/v1/companies/co-acgiw7/jobs?limit=3&cursor=' + encodeURIComponent(cursor || ''));
  const merged = [...(dataOf(p1).list || []).map((j) => j.id), ...(dataOf(p2).list || []).map((j) => j.id)];
  check('公司职位游标分页：前两页 6 条不重不漏',
    (dataOf(p1).list || []).length === 3 && (dataOf(p2).list || []).length === 3
    && merged.length === 6 && new Set(merged).size === 6
    && (dataOf(p1).pagination?.total === 7),
    `page1=${(dataOf(p1).list || []).length} page2=${(dataOf(p2).list || []).length}`);

  r = await GET('/api/v1/companies/not-exist-company/jobs');
  check('不存在公司的职位 → 404 + 40410', r.status === 404 && codeOf(r) === 40410,
    `HTTP ${r.status} code=${codeOf(r)}`);

  // ============================== §5.3 我的公司 ==============================
  console.log('== §5.3 我的公司 ==');

  r = await GET('/api/v1/company/me');
  check('未登录读取我的公司 → 401 + 40101', r.status === 401 && codeOf(r) === 40101,
    `HTTP ${r.status} code=${codeOf(r)}`);

  r = await GET('/api/v1/company/me', { token: hrToken });
  d = dataOf(r);
  check('HR 读取我的公司 → 云杉科技',
    r.status === 200 && codeOf(r) === 0 && d.id === 'co-acgiw7' && d.name === '云杉科技',
    `company=${d.name}`);

  r = await GET('/api/v1/company/me', { token: seekerToken });
  check('求职者读取我的公司 → 403 + 40301', r.status === 403 && codeOf(r) === 40301,
    `HTTP ${r.status} code=${codeOf(r)}`);

  // ============================== §5.4 / §5.5 资料与成员 ==============================
  console.log('== §5.4 / §5.5 资料编辑与成员 ==');

  // 临时 HR（新公司，ADMIN 成员）用于写接口；产物前缀已被 verify-seed 排除
  const tail = String(Date.now()).slice(-8);
  const phone = '137' + tail;
  await POST('/api/v1/auth/sms/code', { body: { phone, scene: 'REGISTER' } });
  const reg = await POST('/api/v1/auth/register', {
    body: { phone, code: '123456', password: '123456', role: 'HR' },
  });
  const regData = dataOf(reg);
  const tempToken = regData.accessToken;
  const tempCompanyId = regData.company?.id;
  const tempName = regData.company?.name;
  check('临时 HR 注册成功并自动建未认证公司',
    reg.status === 200 && typeof tempToken === 'string' && tempCompanyId?.startsWith('co-reg-')
    && regData.company?.verified === false && regData.member?.role === 'ADMIN',
    `company=${tempName} member=${regData.member?.role}`);

  r = await PATCH('/api/v1/company/me', { token: seekerToken, body: { name: '越权改名' } });
  check('求职者更新公司 → 403 + 40301', r.status === 403 && codeOf(r) === 40301,
    `HTTP ${r.status} code=${codeOf(r)}`);

  r = await PATCH('/api/v1/company/me', { token: tempToken, body: { name: 'A' } });
  check('公司名称过短 → 422 + 42210', r.status === 422 && codeOf(r) === 42210,
    `HTTP ${r.status} code=${codeOf(r)}`);

  const newName = '冒烟企业-' + tail.slice(-4);
  r = await PATCH('/api/v1/company/me', {
    token: tempToken,
    body: {
      name: '  ' + newName + '  ',
      intro: '公司模块冒烟测试临时企业。',
      industry: '企业服务',
      stage: '未融资',
      size: '1-19人',
      website: 'https://smoke.pinliao.dev',
    },
  });
  d = dataOf(r);
  check('更新公司资料：名称 trim / 字段生效',
    r.status === 200 && codeOf(r) === 0 && d.name === newName
    && d.intro === '公司模块冒烟测试临时企业。' && d.website === 'https://smoke.pinliao.dev',
    `name=${d.name}`);

  r = await GET('/api/v1/companies/' + tempCompanyId);
  check('公开详情即时读到新公司名', r.status === 200 && dataOf(r).name === newName,
    `name=${dataOf(r).name}`);

  r = await GET('/api/v1/company/members', { token: hrToken });
  d = dataOf(r);
  check('云杉科技成员列表：1 名 ADMIN 带 user / hrProfile',
    r.status === 200 && Array.isArray(d) && d.length === 1 && d[0].id === 'cm-1'
    && d[0].role === 'ADMIN' && d[0].user?.phone === '13800000002'
    && d[0].hrProfile?.name === '陈女士',
    `members=${Array.isArray(d) ? d.length : 'n/a'}`);

  r = await GET('/api/v1/company/members', { token: seekerToken });
  check('求职者读取成员列表 → 403 + 40301', r.status === 403 && codeOf(r) === 40301,
    `HTTP ${r.status} code=${codeOf(r)}`);

  r = await GET('/api/v1/company/members?tmp=' + tail, { token: tempToken });
  check('新公司成员列表：注册自动建 ADMIN', r.status === 200 && Array.isArray(dataOf(r))
    && dataOf(r).length === 1 && dataOf(r)[0].role === 'ADMIN', `members=${dataOf(r).length}`);

  // 发布一个临时职位，验证「改名同步职位公司名」（finally 软删，保持可见 38 条）
  let jobId = '';
  try {
    r = await POST('/api/v1/jobs', {
      token: tempToken,
      body: {
        title: '冒烟测试 · 公司模块',
        salaryMin: 18,
        salaryMax: 30,
        city: '上海',
        area: '浦东新区',
        exp: '3-5年',
        edu: '本科',
        cat: '互联网',
        sub: '前端开发',
        tags: ['React'],
        responsibilities: '公司模块冒烟测试。',
        requirements: '无。',
        welfare: ['五险一金'],
      },
    });
    jobId = dataOf(r).id;
    check('临时 HR 发布职位成功', r.status === 200 && typeof jobId === 'string' && jobId.startsWith('job-new-'),
      `job=${jobId}`);

    r = await GET('/api/v1/companies/' + tempCompanyId + '/jobs?limit=100');
    d = dataOf(r);
    check('公司职位接口使用最新公司名（改名同步）',
      r.status === 200 && (d.list || []).length === 1 && d.list[0].id === jobId
      && d.list[0].company?.name === newName,
      `name=${d.list?.[0]?.company?.name}`);
  } finally {
    if (jobId) {
      try {
        await DELETE('/api/v1/jobs/' + jobId, { token: tempToken });
      } catch {
        /* 清理失败不覆盖主断言；下面单独检查 */
      }
    }
  }

  if (jobId) {
    r = await GET('/api/v1/companies/' + tempCompanyId + '/jobs?limit=100');
    check('临时职位已软删，公司职位回到 0 条',
      r.status === 200 && (dataOf(r).list || []).length === 0,
      `count=${(dataOf(r).list || []).length}`);
  }

  // ============================== §5.6 企业认证 ==============================
  console.log('== §5.6 企业认证（PENDING → 约 1.2s 自动通过） ==');

  r = await POST('/api/v1/company/me/verify', {
    token: tempToken,
    body: { companyName: newName, licenseNo: '123', contactName: '陈女士', contactPhone: phone },
  });
  check('认证资料不合法 → 422 + 42210', r.status === 422 && codeOf(r) === 42210,
    `HTTP ${r.status} code=${codeOf(r)}`);

  r = await POST('/api/v1/company/me/verify', {
    token: seekerToken,
    body: { companyName: '冒烟企业', licenseNo: '91310000MA1K35Q99X', contactName: '陈女士', contactPhone: phone },
  });
  check('求职者提交认证 → 403 + 40301', r.status === 403 && codeOf(r) === 40301,
    `HTTP ${r.status} code=${codeOf(r)}`);

  const verifyBody = {
    companyName: newName,
    licenseNo: '91310000MA1K35Q99X',
    contactName: '陈女士',
    contactPhone: phone,
  };
  r = await POST('/api/v1/company/me/verify', { token: tempToken, body: verifyBody });
  d = dataOf(r);
  check('提交认证成功 → PENDING / verified=false',
    r.status === 200 && codeOf(r) === 0 && d.status === 'PENDING' && d.verified === false,
    `status=${d.status} verified=${d.verified}`);

  let approved = null;
  for (let i = 0; i < 20; i += 1) {
    await sleep(100);
    const poll = await GET('/api/v1/company/me', { token: tempToken });
    const current = dataOf(poll);
    if (current.verified === true) {
      approved = current;
      break;
    }
  }
  check('轮询 §5.3：约 1.2s 后自动通过 → ACTIVE / verified=true',
    approved !== null && approved.status === 'ACTIVE' && approved.verified === true,
    approved ? `status=${approved.status}` : '等待超时');

  r = await POST('/api/v1/company/me/verify', { token: tempToken, body: verifyBody });
  check('已认证重复提交 → 409 + 40916', r.status === 409 && codeOf(r) === 40916,
    `HTTP ${r.status} code=${codeOf(r)}`);

  r = await GET('/api/v1/companies/' + tempCompanyId);
  check('公开详情同步认证结果', r.status === 200 && dataOf(r).verified === true
    && dataOf(r).status === 'ACTIVE', `verified=${dataOf(r).verified}`);

  console.log(`\n结果：${passed}/${passed + failed} 通过${failed ? '（' + failed + ' 项失败）' : ''}`);
  if (failed) process.exit(1);
})().catch((e) => {
  console.error('FAILED', e);
  process.exit(1);
});
