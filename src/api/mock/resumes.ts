/**
 * 聘聊 · Mock 数据 - 在线简历正文（人才筛选的数据源）
 * =====================================================================
 * 为什么单独成文件：
 *   「候选人管理 / 人才筛选」此前只有候选人画像（姓名 / 期望 / 年限），
 *   没有简历正文，无法演示「按技能需求筛选简历」。
 *   本模块为 6 位演示候选人与求职者测试账号补齐 ResumeContent 形状的
 *   简历正文（技能 / 简介 / 经历 / 项目），供匹配模块检索。
 *
 * 依赖方向：本模块不 import 任何其它数据模块（纯数据），避免循环依赖。
 * 与 mock/applications.ts 的约定一致：resumeId = `resume-${userId}`
 * （求职者测试账号为固定值 `resume-seeker`）。
 * Step 3 接后端后本文件整体废弃（简历改由求职者维护、后端存储）。
 * =====================================================================
 */

import type { Resume } from '@/types/api'

/** 稳定的 ISO 时间（避免 SSR/CSR 不一致，不使用 Date.now()） */
const T = (day: number, hour = 9): string =>
  new Date(Date.UTC(2026, 8, 1 + day, hour, 0, 0)).toISOString()

/** 简历正文种子（键：userId；简历 id 与投递种子保持一致） */
const RESUME_CONTENTS: Record<string, Omit<Resume, 'id' | 'userId' | 'name' | 'isDefault' | 'createdAt' | 'updatedAt'>> = {
  // ---- 林小满 · 前端开发 ----
  'u-cand-1': {
    content: {
      basics: {
        name: '林小满',
        phone: '13800001101',
        city: '上海',
        summary: '3 年前端开发经验，熟悉 React 技术栈与前端工程化建设，关注性能优化与用户体验。',
      },
      educations: [
        { school: '某某大学', major: '计算机科学与技术', degree: '本科', startDate: '2018-09', endDate: '2022-06' },
      ],
      experiences: [
        {
          company: '某互联网科技有限公司',
          title: '前端开发工程师',
          startDate: '2022-07',
          endDate: 'present',
          description: '负责 B 端管理后台与微信小程序开发，主导前端工程化改造，沉淀通用组件库，整体首屏性能优化 40%。',
        },
      ],
      projects: [
        {
          name: '招聘管理系统前端重构',
          role: '前端负责人',
          startDate: '2024-03',
          endDate: '2025-06',
          description: '基于 React + TypeScript + Vite 重构招聘管理后台，封装 12 个通用业务组件，首屏加载时间降低 40%，支撑日均 8 万 PV。',
        },
      ],
      skills: ['React', 'TypeScript', 'Vue', '微信小程序', '性能优化', '组件库'],
    },
  },

  // ---- 周叙白 · 后端开发 ----
  'u-cand-2': {
    content: {
      basics: {
        name: '周叙白',
        phone: '13800001102',
        city: '上海',
        summary: '6 年后端开发经验，主攻 Java 服务端与高并发架构设计，有大型电商核心系统经验。',
      },
      educations: [
        { school: '某某大学', major: '软件工程', degree: '硕士', startDate: '2016-09', endDate: '2019-06' },
      ],
      experiences: [
        {
          company: '某电商平台',
          title: '高级后端工程师',
          startDate: '2019-07',
          endDate: 'present',
          description: '负责订单与营销核心链路，主导秒杀系统改造，引入缓存与消息队列削峰，QPS 从 3000 提升至 12000。',
        },
      ],
      projects: [
        {
          name: '秒杀系统高并发改造',
          role: '后端负责人',
          startDate: '2023-02',
          endDate: '2024-05',
          description: '基于 Spring Boot + Redis 预扣库存与消息队列异步下单，支撑峰值 1.2 万 QPS，上线至今零超卖。',
        },
      ],
      skills: ['Java', 'Spring Boot', 'MySQL', 'Redis', '微服务', '高并发'],
    },
  },

  // ---- 沈禾 · 测试 ----
  'u-cand-3': {
    content: {
      basics: {
        name: '沈禾',
        phone: '13800001103',
        city: '上海',
        summary: '4 年测试开发经验，擅长自动化测试体系建设，兼顾接口测试与性能测试。',
      },
      educations: [
        { school: '某某大学', major: '信息管理', degree: '本科', startDate: '2017-09', endDate: '2021-06' },
      ],
      experiences: [
        {
          company: '某金融科技公司',
          title: '测试开发工程师',
          startDate: '2021-07',
          endDate: 'present',
          description: '负责自动化测试平台搭建与维护，回归用例自动化覆盖率从 20% 提升至 75%。',
        },
      ],
      projects: [
        {
          name: '自动化测试平台',
          role: '核心开发',
          startDate: '2022-05',
          endDate: '2023-12',
          description: '基于 Pytest + Selenium 搭建自动化测试平台，支持 300+ 用例定时回归，单轮回归时长压缩至 30 分钟。',
        },
      ],
      skills: ['自动化测试', '接口测试', '性能测试', 'Selenium', 'Pytest', 'JMeter'],
    },
  },

  // ---- 赵之衡 · 运维 / 安全 ----
  'u-cand-4': {
    content: {
      basics: {
        name: '赵之衡',
        phone: '13800001104',
        city: '上海',
        summary: '5 年运维开发经验，聚焦容器化与云原生基础设施，兼顾网络安全防护。',
      },
      educations: [
        { school: '某某大学', major: '网络工程', degree: '本科', startDate: '2017-09', endDate: '2021-06' },
      ],
      experiences: [
        {
          company: '某云计算服务商',
          title: '运维开发工程师',
          startDate: '2021-08',
          endDate: 'present',
          description: '负责生产环境容器化迁移与监控告警体系建设，发布效率提升 3 倍。',
        },
      ],
      projects: [
        {
          name: '容器化与监控体系迁移',
          role: '项目负责人',
          startDate: '2023-03',
          endDate: '2024-08',
          description: '基于 Kubernetes 完成 60+ 服务容器化改造，搭建 Prometheus 全链路监控，故障平均定位时间从小时级降到分钟级。',
        },
      ],
      skills: ['Kubernetes', 'Docker', 'CI/CD', 'Prometheus', 'Linux', '网络安全'],
    },
  },

  // ---- 苏晚 · 产品经理 ----
  'u-cand-5': {
    content: {
      basics: {
        name: '苏晚',
        phone: '13800001105',
        city: '上海',
        summary: '4 年 B 端产品经理经验，擅长需求分析、原型设计与数据驱动的迭代决策。',
      },
      educations: [
        { school: '某某大学', major: '管理科学与工程', degree: '硕士', startDate: '2016-09', endDate: '2019-06' },
      ],
      experiences: [
        {
          company: '某企业服务公司',
          title: '产品经理',
          startDate: '2019-07',
          endDate: 'present',
          description: '负责招聘 SaaS 产品需求分析与迭代规划，主导两个版本的核心功能设计并落地。',
        },
      ],
      projects: [
        {
          name: '招聘产品改版',
          role: '产品负责人',
          startDate: '2023-04',
          endDate: '2024-06',
          description: '通过用户研究与数据分析定位转化漏斗问题，改版后简历投递转化率提升 18%。',
        },
      ],
      skills: ['需求分析', '原型设计', '数据分析', 'A/B 测试', '用户研究', '竞品分析'],
    },
  },

  // ---- 何知遥 · UI/UX 设计 ----
  'u-cand-6': {
    content: {
      basics: {
        name: '何知遥',
        phone: '13800001106',
        city: '上海',
        summary: '3 年 UI/UX 设计经验，负责移动端产品设计与设计系统建设。',
      },
      educations: [
        { school: '某某大学', major: '视觉传达设计', degree: '本科', startDate: '2019-09', endDate: '2023-06' },
      ],
      experiences: [
        {
          company: '某移动互联网公司',
          title: 'UI/UX 设计师',
          startDate: '2023-07',
          endDate: 'present',
          description: '负责招聘 App 的交互设计与视觉规范，参与可用性测试并持续优化关键流程。',
        },
      ],
      projects: [
        {
          name: '移动端设计规范（设计系统）',
          role: '设计负责人',
          startDate: '2024-02',
          endDate: '2025-01',
          description: '基于 Figma 搭建设计系统，沉淀 80+ 组件规范，设计交付效率提升 50%。',
        },
      ],
      skills: ['Figma', '交互设计', '视觉设计', '设计系统', 'Sketch', '用户界面'],
    },
  },

  // ---- 求职者测试账号（林晓）· 前端开发 ----
  'u-seeker-1': {
    content: {
      basics: {
        name: '林晓',
        phone: '13800000001',
        city: '上海',
        summary: '4 年前端开发经验，熟悉 React 与 TypeScript，参与中后台系统与营销活动页开发。',
      },
      educations: [
        { school: '某某大学', major: '软件工程', degree: '本科', startDate: '2018-09', endDate: '2022-06' },
      ],
      experiences: [
        {
          company: '某某网络科技有限公司',
          title: '前端开发工程师',
          startDate: '2022-07',
          endDate: 'present',
          description: '负责营销活动页与中后台系统开发，适配多端展示，沉淀活动页脚手架。',
        },
      ],
      projects: [
        {
          name: '营销活动页脚手架',
          role: '核心开发',
          startDate: '2023-06',
          endDate: '2024-10',
          description: '基于 Vite + React 搭建活动页脚手架，模板化开发把单页上线周期从 2 天缩短到 4 小时。',
        },
      ],
      skills: ['React', 'TypeScript', 'Vue', 'Node.js', 'Webpack', '性能优化'],
    },
  },
}

/** 全部简历记录（id 与投递种子的 resumeId 约定一致） */
export const MOCK_RESUMES: Resume[] = Object.entries(RESUME_CONTENTS).map(([userId, item], i) => ({
  id: userId === 'u-seeker-1' ? 'resume-seeker' : `resume-${userId}`,
  userId,
  name: '默认简历',
  content: item.content,
  isDefault: true,
  createdAt: T(i),
  updatedAt: T(i + 6),
}))

/** 按 userId 查简历正文（人才筛选检索用；无简历返回 undefined） */
export function resumeContentOfUser(userId: string): Resume['content'] | undefined {
  return MOCK_RESUMES.find(r => r.userId === userId)?.content
}
