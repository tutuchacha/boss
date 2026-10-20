import { NextResponse, type NextRequest } from 'next/server'

/**
 * 单简历问答 SSE 代理（Step 4 T-AI3）
 * =====================================================================
 * 为什么需要它：`next.config.ts` 的 rewrites（`/api/v1/:path*` → Spring Boot）**会缓冲响应体** ——
 * 实测直连 :8080 时 delta 分别在第 150/180/210ms 到达，经 rewrites 后全部在结束时一次性吐出
 * （首字延迟 = 总耗时），打字机效果失效；且 rewrites 的优先级高于同路径的 Route Handler，
 * 无法用 `/api/v1/...` 下的文件覆盖它。
 *
 * 所以流式问答单独走本站根路径 `/api/ai/qa-stream/{applicationId}`（不在 `/api/v1` 下），
 * 用 Web Streams 把后端 `upstream.body` 原样透传，不聚合、不缓存。
 * 其余接口继续走 rewrites，契约与鉴权方式不变（Authorization 头原样转发）。
 *
 * 后端地址与 rewrites 共用 `API_PROXY_TARGET`（默认 http://localhost:8080）。
 */

export const dynamic = 'force-dynamic'
export const runtime = 'nodejs'

const BACKEND_ORIGIN = process.env.API_PROXY_TARGET ?? 'http://localhost:8080'

export async function POST(
  request: NextRequest,
  context: { params: Promise<{ applicationId: string }> },
) {
  const { applicationId } = await context.params
  const authorization = request.headers.get('authorization')
  const body = await request.text()

  let upstream: Response
  try {
    upstream = await fetch(
      `${BACKEND_ORIGIN}/api/v1/hr/applications/${encodeURIComponent(applicationId)}/ai/qa/stream`,
      {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          // 同时接受 JSON：校验类错误（42220 / 40301 / 42201 / 40401 / 42902）是普通 JSON 包络，
          // 只写 text/event-stream 时 Spring 无法写出错误正文（406 → 500 空响应）。
          Accept: 'text/event-stream, application/json',
          ...(authorization ? { Authorization: authorization } : {}),
        },
        body,
        cache: 'no-store',
      },
    )
  } catch {
    // 后端未启动 / 连不上：给前端一个可解析的包络，前端会退回非流式接口
    return NextResponse.json(
      { code: 50020, message: 'AI 服务暂时不可用（后端未启动）', data: null },
      { status: 502 },
    )
  }

  // 校验类错误（42220 / 40301 / 42201 / 40401 / 42902）是普通 JSON 包络：原样透传状态与正文，
  // 前端 `toApiError` 据此转 ApiError；只有 200 + text/event-stream 才读流。
  const contentType = upstream.headers.get('content-type') ?? 'text/event-stream; charset=utf-8'
  return new Response(upstream.body, {
    status: upstream.status,
    headers: {
      'Content-Type': contentType,
      'Cache-Control': 'no-cache, no-transform',
      'X-Accel-Buffering': 'no',
    },
  })
}
