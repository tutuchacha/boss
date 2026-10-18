// 聘聊 · 职位与字典接口冒烟（Step 3 T3a；契约 docs/api-endpoints.md §6.1~§6.4、§6.9 + §7，26 项）
// 用法：后端启动后运行 `node scripts/smoke-jobs.mjs`（可用 PINLIAO_BASE 覆盖地址）
// 前置：Flyway V1~V8 已迁移（可见职位 38 / 云杉 7 / 分类 84（17 根）/ 城市 9）；MySQL + Redis 运行中
// 演示账号：13800000001（求职者）/ 13800000002（HR），密码均 123456
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
    /* 304 空响应等 */
  }
  return { status: res.status, headers: res.headers, json, text };
}
const GET = (path, opts) => http('GET', path, opts);
const POST = (path, opts) => http('POST', path, opts);
const dataOf = (r) => (r.json && typeof r.json === 'object' ? r.json.data : undefined) || {};

(async () => {
  console.log('== §6.1 列表（公开浏览） ==');

  let r = await GET('/api/v1/jobs');
  let d = dataOf(r);
  check('匿名访问列表 code=0 且默认 limit=20', r.status === 200 && r.json?.code === 0 && d.list?.length === 20,
    `HTTP ${r.status} list=${d.list?.length}`);

  let pag = d.pagination || {};
  check('total=38 / hasMore / nextCursor', pag.total === 38 && pag.hasMore === true && typeof pag.nextCursor === 'string',
    `total=${pag.total} hasMore=${pag.hasMore}`);

  check('Cache-Control 30s', (r.headers.get('cache-control') || '').includes('max-age=30'),
    r.headers.get('cache-control'));

  check('匿名默认排序首条为最新发布 job-1', d.list?.[0]?.id === 'job-1', `首条=${d.list?.[0]?.id}`);

  r = await GET('/api/v1/jobs?limit=100');
  d = dataOf(r);
  check('limit=100 返回全部 38 条且无更多', d.list?.length === 38 && d.pagination?.hasMore === false
    && d.pagination?.nextCursor === null, `list=${d.list?.length}`);

  const p1 = await GET('/api/v1/jobs?limit=20');
  const cursor = dataOf(p1).pagination?.nextCursor;
  const p2 = await GET('/api/v1/jobs?limit=20&cursor=' + encodeURIComponent(cursor));
  const merged = [...(dataOf(p1).list || []).map((j) => j.id), ...(dataOf(p2).list || []).map((j) => j.id)];
  check('游标翻页：第二页 18 条，合并 38 条不重不漏',
    (dataOf(p2).list || []).length === 18 && merged.length === 38 && new Set(merged).size === 38,
    `第二页=${(dataOf(p2).list || []).length}`);

  r = await GET('/api/v1/jobs?city=' + encodeURIComponent('上海') + '&limit=100');
  let list = dataOf(r).list || [];
  check('城市筛选：上海全部命中', list.length > 0 && list.every((j) => j.city === '上海'), `count=${list.length}`);

  r = await GET('/api/v1/jobs?cat=' + encodeURIComponent('互联网') + '&sub=' + encodeURIComponent('前端开发') + '&limit=100');
  list = dataOf(r).list || [];
  check('分类联动：互联网/前端开发全部命中',
    list.length > 0 && list.every((j) => j.cat === '互联网' && j.sub === '前端开发'), `count=${list.length}`);

  r = await GET('/api/v1/jobs?exp=' + encodeURIComponent('1-3年') + '&limit=100');
  list = dataOf(r).list || [];
  check('经验筛选生效', list.length > 0 && list.every((j) => j.exp === '1-3年'), `count=${list.length}`);

  r = await GET('/api/v1/jobs?sal=' + encodeURIComponent('20-40K') + '&limit=100');
  list = dataOf(r).list || [];
  check('薪资区间相交筛选', list.length > 0 && list.every((j) => j.salaryMin < 40 && j.salaryMax > 20),
    `count=${list.length}`);

  r = await GET('/api/v1/jobs?q=' + encodeURIComponent('云杉') + '&limit=100');
  list = dataOf(r).list || [];
  check('关键词命中公司名（云杉 7 条）',
    list.length === 7 && list.every((j) => j.company?.name === '云杉科技'), `count=${list.length}`);

  r = await GET('/api/v1/jobs?q=' + encodeURIComponent('前端') + '&limit=100');
  list = dataOf(r).list || [];
  check('关键词命中标题/岗位/标签', list.length > 0 && list.every((j) => JSON.stringify(j).includes('前端')),
    `count=${list.length}`);

  r = await GET('/api/v1/search/jobs?q=' + encodeURIComponent('云杉') + '&limit=100');
  check('§6.2 搜索独立路径与 §6.1 同实现', (dataOf(r).list || []).length === 7,
    `count=${(dataOf(r).list || []).length}`);

  const seekerLogin = await POST('/api/v1/auth/login/password', {
    body: { account: '13800000001', password: '123456' },
  });
  const seekerToken = dataOf(seekerLogin).accessToken;
  r = await GET('/api/v1/jobs?sort=recommend&limit=100', { token: seekerToken });
  list = dataOf(r).list || [];
  check('登录求职者 recommend 期望岗位置顶',
    list.length > 0 && list[0].sub === '前端开发' && list[0].cat === '互联网', `首条=${list[0]?.title}`);

  r = await GET('/api/v1/jobs?sort=latest', { token: seekerToken });
  check('sort=latest 首条为最新发布 job-1', (dataOf(r).list || [])[0]?.id === 'job-1',
    `首条=${(dataOf(r).list || [])[0]?.id}`);

  const hrLogin = await POST('/api/v1/auth/login/password', {
    body: { account: '13800000002', password: '123456' },
  });
  const hrToken = dataOf(hrLogin).accessToken;
  r = await GET('/api/v1/jobs?limit=100', { token: hrToken });
  check('HR 账号公开浏览不受租户隔离（仍是 38 条）', (dataOf(r).list || []).length === 38,
    `count=${(dataOf(r).list || []).length}`);

  console.log('== §6.3/§6.4/§6.9 详情 / 相似 / 埋点 ==');

  r = await GET('/api/v1/jobs/job-1');
  d = dataOf(r);
  check('详情字段（公司 / HR / 薪资 / 职责 / 福利）',
    r.status === 200 && r.json?.code === 0 && d.id === 'job-1' && d.salary === '20-35K'
    && d.company?.name === '云杉科技' && d.companyId === 'co-acgiw7'
    && d.hr?.id === 'hr-x2270n' && d.hr?.name === '陈女士' && d.hr?.title === '技术招聘经理'
    && typeof d.responsibilities === 'string' && d.responsibilities.length > 10
    && Array.isArray(d.welfare) && d.welfare.length === 4,
    `salary=${d.salary} hr=${d.hr?.name} welfare=${d.welfare?.length}`);

  check('相似职位规则（≤6 条 / 同 sub 或同 cat / 排除自身）',
    Array.isArray(d.similarJobs) && d.similarJobs.length > 0 && d.similarJobs.length <= 6
    && d.similarJobs.every((s) => s.id !== 'job-1' && (s.sub === d.sub || s.cat === d.cat)),
    `count=${d.similarJobs?.length}`);

  const etag = r.headers.get('etag');
  check('详情 Cache-Control 60s + ETag', (r.headers.get('cache-control') || '').includes('max-age=60') && !!etag, etag);

  r = await GET('/api/v1/jobs/job-1', { headers: { 'If-None-Match': etag } });
  check('If-None-Match 命中返回 304', r.status === 304, `HTTP ${r.status}`);

  r = await GET('/api/v1/jobs/not-exist-job');
  check('不可见职位 404 + 40410', r.status === 404 && r.json?.code === 40410, `HTTP ${r.status} code=${r.json?.code}`);

  r = await GET('/api/v1/jobs/job-1/similar?limit=3');
  check('相似职位独立路径 limit=3', r.status === 200 && (dataOf(r).length || 0) <= 3 && (dataOf(r).length || 0) > 0,
    `count=${(dataOf(r).length || 0)}`);

  const v0 = dataOf(await GET('/api/v1/jobs/job-1')).viewCount;
  await POST('/api/v1/jobs/job-1/view');
  const v1 = dataOf(await GET('/api/v1/jobs/job-1')).viewCount;
  await POST('/api/v1/jobs/job-1/view');
  const v2 = dataOf(await GET('/api/v1/jobs/job-1')).viewCount;
  check('浏览埋点：首次至多 +1，窗口内重复不累加', (v1 === v0 || v1 === v0 + 1) && v2 === v1, `${v0}→${v1}→${v2}`);

  r = await POST('/api/v1/jobs/not-exist-job/view');
  check('不存在职位埋点容错 200', r.status === 200 && r.json?.code === 0, `HTTP ${r.status}`);

  r = await GET('/api/v1/jobs?cursor=' + encodeURIComponent('!!!bad-cursor!!!'));
  check('非法游标 400 + 40001', r.status === 400 && r.json?.code === 40001, `HTTP ${r.status} code=${r.json?.code}`);

  console.log('== §7 分类 / 城市 / 字典 ==');

  r = await GET('/api/v1/categories');
  const roots = dataOf(r);
  const totalNodes = (nodes) => nodes.reduce((n, x) => n + 1 + totalNodes(x.children || []), 0);
  check('分类树 17 根 / 84 节点 / 首根互联网 7 子',
    Array.isArray(roots) && roots.length === 17 && totalNodes(roots) === 84
    && roots[0]?.name === '互联网' && roots[0]?.children?.length === 7,
    `根=${roots?.length} 总=${totalNodes(Array.isArray(roots) ? roots : [])}`);
  check('分类缓存 24h', (r.headers.get('cache-control') || '').includes('max-age=86400'),
    r.headers.get('cache-control'));

  r = await GET('/api/v1/cities');
  const cities = dataOf(r);
  check('城市 9 座 / 热门 5 / 全国置顶',
    Array.isArray(cities) && cities.length === 9 && cities.filter((c) => c.hot).length === 5
    && cities[0]?.name === '全国' && cities[0]?.hot === false,
    `count=${cities?.length}`);

  r = await GET('/api/v1/dict');
  const dict = dataOf(r);
  check('字典枚举与 mock 完全一致',
    JSON.stringify(dict.exps) === JSON.stringify(['不限', '应届', '1-3年', '3-5年', '5-10年'])
    && JSON.stringify(dict.edus) === JSON.stringify(['学历不限', '大专', '本科', '硕士', '博士'])
    && JSON.stringify(dict.salaryRanges) === JSON.stringify(['不限', '10K以下', '10-20K', '20-40K', '40K以上'])
    && JSON.stringify(dict.welfare) === JSON.stringify(['五险一金', '带薪年假', '年度体检', '弹性工作', '年终奖', '餐补']));

  console.log(`\n结果：${passed}/${passed + failed} 通过${failed ? '（' + failed + ' 项失败）' : ''}`);
  if (failed) process.exit(1);
})().catch((e) => {
  console.error('FAILED', e);
  process.exit(1);
});
