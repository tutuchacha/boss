/**
 * 聘聊 · Mock 数据 - 公司字典
 * =====================================================================
 * 来源：HTML 原型 CO 字典（docs/legacy/pinliao-app-v0.2.html line 189-194）
 * =====================================================================
 */

import type { Company } from '@/types/api'

interface CoRaw {
  stage: string
  size: string
  industry: string
}

const CO_RAW: Record<string, CoRaw> = {
  云杉科技: { stage: 'B轮', size: '500-999人', industry: '企业服务' },
  星河数据: { stage: 'C轮', size: '1000-9999人', industry: '大数据' },
  木棉云: { stage: 'A轮', size: '100-499人', industry: '企业服务' },
  溪流互动: { stage: '不需要融资', size: '100-499人', industry: '游戏' },
  青柚生活: { stage: '天使轮', size: '20-99人', industry: '电商' },
  蜂鸟出行: { stage: 'B轮', size: '500-999人', industry: '出行' },
  极光量子: { stage: 'D轮', size: '1000-9999人', industry: '金融科技' },
  麦田教育: { stage: '已上市', size: '10000人以上', industry: '在线教育' },
  安盛财险: { stage: '已上市', size: '10000人以上', industry: '保险' },
  恒岳制造: { stage: '已上市', size: '1000-9999人', industry: '智能制造' },
  仁和医疗: { stage: '不需要融资', size: '1000-9999人', industry: '医疗健康' },
  启明教育: { stage: 'B轮', size: '500-999人', industry: '教育培训' },
  锦程物流: { stage: '已上市', size: '10000人以上', industry: '物流供应链' },
  拾味餐饮: { stage: '不需要融资', size: '100-499人', industry: '餐饮' },
  悦居地产: { stage: '不需要融资', size: '1000-9999人', industry: '房地产' },
  彩虹传媒: { stage: 'A轮', size: '100-499人', industry: '文化传媒' },
}

/** 公司名 → companyId（稳定哈希，便于 mock 与 job / hr 互相关联） */
const nameToId = (name: string): string => {
  let h = 0
  for (let i = 0; i < name.length; i++) h = (h * 31 + name.charCodeAt(i)) >>> 0
  return `co-${h.toString(36)}`
}

const _companyMap = new Map<string, Company>()
const _companyList: Company[] = []

for (const [name, info] of Object.entries(CO_RAW)) {
  const c: Company = {
    id: nameToId(name),
    name,
    stage: info.stage,
    size: info.size,
    industry: info.industry,
    verified: true,
    status: 'ACTIVE',
  }
  _companyMap.set(name, c)
  _companyList.push(c)
}

export const companies: Company[] = _companyList
export const companyByName = (name: string): Company | undefined => _companyMap.get(name)
export const companyById = (id: string): Company | undefined => _companyList.find(c => c.id === id)
