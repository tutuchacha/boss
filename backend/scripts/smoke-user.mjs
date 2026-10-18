// 聘聊 · T7 用户 / 简历 / 收藏 / 上传接口冒烟（契约 §2 / §3 / §11 / §12）
// 用法：后端启动后运行 `node scripts/smoke-user.mjs`（可用 PINLIAO_BASE 覆盖地址）
// 前置：Flyway V1~V8 已迁移；MySQL + Redis 运行中；演示账号 13800000001 / 13800000002，密码 123456
// 说明：
//  - 每次运行创建 u-reg-* 临时求职者，不修改种子用户 / 简历 / 收藏；
//  - 注册 / 重置验证码前清理 Redis 60 秒冷却，保证脚本可连续执行；
//  - 测试创建的简历与收藏在 finally 中清理，避免影响 verify-seed 基线。
const base = process.env.PINLIAO_BASE || 'http://localhost:8080';
const redisHost = process.env.PINLIAO_REDIS_HOST || '127.0.0.1';
const redisPort = Number(process.env.PINLIAO_REDIS_PORT || 6379);

import net from 'node:net';

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
    /* 非 JSON 响应 */
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

/** 仅用于清理验证码冷却；不依赖 redis-cli，直接走 RESP。 */
function redisDel(key) {
  return new Promise((resolve, reject) => {
    const socket = net.createConnection({ host: redisHost, port: redisPort });
    let settled = false;
    const done = (fn, value) => {
      if (settled) return;
      settled = true;
      socket.destroy();
      fn(value);
    };
    socket.setTimeout(3000);
    socket.on('connect', () => {
      const args = ['DEL', key];
      let command = `*${args.length}\r\n`;
      for (const arg of args) {
        command += `$${Buffer.byteLength(arg)}\r\n${arg}\r\n`;
      }
      socket.write(command);
    });
    socket.on('data', (chunk) => {
      const text = chunk.toString();
      if (text.startsWith(':')) {
        done(resolve, Number(text.slice(1).trim()));
      } else if (text.startsWith('-')) {
        done(reject, new Error(text.trim()));
      }
    });
    socket.on('timeout', () => done(reject, new Error('Redis 连接超时')));
    socket.on('error', (error) => done(reject, error));
  });
}

async function sendSms(phone, scene) {
  await redisDel(`pinliao:auth:sms:cd:${phone}`);
  const r = await POST('/api/v1/auth/sms/code', { body: { phone, scene } });
  if (codeOf(r) !== 0) {
    throw new Error(`发送验证码失败 phone=${phone} scene=${scene} HTTP=${r.status} code=${codeOf(r)} ${r.text}`);
  }
}

async function register(phone) {
  await sendSms(phone, 'REGISTER');
  const r = await POST('/api/v1/auth/register', {
    body: { phone, code: '123456', password: '123456', role: 'JOB_SEEKER' },
  });
  if (r.status !== 200 || !dataOf(r).accessToken) {
    throw new Error(`注册临时求职者失败 phone=${phone} HTTP=${r.status} ${r.text}`);
  }
  return dataOf(r);
}

async function login(account, password = '123456') {
  const r = await POST('/api/v1/auth/login/password', { body: { account, password } });
  return { r, data: dataOf(r) };
}

function resumeContent(name, phone, skill = 'TypeScript') {
  return {
    basics: {
      name,
      phone,
      email: 'smoke-t7@pinliao.dev',
      city: '杭州',
      summary: 'T7 冒烟测试简历',
    },
    educations: [{ school: '测试大学', major: '计算机科学与技术', degree: '本科', startDate: '2016-09', endDate: '2020-06' }],
    experiences: [{ company: '测试科技', title: '前端工程师', startDate: '2020-07', endDate: 'present', description: '负责前端工程化' }],
    projects: [{ name: 'T7 项目', role: '负责人', startDate: '2024-01', endDate: '2024-12', description: '冒烟项目', link: 'https://example.com' }],
    skills: [skill, 'React'],
  };
}

(async () => {
  const tail = String(Date.now()).slice(-8);
  const phone = '136' + tail;
  const newPhone = '135' + tail;
  const deactivatePhone = '134' + tail;

  let mainToken = null;
  let mainUserId = null;
  let createdResumeIds = [];
  let favoriteJobIds = [];

  try {
    // ============================== 准备账号 ==============================
    console.log('== 准备临时求职者 ==');
    const main = await register(phone);
    mainToken = main.accessToken;
    mainUserId = main.user.id;
    check('临时求职者注册成功（u-reg-*）',
      typeof mainToken === 'string' && mainUserId === 'u-reg-' + phone && main.profile?.name?.startsWith('求职者'),
      `userId=${mainUserId}`);

    const hr = await login('13800000002');
    const hrToken = hr.data.accessToken;
    check('演示 HR 登录成功', typeof hrToken === 'string' && hr.data.user?.role === 'HR');

    // ============================== §2 用户 ==============================
    console.log('== §2 用户 / 求职者 ==');

    let r = await GET('/api/v1/user/profile');
    check('未登录读取画像 → 401 + 40101', r.status === 401 && codeOf(r) === 40101,
      `HTTP ${r.status} code=${codeOf(r)}`);

    r = await GET('/api/v1/user/profile', { token: hrToken });
    check('HR 读取求职者画像 → 403 + 40301', r.status === 403 && codeOf(r) === 40301,
      `HTTP ${r.status} code=${codeOf(r)}`);

    r = await GET('/api/v1/user/profile', { token: mainToken });
    let d = dataOf(r);
    check('读取求职者画像 → 手机号 / 默认 ACTIVE',
      r.status === 200 && codeOf(r) === 0 && d.userId === mainUserId && d.phone === undefined
      && d.seekingStatus === 'ACTIVE',
      `name=${d.name}`);

    r = await PATCH('/api/v1/user/profile', {
      token: mainToken,
      body: {
        name: 'T7 测试求职者',
        expectCat: '技术',
        expectSub: '前端开发',
        expectCity: '杭州',
        expectSalaryMin: 25,
        expectSalaryMax: 45,
        seekingStatus: 'PASSIVE',
        workYears: 5,
        edu: '本科',
        gender: 'male',
        birthDate: '1998-05-20',
      },
    });
    d = dataOf(r);
    check('更新画像 → 字段全部生效',
      r.status === 200 && d.name === 'T7 测试求职者' && d.expectCity === '杭州'
      && d.expectSalaryMin === 25 && d.expectSalaryMax === 45 && d.seekingStatus === 'PASSIVE',
      `name=${d.name} city=${d.expectCity}`);

    r = await GET('/api/v1/user/profile', { token: mainToken });
    d = dataOf(r);
    check('更新后重读画像 → 已持久化', r.status === 200 && d.workYears === 5 && d.edu === '本科');

    r = await PATCH('/api/v1/user/profile', {
      token: mainToken,
      body: { expectSalaryMin: 50, expectSalaryMax: 10 },
    });
    check('薪资下限大于上限 → 422 + 42201', r.status === 422 && codeOf(r) === 42201,
      `HTTP ${r.status} code=${codeOf(r)}`);

    r = await POST('/api/v1/user/password/change', {
      token: mainToken,
      body: { oldPassword: 'wrong', newPassword: '654321' },
    });
    check('修改密码原密码错误 → 422 + 42201', r.status === 422 && codeOf(r) === 42201,
      `HTTP ${r.status} code=${codeOf(r)}`);

    r = await POST('/api/v1/user/password/change', {
      token: mainToken,
      body: { oldPassword: '123456', newPassword: '654321' },
    });
    check('修改密码成功 → {}', r.status === 200 && codeOf(r) === 0 && r.json?.data
      && Object.keys(r.json.data).length === 0);

    r = await login(phone, '654321');
    check('新密码登录成功', r.r.status === 200 && codeOf(r.r) === 0 && typeof r.data.accessToken === 'string');

    r = await POST('/api/v1/user/password/change', {
      token: mainToken,
      body: { oldPassword: '654321', newPassword: '123456' },
    });
    check('再次修改密码恢复 123456', r.status === 200 && codeOf(r) === 0);

    await sendSms(phone, 'RESET');
    r = await POST('/api/v1/user/password/reset', {
      body: { phone, code: '000000', newPassword: '123456' },
    });
    check('重置密码错误验证码 → 401 + 40110', r.status === 401 && codeOf(r) === 40110,
      `HTTP ${r.status} code=${codeOf(r)}`);

    r = await POST('/api/v1/user/password/reset', {
      body: { phone, code: '123456', newPassword: '123456' },
    });
    check('重置密码成功（未登录） → {}', r.status === 200 && codeOf(r) === 0);

    r = await login(phone, '123456');
    check('重置密码后可登录', r.r.status === 200 && typeof r.data.accessToken === 'string');

    r = await POST('/api/v1/user/phone/change', {
      token: mainToken,
      body: { newPhone: '13800000002', code: '123456' },
    });
    check('修改手机号为已占用号码 → 409 + 40910', r.status === 409 && codeOf(r) === 40910,
      `HTTP ${r.status} code=${codeOf(r)}`);

    await sendSms(newPhone, 'LOGIN');
    r = await POST('/api/v1/user/phone/change', {
      token: mainToken,
      body: { newPhone, code: '123456' },
    });
    check('修改手机号成功 → {}', r.status === 200 && codeOf(r) === 0);

    r = await login(newPhone, '123456');
    check('新手机号可登录', r.r.status === 200 && r.data.user?.phone === newPhone,
      `phone=${r.data.user?.phone}`);

    r = await login(phone, '123456');
    check('旧手机号不可登录 → 401 + 40110', r.r.status === 401 && codeOf(r.r) === 40110,
      `HTTP ${r.r.status} code=${codeOf(r.r)}`);

    r = await GET('/api/v1/user/preferences', { token: mainToken });
    check('外观偏好默认 auto', r.status === 200 && dataOf(r).theme === 'auto', `theme=${dataOf(r).theme}`);

    r = await PATCH('/api/v1/user/preferences', { token: mainToken, body: { theme: 'dark' } });
    check('更新外观偏好为 dark', r.status === 200 && dataOf(r).theme === 'dark');

    r = await GET('/api/v1/user/preferences', { token: mainToken });
    check('重读外观偏好为 dark', r.status === 200 && dataOf(r).theme === 'dark');

    r = await PATCH('/api/v1/user/preferences', { token: mainToken, body: { theme: 'blue' } });
    check('非法 theme → 422 + 42201', r.status === 422 && codeOf(r) === 42201,
      `HTTP ${r.status} code=${codeOf(r)}`);

    // ============================== §2.8 注销 ==============================
    console.log('== §2.8 注销账号 ==');
    const deactivateAccount = await register(deactivatePhone);
    r = await POST('/api/v1/user/deactivate', {
      token: deactivateAccount.accessToken,
      body: { reason: 'smoke 临时账号' },
    });
    check('注销账号成功 → {}', r.status === 200 && codeOf(r) === 0);

    r = await GET('/api/v1/auth/me', { token: deactivateAccount.accessToken });
    check('注销后当前 accessToken 立即失效 → 401 + 40102',
      r.status === 401 && codeOf(r) === 40102, `HTTP ${r.status} code=${codeOf(r)}`);

    // ============================== §3 简历 ==============================
    console.log('== §3 简历 ==');

    r = await GET('/api/v1/resumes');
    check('未登录读取简历列表 → 401 + 40101', r.status === 401 && codeOf(r) === 40101);

    r = await GET('/api/v1/resumes', { token: hrToken });
    check('HR 读取我的简历列表 → 403 + 40301', r.status === 403 && codeOf(r) === 40301);

    r = await GET('/api/v1/resumes', { token: mainToken });
    check('新账号简历列表为空', r.status === 200 && Array.isArray(dataOf(r)) && dataOf(r).length === 0);

    r = await POST('/api/v1/resumes', {
      token: mainToken,
      body: { name: 'T7 默认简历', content: resumeContent('T7 测试求职者', newPhone) },
    });
    d = dataOf(r);
    const resumeA = d.id;
    if (resumeA) createdResumeIds.push(resumeA);
    check('创建首份简历 → 自动默认 / content 原样返回',
      r.status === 200 && d.isDefault === true && d.content?.educations?.length === 1
      && d.content?.skills?.includes('TypeScript') && d.userId === mainUserId,
      `id=${d.id}`);

    r = await POST('/api/v1/resumes', {
      token: mainToken,
      body: { name: '缺少手机号的简历', content: { basics: { name: 'T7' }, educations: [], experiences: [], projects: [], skills: [] } },
    });
    check('创建非法简历 → 422 + 42201', r.status === 422 && codeOf(r) === 42201,
      `HTTP ${r.status} code=${codeOf(r)}`);

    r = await POST('/api/v1/resumes', {
      token: mainToken,
      body: { name: 'T7 第二份简历', content: resumeContent('T7 测试求职者', newPhone, 'Java') },
    });
    d = dataOf(r);
    const resumeB = d.id;
    if (resumeB) createdResumeIds.push(resumeB);
    check('创建第二份简历 → 非默认', r.status === 200 && d.isDefault === false && d.id !== resumeA);

    r = await PATCH('/api/v1/resumes/' + resumeB, {
      token: mainToken,
      body: {
        name: 'T7 更新后的简历',
        content: resumeContent('T7 更新姓名', newPhone, 'Spring'),
        attachmentUrl: '/uploads/smoke-resume.pdf',
      },
    });
    d = dataOf(r);
    check('更新简历 → 名称 / content / attachmentUrl 生效',
      r.status === 200 && d.name === 'T7 更新后的简历'
      && d.content?.basics?.name === 'T7 更新姓名'
      && d.content?.skills?.includes('Spring')
      && d.attachmentUrl === '/uploads/smoke-resume.pdf');

    r = await GET('/api/v1/resumes/' + resumeB, { token: mainToken });
    check('读取本人简历详情', r.status === 200 && dataOf(r).id === resumeB);

    r = await POST('/api/v1/resumes/' + resumeB + '/set-default', { token: mainToken });
    check('设为默认简历 → isDefault=true', r.status === 200 && dataOf(r).isDefault === true);

    r = await GET('/api/v1/resumes', { token: mainToken });
    d = dataOf(r);
    check('列表默认优先 / 共 2 份',
      r.status === 200 && d.length === 2 && d[0].id === resumeB && d[0].isDefault === true
      && d[1].id === resumeA && d[1].isDefault === false);

    r = await GET('/api/v1/resumes/' + resumeB, { token: hrToken });
    check('HR 未收到投递不可查看简历 → 404 + 40410',
      r.status === 404 && codeOf(r) === 40410, `HTTP ${r.status} code=${codeOf(r)}`);

    r = await DELETE('/api/v1/resumes/' + resumeA, { token: mainToken });
    check('删除非默认简历 → {}', r.status === 200 && codeOf(r) === 0);

    r = await GET('/api/v1/resumes/' + resumeA, { token: mainToken });
    check('已删除简历详情不可读 → 404 + 40410', r.status === 404 && codeOf(r) === 40410);

    r = await DELETE('/api/v1/resumes/' + resumeB, { token: mainToken });
    check('删除默认简历 → {}', r.status === 200 && codeOf(r) === 0);

    r = await GET('/api/v1/resumes', { token: mainToken });
    check('删除后简历列表为空', r.status === 200 && dataOf(r).length === 0);

    // ============================== §11 收藏 ==============================
    console.log('== §11 收藏 ==');

    r = await GET('/api/v1/jobs?limit=3');
    const jobIds = (dataOf(r).list || []).map((job) => job.id);
    check('准备 3 个可见职位', jobIds.length === 3, jobIds.join(','));

    for (const jobId of jobIds) {
      r = await POST('/api/v1/favorites', { token: mainToken, body: { jobId } });
      if (r.status === 200) favoriteJobIds.push(jobId);
      check('收藏职位 ' + jobId + ' → 200', r.status === 200 && dataOf(r).jobId === jobId);
    }

    r = await POST('/api/v1/favorites', { token: mainToken, body: { jobId: jobIds[0] } });
    check('重复收藏 → 409 + 40910', r.status === 409 && codeOf(r) === 40910,
      `HTTP ${r.status} code=${codeOf(r)}`);

    r = await POST('/api/v1/favorites', { token: mainToken, body: { jobId: 'job-not-exist' } });
    check('收藏不存在职位 → 404 + 40410', r.status === 404 && codeOf(r) === 40410);

    r = await POST('/api/v1/favorites', { token: hrToken, body: { jobId: jobIds[0] } });
    check('HR 收藏职位 → 403 + 40301', r.status === 403 && codeOf(r) === 40301);

    r = await GET('/api/v1/favorites?limit=2', { token: mainToken });
    d = dataOf(r);
    const favPage1 = d.list || [];
    const favCursor = d.pagination?.nextCursor;
    check('收藏列表第 1 页 → 2 条 / total=3 / 含职位摘要',
      r.status === 200 && favPage1.length === 2 && d.pagination?.total === 3
      && favPage1.every((item) => item.job && item.job.id === item.jobId),
      `count=${favPage1.length} total=${d.pagination?.total}`);

    r = await GET('/api/v1/favorites?limit=2&cursor=' + encodeURIComponent(favCursor || ''), { token: mainToken });
    d = dataOf(r);
    const favPage2 = d.list || [];
    const allFavoriteIds = [...favPage1, ...favPage2].map((item) => item.id);
    check('收藏列表第 2 页 → 1 条 / 无重复 / total=3',
      favPage2.length === 1 && new Set(allFavoriteIds).size === 3 && d.pagination?.total === 3,
      `count=${favPage2.length}`);

    for (const jobId of jobIds) {
      r = await DELETE('/api/v1/favorites/' + jobId, { token: mainToken });
      check('取消收藏 ' + jobId + ' → {}', r.status === 200 && codeOf(r) === 0);
    }
    favoriteJobIds = [];

    r = await DELETE('/api/v1/favorites/' + jobIds[0], { token: mainToken });
    check('重复取消收藏幂等 → 200', r.status === 200 && codeOf(r) === 0);

    r = await GET('/api/v1/favorites', { token: mainToken });
    check('取消后收藏列表 total=0', r.status === 200 && dataOf(r).pagination?.total === 0);

    // ============================== §12 上传 ==============================
    console.log('== §12 文件上传 ==');

    r = await POST('/api/v1/uploads/sign', {
      body: { type: 'avatar', contentType: 'image/png', ext: 'png' },
    });
    check('未登录获取上传签名 → 401 + 40101', r.status === 401 && codeOf(r) === 40101);

    r = await POST('/api/v1/uploads/sign', {
      token: mainToken,
      body: { type: 'avatar', contentType: 'image/png', ext: 'png' },
    });
    const sign = dataOf(r);
    check('获取头像上传签名 → objectKey / uploadUrl / expiresAt',
      r.status === 200 && sign.objectKey?.startsWith('upload-avatar-u-reg-')
      && sign.uploadUrl?.startsWith('/api/v1/uploads/local/')
      && typeof sign.expiresAt === 'string',
      `objectKey=${sign.objectKey}`);

    const pngBytes = Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 1, 2, 3, 4]);
    const putRes = await fetch(base + sign.uploadUrl, {
      method: 'PUT',
      headers: { 'Content-Type': 'image/png' },
      body: pngBytes,
    });
    let putJson = null;
    try {
      putJson = await putRes.json();
    } catch {
      /* ignore */
    }
    check('预签名 PUT 直传成功 → 200', putRes.status === 200 && putJson?.code === 0,
      `HTTP ${putRes.status}`);

    r = await POST('/api/v1/uploads/confirm', {
      token: mainToken,
      body: { objectKey: sign.objectKey },
    });
    d = dataOf(r);
    check('确认上传 → /uploads URL / contentType / size',
      r.status === 200 && d.url === '/uploads/' + sign.objectKey
      && d.contentType === 'image/png' && d.size === pngBytes.length,
      `size=${d.size}`);

    const publicRes = await fetch(base + d.url);
    const publicBytes = Buffer.from(await publicRes.arrayBuffer());
    check('静态 /uploads/** 可访问且内容一致',
      publicRes.status === 200 && publicRes.headers.get('content-type')?.startsWith('image/png')
      && publicBytes.length === pngBytes.length,
      `HTTP ${publicRes.status} bytes=${publicBytes.length}`);

    r = await POST('/api/v1/uploads/sign', {
      token: mainToken,
      body: { type: 'avatar', contentType: 'application/pdf', ext: 'pdf' },
    });
    check('头像上传 PDF → 422 + 42201', r.status === 422 && codeOf(r) === 42201,
      `HTTP ${r.status} code=${codeOf(r)}`);

    r = await POST('/api/v1/uploads/sign', {
      token: mainToken,
      body: { type: 'avatar', contentType: 'image/png', ext: 'jpg' },
    });
    check('扩展名与 MIME 不匹配 → 422 + 42201', r.status === 422 && codeOf(r) === 42201);

    r = await POST('/api/v1/uploads/sign', {
      token: mainToken,
      body: { type: 'resume', contentType: 'application/pdf', ext: 'pdf' },
    });
    check('简历 PDF 上传签名可用', r.status === 200 && dataOf(r).objectKey?.includes('-resume-'));

    const badSign = await POST('/api/v1/uploads/sign', {
      token: mainToken,
      body: { type: 'avatar', contentType: 'image/png', ext: 'png' },
    });
    const badUrl = dataOf(badSign).uploadUrl.replace(/sig=[^&]+/, 'sig=invalid');
    const badPut = await fetch(base + badUrl, {
      method: 'PUT',
      headers: { 'Content-Type': 'image/png' },
      body: pngBytes,
    });
    let badJson = null;
    try {
      badJson = await badPut.json();
    } catch {
      /* ignore */
    }
    check('篡改上传签名 → 403 + 40301', badPut.status === 403 && badJson?.code === 40301,
      `HTTP ${badPut.status} code=${badJson?.code}`);

    r = await POST('/api/v1/uploads/confirm', {
      token: mainToken,
      body: { objectKey: 'upload-avatar-u-seeker-1-1.png' },
    });
    check('确认他人 objectKey → 404 + 40410', r.status === 404 && codeOf(r) === 40410,
      `HTTP ${r.status} code=${codeOf(r)}`);
  } catch (e) {
    failed += 1;
    console.error('\n未捕获错误：', e);
  } finally {
    // 清理测试数据（失败也尽量清理，避免影响 verify-seed / 后续冒烟）
    if (mainToken) {
      for (const jobId of favoriteJobIds) {
        try {
          await DELETE('/api/v1/favorites/' + jobId, { token: mainToken });
        } catch {
          /* best effort */
        }
      }
      for (const resumeId of createdResumeIds) {
        try {
          await DELETE('/api/v1/resumes/' + resumeId, { token: mainToken });
        } catch {
          /* best effort */
        }
      }
    }
  }

  await sleep(20);
  console.log(`\n结果：${passed}/${passed + failed} 通过${failed ? `，${failed} 项失败` : ''}`);
  if (failed) process.exit(1);
})();
