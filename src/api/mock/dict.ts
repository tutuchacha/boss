/**
 * 聘聊 · Mock 数据 - 字典（城市/经验/学历/薪资/福利）
 * =====================================================================
 * 来源：HTML 原型 CITIES / EXPS / SALS / SAL / WELFARE
 * =====================================================================
 */

import type { City, DictBundle, SalaryRangeKey } from '@/types/api'

export const CITIES_RAW = ['全国', '上海', '北京', '深圳', '杭州', '广州', '成都', '武汉', '苏州']

export const EXPS_RAW = ['不限', '应届', '1-3年', '3-5年', '5-10年']

export const SALS_RAW: SalaryRangeKey[] = ['不限', '10K以下', '10-20K', '20-40K', '40K以上']

/** 薪资区间 → [min, max]（K） */
export const SAL_RANGE: Record<SalaryRangeKey, [number, number]> = {
  不限: [0, 999],
  '10K以下': [0, 10],
  '10-20K': [10, 20],
  '20-40K': [20, 40],
  '40K以上': [40, 999],
}

export const WELFARE_RAW = ['五险一金', '带薪年假', '年度体检', '弹性工作', '年终奖', '餐补']

/** 求职者期望薪资区间（个人中心编辑用） */
export const EXPECT_SALARY_RANGES = ['5K以下', '5-10K', '10-20K', '20-30K', '30-50K', '50K以上']

export const EDUS_RAW = ['学历不限', '大专', '本科', '硕士', '博士']

/** City 字典表（带 code/province/hot/sort） */
export const cities: City[] = CITIES_RAW.map((name, i) => ({
  id: `city-${i}`,
  code: String(10000 + i),
  name,
  province: undefined,
  hot: i > 0 && i <= 5,
  sort: i,
}))

export const dictBundle: DictBundle = {
  exps: EXPS_RAW,
  edus: EDUS_RAW,
  salaryRanges: SALS_RAW,
  welfare: WELFARE_RAW,
}
