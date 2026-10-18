// 聘聊 · 投递与面试接口冒烟（Step 3 T4；契约 docs/api-endpoints.md §9 / §10 + §8.7，约 38 项）
// 用法：后端启动后运行 `node scripts/smoke-applications.mjs`（可用 PINLIAO_BASE 覆盖地址）
// 前置：Flyway V1~V8 已迁移；MySQL + Redis 运行中；演示账号 13800000001（求职者）/ 13800000002（HR），密码 123456
// 说明：
//  - 使用「云杉科技」下未投递或已撤回的职位，完整跑完会把投递收尾为 WITHDRAWN，因此可重复执行；
//  - 新增数据统一 app-new- / int-new- / conv-new- / msg-new- 前缀，verify-seed.ps1 按前缀排除；
//  - 临时 HR（他公司）仅用于越权断言，注册产物 u-reg- / co-reg- 已被种子巡检排除。
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
const dataOf = (r) => (r.json && typeof r.json === 'object' ? r.json.data : undefined) || {};
const codeOf = (r) => (r.json && typeof r.json === 'object' ? r.json.code : undefined);

async function login(account) {
  const r = await POST('/api/v1/auth/login/password', {
    body: { account, password: '123456' },
  });
  return dataOf(r).accessToken;
}

(async () => {
  console.log('== §9.1 投递（前置 / 校验码） ==');

  const seekerToken = await login('13800000001');
  const hrToken = await login('13800000002');
  check('演示账号登录（求职者 / HR）', typeof seekerToken === 'string' && typeof hrToken === 'string');

  let r = await POST('/api/v1/applications', {
    body: { jobId: 'job-1', resumeId: 'resume-seeker' },
  });
  check('未登录投递 → 401 + 40101', r.status === 401 && codeOf(r) === 40101,
    `HTTP ${r.status} code=${codeOf(r)}`);

  r = await POST('/api/v1/applications', {
    token: hrToken,
    body: { jobId: 'job-1', resumeId: 'resume-seeker' },
  });
  check('HR 身份投递 → 403 + 40301', r.status === 403 && codeOf(r) === 40301,
    `HTTP ${r.status} code=${codeOf(r)}`);

  r = await GET('/api/v1/applications', { token: hrToken });
  check('HR 访问我的投递 → 403 + 40301', r.status === 403 && codeOf(r) === 40301,
    `HTTP ${r.status} code=${codeOf(r)}`);

  // 选择「云杉科技」下尚未投递或已撤回的职位（保证脚本可重复执行）
  const jobs = (await GET('/api/v1/jobs?limit=100')).json?.data?.list || [];
  const myApps = (await GET('/api/v1/applications?limit=100', { token: seekerToken })).json?.data?.list || [];
  const target = jobs
    .filter((j) => j.company?.id === 'co-acgiw7')
    .find((j) => {
      const app = myApps.find((a) => a.jobId === j.id);
      return !app || app.status === 'WITHDRAWN';
    });
  check('找到可用于投递的云杉职位', Boolean(target), target ? `${target.id} ${target.title}` : '无可用职位');
  if (!target) throw new Error('没有可重复使用的职位，请检查种子或手工清理 app-new-% 数据');
  const jobId = target.id;

  r = await POST('/api/v1/applications', {
    token: seekerToken,
    body: { jobId, resumeId: 'resume-not-exist' },
  });
  check('简历不属于本人 → 422 + 42210', r.status === 422 && codeOf(r) === 42210,
    `HTTP ${r.status} code=${codeOf(r)}`);

  r = await POST('/api/v1/applications', {
    token: seekerToken,
    body: { jobId, coverLetter: 'T4 冒烟投递：期待与团队沟通。' },
  });
  let app = dataOf(r);
  const appId = app.id;
  check('投递成功（缺省默认简历）→ SUBMITTED / app-new-*',
    r.status === 200 && codeOf(r) === 0 && app.status === 'SUBMITTED' && String(appId).startsWith('app-new-')
      && app.companyId === 'co-acgiw7', `id=${appId} status=${app.status}`);

  r = await POST('/api/v1/applications', {
    token: seekerToken,
    body: { jobId, resumeId: 'resume-seeker' },
  });
  check('重复投递 → 409 + 40911', r.status === 409 && codeOf(r) === 40911,
    `HTTP ${r.status} code=${codeOf(r)}`);

  console.log('== §9.2 / §9.3 我的投递与详情 ==');

  r = await GET('/api/v1/applications?limit=100', { token: seekerToken });
  let page = dataOf(r);
  check('我的投递包含新投递且 total ≥ 4',
    page.list?.some((a) => a.id === appId) && page.pagination?.total >= 4,
    `total=${page.pagination?.total}`);

  r = await GET('/api/v1/applications?status=SUBMITTED&limit=100', { token: seekerToken });
  let list = dataOf(r).list || [];
  check('状态筛选 SUBMITTED 全部命中', list.length > 0 && list.every((a) => a.status === 'SUBMITTED'),
    `count=${list.length}`);

  const p1 = await GET('/api/v1/applications?limit=2', { token: seekerToken });
  const cursor = dataOf(p1).pagination?.nextCursor;
  const p2 = await GET('/api/v1/applications?limit=2&cursor=' + encodeURIComponent(cursor), { token: seekerToken });
  const ids1 = (dataOf(p1).list || []).map((a) => a.id);
  const ids2 = (dataOf(p2).list || []).map((a) => a.id);
  check('投递游标翻页不重不漏', ids1.length === 2 && ids2.length > 0
    && ids1.every((id) => !ids2.includes(id)), `p1=${ids1.length} p2=${ids2.length}`);

  r = await GET('/api/v1/applications?cursor=' + encodeURIComponent('%%%'), { token: seekerToken });
  check('非法游标 → 400 + 40001', r.status === 400 && codeOf(r) === 40001,
    `HTTP ${r.status} code=${codeOf(r)}`);

  r = await GET('/api/v1/applications/' + appId, { token: seekerToken });
  let detail = dataOf(r);
  const conversationId = detail.conversationId;
  check('求职者投递详情（职位 / 简历 / 会话占位）',
    r.status === 200 && detail.job?.id === jobId && detail.resume?.id === 'resume-seeker'
      && Array.isArray(detail.interviews) && String(conversationId).startsWith('conv-new-'),
    `job=${detail.job?.id} conversation=${conversationId}`);

  r = await GET('/api/v1/applications/not-exist-app', { token: seekerToken });
  check('投递不存在 → 404 + 40410', r.status === 404 && codeOf(r) === 40410,
    `HTTP ${r.status} code=${codeOf(r)}`);

  // 他公司 HR（越权隔离）
  const hrPhone = '137' + String(Date.now()).slice(-8);
  await POST('/api/v1/auth/sms/code', { body: { phone: hrPhone, scene: 'REGISTER' } });
  const reg = await POST('/api/v1/auth/register', {
    body: { phone: hrPhone, code: '123456', password: '123456', role: 'HR' },
  });
  const otherToken = dataOf(reg).accessToken;
  check('临时他公司 HR 注册成功（越权用例前置）', typeof otherToken === 'string' && otherToken.length > 20);

  r = await GET('/api/v1/applications/' + appId, { token: otherToken });
  check('他公司 HR 查看投递 → 404 + 40410', r.status === 404 && codeOf(r) === 40410,
    `HTTP ${r.status} code=${codeOf(r)}`);

  r = await GET('/api/v1/applications/' + appId, { token: hrToken });
  check('本公司 HR 查看投递详情', r.status === 200 && dataOf(r).id === appId, `HTTP ${r.status}`);

  console.log('== §9.4 / §9.5 状态机 ==');

  r = await POST('/api/v1/applications/' + appId + '/status', {
    token: hrToken,
    body: { status: 'VIEWED' },
  });
  check('HR 标记已查看 → VIEWED', r.status === 200 && dataOf(r).status === 'VIEWED',
    `status=${dataOf(r).status}`);

  r = await POST('/api/v1/applications/' + appId + '/status', {
    token: hrToken,
    body: { status: 'FOO' },
  });
  check('非法投递状态 → 422 + 42201', r.status === 422 && codeOf(r) === 42201,
    `HTTP ${r.status} code=${codeOf(r)}`);

  r = await POST('/api/v1/applications/' + appId + '/status', {
    token: otherToken,
    body: { status: 'OFFER' },
  });
  check('他公司 HR 改状态 → 404 + 40410', r.status === 404 && codeOf(r) === 40410,
    `HTTP ${r.status} code=${codeOf(r)}`);

  console.log('== §8.7 约面试（创建 + 消息 + 校验码） ==');

  r = await POST('/api/v1/conversations/conv-hr-14/actions/invite-interview', {
    token: hrToken,
    body: { scheduledAt: new Date(Date.now() + 86400_000).toISOString(), format: 'VIDEO' },
  });
  check('已拒绝投递发起面试 → 409 + 40914', r.status === 409 && codeOf(r) === 40914,
    `HTTP ${r.status} code=${codeOf(r)}`);

  r = await POST('/api/v1/conversations/conv-hr-11/actions/invite-interview', {
    token: hrToken,
    body: { scheduledAt: new Date(Date.now() + 86400_000).toISOString(), format: 'VIDEO' },
  });
  check('已有待确认邀请 → 409 + 40915', r.status === 409 && codeOf(r) === 40915,
    `HTTP ${r.status} code=${codeOf(r)}`);

  r = await POST('/api/v1/conversations/conv-hr-4/actions/invite-interview', {
    token: hrToken,
    body: { scheduledAt: new Date(Date.now() + 86400_000).toISOString(), format: 'FOO' },
  });
  check('非法面试形式 → 422 + 42201', r.status === 422 && codeOf(r) === 42201,
    `HTTP ${r.status} code=${codeOf(r)}`);

  r = await POST('/api/v1/conversations/conv-hr-4/actions/invite-interview', {
    token: hrToken,
    body: { scheduledAt: new Date(Date.now() + 86400_000).toISOString(), format: 'ONSITE' },
  });
  check('现场面试缺地点 → 422 + 42201', r.status === 422 && codeOf(r) === 42201,
    `HTTP ${r.status} code=${codeOf(r)}`);

  r = await POST('/api/v1/conversations/' + conversationId + '/actions/invite-interview', {
    token: hrToken,
    body: { scheduledAt: new Date(Date.now() - 3600_000).toISOString(), format: 'VIDEO' },
  });
  check('面试时间早于当前 → 422 + 42210', r.status === 422 && codeOf(r) === 42210,
    `HTTP ${r.status} code=${codeOf(r)}`);

  r = await POST('/api/v1/conversations/' + conversationId + '/actions/invite-interview', {
    token: otherToken,
    body: { scheduledAt: new Date(Date.now() + 86400_000).toISOString(), format: 'VIDEO' },
  });
  check('他公司 HR 约面试 → 404 + 40410', r.status === 404 && codeOf(r) === 40410,
    `HTTP ${r.status} code=${codeOf(r)}`);

  const scheduledAt = new Date(Date.now() + 2 * 86400_000).toISOString();
  r = await POST('/api/v1/conversations/' + conversationId + '/actions/invite-interview', {
    token: hrToken,
    body: {
      scheduledAt,
      format: 'VIDEO',
      onlineUrl: 'https://meeting.pinliao.dev/smoke-t4',
      note: 'T4 冒烟面试',
    },
  });
  let inviteMessage = dataOf(r);
  check('约面试成功 → INTERVIEW_INVITE 消息（msg-new-*）',
    r.status === 200 && inviteMessage.type === 'INTERVIEW_INVITE'
      && String(inviteMessage.id).startsWith('msg-new-'),
    `id=${inviteMessage.id}`);
  check('邀请消息未读 / 内容含面试 id',
    inviteMessage.readByUser === false && String(inviteMessage.content).includes('interviewId'),
    `readByUser=${inviteMessage.readByUser}`);

  r = await POST('/api/v1/conversations/' + conversationId + '/actions/invite-interview', {
    token: hrToken,
    body: { scheduledAt: new Date(Date.now() + 3 * 86400_000).toISOString(), format: 'VIDEO' },
  });
  check('重复约面试 → 409 + 40915', r.status === 409 && codeOf(r) === 40915,
    `HTTP ${r.status} code=${codeOf(r)}`);

  r = await GET('/api/v1/applications/' + appId, { token: hrToken });
  detail = dataOf(r);
  const pendingInterview = (detail.interviews || []).find((i) => i.status === 'PENDING');
  const interviewId = pendingInterview?.id;
  check('约面试后投递置 INTERVIEW 且生成 PENDING 面试',
    detail.status === 'INTERVIEW' && String(interviewId).startsWith('int-new-'),
    `status=${detail.status} interview=${interviewId}`);

  console.log('== §10.1 ~ §10.3 面试列表与详情 ==');

  r = await GET('/api/v1/interviews?limit=100', { token: seekerToken });
  page = dataOf(r);
  check('求职者面试列表包含新邀请与职位摘要',
    (page.list || []).some((i) => i.id === interviewId && i.job?.id === jobId),
    `total=${page.pagination?.total}`);

  r = await GET('/api/v1/interviews?status=PENDING&limit=100', { token: seekerToken });
  list = dataOf(r).list || [];
  check('面试状态筛选 PENDING 全部命中', list.length > 0 && list.every((i) => i.status === 'PENDING'),
    `count=${list.length}`);

  r = await GET('/api/v1/interviews/' + interviewId, { token: seekerToken });
  check('求职者查看面试详情', r.status === 200 && dataOf(r).id === interviewId, `HTTP ${r.status}`);

  r = await GET('/api/v1/interviews/' + interviewId, { token: otherToken });
  check('他公司 HR 查看面试 → 404 + 40410', r.status === 404 && codeOf(r) === 40410,
    `HTTP ${r.status} code=${codeOf(r)}`);

  r = await GET('/api/v1/interviews/not-exist-int', { token: seekerToken });
  check('面试不存在 → 404 + 40410', r.status === 404 && codeOf(r) === 40410,
    `HTTP ${r.status} code=${codeOf(r)}`);

  r = await GET('/api/v1/hr/interviews?limit=100', { token: hrToken });
  page = dataOf(r);
  const hrItem = (page.list || []).find((i) => i.id === interviewId);
  check('HR 面试列表含候选人姓名 / 手机号',
    Boolean(hrItem) && hrItem.user?.id === 'u-seeker-1' && typeof hrItem.profile?.name === 'string'
      && hrItem.profile.name.length > 0,
    `candidate=${hrItem?.profile?.name} phone=${hrItem?.user?.phone}`);

  r = await GET('/api/v1/hr/interviews?limit=100', { token: otherToken });
  check('他公司 HR 列表看不到该面试',
    !(dataOf(r).list || []).some((i) => i.id === interviewId), `HTTP ${r.status}`);

  r = await GET('/api/v1/hr/interviews', { token: seekerToken });
  check('求职者访问 HR 面试列表 → 403 + 40301', r.status === 403 && codeOf(r) === 40301,
    `HTTP ${r.status} code=${codeOf(r)}`);

  console.log('== §10.4 / §10.5 接受与拒绝 ==');

  r = await POST('/api/v1/interviews/' + interviewId + '/accept', { token: seekerToken });
  check('接受面试 → ACCEPTED', r.status === 200 && dataOf(r).status === 'ACCEPTED',
    `status=${dataOf(r).status}`);

  r = await POST('/api/v1/interviews/' + interviewId + '/accept', { token: seekerToken });
  check('重复接受幂等 → ACCEPTED', r.status === 200 && dataOf(r).status === 'ACCEPTED',
    `status=${dataOf(r).status}`);

  r = await POST('/api/v1/interviews/' + interviewId + '/accept', { token: hrToken });
  check('HR 接受面试 → 403 + 40301', r.status === 403 && codeOf(r) === 40301,
    `HTTP ${r.status} code=${codeOf(r)}`);

  r = await POST('/api/v1/conversations/' + conversationId + '/actions/invite-interview', {
    token: hrToken,
    body: {
      scheduledAt: new Date(Date.now() + 4 * 86400_000).toISOString(),
      format: 'PHONE',
      note: '二面电话沟通',
    },
  });
  check('已接受后再次约面成功', r.status === 200 && dataOf(r).type === 'INTERVIEW_INVITE', `HTTP ${r.status}`);

  const secondInterview = (await GET('/api/v1/applications/' + appId, { token: hrToken })).json?.data?.interviews
    ?.find((i) => i.status === 'PENDING');
  check('生成第二条 PENDING 面试', Boolean(secondInterview), secondInterview?.id);

  r = await POST('/api/v1/interviews/' + secondInterview.id + '/decline', {
    token: seekerToken,
    body: { reason: '时间冲突' },
  });
  check('拒绝面试 → DECLINED', r.status === 200 && dataOf(r).status === 'DECLINED',
    `status=${dataOf(r).status}`);

  r = await POST('/api/v1/interviews/' + secondInterview.id + '/decline', {
    token: seekerToken,
  });
  check('重复拒绝幂等 → DECLINED', r.status === 200 && dataOf(r).status === 'DECLINED',
    `status=${dataOf(r).status}`);

  console.log('== 收尾：撤回 / 重新投递（可重复执行） ==');

  r = await POST('/api/v1/applications/' + appId + '/withdraw', { token: seekerToken });
  check('面试中撤回投递 → WITHDRAWN', r.status === 200 && dataOf(r).status === 'WITHDRAWN',
    `status=${dataOf(r).status}`);

  r = await POST('/api/v1/applications/' + appId + '/withdraw', { token: seekerToken });
  check('重复撤回 → 409 + 40913', r.status === 409 && codeOf(r) === 40913,
    `HTTP ${r.status} code=${codeOf(r)}`);

  r = await POST('/api/v1/applications/' + appId + '/status', {
    token: hrToken,
    body: { status: 'OFFER' },
  });
  check('已撤回后 HR 改状态 → 409 + 40913', r.status === 409 && codeOf(r) === 40913,
    `HTTP ${r.status} code=${codeOf(r)}`);

  r = await GET('/api/v1/applications?status=WITHDRAWN&limit=100', { token: seekerToken });
  check('撤回后状态筛选可见该投递',
    (dataOf(r).list || []).some((a) => a.id === appId && a.status === 'WITHDRAWN'));

  r = await POST('/api/v1/applications', {
    token: seekerToken,
    body: { jobId, coverLetter: 'T4 冒烟重新投递' },
  });
  check('撤回后重新投递 → 复用 app-new-* 并回到 SUBMITTED',
    r.status === 200 && dataOf(r).id === appId && dataOf(r).status === 'SUBMITTED',
    `id=${dataOf(r).id} status=${dataOf(r).status}`);

  r = await POST('/api/v1/applications/' + appId + '/withdraw', { token: seekerToken });
  check('收尾撤回（保证脚本可重复执行）', r.status === 200 && dataOf(r).status === 'WITHDRAWN',
    `status=${dataOf(r).status}`);

  console.log(`\n结果：${passed}/${passed + failed} 通过${failed ? '（' + failed + ' 项失败）' : ''}`);
  if (failed) process.exit(1);
})().catch((e) => {
  console.error('FAILED', e);
  process.exit(1);
});
