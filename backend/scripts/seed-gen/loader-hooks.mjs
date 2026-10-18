// Node ESM resolve hook：把 '@/x' 映射到仓库 src/x，并为无扩展名相对导入补 .ts / .tsx
// 用途：让 generate.mjs 能直接 import 前端的 mock TS 模块（Node >= 22.7，--experimental-transform-types）
import { existsSync } from 'node:fs'
import path from 'node:path'
import { pathToFileURL, fileURLToPath } from 'node:url'

// backend/scripts/seed-gen → 仓库根
const REPO_ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..', '..')
const SRC_ROOT = path.join(REPO_ROOT, 'src')

/** 尝试的扩展名顺序 */
const EXTS = ['', '.ts', '.tsx', '/index.ts', '/index.tsx']

function firstExistingCandidate(fileUrl) {
  for (const ext of EXTS) {
    const u = ext === '' ? fileUrl : fileUrl + ext
    try {
      if (existsSync(fileURLToPath(u))) return u
    } catch {
      /* ignore */
    }
  }
  return null
}

export async function resolve(specifier, context, nextResolve) {
  let baseUrl = null
  if (specifier.startsWith('@/')) {
    baseUrl = pathToFileURL(path.join(SRC_ROOT, specifier.slice(2))).href
  } else if (specifier.startsWith('./') || specifier.startsWith('../')) {
    baseUrl = new URL(specifier, context.parentURL).href
  }
  if (baseUrl) {
    const hit = firstExistingCandidate(baseUrl)
    if (hit) return { url: hit, shortCircuit: true }
  }
  return nextResolve(specifier, context)
}
