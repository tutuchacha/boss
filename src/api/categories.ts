/**
 * 聘聊 · 分类/字典 service
 * =====================================================================
 * 与 docs/api-endpoints.md §7 对应：
 *  - GET /api/v1/categories
 *  - GET /api/v1/cities
 *  - GET /api/v1/dict
 * =====================================================================
 */

import type { CategoryNode, City, DictBundle } from '@/types/api'
import { USE_MOCK } from '@/config/env'
import { request } from './client'
import { categoryTree, cities, dictBundle } from './mock'

export async function getCategories(): Promise<CategoryNode[]> {
  if (USE_MOCK) return categoryTree
  return request<CategoryNode[]>('/categories')
}

export async function getCities(): Promise<City[]> {
  if (USE_MOCK) return cities
  return request<City[]>('/cities')
}

export async function getDict(): Promise<DictBundle> {
  if (USE_MOCK) return dictBundle
  return request<DictBundle>('/dict')
}
