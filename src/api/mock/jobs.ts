/**
 * 聘聊 · Mock 数据 - 职位（51 条）
 * =====================================================================
 * 来源：HTML 原型 JOBS 数组（docs/legacy/pinliao-app-v0.2.html line 197-249）
 * 字段：J(id, title, sMin, sMax, city, area, exp, edu, cat, sub, tags, company, hrName, hrTitle, days)
 * =====================================================================
 */

import type { JobSummary } from '@/types/api'
import { companyByName } from './companies'

interface JobRaw {
  id: number
  title: string
  sMin: number
  sMax: number
  city: string
  area: string
  exp: string
  edu: string
  cat: string
  sub: string
  tags: string[]
  company: string
  hrName: string
  hrTitle: string
  days: number
}

const JOBS_RAW: JobRaw[] = [
  { id: 1, title: '前端开发工程师', sMin: 20, sMax: 35, city: '上海', area: '浦东新区', exp: '3-5年', edu: '本科', cat: '互联网', sub: '前端开发', tags: ['Vue3', 'TypeScript', 'Vite'], company: '云杉科技', hrName: '陈女士', hrTitle: '技术招聘经理', days: 0 },
  { id: 2, title: 'Java后端工程师', sMin: 25, sMax: 45, city: '北京', area: '海淀区', exp: '5-10年', edu: '本科', cat: '互联网', sub: '后端开发', tags: ['Spring Cloud', 'MySQL', '高并发'], company: '星河数据', hrName: '王先生', hrTitle: '研发负责人', days: 0 },
  { id: 11, title: '高级产品经理（AI方向）', sMin: 35, sMax: 55, city: '北京', area: '中关村', exp: '5-10年', edu: '硕士', cat: '产品', sub: '产品经理', tags: ['大模型', 'Prompt', '产品规划'], company: '星河数据', hrName: '何女士', hrTitle: '产品VP', days: 0 },
  { id: 21, title: '大客户销售经理', sMin: 15, sMax: 30, city: '上海', area: '浦东新区', exp: '3-5年', edu: '本科', cat: '销售', sub: '大客户销售', tags: ['SaaS销售', '商务谈判', '客户关系'], company: '云杉科技', hrName: '钱先生', hrTitle: '销售总监', days: 0 },
  { id: 35, title: '高中数学教师', sMin: 10, sMax: 18, city: '北京', area: '海淀区', exp: '3-5年', edu: '本科', cat: '教育培训', sub: '学科教师', tags: ['教师资格证', '教学设计', '高考数学'], company: '麦田教育', hrName: '曹女士', hrTitle: '教学总监', days: 0 },
  { id: 37, title: '临床护士', sMin: 7, sMax: 11, city: '成都', area: '锦江区', exp: '1-3年', edu: '大专', cat: '医疗健康', sub: '护士', tags: ['护士执业资格证', '临床护理', '沟通耐心'], company: '仁和医疗', hrName: '唐女士', hrTitle: '护理部主任', days: 0 },
  { id: 3, title: '产品经理（B端）', sMin: 18, sMax: 30, city: '杭州', area: '余杭区', exp: '3-5年', edu: '本科', cat: '产品', sub: '产品经理', tags: ['需求分析', 'Axure', 'SaaS'], company: '木棉云', hrName: '李女士', hrTitle: 'HRBP', days: 1 },
  { id: 4, title: 'UI/UX设计师', sMin: 15, sMax: 25, city: '深圳', area: '南山区', exp: '1-3年', edu: '大专', cat: '设计', sub: 'UI/UX设计', tags: ['Figma', '设计系统', '动效'], company: '溪流互动', hrName: '周女士', hrTitle: '设计总监', days: 1 },
  { id: 24, title: '市场推广经理', sMin: 18, sMax: 30, city: '北京', area: '朝阳区', exp: '3-5年', edu: '本科', cat: '市场/广告', sub: '市场推广', tags: ['整合营销', '渠道投放', '预算管理'], company: '麦田教育', hrName: '刘女士', hrTitle: '市场负责人', days: 1 },
  { id: 31, title: '会计', sMin: 8, sMax: 14, city: '广州', area: '天河区', exp: '3-5年', edu: '本科', cat: '财务', sub: '会计', tags: ['总账', '增值税申报', 'Excel'], company: '青柚生活', hrName: '吴女士', hrTitle: '财务经理', days: 1 },
  { id: 46, title: '中餐厨师', sMin: 9, sMax: 16, city: '成都', area: '锦江区', exp: '3-5年', edu: '学历不限', cat: '零售/餐饮', sub: '厨师', tags: ['川菜', '出品稳定', '成本意识'], company: '拾味餐饮', hrName: '廖先生', hrTitle: '行政总厨', days: 1 },
  { id: 5, title: '新媒体运营', sMin: 8, sMax: 14, city: '广州', area: '天河区', exp: '1-3年', edu: '大专', cat: '运营', sub: '新媒体运营', tags: ['小红书', '短视频', '数据分析'], company: '青柚生活', hrName: '吴女士', hrTitle: '运营主管', days: 2 },
  { id: 6, title: '测试开发工程师', sMin: 18, sMax: 28, city: '上海', area: '徐汇区', exp: '3-5年', edu: '本科', cat: '互联网', sub: '测试', tags: ['Python', '自动化测试', 'CI/CD'], company: '云杉科技', hrName: '赵先生', hrTitle: '测试经理', days: 2 },
  { id: 14, title: '运营经理（用户增长）', sMin: 25, sMax: 40, city: '深圳', area: '福田区', exp: '5-10年', edu: '本科', cat: '运营', sub: '用户运营', tags: ['用户增长', '私域', 'AB实验'], company: '溪流互动', hrName: '周女士', hrTitle: '设计总监', days: 2 },
  { id: 33, title: '投资顾问', sMin: 12, sMax: 25, city: '深圳', area: '福田区', exp: '1-3年', edu: '本科', cat: '金融', sub: '投资/理财顾问', tags: ['基金从业资格', '客户经营', '资产配置'], company: '极光量子', hrName: '徐女士', hrTitle: '财富中心负责人', days: 2 },
  { id: 38, title: '医药代表', sMin: 12, sMax: 25, city: '上海', area: '徐汇区', exp: '3-5年', edu: '本科', cat: '医疗健康', sub: '医药代表', tags: ['医院准入', '学术推广', '客户拜访'], company: '仁和医疗', hrName: '姜先生', hrTitle: '销售经理', days: 2 },
  { id: 7, title: 'React Native 开发', sMin: 22, sMax: 38, city: '成都', area: '高新区', exp: '3-5年', edu: '本科', cat: '互联网', sub: '移动开发', tags: ['React Native', 'iOS', 'Android'], company: '蜂鸟出行', hrName: '郑女士', hrTitle: '招聘专员', days: 3 },
  { id: 8, title: 'Go后端工程师', sMin: 30, sMax: 50, city: '深圳', area: '前海', exp: '5-10年', edu: '本科', cat: '互联网', sub: '后端开发', tags: ['Go', '微服务', 'K8s'], company: '极光量子', hrName: '孙先生', hrTitle: '技术总监', days: 3 },
  { id: 15, title: '算法工程师（推荐方向）', sMin: 30, sMax: 55, city: '北京', area: '海淀区', exp: '3-5年', edu: '硕士', cat: '互联网', sub: '算法/AI', tags: ['PyTorch', '推荐系统', '特征工程'], company: '星河数据', hrName: '何女士', hrTitle: '算法负责人', days: 3 },
  { id: 22, title: '销售代表', sMin: 6, sMax: 12, city: '成都', area: '武侯区', exp: '应届', edu: '大专', cat: '销售', sub: '销售代表', tags: ['地推', '客户拜访', '抗压能力'], company: '蜂鸟出行', hrName: '郑女士', hrTitle: '招聘专员', days: 3 },
  { id: 25, title: '品牌公关经理', sMin: 15, sMax: 25, city: '上海', area: '静安区', exp: '3-5年', edu: '本科', cat: '市场/广告', sub: '品牌公关', tags: ['媒介关系', '舆情管理', '品牌策略'], company: '彩虹传媒', hrName: '蒋女士', hrTitle: '品牌总监', days: 3 },
  { id: 39, title: '质检员', sMin: 6, sMax: 10, city: '深圳', area: '宝安区', exp: '1-3年', edu: '大专', cat: '生产制造', sub: '质检', tags: ['IQC', '质量体系', '量具使用'], company: '恒岳制造', hrName: '尹先生', hrTitle: '品质经理', days: 3 },
  { id: 45, title: '门店店长', sMin: 8, sMax: 15, city: '武汉', area: '江汉区', exp: '3-5年', edu: '大专', cat: '零售/餐饮', sub: '店长', tags: ['门店管理', '成本控制', '团队带教'], company: '拾味餐饮', hrName: '田女士', hrTitle: '区域经理', days: 3 },
  { id: 9, title: '前端开发（校招）', sMin: 12, sMax: 18, city: '北京', area: '朝阳区', exp: '应届', edu: '本科', cat: '互联网', sub: '前端开发', tags: ['JavaScript', 'React', '计算机基础'], company: '麦田教育', hrName: '刘女士', hrTitle: '校招负责人', days: 4 },
  { id: 10, title: '数据分析师', sMin: 15, sMax: 25, city: '杭州', area: '滨江区', exp: '1-3年', edu: '本科', cat: '互联网', sub: '数据分析', tags: ['SQL', 'Tableau', '增长分析'], company: '木棉云', hrName: '李女士', hrTitle: 'HRBP', days: 4 },
  { id: 18, title: '平面设计师', sMin: 8, sMax: 15, city: '广州', area: '天河区', exp: '1-3年', edu: '大专', cat: '设计', sub: '平面设计', tags: ['Photoshop', 'Illustrator', '品牌视觉'], company: '青柚生活', hrName: '吴女士', hrTitle: '品牌主管', days: 4 },
  { id: 27, title: '在线客服', sMin: 5, sMax: 8, city: '成都', area: '高新区', exp: '应届', edu: '大专', cat: '客服', sub: '在线客服', tags: ['沟通表达', '工单系统', '耐心细致'], company: '蜂鸟出行', hrName: '郑女士', hrTitle: '招聘专员', days: 4 },
  { id: 29, title: '招聘专员', sMin: 8, sMax: 14, city: '深圳', area: '福田区', exp: '1-3年', edu: '本科', cat: '人力/行政', sub: '招聘专员', tags: ['简历筛选', '面试邀约', '招聘渠道'], company: '溪流互动', hrName: '周女士', hrTitle: 'HRBP', days: 4 },
  { id: 40, title: '机械工程师', sMin: 12, sMax: 22, city: '苏州', area: '工业园区', exp: '3-5年', edu: '本科', cat: '生产制造', sub: '机械工程师', tags: ['SolidWorks', 'DFM', '非标设计'], company: '恒岳制造', hrName: '高先生', hrTitle: '研发经理', days: 4 },
  { id: 42, title: '仓储主管', sMin: 8, sMax: 14, city: '武汉', area: '东西湖区', exp: '3-5年', edu: '大专', cat: '物流/采购', sub: '仓储管理', tags: ['WMS', '库存管理', '团队管理'], company: '锦程物流', hrName: '彭先生', hrTitle: '运营经理', days: 4 },
  { id: 12, title: '交互设计师', sMin: 18, sMax: 30, city: '上海', area: '静安区', exp: '3-5年', edu: '本科', cat: '设计', sub: 'UI/UX设计', tags: ['交互', '用户研究', 'B端设计'], company: '云杉科技', hrName: '陈女士', hrTitle: '技术招聘经理', days: 5 },
  { id: 16, title: '运维工程师', sMin: 15, sMax: 28, city: '上海', area: '浦东新区', exp: '3-5年', edu: '本科', cat: '互联网', sub: '运维/安全', tags: ['Linux', 'Docker', '监控告警'], company: '云杉科技', hrName: '赵先生', hrTitle: '运维负责人', days: 5 },
  { id: 19, title: '视频剪辑师', sMin: 8, sMax: 16, city: '深圳', area: '南山区', exp: '1-3年', edu: '大专', cat: '设计', sub: '视频/动效', tags: ['Premiere', 'After Effects', '短视频'], company: '溪流互动', hrName: '周女士', hrTitle: '内容负责人', days: 5 },
  { id: 20, title: '电商运营', sMin: 10, sMax: 18, city: '广州', area: '天河区', exp: '1-3年', edu: '大专', cat: '运营', sub: '电商运营', tags: ['淘宝天猫', '直通车', '数据复盘'], company: '青柚生活', hrName: '吴女士', hrTitle: '运营主管', days: 5 },
  { id: 26, title: '活动策划', sMin: 8, sMax: 14, city: '杭州', area: '西湖区', exp: '1-3年', edu: '大专', cat: '市场/广告', sub: '活动策划', tags: ['创意策划', '活动执行', '供应商管理'], company: '彩虹传媒', hrName: '蒋女士', hrTitle: '品牌总监', days: 5 },
  { id: 32, title: '财务分析', sMin: 18, sMax: 30, city: '深圳', area: '前海', exp: '3-5年', edu: '本科', cat: '财务', sub: '财务分析', tags: ['预算管理', '经营分析', 'Excel建模'], company: '极光量子', hrName: '林先生', hrTitle: '财务总监', days: 5 },
  { id: 34, title: '信贷风控专员', sMin: 15, sMax: 28, city: '深圳', area: '前海', exp: '3-5年', edu: '本科', cat: '金融', sub: '风控', tags: ['信贷风控', 'SQL', '策略规则'], company: '极光量子', hrName: '韩先生', hrTitle: '风控负责人', days: 5 },
  { id: 36, title: '课程顾问', sMin: 8, sMax: 15, city: '上海', area: '浦东新区', exp: '1-3年', edu: '大专', cat: '教育培训', sub: '课程顾问', tags: ['电话邀约', '需求挖掘', '签单转化'], company: '启明教育', hrName: '罗女士', hrTitle: '招生主管', days: 5 },
  { id: 13, title: '客户端测试工程师', sMin: 12, sMax: 20, city: '广州', area: '海珠区', exp: '1-3年', edu: '大专', cat: '互联网', sub: '测试', tags: ['手工测试', 'Charles', '接口测试'], company: '青柚生活', hrName: '吴女士', hrTitle: '运营主管', days: 6 },
  { id: 17, title: '项目经理', sMin: 20, sMax: 35, city: '上海', area: '徐汇区', exp: '5-10年', edu: '本科', cat: '产品', sub: '项目经理', tags: ['PMP', '敏捷管理', '跨部门协作'], company: '云杉科技', hrName: '陈女士', hrTitle: 'PMO总监', days: 6 },
  { id: 23, title: '保险电话销售', sMin: 6, sMax: 10, city: '深圳', area: '罗湖区', exp: '1-3年', edu: '大专', cat: '销售', sub: '电话销售', tags: ['保险电销', '话术', '抗压能力'], company: '安盛财险', hrName: '冯先生', hrTitle: '销售主管', days: 6 },
  { id: 28, title: '客户成功经理', sMin: 12, sMax: 22, city: '上海', area: '徐汇区', exp: '3-5年', edu: '本科', cat: '客服', sub: '客户成功', tags: ['客户续约', '客户培训', '数据分析'], company: '云杉科技', hrName: '沈女士', hrTitle: '客户成功负责人', days: 6 },
  { id: 30, title: '行政助理', sMin: 6, sMax: 10, city: '北京', area: '朝阳区', exp: '应届', edu: '大专', cat: '人力/行政', sub: '行政助理', tags: ['办公软件', '会务安排', '沟通协调'], company: '麦田教育', hrName: '刘女士', hrTitle: '行政主管', days: 6 },
  { id: 41, title: '普工（电子装配）', sMin: 5, sMax: 8, city: '苏州', area: '吴中区', exp: '经验不限', edu: '学历不限', cat: '生产制造', sub: '普工/操作工', tags: ['流水线', '吃苦耐劳', '两班倒'], company: '恒岳制造', hrName: '谢女士', hrTitle: '人事主管', days: 6 },
  { id: 43, title: '供应链经理', sMin: 18, sMax: 30, city: '上海', area: '松江区', exp: '5-10年', edu: '本科', cat: '物流/采购', sub: '供应链', tags: ['需求计划', '库存优化', '供应商管理'], company: '锦程物流', hrName: '侯女士', hrTitle: '供应链总监', days: 6 },
  { id: 44, title: '采购专员', sMin: 7, sMax: 12, city: '苏州', area: '相城区', exp: '1-3年', edu: '大专', cat: '物流/采购', sub: '采购专员', tags: ['询比价', '供应商开发', 'ERP'], company: '恒岳制造', hrName: '谢女士', hrTitle: '人事主管', days: 7 },
  { id: 47, title: '房产经纪人', sMin: 6, sMax: 15, city: '武汉', area: '武昌区', exp: '经验不限', edu: '学历不限', cat: '建筑/房地产', sub: '房产经纪', tags: ['客户接待', '带看', '谈判技巧'], company: '悦居地产', hrName: '任先生', hrTitle: '门店经理', days: 7 },
  { id: 48, title: '室内设计师', sMin: 10, sMax: 20, city: '杭州', area: '西湖区', exp: '3-5年', edu: '大专', cat: '建筑/房地产', sub: '室内设计', tags: ['CAD', '3D Max', '施工图'], company: '悦居地产', hrName: '邓女士', hrTitle: '设计总监', days: 7 },
  { id: 49, title: '内容编辑', sMin: 8, sMax: 14, city: '上海', area: '静安区', exp: '1-3年', edu: '本科', cat: '传媒/文化', sub: '编辑/记者', tags: ['文案策划', '选题', '采编'], company: '彩虹传媒', hrName: '章女士', hrTitle: '主编', days: 7 },
  { id: 50, title: '摄影摄像师', sMin: 8, sMax: 15, city: '成都', area: '高新区', exp: '1-3年', edu: '大专', cat: '传媒/文化', sub: '摄影摄像', tags: ['拍摄', '灯光布置', '后期调色'], company: '彩虹传媒', hrName: '白先生', hrTitle: '制片人', days: 8 },
  { id: 51, title: '电气工程师', sMin: 14, sMax: 25, city: '苏州', area: '工业园区', exp: '3-5年', edu: '本科', cat: '生产制造', sub: '电气工程师', tags: ['PLC', '电气原理图', '现场调试'], company: '恒岳制造', hrName: '高先生', hrTitle: '研发经理', days: 8 },
]

/** HrProfile id（按 hrName + company 哈希，保证同一 HR 在多个职位间一致） */
const hrIdOf = (hrName: string, company: string): string => {
  let h = 0
  const s = `${company}@${hrName}`
  for (let i = 0; i < s.length; i++) h = (h * 31 + s.charCodeAt(i)) >>> 0
  return `hr-${h.toString(36)}`
}

/** 现在时间（用于 publishedAt 计算） */
const NOW = Date.now()
const DAY_MS = 24 * 60 * 60 * 1000

export const jobs: JobSummary[] = JOBS_RAW.map(raw => {
  const company = companyByName(raw.company)!
  return {
    id: `job-${raw.id}`,
    title: raw.title,
    salaryMin: raw.sMin,
    salaryMax: raw.sMax,
    salary: `${raw.sMin}-${raw.sMax}K`,
    city: raw.city,
    area: raw.area,
    exp: raw.exp,
    edu: raw.edu,
    cat: raw.cat,
    sub: raw.sub,
    tags: raw.tags,
    company: { id: company.id, name: company.name, stage: company.stage!, size: company.size! },
    hr: { id: hrIdOf(raw.hrName, raw.company), name: raw.hrName, title: raw.hrTitle },
    days: raw.days,
    publishedAt: new Date(NOW - raw.days * DAY_MS).toISOString(),
    viewCount: Math.floor(Math.abs(Math.sin(raw.id)) * 500) + 50,
    applyCount: Math.floor(Math.abs(Math.cos(raw.id)) * 50) + 5,
  }
})

/** 按 id 查职位 */
export const jobById = (id: string): JobSummary | undefined => jobs.find(j => j.id === id)

/** 按 HTML 原始数字 id 查（便于与原型交互对齐） */
export const jobByLegacyId = (legacyId: number): JobSummary | undefined =>
  jobs.find(j => j.id === `job-${legacyId}`)

/** 全部公司名集合 */
export const allCompanyNames = (): string[] => Array.from(new Set(JOBS_RAW.map(j => j.company)))
