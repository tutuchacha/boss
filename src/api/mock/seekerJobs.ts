/**
 * 聘聊 · 求职者端可见职位（Mock 数据合成）
 * =====================================================================
 * 解决的问题：
 *   HR 端的新增/编辑/上下架/删除作用在 src/api/mock/hrJobs.ts 的
 *   hrJobRecords（从 jobs.ts 派生的独立副本）上；而求职者端原先只读
 *   jobs.ts 的静态数组 —— 导致「HR 发布职位后求职者在列表里看不到」，
 *   而这恰恰是发布岗位的核心价值。
 *
 * 本模块把两侧合成一份「求职者可见职位」：
 *   - 已软删除（deleted）→ 不出现
 *   - 已关闭（CLOSED）  → 不出现（与关闭时的提示文案一致）
 *   - 招聘中            → 用 HR 端的最新内容覆盖原始条目
 *     （即使某条只是被 HR 编辑过而状态未变，也需覆盖，
 *       否则改标题/薪资后求职者端仍显示旧内容）
 *   - HR 新建的职位     → 追加，转为 JobSummary 形态
 *
 * 依赖方向：本模块**不 import 任何数据模块**，由调用方注入数据源。
 *   这是为了彻底消除循环依赖：
 *     jobs.ts ──┐
 *               ├─► seekerJobs（纯函数）
 *     hrJobs.ts ┘        ▲
 *                        └── jobs.ts 也需要它来实现 jobById
 *   若 seekerJobs 反过来 import jobs.ts 就会成环（Next/Turbopack 下
 *   可能表现为初始化期 undefined）。
 *
 * Step 3 接后端后本文件整体废弃（由服务端返回已发布的职位）。
 * =====================================================================
 */

import { JobStatusEnum } from '@/types/api'
import type { JobSummary } from '@/types/api'

/** 新建职位 id 前缀（由 hrJobs.ts 的 nextJobId 生成） */
const NEW_JOB_PREFIX = 'job-new-'

/** HR 端记录在 JobSummary 之外额外带有的字段 */
interface MaybeHrFields {
  status?: JobStatusEnum
  deleted?: boolean
}

/**
 * 创建求职者端职位查询器。
 * @param baseJobs       基础职位（jobs.ts 的 `jobs`）
 * @param hrRecords      HR 端可变记录（hrJobs.ts 经 initHrJobs 初始化后的数组）
 */
export function createSeekerJobFinder(
  baseJobs: JobSummary[],
  hrRecords: () => (JobSummary & MaybeHrFields)[],
) {
  /** 求职者端可见职位列表（由基础职位与 HR 端状态合成） */
  function listSeekerJobs(): JobSummary[] {
    const baseIds = new Set(baseJobs.map(j => j.id))
    const visible = new Map<string, JobSummary>()

    for (const j of baseJobs) visible.set(j.id, j)

    for (const r of hrRecords()) {
      if (r.deleted || r.status === JobStatusEnum.CLOSED) {
        visible.delete(r.id)
        continue
      }
      if (baseIds.has(r.id) || r.id.startsWith(NEW_JOB_PREFIX)) {
        // 既有职位 → 以 HR 端最新内容覆盖；HR 新建 → 追加
        visible.set(r.id, r)
      }
      // 其它未知 id（理论上不存在）忽略，避免脏数据进入列表
    }

    return Array.from(visible.values())
  }

  /** 按 id 查职位（含 HR 新增、不含已关闭/已删除） */
  function jobById(id: string): JobSummary | undefined {
    const base = baseJobs.find(j => j.id === id)
    const rec = hrRecords().find(r => r.id === id)
    if (rec && (rec.deleted || rec.status === JobStatusEnum.CLOSED)) return undefined
    if (base) return rec ?? base
    if (rec && rec.id.startsWith(NEW_JOB_PREFIX)) return rec
    return undefined
  }

  return { listSeekerJobs, jobById }
}
