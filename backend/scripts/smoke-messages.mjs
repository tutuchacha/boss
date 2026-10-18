// 聘聊 · 消息与 WebSocket 接口冒烟（Step 3 T6；契约 docs/api-endpoints.md §8 + api-contract.md §10）
// 用法：后端启动后运行 `node scripts/smoke-messages.mjs`（可用 PINLIAO_BASE 覆盖地址）
// 前置：Flyway V1~V8 已迁移；MySQL + Redis 运行中；演示账号 13800000001（求职者）/ 13800000002（HR），密码 123456
// 说明：
//  - 只读验证种子会话（conv-seed-* / conv-hr-*），绝不标记种子会话已读，避免破坏 verify-seed 的未读基线；
//  - 写操作全部落在 conv-new-* / msg-new-*（verify-seed.ps1 已按前缀排除），可重复执行；
//  - WebSocket 用 Node 内置 WebSocket 手写最小 STOMP 帧，验证 /ws token 握手、用户队列与 typing topic。
const base = process.env.PINLIAO_BASE || 'http://localhost:8080';
const wsBase = base.replace(/^http/i, 'ws');

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
const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

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
const dataOf = (r) => (r.json && typeof r.json === 'object' ? r.json.data : undefined) || {};
const codeOf = (r) => (r.json && typeof r.json === 'object' ? r.json.code : undefined);
const listOf = (r) => dataOf(r).list || [];

async function login(account) {
  const r = await POST('/api/v1/auth/login/password', {
    body: { account, password: '123456' },
  });
  return dataOf(r).accessToken;
}

// ---------------------------------------------------------------------------
// 最小 STOMP over WebSocket 客户端（够冒烟即可，不引入额外依赖）
// ---------------------------------------------------------------------------

function stompFrame(command, headers = {}, body = '') {
  const head = Object.entries(headers).map(([k, v]) => `${k}:${v}`).join('\n');
  return `${command}\n${head}${head ? '\n' : ''}\n${body}\0`;
}

function parseStompFrame(raw) {
  const normalized = raw.replace(/\r\n/g, '\n').replace(/^\n+/, '');
  const split = normalized.indexOf('\n\n');
  const head = split >= 0 ? normalized.slice(0, split) : normalized;
  const bodyText = split >= 0 ? normalized.slice(split + 2) : '';
  const lines = head.split('\n');
  const command = (lines.shift() || '').trim();
  const headers = {};
  for (const line of lines) {
    const at = line.indexOf(':');
    if (at > 0) headers[line.slice(0, at)] = line.slice(at + 1);
  }
  let body = null;
  if (bodyText.trim()) {
    try {
      body = JSON.parse(bodyText.trim());
    } catch {
      body = bodyText.trim();
    }
  }
  return { command, headers, body, raw };
}

function stompConnect(token) {
  return new Promise((resolve, reject) => {
    const query = token ? '?token=' + encodeURIComponent(token) : '';
    const ws = new WebSocket(wsBase + '/ws' + query);
    let buffer = '';
    let settled = false;
    let serial = 0;
    const inbox = [];
    const listeners = new Set();
    const timer = setTimeout(() => fail(new Error('STOMP CONNECT 超时')), 6000);

    function fail(error) {
      if (settled) return;
      settled = true;
      clearTimeout(timer);
      try { ws.close(); } catch { /* ignore */ }
      reject(error);
    }

    const client = {
      ws,
      inbox,
      clear() { inbox.length = 0; },
      subscribe(destination) {
        const id = 'sub-' + serial++;
        ws.send(stompFrame('SUBSCRIBE', { id, destination, ack: 'auto' }));
        return id;
      },
      send(destination, body) {
        ws.send(stompFrame('SEND', {
          destination,
          'content-type': 'application/json',
        }, JSON.stringify(body)));
      },
      waitFor(predicate, timeoutMs = 5000) {
        const existing = inbox.findIndex(predicate);
        if (existing >= 0) {
          const frame = inbox.splice(existing, 1)[0];
          return Promise.resolve(frame);
        }
        return new Promise((resolveFrame, rejectFrame) => {
          const waiter = (frame) => {
            if (!predicate(frame)) return;
            clearTimeout(waitTimer);
            listeners.delete(waiter);
            resolveFrame(frame);
          };
          const waitTimer = setTimeout(() => {
            listeners.delete(waiter);
            rejectFrame(new Error('等待 STOMP 帧超时'));
          }, timeoutMs);
          listeners.add(waiter);
        });
      },
      close() {
        settled = true;
        clearTimeout(timer);
        try { ws.close(); } catch { /* ignore */ }
      },
    };

    ws.addEventListener('open', () => {
      ws.send(stompFrame('CONNECT', {
        'accept-version': '1.2',
        host: 'localhost',
        'heart-beat': '0,0',
      }));
    });

    ws.addEventListener('message', (event) => {
      const text = typeof event.data === 'string'
        ? event.data
        : Buffer.from(event.data).toString('utf8');
      buffer += text;
      let end;
      while ((end = buffer.indexOf('\0')) >= 0) {
        const raw = buffer.slice(0, end);
        buffer = buffer.slice(end + 1);
        if (!raw.trim()) continue;
        const frame = parseStompFrame(raw);
        if (frame.command === 'CONNECTED' && !settled) {
          settled = true;
          clearTimeout(timer);
          resolve(client);
        } else if (frame.command === 'ERROR' && !settled) {
          fail(new Error('STOMP ERROR: ' + (frame.body?.message || frame.text || 'unknown')));
        } else if (frame.command === 'MESSAGE' || frame.command === 'ERROR') {
          inbox.push(frame);
          for (const listener of [...listeners]) listener(frame);
        }
      }
    });

    ws.addEventListener('error', () => fail(new Error('WebSocket 连接失败')));
    ws.addEventListener('close', () => fail(new Error('WebSocket 连接已关闭')));
  });
}

// ---------------------------------------------------------------------------

(async () => {
  console.log('== §8.1 / §8.2 会话列表与消息（只读种子） ==');
  const seekerToken = await login('13800000001');
  const hrToken = await login('13800000002');
  check('演示账号登录（求职者 / HR）', typeof seekerToken === 'string' && typeof hrToken === 'string');

  let r = await GET('/api/v1/conversations');
  check('未登录会话列表 → 401 + 40101', r.status === 401 && codeOf(r) === 40101,
    `HTTP ${r.status} code=${codeOf(r)}`);

  r = await GET('/api/v1/conversations?limit=100', { token: seekerToken });
  let seekerPage = dataOf(r);
  let seekerSeed = (seekerPage.list || []).filter((v) => !v.conversation.id.startsWith('conv-new-'));
  check('求职者会话列表 → 2 条种子会话', r.status === 200 && seekerSeed.length === 2,
    `seed=${seekerSeed.length} total=${seekerPage.pagination?.total}`);
  check('求职者视角只返回本人会话且含职位/公司/HR',
    seekerSeed.every((v) => v.conversation.userId === 'u-seeker-1' && v.job?.id && v.company?.id && v.hr?.id),
    seekerSeed.map((v) => v.conversation.id).join(','));
  check('求职者种子未读合计 2', seekerSeed.reduce((sum, v) => sum + v.conversation.userUnreadCount, 0) === 2,
    `unread=${seekerSeed.reduce((sum, v) => sum + v.conversation.userUnreadCount, 0)}`);

  r = await GET('/api/v1/conversations?limit=100', { token: hrToken });
  let hrPage = dataOf(r);
  let hrSeed = (hrPage.list || []).filter((v) => !v.conversation.id.startsWith('conv-new-'));
  check('HR 会话列表 → 6 条种子会话', r.status === 200 && hrSeed.length === 6,
    `seed=${hrSeed.length} total=${hrPage.pagination?.total}`);
  check('HR 种子未读合计 4（未被 T6 冒烟破坏）',
    hrSeed.reduce((sum, v) => sum + v.conversation.hrUnreadCount, 0) === 4,
    `unread=${hrSeed.reduce((sum, v) => sum + v.conversation.hrUnreadCount, 0)}`);
  check('HR 视角候选人与本公司字段完整',
    hrSeed.every((v) => v.conversation.companyId === 'co-acgiw7' && v.candidate?.userId
      && v.candidate?.name && typeof v.candidate.resumeCompleteness === 'number'),
    `first=${hrSeed[0]?.candidate?.name}`);

  const p1 = await GET('/api/v1/conversations?limit=2', { token: seekerToken });
  const cursor = dataOf(p1).pagination?.nextCursor;
  const p2 = await GET('/api/v1/conversations?limit=2&cursor=' + encodeURIComponent(cursor), { token: seekerToken });
  const ids1 = listOf(p1).map((v) => v.conversation.id);
  const ids2 = listOf(p2).map((v) => v.conversation.id);
  check('会话游标分页不重复', ids1.length === 2 && ids2.length > 0 && ids1.every((id) => !ids2.includes(id)),
    `p1=${ids1.join(',')} p2=${ids2.join(',')}`);
  r = await GET('/api/v1/conversations?cursor=' + encodeURIComponent('%%%'), { token: seekerToken });
  check('非法游标 → 400 + 40001', r.status === 400 && codeOf(r) === 40001,
    `HTTP ${r.status} code=${codeOf(r)}`);

  r = await GET('/api/v1/conversations/conv-hr-1', { token: hrToken });
  check('HR 会话详情 → candidate + job',
    r.status === 200 && dataOf(r).conversation?.id === 'conv-hr-1' && dataOf(r).candidate?.userId,
    `candidate=${dataOf(r).candidate?.name}`);

  r = await GET('/api/v1/conversations/conv-hr-1/messages?limit=100', { token: hrToken });
  let seedMessages = listOf(r);
  check('HR 种子会话消息 → 2 条且按 createdAt 降序',
    r.status === 200 && seedMessages.length === 2
      && Date.parse(seedMessages[0].createdAt) >= Date.parse(seedMessages[1].createdAt),
    `count=${seedMessages.length}`);

  r = await GET('/api/v1/conversations/conv-hr-1/messages', { token: seekerToken });
  check('求职者访问他人会话消息 → 404 + 40410', r.status === 404 && codeOf(r) === 40410,
    `HTTP ${r.status} code=${codeOf(r)}`);

  const seekerSeedConv = seekerSeed[0];
  r = await GET('/api/v1/conversations/' + seekerSeedConv.conversation.id, { token: seekerToken });
  check('求职者会话详情 → 职位 / HR / 公司齐备',
    r.status === 200 && dataOf(r).job?.title && dataOf(r).hr?.name && dataOf(r).company?.name);

  console.log('== §8.3 发起会话（复用/创建 conv-new-*） ==');

  let convItem = (seekerPage.list || []).find((v) =>
    v.conversation.id.startsWith('conv-new-') && v.conversation.companyId === 'co-acgiw7');
  let convId = convItem?.conversation.id;
  let createdHere = false;
  if (!convItem) {
    const jobs = listOf(await GET('/api/v1/jobs?limit=100'));
    const used = new Set((seekerPage.list || []).map((v) => v.conversation.jobId));
    const target = jobs.find((j) => j.company?.id === 'co-acgiw7' && !used.has(j.id));
    check('找到可发起沟通的云杉职位', Boolean(target), target ? target.id : '无');
    if (!target) throw new Error('云杉 7 个职位均已有会话，请手工清理 conv-new-%');
    r = await POST('/api/v1/conversations', {
      token: seekerToken,
      body: { jobId: target.id, firstMessage: '您好，我想了解一下这个岗位' },
    });
    convId = dataOf(r).id;
    convItem = { conversation: dataOf(r), job: target };
    createdHere = true;
    check('发起会话成功 → conv-new-* + 未读 1',
      r.status === 200 && String(convId).startsWith('conv-new-') && dataOf(r).hrUnreadCount === 1,
      `id=${convId}`);
    await sleep(1700); // 等首条消息的自动回复落库，避免干扰后续计数
  } else {
    check('复用已有 conv-new-* 会话', Boolean(convId), convId);
  }

  r = await POST('/api/v1/conversations', {
    token: seekerToken,
    body: { jobId: convItem.job.id, firstMessage: '重复发起' },
  });
  check('重复发起同一职位 → 409 + 40912', r.status === 409 && codeOf(r) === 40912,
    `HTTP ${r.status} code=${codeOf(r)}`);
  r = await POST('/api/v1/conversations', { token: hrToken, body: { jobId: convItem.job.id } });
  check('HR 发起会话 → 403 + 40301', r.status === 403 && codeOf(r) === 40301,
    `HTTP ${r.status} code=${codeOf(r)}`);

  console.log('== §8.4~§8.6 发送 / 自动回复 / 已读 / typing ==');

  let detailBefore = dataOf(await GET('/api/v1/conversations/' + convId, { token: hrToken }));
  const hrUnreadBefore = detailBefore.conversation?.hrUnreadCount ?? 0;
  const idem = 'smoke-msg-' + Date.now();
  r = await POST(`/api/v1/conversations/${convId}/messages`, {
    token: seekerToken,
    body: { type: 'TEXT', content: '请问薪资待遇？', idempotencyKey: idem },
  });
  const sent = dataOf(r);
  check('求职者发送 TEXT → USER / readByUser=true',
    r.status === 200 && codeOf(r) === 0 && sent.id?.startsWith('msg-new-')
      && sent.fromType === 'USER' && sent.readByUser === true && sent.readByHr === false,
    `id=${sent.id}`);

  let detailAfter = dataOf(await GET('/api/v1/conversations/' + convId, { token: hrToken }));
  check('发送后 HR 未读 +1',
    detailAfter.conversation?.hrUnreadCount === hrUnreadBefore + 1,
    `${hrUnreadBefore} → ${detailAfter.conversation?.hrUnreadCount}`);

  r = await POST(`/api/v1/conversations/${convId}/messages`, {
    token: seekerToken,
    body: { type: 'TEXT', content: '重复内容', idempotencyKey: idem },
  });
  check('idempotencyKey 重复提交返回同一条消息', dataOf(r).id === sent.id,
    `again=${dataOf(r).id}`);

  const beforeReply = listOf(await GET(`/api/v1/conversations/${convId}/messages?limit=100`, { token: hrToken }));
  await sleep(1700);
  const afterReply = listOf(await GET(`/api/v1/conversations/${convId}/messages?limit=100`, { token: hrToken }));
  const autoHr = afterReply.find((m) => m.fromType === 'HR' && m.type === 'TEXT'
    && m.createdAt >= sent.createdAt && (m.content.includes('薪资') || /\d+-\d+K/.test(m.content)));
  check('TEXT 消息触发 HR 自动回复（含薪资关键词）',
    afterReply.length === beforeReply.length + 1 && Boolean(autoHr),
    autoHr ? autoHr.content.slice(0, 30) : '未收到');

  const userUnreadBeforeHr = dataOf(await GET('/api/v1/conversations/' + convId, { token: seekerToken }))
    .conversation?.userUnreadCount ?? 0;
  r = await POST(`/api/v1/conversations/${convId}/messages`, {
    token: hrToken,
    body: { type: 'TEXT', content: '方便约个面试吗？' },
  });
  check('HR 发送 TEXT → HR / readByHr=true',
    r.status === 200 && dataOf(r).fromType === 'HR' && dataOf(r).fromHrId === 'hr-x2270n'
      && dataOf(r).readByHr === true && dataOf(r).readByUser === false,
    `fromHrId=${dataOf(r).fromHrId}`);
  const afterHrSend = dataOf(await GET('/api/v1/conversations/' + convId, { token: seekerToken }));
  check('HR 发送后求职者未读 +1',
    afterHrSend.conversation?.userUnreadCount === userUnreadBeforeHr + 1,
    `${userUnreadBeforeHr} → ${afterHrSend.conversation?.userUnreadCount}`);

  await sleep(1700);
  const afterCandidate = listOf(await GET(`/api/v1/conversations/${convId}/messages?limit=100`, { token: hrToken }));
  check('HR 发送 TEXT 触发候选人自动回复',
    afterCandidate.some((m) => m.fromType === 'USER' && m.type === 'TEXT' && m.content.includes('周三或周四')),
    afterCandidate.find((m) => m.fromType === 'USER' && m.content.includes('周三或周四'))?.content.slice(0, 24));

  r = await POST(`/api/v1/conversations/${convId}/typing`, {
    token: hrToken, body: { isTyping: true },
  });
  check('typing 接口 → 200 + 空对象', r.status === 200 && codeOf(r) === 0
    && dataOf(r) && Object.keys(dataOf(r)).length === 0, `HTTP ${r.status}`);

  r = await POST(`/api/v1/conversations/${convId}/read`, { token: hrToken });
  check('HR 标记已读 → unreadCount=0', r.status === 200 && dataOf(r).unreadCount === 0,
    `data=${JSON.stringify(dataOf(r))}`);
  r = await GET('/api/v1/conversations/' + convId, { token: hrToken });
  check('HR 已读后会话 hrUnreadCount=0', dataOf(r).conversation?.hrUnreadCount === 0,
    `hrUnread=${dataOf(r).conversation?.hrUnreadCount}`);

  r = await POST(`/api/v1/conversations/${convId}/read`, { token: seekerToken });
  check('求职者标记已读 → unreadCount=0', r.status === 200 && dataOf(r).unreadCount === 0);

  console.log('== §8.7 / §8.8 简历快捷操作与关闭 ==');
  r = await POST(`/api/v1/conversations/${convId}/actions/send-resume`, { token: seekerToken, body: {} });
  check('发送简历 → RESUME 消息 + 默认简历',
    r.status === 200 && dataOf(r).type === 'RESUME' && dataOf(r).content.includes('resume-seeker'),
    `content=${String(dataOf(r).content).slice(0, 48)}`);

  r = await POST(`/api/v1/conversations/${convId}/close`, { token: seekerToken });
  check('求职者关闭会话 → 403 + 40301', r.status === 403 && codeOf(r) === 40301,
    `HTTP ${r.status} code=${codeOf(r)}`);
  r = await POST(`/api/v1/conversations/${convId}/close`, { token: hrToken });
  check('HR 关闭会话 → closedByHr=true', r.status === 200 && dataOf(r).closedByHr === true);

  console.log('== §10 WebSocket（STOMP /ws） ==');

  const wsClient = await stompConnect(seekerToken);
  check('WebSocket token 握手 + STOMP CONNECTED', Boolean(wsClient), 'ok');
  wsClient.subscribe('/user/queue/messages');
  wsClient.clear();
  const wsText = 'WebSocket 联调消息 ' + Date.now();
  const wsMessageWait = wsClient.waitFor((f) => f.command === 'MESSAGE'
    && f.body?.data?.conversationId === convId && f.body?.data?.content === wsText, 5000);
  r = await POST(`/api/v1/conversations/${convId}/messages`, {
    token: hrToken, body: { type: 'TEXT', content: wsText },
  });
  const wsFrame = await wsMessageWait;
  check('HR 发送后求职者收到 /user/queue/messages 推送',
    wsFrame.body?.data?.id === dataOf(r).id && wsFrame.body?.data?.fromType === 'HR',
    `id=${wsFrame.body?.data?.id}`);

  wsClient.subscribe(`/topic/conversation/${convId}/typing`);
  wsClient.clear();
  const typingWait = wsClient.waitFor((f) => f.command === 'MESSAGE'
    && String(f.headers.destination || '').includes('/typing')
    && f.body?.data?.conversationId === convId && f.body?.data?.isTyping === true, 5000);
  r = await POST(`/api/v1/conversations/${convId}/typing`, {
    token: hrToken, body: { isTyping: true },
  });
  const typingFrame = await typingWait;
  check('typing 事件到达 /topic/conversation/{id}/typing',
    typingFrame.body?.data?.fromType === 'HR' && typingFrame.body?.data?.isTyping === true,
    `fromType=${typingFrame.body?.data?.fromType}`);

  const hrWs = await stompConnect(hrToken);
  hrWs.subscribe('/user/queue/messages');
  hrWs.subscribe(`/topic/conversation/${convId}/typing`);
  hrWs.subscribe('/user/queue/errors');
  hrWs.clear();
  const stompText = 'STOMP 发送联调 ' + Date.now();
  const stompWait = hrWs.waitFor((f) => f.command === 'MESSAGE'
    && f.body?.data?.conversationId === convId && f.body?.data?.content === stompText, 5000);
  wsClient.send(`/app/conversation/${convId}/send`, { type: 'TEXT', content: stompText });
  const stompFrame = await stompWait;
  check('STOMP /app/.../send 落库并推送 HR 用户队列',
    stompFrame.body?.data?.fromType === 'USER' && stompFrame.body?.data?.id?.startsWith('msg-new-'),
    `id=${stompFrame.body?.data?.id}`);
  const stompStored = listOf(await GET(`/api/v1/conversations/${convId}/messages?limit=100`, { token: hrToken }));
  check('STOMP 发送的消息可由 REST 历史读取',
    stompStored.some((m) => m.content === stompText && m.fromType === 'USER'));

  hrWs.clear();
  const stompTypingWait = hrWs.waitFor((f) => f.command === 'MESSAGE'
    && String(f.headers.destination || '').includes('/typing')
    && f.body?.data?.conversationId === convId && f.body?.data?.fromType === 'USER'
    && f.body?.data?.isTyping === true, 5000);
  wsClient.send(`/app/conversation/${convId}/typing`, { isTyping: true });
  const stompTypingFrame = await stompTypingWait;
  check('STOMP /app/.../typing 推送 typing topic',
    stompTypingFrame.body?.data?.fromType === 'USER');

  hrWs.clear();
  const errorWait = hrWs.waitFor((f) => f.command === 'MESSAGE'
    && f.body?.code === 42201, 5000);
  hrWs.send(`/app/conversation/${convId}/send`, { type: 'INVALID', content: 'bad' });
  const errorFrame = await errorWait;
  check('STOMP 业务错误推送到 /user/queue/errors',
    errorFrame.body?.code === 42201, `code=${errorFrame.body?.code}`);

  wsClient.close();
  hrWs.close();

  let badTokenRejected = false;
  try {
    const bad = await stompConnect('bad-token');
    bad.close();
  } catch {
    badTokenRejected = true;
  }
  check('非法 token 无法建立聊天连接', badTokenRejected);

  let anonymousRejected = false;
  try {
    const anonymous = await stompConnect(null);
    anonymous.close();
  } catch {
    anonymousRejected = true;
  }
  check('未登录 STOMP CONNECT 被拒绝', anonymousRejected);

  console.log(`\n结果：${passed} 通过，${failed} 失败`);
  if (failed > 0) process.exitCode = 1;
})().catch((error) => {
  console.error('\n冒烟中断：', error);
  process.exitCode = 1;
});
