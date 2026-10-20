// 聘聊 · T-AI1~T-AI4 AI 冒烟（Step 4）：索引 + 单简历问答（非流式 / SSE 流式）+ 语义人才检索
// 用法：后端启动后运行 `node scripts/smoke-ai.mjs`（可用 PINLIAO_BASE 覆盖地址）
// 前置：Flyway V1~V9 已迁移；MySQL + Redis 运行中；后端以「假向量」启动：
//         $env:PINLIAO_AI_ENABLED='true'
//         $env:PINLIAO_AI_PROVIDER='fake'
// 说明：
//  - 默认拒绝在 provider=springai（真实上游）下执行，避免冒烟消耗嵌入额度；
//    确实要验证真实链路时加 `--allow-real`（会产生少量费用）。
//  - 只重建索引，不改业务数据；假向量模型 id 为 fake-bag-of-words-v1，维度跟随
//    PINLIAO_AI_EMBEDDING_DIM（默认 1024）。
//  - 幂等判据：同一份内容第二次重建 embedded=0、reused=chunks（不重复计费）。
//  - T-AI2 问答段使用假对话模型（provider=fake）验证「检索 → 引用 → 无依据」链路；
//    真实 provider 下问答会产生对话费用，同样受 --allow-real 保护。
//  - T-AI3 流式段断言「delta → citation → done」事件序列与拼接正文，
//    并校验校验类错误仍是普通 JSON 包络（不是半截 SSE）。
//  - T-AI4 语义检索段断言权限 / 入参 / 相似度排序 / 证据结构 / 标签单独可用 / limit / 跨公司不可见。
const base = process.env.PINLIAO_BASE || 'http://localhost:8080';
const allowReal = process.argv.includes('--allow-real');

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
const dataOf = (r) => (r.json && typeof r.json === 'object' ? r.json.data : undefined);
const codeOf = (r) => (r.json && typeof r.json === 'object' ? r.json.code : undefined);

/**
 * SSE 请求（T-AI3）：返回状态、响应头与逐条解析后的事件。
 * 校验类错误仍是普通 JSON 包络（非 2xx、非 text/event-stream），此时只回 json、events 为空。
 * Accept 必须同时包含 application/json —— 只接受 text/event-stream 时 Spring 无法写出错误包络（406 → 500）。
 */
async function sse(path, { token, body } = {}) {
  const h = { Accept: 'text/event-stream, application/json' };
  if (token) h.Authorization = 'Bearer ' + token;
  if (body !== undefined) h['Content-Type'] = 'application/json';
  const res = await fetch(base + path, {
    method: 'POST',
    headers: h,
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const contentType = res.headers.get('content-type') || '';
  if (!res.ok || !contentType.includes('text/event-stream')) {
    const text = await res.text();
    let json = null;
    try {
      json = JSON.parse(text);
    } catch {
      /* 非 JSON */
    }
    return { status: res.status, contentType, json, events: [], ttfbMs: null, totalMs: null };
  }

  const t0 = Date.now();
  const reader = res.body.getReader();
  const decoder = new TextDecoder();
  const events = [];
  let buffer = '';
  let ttfbMs = null;
  for (;;) {
    const { done, value } = await reader.read();
    if (done) break;
    if (ttfbMs === null) ttfbMs = Date.now() - t0;
    buffer += decoder.decode(value, { stream: true }).replace(/\r\n/g, '\n');
    let idx;
    while ((idx = buffer.indexOf('\n\n')) >= 0) {
      const block = buffer.slice(0, idx);
      buffer = buffer.slice(idx + 2);
      const evt = { event: 'message', data: '', atMs: Date.now() - t0 };
      const dataLines = [];
      for (const line of block.split('\n')) {
        if (line.startsWith('event:')) evt.event = line.slice(6).trim();
        else if (line.startsWith('data:')) dataLines.push(line.slice(5).replace(/^ /, ''));
      }
      if (dataLines.length) {
        evt.data = dataLines.join('\n');
        events.push(evt);
      }
    }
  }
  return {
    status: res.status,
    contentType,
    cacheControl: res.headers.get('cache-control'),
    noBuffer: res.headers.get('x-accel-buffering'),
    events,
    ttfbMs,
    totalMs: Date.now() - t0,
  };
}
const parse = (e) => {
  try {
    return JSON.parse(e.data);
  } catch {
    return null;
  }
};

(async () => {
  let hrToken = null;
  let seekerToken = null;

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
    seekerToken = dataOf(seekerLogin)?.accessToken;
    check('演示求职者登录成功', typeof seekerToken === 'string');

    let r = await GET('/api/v1/ai/status', { token: hrToken });
    const status = dataOf(r) || {};
    check('AI 状态：enabled=true', r.status === 200 && codeOf(r) === 0 && status.enabled === true,
      `enabled=${status.enabled} provider=${status.provider}`);
    if (!status.enabled) {
      console.log('\n后端 AI 未启用：请用 PINLIAO_AI_ENABLED=true 重启后端后再跑本脚本。');
      process.exitCode = 2;
      return;
    }
    if (status.provider !== 'fake' && !allowReal) {
      console.log('\n当前 provider=' + status.provider + '（真实上游）。为避免消耗嵌入 / 对话额度，脚本已停止；'
        + '如确认要测真实链路请加 --allow-real。');
      process.exitCode = 2;
      return;
    }

    // ============================== 权限 ==============================
    console.log('== §权限：索引接口仅 HR / 管理员 ==');
    r = await POST('/api/v1/ai/index/rebuild');
    check('未登录重建 → 401 + 40101', r.status === 401 && codeOf(r) === 40101,
      `HTTP ${r.status} code=${codeOf(r)}`);

    r = await POST('/api/v1/ai/index/rebuild', { token: seekerToken });
    check('求职者重建 → 403 + 40301', r.status === 403 && codeOf(r) === 40301,
      `HTTP ${r.status} code=${codeOf(r)}`);

    // ============================== §全量重建 ==============================
    console.log('== §全量重建 ==');
    r = await POST('/api/v1/ai/index/rebuild', { token: hrToken });
    const first = dataOf(r) || {};
    check('全量重建成功：scope=all 且简历数 / 分块数合理',
      r.status === 200 && codeOf(r) === 0 && first.scope === 'all'
      && first.resumes >= 7 && first.chunks >= 20,
      `resumes=${first.resumes} chunks=${first.chunks} skipped=${first.skipped}`);
    check('首次重建全部新嵌入（embedded=chunks，reused=0）',
      first.embedded === first.chunks && first.reused === 0,
      `embedded=${first.embedded} reused=${first.reused}`);

    r = await POST('/api/v1/ai/index/rebuild', { token: hrToken });
    const second = dataOf(r) || {};
    check('重复重建幂等：分块数不变',
      r.status === 200 && second.chunks === first.chunks,
      `first=${first.chunks} second=${second.chunks}`);
    check('重复重建零费用：embedded=0 且 reused=chunks',
      second.embedded === 0 && second.reused === second.chunks,
      `embedded=${second.embedded} reused=${second.reused}`);

    // ============================== §单份重建 ==============================
    console.log('== §单份重建 ==');
    r = await POST('/api/v1/ai/index/rebuild', { token: hrToken, body: { resumeId: 'resume-u-cand-1' } });
    const single = dataOf(r) || {};
    check('单份重建：scope=resume 且 5 个分块（基本信息 / 教育 / 工作 / 项目 / 技能）',
      r.status === 200 && single.scope === 'resume' && single.resumes === 1 && single.chunks === 5,
      `resumes=${single.resumes} chunks=${single.chunks}`);
    check('单份重建复用已有向量（embedded=0）', single.embedded === 0 && single.reused === 5,
      `embedded=${single.embedded} reused=${single.reused}`);

    r = await POST('/api/v1/ai/index/rebuild', { token: hrToken, body: { resumeId: 'resume-not-exist' } });
    check('重建不存在的简历 → 404 + 40401', r.status === 404 && codeOf(r) === 40401,
      `HTTP ${r.status} code=${codeOf(r)}`);

    // ============================== §索引状态 ==============================
    console.log('== §索引状态（按投递校验可见性） ==');
    r = await GET('/api/v1/hr/applications/app-hr-01/ai/index-status', { token: hrToken });
    const indexStatus = dataOf(r) || {};
    check('HR 查看本公司候选人索引状态 → 已就绪',
      r.status === 200 && codeOf(r) === 0
      && indexStatus.resumeId === 'resume-u-cand-1'
      && indexStatus.indexed === true
      && indexStatus.chunkCount === 5
      && typeof indexStatus.model === 'string'
      && indexStatus.dim > 0,
      `chunks=${indexStatus.chunkCount} model=${indexStatus.model} dim=${indexStatus.dim}`);

    r = await GET('/api/v1/hr/applications/app-hr-01/ai/index-status', { token: seekerToken });
    check('求职者查看索引状态 → 403 + 40301', r.status === 403 && codeOf(r) === 40301,
      `HTTP ${r.status} code=${codeOf(r)}`);

    r = await GET('/api/v1/hr/applications/app-hr-01/ai/index-status');
    check('未登录查看索引状态 → 401 + 40101', r.status === 401 && codeOf(r) === 40101,
      `HTTP ${r.status} code=${codeOf(r)}`);

    r = await GET('/api/v1/hr/applications/app-seek-01/ai/index-status', { token: hrToken });
    check('跨公司投递 → 404（不泄露存在性）', r.status === 404 && codeOf(r) === 40401,
      `HTTP ${r.status} code=${codeOf(r)}`);

    r = await GET('/api/v1/hr/applications/app-not-exist/ai/index-status', { token: hrToken });
    check('不存在的投递 → 404 + 40401', r.status === 404 && codeOf(r) === 40401,
      `HTTP ${r.status} code=${codeOf(r)}`);

    // ============================== T-AI2 §问答：权限与校验 ==============================
    console.log('== §问答：权限与入参校验 ==');
    const qaPath = '/api/v1/hr/applications/app-hr-01/ai/qa';
    const askBody = (question) => ({ body: { question } });

    r = await POST(qaPath, askBody('候选人的技能包括 React 和 TypeScript 吗？'));
    check('未登录问答 → 401 + 40101', r.status === 401 && codeOf(r) === 40101,
      `HTTP ${r.status} code=${codeOf(r)}`);

    r = await POST(qaPath, { token: seekerToken, ...askBody('候选人的技能包括 React 和 TypeScript 吗？') });
    check('求职者问答 → 403 + 40301', r.status === 403 && codeOf(r) === 40301,
      `HTTP ${r.status} code=${codeOf(r)}`);

    r = await POST(qaPath, { token: hrToken, ...askBody('   ') });
    check('空问题 → 422 + 42201', r.status === 422 && codeOf(r) === 42201,
      `HTTP ${r.status} code=${codeOf(r)}`);

    r = await POST(qaPath, { token: hrToken, ...askBody('问'.repeat(501)) });
    check('超长问题（>500 字）→ 422 + 42201', r.status === 422 && codeOf(r) === 42201,
      `HTTP ${r.status} code=${codeOf(r)}`);

    r = await POST('/api/v1/hr/applications/app-seek-01/ai/qa',
      { token: hrToken, ...askBody('候选人的技能是什么？') });
    check('跨公司投递问答 → 404（不泄露存在性）', r.status === 404 && codeOf(r) === 40401,
      `HTTP ${r.status} code=${codeOf(r)}`);

    r = await POST('/api/v1/hr/applications/app-not-exist/ai/qa',
      { token: hrToken, ...askBody('候选人的技能是什么？') });
    check('不存在投递问答 → 404 + 40401', r.status === 404 && codeOf(r) === 40401,
      `HTTP ${r.status} code=${codeOf(r)}`);

    // ============================== T-AI2 §问答：带引用回答 ==============================
    console.log('== §问答：带引用的回答 ==');
    r = await POST(qaPath, { token: hrToken, ...askBody('候选人的技能包括 React 和 TypeScript 吗？') });
    const answer = dataOf(r) || {};
    const citations = Array.isArray(answer.citations) ? answer.citations : [];
    const firstCitation = citations[0] || {};
    check('有依据的提问 → 200 + 0',
      r.status === 200 && codeOf(r) === 0, `HTTP ${r.status} code=${codeOf(r)}`);
    check('回答非空且带 [1] 引用标记',
      typeof answer.answer === 'string' && answer.answer.includes('[1]'),
      `answer=${String(answer.answer || '').slice(0, 40)}`);
    check('grounded=true 且至少 1 条引用',
      answer.grounded === true && citations.length >= 1,
      `grounded=${answer.grounded} citations=${citations.length}`);
    check('引用结构完整（index / chunkId / resumeId / section / title / snippet / score）',
      firstCitation.index === 1
      && typeof firstCitation.chunkId === 'string' && firstCitation.chunkId.startsWith('rc_')
      && firstCitation.resumeId === 'resume-u-cand-1'
      && ['basics', 'educations', 'experiences', 'projects', 'skills'].includes(firstCitation.section)
      && typeof firstCitation.title === 'string' && firstCitation.title.length > 0
      && typeof firstCitation.snippet === 'string' && firstCitation.snippet.length > 0
      && typeof firstCitation.score === 'number' && firstCitation.score > 0,
      `section=${firstCitation.section} score=${firstCitation.score} title=${firstCitation.title}`);
    check('返回假对话模型标识与检索命中数',
      answer.chatModel === 'fake-chat-v1' && typeof answer.retrieved === 'number' && answer.retrieved >= 1,
      `chatModel=${answer.chatModel} retrieved=${answer.retrieved} elapsedMs=${answer.elapsedMs}`);

    // ============================== T-AI2 §问答：无依据 ==============================
    console.log('== §问答：简历外问题如实说明 ==');
    r = await POST(qaPath, { token: hrToken, ...askBody('What is their experience with quantum chromodynamics?') });
    const noEvidence = dataOf(r) || {};
    check('无依据提问 → 200 且明确回答「简历中没有相关信息」',
      r.status === 200 && codeOf(r) === 0
      && noEvidence.answer === '简历中没有相关信息。'
      && noEvidence.grounded === false,
      `answer=${noEvidence.answer} grounded=${noEvidence.grounded}`);
    check('无依据时不编造引用（citations 为空）',
      Array.isArray(noEvidence.citations) && noEvidence.citations.length === 0,
      `citations=${Array.isArray(noEvidence.citations) ? noEvidence.citations.length : 'n/a'}`);

    // ============================== T-AI3 §流式问答：权限与校验（普通 JSON 包络） ==============================
    console.log('== §流式问答：校验类错误仍是 JSON 包络 ==');
    const streamPath = '/api/v1/hr/applications/app-hr-01/ai/qa/stream';

    let s = await sse(streamPath, askBody('候选人的技能包括 React 和 TypeScript 吗？'));
    check('未登录流式问答 → 401 + 40101（非 SSE）', s.status === 401 && codeOf(s) === 40101
      && !s.contentType.includes('text/event-stream') && s.events.length === 0,
      `HTTP ${s.status} code=${codeOf(s)} ct=${s.contentType}`);

    s = await sse(streamPath, { token: seekerToken, ...askBody('候选人的技能包括 React 和 TypeScript 吗？') });
    check('求职者流式问答 → 403 + 40301', s.status === 403 && codeOf(s) === 40301 && s.events.length === 0,
      `HTTP ${s.status} code=${codeOf(s)}`);

    s = await sse(streamPath, { token: hrToken, ...askBody('   ') });
    check('流式空问题 → 422 + 42201', s.status === 422 && codeOf(s) === 42201 && s.events.length === 0,
      `HTTP ${s.status} code=${codeOf(s)}`);

    s = await sse('/api/v1/hr/applications/app-seek-01/ai/qa/stream',
      { token: hrToken, ...askBody('候选人的技能是什么？') });
    check('流式跨公司投递 → 404 + 40401', s.status === 404 && codeOf(s) === 40401 && s.events.length === 0,
      `HTTP ${s.status} code=${codeOf(s)}`);

    // ============================== T-AI3 §流式问答：事件序列 ==============================
    console.log('== §流式问答：delta → citation → done ==');
    const hrApp = '/api/v1/hr/applications/app-hr-01/ai/qa/stream';
    s = await sse(hrApp, { token: hrToken, ...askBody('候选人的技能包括 React 和 TypeScript 吗？') });
    const deltas = s.events.filter((e) => e.event === 'delta');
    const citationEvents = s.events.filter((e) => e.event === 'citation');
    const doneEvents = s.events.filter((e) => e.event === 'done');
    const errorEvents = s.events.filter((e) => e.event === 'error');
    const done = doneEvents.length === 1 ? parse(doneEvents[0]) : null;
    const streamedText = deltas.map((e) => (parse(e) || {}).text || '').join('');
    const lastEvent = s.events.length ? s.events[s.events.length - 1].event : null;

    check('流式问答 → 200 + text/event-stream', s.status === 200 && s.contentType.includes('text/event-stream'),
      `HTTP ${s.status} ct=${s.contentType} ttfb=${s.ttfbMs}ms total=${s.totalMs}ms`);
    check('响应头禁用缓冲（Cache-Control / X-Accel-Buffering）',
      String(s.cacheControl || '').includes('no-cache') && s.noBuffer === 'no',
      `cache-control=${s.cacheControl} x-accel-buffering=${s.noBuffer}`);
    check('事件序列完整：≥1 个 delta + ≥1 个 citation + 1 个 done，且 done 收尾、无 error',
      deltas.length >= 1 && citationEvents.length >= 1 && doneEvents.length === 1
      && lastEvent === 'done' && errorEvents.length === 0,
      `delta=${deltas.length} citation=${citationEvents.length} done=${doneEvents.length} error=${errorEvents.length}`);
    check('每个 delta 都带非空 text', deltas.every((e) => typeof (parse(e) || {}).text === 'string'
      && (parse(e) || {}).text.length > 0));
    check('delta 拼接正文非空且含 [1] 引用标记',
      streamedText.length > 0 && streamedText.includes('[1]'),
      `spliced=${streamedText.slice(0, 40)}`);
    check('done 事件携带完整回答（含 citations / grounded / chatModel）',
      done !== null && done.answer === streamedText && done.grounded === true
      && Array.isArray(done.citations) && done.citations.length >= 1
      && done.chatModel === 'fake-chat-v1',
      `answer=${String(done?.answer || '').slice(0, 30)} citations=${done?.citations?.length}`);
    check('citation 事件先于 done，且结构与非流式一致',
      citationEvents.length >= 1
      && citationEvents.every((e) => e.atMs <= doneEvents[0].atMs)
      && (parse(citationEvents[0]) || {}).index === 1
      && String((parse(citationEvents[0]) || {}).chunkId || '').startsWith('rc_')
      && ['basics', 'educations', 'experiences', 'projects', 'skills']
        .includes((parse(citationEvents[0]) || {}).section),
      `firstSection=${(parse(citationEvents[0]) || {}).section}`);

    // ============================== T-AI3 §流式问答：无依据 ==============================
    console.log('== §流式问答：简历外问题如实说明 ==');
    s = await sse(hrApp, { token: hrToken, ...askBody('What is their experience with quantum chromodynamics?') });
    const nsDeltas = s.events.filter((e) => e.event === 'delta');
    const nsCitations = s.events.filter((e) => e.event === 'citation');
    const nsDone = s.events.filter((e) => e.event === 'done');
    const nsAnswer = nsDone.length === 1 ? parse(nsDone[0]) : null;
    check('无依据流式 → done 为固定话术且 grounded=false、无 citation 事件',
      s.status === 200 && nsDone.length === 1 && nsAnswer?.answer === '简历中没有相关信息。'
      && nsAnswer?.grounded === false && nsCitations.length === 0 && nsDeltas.length >= 1,
      `answer=${nsAnswer?.answer} grounded=${nsAnswer?.grounded} citation=${nsCitations.length}`);

    // ============================== T-AI4 §语义人才检索 ==============================
    console.log('== §语义人才检索：权限与入参校验 ==');
    const semanticPath = '/api/v1/hr/talent/semantic-search';
    const semanticBody = (query, tags, limit) => ({ body: { query, tags, limit } });

    r = await POST(semanticPath, semanticBody('React', [], null));
    check('未登录语义检索 → 401 + 40101', r.status === 401 && codeOf(r) === 40101,
      `HTTP ${r.status} code=${codeOf(r)}`);

    r = await POST(semanticPath, { token: seekerToken, ...semanticBody('React', [], null) });
    check('求职者语义检索 → 403 + 40301', r.status === 403 && codeOf(r) === 40301,
      `HTTP ${r.status} code=${codeOf(r)}`);

    r = await POST(semanticPath, { token: hrToken, ...semanticBody('  ', [' '], null) });
    check('语义检索条件为空 → 422 + 42210', r.status === 422 && codeOf(r) === 42210,
      `HTTP ${r.status} code=${codeOf(r)}`);

    r = await POST(semanticPath, { token: hrToken, ...semanticBody('需'.repeat(201), [], null) });
    check('语义检索需求过长（>200 字）→ 422 + 42201', r.status === 422 && codeOf(r) === 42201,
      `HTTP ${r.status} code=${codeOf(r)}`);

    console.log('== §语义人才检索：结果与证据 ==');
    r = await POST(semanticPath, { token: hrToken, ...semanticBody('React', ['前端'], null) });
    const semanticResults = dataOf(r);
    const semanticFirst = Array.isArray(semanticResults) ? semanticResults[0] || {} : {};
    const semanticEvidence = Array.isArray(semanticFirst.evidences) ? semanticFirst.evidences[0] || {} : {};
    check('语义检索成功：200 + 返回候选人数组', r.status === 200 && codeOf(r) === 0
      && Array.isArray(semanticResults) && semanticResults.length >= 1,
      `HTTP ${r.status} count=${Array.isArray(semanticResults) ? semanticResults.length : 'n/a'}`);
    check('命中简历含 React 的候选人（林小满）',
      semanticFirst.applicationId === 'app-hr-01' && semanticFirst.candidate?.name === '林小满'
      && typeof semanticFirst.jobTitle === 'string' && semanticFirst.jobTitle.length > 0,
      `applicationId=${semanticFirst.applicationId} name=${semanticFirst.candidate?.name}`);
    check('候选人分值为 0~1 的余弦相似度且按倒序排列',
      typeof semanticFirst.score === 'number' && semanticFirst.score > 0 && semanticFirst.score <= 1
      && semanticResults.every((item, i) => i === 0 || item.score <= semanticResults[i - 1].score),
      `top=${semanticFirst.score}`);
    check('证据结构完整（chunkId / section / title / snippet / score）',
      typeof semanticEvidence.chunkId === 'string'
      && ['basics', 'educations', 'experiences', 'projects', 'skills'].includes(semanticEvidence.section)
      && typeof semanticEvidence.title === 'string' && semanticEvidence.title.length > 0
      && typeof semanticEvidence.snippet === 'string' && semanticEvidence.snippet.length > 0
      && typeof semanticEvidence.score === 'number' && semanticEvidence.score > 0,
      `section=${semanticEvidence.section} score=${semanticEvidence.score} title=${semanticEvidence.title}`);

    r = await POST(semanticPath, { token: hrToken, ...semanticBody('', ['React'], null) });
    const tagOnly = dataOf(r);
    check('仅选择技能标签也可语义检索', r.status === 200 && Array.isArray(tagOnly) && tagOnly.length >= 1,
      `count=${Array.isArray(tagOnly) ? tagOnly.length : 'n/a'}`);

    r = await POST(semanticPath, { token: hrToken, ...semanticBody('React', [], 1) });
    const limited = dataOf(r);
    check('limit=1 时只返回 1 位候选人', r.status === 200 && Array.isArray(limited) && limited.length === 1,
      `count=${Array.isArray(limited) ? limited.length : 'n/a'}`);

    r = await POST(semanticPath, { token: hrToken, ...semanticBody('营销活动页脚手架', [], null) });
    const crossCompany = dataOf(r);
    check('跨公司简历不可见（搜不到其他公司候选人的独有经历）',
      r.status === 200 && Array.isArray(crossCompany)
      && !crossCompany.some((item) => item.candidate?.name === '林晓'),
      `count=${Array.isArray(crossCompany) ? crossCompany.length : 'n/a'}`);

    // ============================== T-AI2 §问答后索引仍就绪 ==============================
    r = await GET('/api/v1/hr/applications/app-hr-01/ai/index-status', { token: hrToken });
    const afterQa = dataOf(r) || {};
    check('问答后索引状态仍为已就绪', r.status === 200 && afterQa.indexed === true,
      `indexed=${afterQa.indexed} chunks=${afterQa.chunkCount}`);
  } catch (error) {
    failed += 1;
    console.log('  \u2718 脚本异常：' + (error && error.message ? error.message : error));
  } finally {
    console.log(`\n结果：${passed}/${passed + failed} 通过${failed ? '（' + failed + ' 项失败）' : ''}`);
    if (failed) process.exitCode = 1;
  }
})();
