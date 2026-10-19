import type { NextConfig } from "next";

/**
 * 后端地址（Step 3 联调用，服务端环境变量，不需要 NEXT_PUBLIC_ 前缀）。
 * 生产环境用网关 / Nginx 做同样的事，或在此覆盖。
 */
const BACKEND_ORIGIN = process.env.API_PROXY_TARGET ?? "http://localhost:8080";

const nextConfig: NextConfig = {
  output: "standalone",
  // 关闭开发模式左下角的 Next.js 调试指示器：
  // 它是固定定位的圆形徽标，会挡住移动端底部 TabBar 最左侧的「职位」Tab，
  // 影响 H5 验收点击。仅开发模式显示，不影响生产构建。
  devIndicators: false,
  /* config options here */
  typescript: {
    ignoreBuildErrors: true,
  },
  reactStrictMode: false,
  /**
   * 联调代理（T9 起）：`src/api/client.ts` 只发相对路径（避免跨域），
   * `USE_MOCK=false` 时由 Next 把 `/api/v1/**` 与 `/uploads/**` 转发到 Spring Boot :8080。
   */
  async rewrites() {
    return [
      {
        source: "/api/v1/:path*",
        destination: `${BACKEND_ORIGIN}/api/v1/:path*`,
      },
      {
        source: "/uploads/:path*",
        destination: `${BACKEND_ORIGIN}/uploads/:path*`,
      },
    ];
  },
};

export default nextConfig;
