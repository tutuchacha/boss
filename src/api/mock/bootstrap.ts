/**
 * 聘聊 · Mock 数据装配（引导模块）
 * =====================================================================
 * 存在的原因：打破模块循环依赖。
 *
 *   jobs.ts ──────────► hrJobs.ts        （需要 hrJobRecords 做求职者可见合成）
 *   hrJobs.ts ────────► jobs.ts          （需要基础职位做初始数据）
 *        ↑ 直接互相 import 会成环 → 初始化期取到 undefined → 运行时 500
 *
 * 解法：两条边都改为「不互相 import」，统一由本模块按序装配：
 *
 *   1) hrJobs.initHrJobs(jobs)     用基础职位填充 HR 端可变记录
 *   2) jobs 内部的 seekerFinder    在调用时惰性读取 hrJobRecords（已是同一数组引用）
 *
 * 因此本模块**只需被 import 一次**（入口顺序最早）即可保证数据一致。
 * 已被 src/api/jobs.ts 顶部引入，组件侧无需关心。
 *
 * Step 3 接后端后本文件整体废弃。
 * =====================================================================
 */

import { jobs } from './jobs'
import { initHrJobs } from './hrJobs'

/** 标记装配完成，便于调试时确认执行顺序 */
export const mockDataReady = true

initHrJobs(jobs)
