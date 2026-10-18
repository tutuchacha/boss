// 聘聊 · 鉴权接口冒烟（Step 3 T2a/T2b；契约 docs/api-endpoints.md §1.1~§1.7，20 项）
// 用法：后端启动后运行 `node scripts/smoke-auth.mjs`（可用 PINLIAO_BASE 覆盖地址）
// 依赖：Node 18+（原生 fetch）；演示账号 13800000001 / 13800000002，密码均 123456，验证码固定 123456
const base = process.env.PINLIAO_BASE || 'http://localhost:8080';

async function post(path, body, token) {
  const headers = { 'Content-Type': 'application/json' };
  if (token) headers.Authorization = 'Bearer ' + token;
  const r = await fetch(base + path, { method: 'POST', headers, body: JSON.stringify(body) });
  let d;
  try { d = await r.json(); } catch { d = await r.text(); }
  return { status: r.status, d };
}
async function get(path, token) {
  const headers = token ? { Authorization: 'Bearer ' + token } : {};
  const r = await fetch(base + path, { headers });
  let d;
  try { d = await r.json(); } catch { d = await r.text(); }
  return { status: r.status, d };
}

(async () => {
  let r = await post('/api/v1/auth/login/password', { account: '13800000001', password: '123456' });
  console.log('1) seeker login |', r.status, r.d.code, '|', r.d.data.user.role, '|', r.d.data.profile.name, '|', r.d.data.profile.expectCity);

  r = await post('/api/v1/auth/login/password', { account: '13800000002', password: '123456' });
  console.log('2) hr login     |', r.status, r.d.code, '|', r.d.data.company.name, '|', r.d.data.member.role, '|', r.d.data.profile.title);
  const hrAt = r.d.data.accessToken;
  const hrRt = r.d.data.refreshToken;

  r = await post('/api/v1/auth/login/password', { account: '13800000001', password: 'wrong' });
  console.log('3) wrong pwd    |', r.status, r.d.code, '|', r.d.message);

  r = await get('/api/v1/auth/me');
  console.log('4) me no token  |', r.status, r.d.code, '|', r.d.message);

  r = await get('/api/v1/auth/me', hrAt);
  console.log('5) me hr        |', r.status, r.d.code, '|', r.d.data.user.phone, '|', r.d.data.company.name);

  r = await post('/api/v1/auth/sms/code', { phone: '13800000002', scene: 'LOGIN' });
  console.log('6) sms send     |', r.status, r.d.code, '| nextRetryIn=', r.d.data.nextRetryIn);

  r = await post('/api/v1/auth/sms/code', { phone: '13800000002', scene: 'LOGIN' });
  console.log('7) sms resend   |', r.status, r.d.code, '|', r.d.message);

  r = await post('/api/v1/auth/login/sms', { phone: '13800000002', code: '000000' });
  console.log('8) sms bad code |', r.status, r.d.code, '|', r.d.message);

  r = await post('/api/v1/auth/login/sms', { phone: '13800000002', code: '123456' });
  console.log('9) sms login    |', r.status, r.d.code, '|', r.d.data.user.role, '|', r.d.data.company.name);

  r = await post('/api/v1/auth/refresh', { refreshToken: hrRt });
  console.log('10) refresh     |', r.status, r.d.code, '| newPair=', !!(r.d.data.accessToken && r.d.data.refreshToken));
  const hrAt2 = r.d.data.accessToken;
  const hrRt2 = r.d.data.refreshToken;

  r = await post('/api/v1/auth/refresh', { refreshToken: hrRt });
  console.log('11) reuse old   |', r.status, r.d.code, '|', r.d.message);

  r = await post('/api/v1/auth/logout', { refreshToken: hrRt2 }, hrAt2);
  console.log('12) logout      |', r.status, r.d.code, '| data=', JSON.stringify(r.d.data));

  r = await get('/api/v1/auth/me', hrAt2);
  console.log('13) me after out|', r.status, r.d.code, '|', r.d.message);

  r = await post('/api/v1/auth/sms/code', { phone: '13800000001', scene: 'BAD' });
  console.log('14) bad scene   |', r.status, r.d.code, '|', r.d.message, '|', JSON.stringify(r.d.errors));

  // ============================== §1.2 注册（T2b） ==============================
  // 每次运行用时间派生的新手机号（注册会真实落库；u-reg-* / co-reg-* 记录不参与 verify-seed 抽查）
  const tail8 = String(Date.now()).slice(-8);
  const hrPhone = '137' + tail8;
  const seekerPhone = '136' + tail8;

  r = await post('/api/v1/auth/sms/code', { phone: hrPhone, scene: 'REGISTER' });
  console.log('15) reg sms send|', r.status, r.d.code, '| phone=', hrPhone);

  r = await post('/api/v1/auth/register', { phone: hrPhone, code: '000000', password: '123456', role: 'HR' });
  console.log('16) reg bad code|', r.status, r.d.code, '|', r.d.message);

  r = await post('/api/v1/auth/register', { phone: hrPhone, code: '123456', password: '123456', role: 'HR' });
  const regAt = r.d.data.accessToken;
  console.log('17) reg hr ok   |', r.status, r.d.code, '|', r.d.data.company.name, '|', r.d.data.member.role,
    '|', r.d.data.profile.name, '| verified=', r.d.data.company.verified);

  r = await post('/api/v1/auth/register', { phone: hrPhone, code: '123456', password: '123456', role: 'HR' });
  console.log('18) reg dup     |', r.status, r.d.code, '|', r.d.message);

  r = await get('/api/v1/auth/me', regAt);
  console.log('19) me reg hr   |', r.status, r.d.code, '|', r.d.data.user.phone, '|', r.d.data.company.name);

  r = await post('/api/v1/auth/sms/code', { phone: seekerPhone, scene: 'REGISTER' });
  r = await post('/api/v1/auth/register', { phone: seekerPhone, code: '123456', password: '123456', role: 'JOB_SEEKER' });
  console.log('20) reg seeker  |', r.status, r.d.code, '|', r.d.data.profile.name, '| company=', r.d.data.company);
})().catch(e => { console.error('FAILED', e); process.exit(1); });
