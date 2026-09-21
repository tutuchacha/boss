/**
 * 聘聊 · API 出口
 * =====================================================================
 * service 层统一从此文件导入。组件、hooks 仅依赖此文件，不直接依赖子模块。
 * 后端联调时只修改 service 内部实现（USE_MOCK 切换），调用方无感。
 * =====================================================================
 */

export * from './client'
export * from './categories'
export * from './jobs'
export * from './conversations'
