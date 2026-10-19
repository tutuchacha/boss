// 聘聊 · T8 招聘者接口冒烟（契约 §4.1~§4.6）
// 用法：后端启动后运行 `node scripts/smoke-recruiter.mjs`（可用 PINLIAO_BASE 覆盖地址）
// 前置：Flyway V1~V8 已迁移；MySQL + Redis 运行中；演示 HR 13800000002 / 密码 123456
// 说明：
//  - 只读为主：§4.1 / §4.3 列表 / §4.4 / §4.5 / §4.6 均为读取，不改动种子数据；
//  - §4.2 会临时改动画像后用原值还原；§4.3 会创建 job-new-* 用于验证「HR 可见已关闭职位」，
//    并在 finally 中软删，保证 verify-seed 的 7 条云杉职位基线不变；
//  - 人才筛选用例与前端 mock（src/api/mock/talentMatch.ts）同源，T9 切换真实后端后 e2e 断言语义不变。
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

async function http(method, path, { token, body } = {}) {
  const h = { Accept: 'application/json' };
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
    /* 非 JSON 响应 */
  }
  return { status: res.status, json, text };
}
const GET = (path, opts) => http('GET', path, opts);
const POST = (path, opts) => http('POST', path, opts);
const PATCH = (path, opts) => http('PATCH', path, opts);
const DELETE = (path, opts) => http('DELETE', path, opts);
const dataOf = (r) => (r.json && typeof r.json === 'object' ? r.json.data : undefined);
const codeOf = (r) => (r.json && typeof r.json === 'object' ? r.json.code : undefined);
const listOf = (r) => (Array.isArray(dataOf(r)) ? dataOf(r) : []);
const q = (value) => encodeURIComponent(value);

(async () => {
  let hrToken = null;
  let createdJobId = null;
  const seededJobIds = ['job-1', 'job-6', 'job-12', 'job-16', 'job-17', 'job-21', 'job-28'];
  const seededApplicationIds = ['app-hr-01', 'app-hr-02', 'app-hr-03', 'app-hr-04', 'app-hr-05', 'app-hr-06'];

  try {
    // ============================== 准备 ==============================
    console.log('== 准备账号 ==');
    const login = await POST('/api/v1/auth/login/password', {
      body: { account: '13800000002', password: '123456' },
    });
    hrToken = dataOf(login)?.accessToken;
    check('演示 HR 登录成功', typeof hrToken === 'string' && dataOf(login)?.user?.role === 'HR');

    const seekerLogin = await POST('/api/v1/auth/login/password', {
      body: { account: '13800000001', password: '123456' },
    });
    const seekerToken = dataOf(seekerLogin)?.accessToken;

    let r = await GET('/api/v1/hr/profile');
    check('未登录读取招聘者画像 → 401 + 40101', r.status === 401 && codeOf(r) === 40101,
      `HTTP ${r.status} code=${codeOf(r)}`);

    // ============================== §4.1 画像 ==============================
    console.log('== §4.1 获取招聘者画像 ==');

    r = await GET('/api/v1/hr/profile', { token: seekerToken });
    check('求职者读取招聘者画像 → 403 + 40301', r.status === 403 && codeOf(r) === 40301,
      `HTTP ${r.status} code=${codeOf(r)}`);

    r = await GET('/api/v1/hr/profile', { token: hrToken });
    const profile = dataOf(r) || {};
    check('HR 读取画像 → 本人 + 本公司 + 姓名 / 职位齐备',
      r.status === 200 && codeOf(r) === 0 && profile.userId === 'u-hr-1'
      && profile.companyId === 'co-acgiw7'
      && typeof profile.name === 'string' && profile.name.length > 0
      && typeof profile.title === 'string' && profile.title.length > 0,
      `name=${profile.name} title=${profile.title}`);

    // ============================== §4.2 更新画像 ==============================
    console.log('== §4.2 更新招聘者画像 ==');

    r = await PATCH('/api/v1/hr/profile', { token: hrToken, body: { name: '冒烟HR', title: '冒烟测试' } });
    const patched = dataOf(r) || {};
    check('部分更新 → 只改入参字段',
      r.status === 200 && codeOf(r) === 0 && patched.name === '冒烟HR' && patched.title === '冒烟测试'
      && patched.intro === profile.intro,
      `name=${patched.name} intro 未变`);

    r = await PATCH('/api/v1/hr/profile', { token: hrToken, body: { name: '   ' } });
    check('空姓名 → 422 + 42201', r.status === 422 && codeOf(r) === 42201,
      `HTTP ${r.status} code=${codeOf(r)}`);

    r = await PATCH('/api/v1/hr/profile', { token: hrToken, body: { intro: '' } });
    check('空串清空可选字段（intro）', r.status === 200 && codeOf(r) === 0 && !dataOf(r)?.intro,
      `intro=${JSON.stringify(dataOf(r)?.intro)}`);

    r = await PATCH('/api/v1/hr/profile', {
      token: hrToken,
      body: { name: profile.name, title: profile.title, intro: profile.intro },
    });
    check('还原画像为种子值', r.status === 200 && codeOf(r) === 0
      && dataOf(r)?.name === profile.name && dataOf(r)?.title === profile.title
      && dataOf(r)?.intro === profile.intro);

    r = await PATCH('/api/v1/hr/profile', { token: seekerToken, body: { name: '越权' } });
    check('求职者更新画像 → 403 + 40301', r.status === 403 && codeOf(r) === 40301,
      `HTTP ${r.status} code=${codeOf(r)}`);

    // ============================== §4.3 我发布的职位 ==============================
    console.log('== §4.3 我发布的职位 ==');

    r = await GET('/api/v1/hr/jobs', { token: hrToken });
    const jobs = listOf(r);
    check('HR 职位列表 → 本公司 7 条种子职位齐备',
      r.status === 200 && codeOf(r) === 0 && jobs.length >= seededJobIds.length
      && seededJobIds.every((id) => jobs.some((j) => j.id === id)),
      `count=${jobs.length}`);
    check('列表项为 HrJobRecord 超集（状态 / 详情字段 / 软删标记齐备）',
      jobs.every((j) => j.companyId === 'co-acgiw7' && j.deleted === false
        && ['OPEN', 'CLOSED', 'DRAFT'].includes(j.status)
        && typeof j.salary === 'string' && typeof j.company?.name === 'string'
        && typeof j.hr?.id === 'string' && Array.isArray(j.tags) && Array.isArray(j.welfare)),
      `first=${jobs[0]?.id}/${jobs[0]?.status}`);

    r = await GET('/api/v1/hr/jobs?status=OPEN', { token: hrToken });
    check('status=OPEN 过滤 → 结果全为 OPEN 且含种子职位',
      listOf(r).every((j) => j.status === 'OPEN')
      && seededJobIds.every((id) => listOf(r).some((j) => j.id === id)),
      `count=${listOf(r).length}`);

    r = await GET('/api/v1/hr/jobs?status=CLOSED', { token: hrToken });
    check('status=CLOSED 过滤前无已关闭职位（种子云杉职位均为 OPEN）',
      listOf(r).length === 0, `count=${listOf(r).length}`);

    r = await GET('/api/v1/hr/jobs?status=ARCHIVED', { token: hrToken });
    check('非法状态 → 422 + 42201', r.status === 422 && codeOf(r) === 42201,
      `HTTP ${r.status} code=${codeOf(r)}`);

    r = await GET('/api/v1/hr/jobs?q=' + q('前端'), { token: hrToken });
    const byTitle = listOf(r);
    check('q 标题模糊筛选（扩展参数）', byTitle.length >= 1 && byTitle.every((j) => j.title.includes('前端')),
      `count=${byTitle.length} titles=${byTitle.map((j) => j.title).join('/')}`);

    r = await GET('/api/v1/hr/jobs?hrId=hr-x2270n', { token: hrToken });
    const mine = listOf(r);
    check('hrId 仅看我发布的（扩展参数）→ 3 条',
      mine.length === 3 && mine.every((j) => j.hr.id === 'hr-x2270n'),
      `count=${mine.length}`);

    r = await GET('/api/v1/hr/jobs/job-not-exist', { token: hrToken });
    check('不存在的职位详情 → 404 + 40410', r.status === 404 && codeOf(r) === 40410,
      `HTTP ${r.status} code=${codeOf(r)}`);

    r = await GET('/api/v1/hr/jobs', { token: seekerToken });
    check('求职者访问 HR 职位列表 → 403 + 40301', r.status === 403 && codeOf(r) === 40301,
      `HTTP ${r.status} code=${codeOf(r)}`);

    // —— HR 可见已关闭职位：公开详情只认 OPEN，HR 详情 / 列表仍可选 ——
    const upsert = {
      title: '冒烟测试 · 招聘者接口',
      salaryMin: 15,
      salaryMax: 25,
      city: '上海',
      area: '浦东新区',
      exp: '3-5年',
      edu: '本科',
      cat: '互联网',
      sub: '前端开发',
      tags: ['React', 'TypeScript'],
      responsibilities: '负责业务系统前端开发与迭代维护。',
      requirements: '熟悉 React / TypeScript，有工程化经验。',
      welfare: ['五险一金', '带薪年假'],
    };
    r = await POST('/api/v1/jobs', { token: hrToken, body: upsert });
    createdJobId = dataOf(r)?.id;
    check('创建临时职位（用于验证已关闭口径）',
      r.status === 200 && codeOf(r) === 0 && typeof createdJobId === 'string'
      && createdJobId.startsWith('job-new-'),
      `id=${createdJobId}`);

    r = await POST('/api/v1/jobs/' + createdJobId + '/close', { token: hrToken });
    check('关闭职位 → CLOSED', r.status === 200 && codeOf(r) === 0 && dataOf(r)?.status === 'CLOSED');

    r = await GET('/api/v1/hr/jobs?status=CLOSED', { token: hrToken });
    check('HR 列表可见已关闭职位（公开口径不可见）',
      listOf(r).some((j) => j.id === createdJobId && j.status === 'CLOSED'),
      `count=${listOf(r).length}`);

    r = await GET('/api/v1/hr/jobs/' + createdJobId, { token: hrToken });
    check('HR 职位详情可见已关闭职位', r.status === 200 && codeOf(r) === 0
      && dataOf(r)?.id === createdJobId && dataOf(r)?.status === 'CLOSED');

    r = await GET('/api/v1/jobs/' + createdJobId);
    check('同一职位公开详情 → 404 + 40410（只认 OPEN）', r.status === 404 && codeOf(r) === 40410,
      `HTTP ${r.status} code=${codeOf(r)}`);

    r = await DELETE('/api/v1/jobs/' + createdJobId, { token: hrToken });
    check('软删临时职位 → 200', r.status === 200 && codeOf(r) === 0, `HTTP ${r.status}`);

    r = await GET('/api/v1/hr/jobs/' + createdJobId, { token: hrToken });
    check('软删后 HR 详情 → 404 + 40410', r.status === 404 && codeOf(r) === 40410,
      `HTTP ${r.status} code=${codeOf(r)}`);

    r = await GET('/api/v1/hr/jobs', { token: hrToken });
    check('软删后不含可见的临时职位（回到种子基线）',
      listOf(r).length >= seededJobIds.length && listOf(r).every((j) => !j.id.startsWith('job-new-')),
      `count=${listOf(r).length}`);
    createdJobId = null;

    // ============================== §4.4 我收到的投递 ==============================
    console.log('== §4.4 我收到的投递 ==');

    r = await GET('/api/v1/hr/applications', { token: hrToken });
    const applications = listOf(r);
    check('HR 投递列表 → 含本公司 6 条种子投递',
      r.status === 200 && codeOf(r) === 0 && applications.length >= seededApplicationIds.length
      && seededApplicationIds.every((id) => applications.some((a) => a.application.id === id)),
      `count=${applications.length}`);
    check('列表按更新时间倒序',
      applications.every((a, i) => i === 0
        || new Date(applications[i - 1].application.updatedAt).getTime()
          >= new Date(a.application.updatedAt).getTime()),
      `first=${applications[0]?.application?.id}`);
    check('投递卡片为 HrApplicationView 超集（application + candidate + job + 会话 + 面试字段）',
      applications.every((a) => typeof a.application?.status === 'string'
        && typeof a.candidate?.userId === 'string' && a.candidate.userId.length > 0
        && typeof a.candidate.name === 'string'
        && typeof a.job?.id === 'string' && typeof a.job?.title === 'string'
        && typeof a.conversationId === 'string' && a.candidate.resumeCompleteness === 85)
      && seededApplicationIds.every((id) => applications.find((a) => a.application.id === id)
        ?.candidate.userId.startsWith('u-cand-')),
      `candidate0=${applications[0]?.candidate?.name}`);
    check('种子投递的会话 id 与种子一致（6 条均已有会话）',
      seededApplicationIds.every((id) => (applications.find((a) => a.application.id === id)?.conversationId
        || '').startsWith('conv-hr-')));
    check('面试：待确认优先（app-hr-04 → int-hr-01 PENDING）',
      applications.find((a) => a.application.id === 'app-hr-04')?.interview?.id === 'int-hr-01'
      && applications.find((a) => a.application.id === 'app-hr-04')?.interview?.status === 'PENDING');
    check('面试：无待确认时取最近一条（app-hr-06 → int-hr-02 COMPLETED）',
      applications.find((a) => a.application.id === 'app-hr-06')?.interview?.id === 'int-hr-02'
      && applications.find((a) => a.application.id === 'app-hr-06')?.interview?.status === 'COMPLETED');
    check('无面试的投递不返回 interview 字段',
      applications.filter((a) => ['app-hr-01', 'app-hr-02', 'app-hr-03', 'app-hr-05']
        .includes(a.application.id)).every((a) => a.interview === undefined));

    r = await GET('/api/v1/hr/applications?status=OFFER', { token: hrToken });
    check('status=OFFER 过滤 → 结果全为 OFFER 且含 app-hr-06',
      listOf(r).length >= 1 && listOf(r).every((a) => a.application.status === 'OFFER')
      && listOf(r).some((a) => a.application.id === 'app-hr-06'));

    r = await GET('/api/v1/hr/applications?jobId=job-1', { token: hrToken });
    check('jobId 过滤 → 结果全为该职位且含 app-hr-01',
      listOf(r).length >= 1 && listOf(r).every((a) => a.job.id === 'job-1' && a.application.jobId === 'job-1')
      && listOf(r).some((a) => a.application.id === 'app-hr-01'));

    r = await GET('/api/v1/hr/applications?q=' + q('林小满'), { token: hrToken });
    check('q 候选人姓名筛选（扩展参数）→ 结果均为林小满',
      listOf(r).length >= 1 && listOf(r).every((a) => a.candidate.name === '林小满'),
      `count=${listOf(r).length}`);

    r = await GET('/api/v1/hr/applications?status=UNKNOWN', { token: hrToken });
    check('非法投递状态 → 422 + 42201', r.status === 422 && codeOf(r) === 42201,
      `HTTP ${r.status} code=${codeOf(r)}`);

    r = await GET('/api/v1/hr/applications', { token: seekerToken });
    check('求职者访问 HR 投递列表 → 403 + 40301', r.status === 403 && codeOf(r) === 40301,
      `HTTP ${r.status} code=${codeOf(r)}`);

    // ============================== §4.5 候选人匹配 ==============================
    console.log('== §4.5 候选人匹配（关键词） ==');

    r = await POST('/api/v1/hr/candidates/match', { token: hrToken, body: { query: 'React' } });
    const reactMatches = listOf(r);
    const linxiaoman = reactMatches.find((m) => m.candidate?.userId === 'u-cand-1') || {};
    check('React → 命中林小满且分值为技能权重 3',
      r.status === 200 && codeOf(r) === 0 && linxiaoman.score === 3
      && JSON.stringify(linxiaoman.hitTokens) === JSON.stringify(['React']),
      `count=${reactMatches.length} score=${linxiaoman.score}`);
    check('林小满证据字段与 mock 一致（技能 / 个人简介 / 项目经历 · X）',
      JSON.stringify((linxiaoman.evidences || []).map((e) => e.field))
        === JSON.stringify(['技能', '个人简介', '项目经历 · 招聘管理系统前端重构'])
      && linxiaoman.evidences[0].snippet === 'React'
      && linxiaoman.evidences.some((e) => e.field === '个人简介' && e.snippet.includes('技术栈')),
      (linxiaoman.evidences || []).map((e) => e.field).join(' | '));
    check('未命中的候选人不出现（沈禾）',
      reactMatches.every((m) => m.candidate?.userId !== 'u-cand-3'));
    check('匹配结果按分值倒序', reactMatches.every((m, i) =>
      i === 0 || reactMatches[i - 1].score >= m.score));

    r = await POST('/api/v1/hr/candidates/match', { token: hrToken, body: { query: 'COBOL' } });
    check('无命中关键词 → 空数组（不报错）',
      r.status === 200 && codeOf(r) === 0 && listOf(r).length === 0);

    r = await POST('/api/v1/hr/candidates/match', {
      token: hrToken,
      body: { tags: ['自动化测试'] },
    });
    const tagMatches = listOf(r);
    const shenhe = tagMatches.find((m) => m.candidate?.userId === 'u-cand-3') || {};
    check('仅标签筛选 → 命中沈禾且分值为 3',
      r.status === 200 && codeOf(r) === 0 && shenhe.score === 3
      && JSON.stringify(shenhe.hitTokens) === JSON.stringify(['自动化测试']),
      `count=${tagMatches.length} userId=${shenhe.candidate?.userId}`);

    r = await POST('/api/v1/hr/candidates/match', { token: hrToken, body: { query: '前端 测试' } });
    const mixed = listOf(r);
    const indexShenhe = mixed.findIndex((m) => m.candidate.userId === 'u-cand-3');
    const indexLinxiaoman = mixed.findIndex((m) => m.candidate.userId === 'u-cand-1');
    check('多关键词 → 技能命中（3 分）排在文本命中（2 分）之前',
      indexShenhe >= 0 && indexLinxiaoman >= 0
      && mixed[indexShenhe].score === 3 && mixed[indexLinxiaoman].score === 2
      && indexShenhe < indexLinxiaoman,
      mixed.map((m) => `${m.candidate.userId}:${m.score}`).join(' '));

    r = await POST('/api/v1/hr/candidates/match', { token: hrToken, body: {} });
    check('筛选条件为空 → 422 + 42210', r.status === 422 && codeOf(r) === 42210,
      `HTTP ${r.status} code=${codeOf(r)}`);

    r = await POST('/api/v1/hr/candidates/match', { token: hrToken, body: { query: '   ', tags: [] } });
    check('空白条件 → 422 + 42210', r.status === 422 && codeOf(r) === 42210,
      `HTTP ${r.status} code=${codeOf(r)}`);

    r = await POST('/api/v1/hr/candidates/match', { token: seekerToken, body: { query: 'React' } });
    check('求职者调用匹配 → 403 + 40301', r.status === 403 && codeOf(r) === 40301,
      `HTTP ${r.status} code=${codeOf(r)}`);

    // ============================== §4.6 技能标签词表 ==============================
    console.log('== §4.6 技能标签词表 ==');

    r = await GET('/api/v1/hr/candidates/match/tags');
    check('未登录读取词表 → 401 + 40101', r.status === 401 && codeOf(r) === 40101,
      `HTTP ${r.status} code=${codeOf(r)}`);

    r = await GET('/api/v1/hr/candidates/match/tags', { token: hrToken });
    const tags = listOf(r);
    check('词表 20 条且含常用标签',
      r.status === 200 && codeOf(r) === 0 && tags.length === 20
      && ['前端', '自动化测试', 'Figma'].every((t) => tags.includes(t)),
      `count=${tags.length}`);
  } catch (error) {
    failed += 1;
    console.log('  \u2718 脚本异常：' + (error && error.message ? error.message : error));
  } finally {
    // 兜底清理：防止中途断言失败留下可见脏职位（T3b 冒烟与 verify-seed 的基线依赖此步）
    if (createdJobId && hrToken) {
      await DELETE('/api/v1/jobs/' + createdJobId, { token: hrToken });
    }
    console.log(`\n结果：${passed}/${passed + failed} 通过${failed ? '（' + failed + ' 项失败）' : ''}`);
    if (failed) process.exitCode = 1;
  }
})();
