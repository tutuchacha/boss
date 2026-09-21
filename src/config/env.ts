/**
 * 聘聊 · 环境变量配置
 * =====================================================================
 * 集中读取所有 NEXT_PUBLIC_* 变量，避免散落各处。
 * 后端联调阶段只需修改 .env，前端代码无需改动。
 * =====================================================================
 */

/** 是否使用本地 Mock 数据（Step 1 验收阶段为 true；联调后切 false） */
export const USE_MOCK =
  (process.env.NEXT_PUBLIC_USE_MOCK ?? 'true').toLowerCase() !== 'false'

/** 后端 API 基础地址（联调时配置） */
export const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? '/api/v1'

/** WebSocket 基础地址（联调时配置；沙箱约束：路径恒为 /，端口走 XTransformPort） */
export const WS_BASE_URL = process.env.NEXT_PUBLIC_WS_BASE_URL ?? '/'

/** 沙箱网关转发的 WebSocket 端口（聊天 mini-service 端口，如 3003） */
export const WS_PORT = process.env.NEXT_PUBLIC_WS_PORT ?? '3003'
