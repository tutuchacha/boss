import type { NextConfig } from "next";

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
};

export default nextConfig;
