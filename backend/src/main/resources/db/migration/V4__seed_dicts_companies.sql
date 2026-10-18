-- =====================================================================
-- V4 种子：公司字典 / 城市 / 分类树
-- ⚠ 本文件由 backend/scripts/seed-gen/generate.mjs 生成，请勿手改；
--   改数据请改 src/api/mock/* 后重跑生成脚本（见脚本头部说明）。
-- 来源：src/api/mock/companies.ts（16 家；云杉科技由 V2 落库）、dict.ts（9 城）、categories.ts（17 一级 + 67 二级）
-- =====================================================================

INSERT INTO companies (id, name, logo_url, intro, industry, stage, size, website, verified, status, created_at, updated_at) VALUES
('co-dc3wxe', '星河数据', NULL, NULL, '大数据', 'C轮', '1000-9999人', NULL, 1, 'ACTIVE', '2026-09-01 10:00:00.000', '2026-09-01 10:00:00.000'),
('co-fm70w', '木棉云', NULL, NULL, '企业服务', 'A轮', '100-499人', NULL, 1, 'ACTIVE', '2026-09-01 10:00:00.000', '2026-09-01 10:00:00.000'),
('co-eevb71', '溪流互动', NULL, NULL, '游戏', '不需要融资', '100-499人', NULL, 1, 'ACTIVE', '2026-09-01 10:00:00.000', '2026-09-01 10:00:00.000'),
('co-jivah0', '青柚生活', NULL, NULL, '电商', '天使轮', '20-99人', NULL, 1, 'ACTIVE', '2026-09-01 10:00:00.000', '2026-09-01 10:00:00.000'),
('co-hol57z', '蜂鸟出行', NULL, NULL, '出行', 'B轮', '500-999人', NULL, 1, 'ACTIVE', '2026-09-01 10:00:00.000', '2026-09-01 10:00:00.000'),
('co-dekwg9', '极光量子', NULL, NULL, '金融科技', 'D轮', '1000-9999人', NULL, 1, 'ACTIVE', '2026-09-01 10:00:00.000', '2026-09-01 10:00:00.000'),
('co-ki0wn7', '麦田教育', NULL, NULL, '在线教育', '已上市', '10000人以上', NULL, 1, 'ACTIVE', '2026-09-01 10:00:00.000', '2026-09-01 10:00:00.000'),
('co-c1q3jt', '安盛财险', NULL, NULL, '保险', '已上市', '10000人以上', NULL, 1, 'ACTIVE', '2026-09-01 10:00:00.000', '2026-09-01 10:00:00.000'),
('co-cjcb9n', '恒岳制造', NULL, NULL, '智能制造', '已上市', '1000-9999人', NULL, 1, 'ACTIVE', '2026-09-01 10:00:00.000', '2026-09-01 10:00:00.000'),
('co-aadwpz', '仁和医疗', NULL, NULL, '医疗健康', '不需要融资', '1000-9999人', NULL, 1, 'ACTIVE', '2026-09-01 10:00:00.000', '2026-09-01 10:00:00.000'),
('co-b1ox2w', '启明教育', NULL, NULL, '教育培训', 'B轮', '500-999人', NULL, 1, 'ACTIVE', '2026-09-01 10:00:00.000', '2026-09-01 10:00:00.000'),
('co-jbnqe5', '锦程物流', NULL, NULL, '物流供应链', '已上市', '10000人以上', NULL, 1, 'ACTIVE', '2026-09-01 10:00:00.000', '2026-09-01 10:00:00.000'),
('co-culnwz', '拾味餐饮', NULL, NULL, '餐饮', '不需要融资', '100-499人', NULL, 1, 'ACTIVE', '2026-09-01 10:00:00.000', '2026-09-01 10:00:00.000'),
('co-cks652', '悦居地产', NULL, NULL, '房地产', '不需要融资', '1000-9999人', NULL, 1, 'ACTIVE', '2026-09-01 10:00:00.000', '2026-09-01 10:00:00.000'),
('co-clazky', '彩虹传媒', NULL, NULL, '文化传媒', 'A轮', '100-499人', NULL, 1, 'ACTIVE', '2026-09-01 10:00:00.000', '2026-09-01 10:00:00.000');

INSERT INTO cities (id, code, name, province, hot, sort) VALUES
('city-0', '10000', '全国', NULL, 0, 0),
('city-1', '10001', '上海', NULL, 1, 1),
('city-2', '10002', '北京', NULL, 1, 2),
('city-3', '10003', '深圳', NULL, 1, 3),
('city-4', '10004', '杭州', NULL, 1, 4),
('city-5', '10005', '广州', NULL, 1, 5),
('city-6', '10006', '成都', NULL, 0, 6),
('city-7', '10007', '武汉', NULL, 0, 7),
('city-8', '10008', '苏州', NULL, 0, 8);

INSERT INTO categories (id, parent_id, name, sort, icon, responsibilities_template) VALUES
('cat-c4tqn', NULL, '互联网', 0, NULL, '负责相关系统或产品功能的设计、开发与持续优化
与产品、设计、测试协作，按节奏交付高质量成果
参与技术方案评审，沉淀文档与最佳实践'),
('sub-d3oq0d', 'cat-c4tqn', '前端开发', 0, NULL, NULL),
('sub-ddakem', 'cat-c4tqn', '后端开发', 1, NULL, NULL),
('sub-iycas4', 'cat-c4tqn', '移动开发', 2, NULL, NULL),
('sub-kk1s', 'cat-c4tqn', '测试', 3, NULL, NULL),
('sub-1wuhm5h', 'cat-c4tqn', '算法/AI', 4, NULL, NULL),
('sub-v3ares', 'cat-c4tqn', '运维/安全', 5, NULL, NULL),
('sub-fwjik4', 'cat-c4tqn', '数据分析', 6, NULL, NULL),
('cat-dud6', NULL, '产品', 1, NULL, '调研用户与业务场景，输出清晰的需求与方案
推动研发、设计、运营协作，跟进上线与数据复盘
持续迭代，提升核心业务指标'),
('sub-cdj3st', 'cat-dud6', '产品经理', 0, NULL, NULL),
('sub-nrjl6k', 'cat-dud6', '项目经理', 1, NULL, NULL),
('sub-cdb40n', 'cat-dud6', '产品助理', 2, NULL, NULL),
('cat-ojab', NULL, '设计', 2, NULL, '负责视觉或交互方案设计，输出高质量设计稿
建设并维护设计规范，推动设计落地
结合反馈与数据持续优化体验'),
('sub-gwu7vd', 'cat-ojab', 'UI/UX设计', 0, NULL, NULL),
('sub-f36m50', 'cat-ojab', '平面设计', 1, NULL, NULL),
('sub-4nyyza', 'cat-ojab', '视频/动效', 2, NULL, NULL),
('cat-p6qd', NULL, '运营', 3, NULL, '制定运营计划并执行，达成阶段性增长目标
分析用户与内容数据，输出复盘与优化策略
协同产品、市场团队推进活动落地'),
('sub-iar3tg', 'cat-p6qd', '用户运营', 0, NULL, NULL),
('sub-rijcp6', 'cat-p6qd', '新媒体运营', 1, NULL, NULL),
('sub-i8vsgg', 'cat-p6qd', '电商运营', 2, NULL, NULL),
('sub-cuha37', 'cat-p6qd', '内容运营', 3, NULL, NULL),
('cat-pt7y', NULL, '销售', 4, NULL, '开拓并维护客户，完成业绩目标
跟进销售全流程：需求沟通、方案、报价与签约
定期复盘客户与业绩数据，优化销售策略'),
('sub-n2olll', 'cat-pt7y', '销售代表', 0, NULL, NULL),
('sub-zuutke', 'cat-pt7y', '大客户销售', 1, NULL, NULL),
('sub-ihynvk', 'cat-pt7y', '电话销售', 2, NULL, NULL),
('sub-6nire1', 'cat-pt7y', '渠道/商务', 3, NULL, NULL),
('cat-nfg59e', NULL, '市场/广告', 5, NULL, '制定并执行市场推广与品牌传播计划
管理渠道与供应商，控制预算，评估投放效果
输出市场洞察，支持业务增长'),
('sub-epwpsz', 'cat-nfg59e', '市场推广', 0, NULL, NULL),
('sub-dfmd3g', 'cat-nfg59e', '品牌公关', 1, NULL, NULL),
('sub-h0nb57', 'cat-nfg59e', '活动策划', 2, NULL, NULL),
('sub-es3ae4', 'cat-nfg59e', '广告投放', 3, NULL, NULL),
('cat-g5gr', NULL, '客服', 6, NULL, '通过在线或电话渠道解答客户咨询，处理投诉与售后
记录并跟进问题，推动内部协同解决
收集客户反馈，协助优化产品与服务流程'),
('sub-duwfbq', 'cat-g5gr', '在线客服', 0, NULL, NULL),
('sub-eeppfs', 'cat-g5gr', '客户成功', 1, NULL, NULL),
('sub-dd1afg', 'cat-g5gr', '售后支持', 2, NULL, NULL),
('cat-y4svtt', NULL, '人力/行政', 7, NULL, '负责本职模块的日常事务与流程执行
跟进各部门需求，保障事务高效运转
整理制度与档案，支持组织管理'),
('sub-fn6bry', 'cat-y4svtt', '招聘专员', 0, NULL, NULL),
('sub-cdnhnt', 'cat-y4svtt', '人力资源', 1, NULL, NULL),
('sub-l7saze', 'cat-y4svtt', '行政助理', 2, NULL, NULL),
('sub-k5qu', 'cat-y4svtt', '法务', 3, NULL, NULL),
('cat-ogjj', NULL, '财务', 8, NULL, '负责本职模块的账务处理、核算与报表
配合审计、税务等外部检查，保证合规
分析经营数据，为管理层提供决策支持'),
('sub-f37f', 'cat-ogjj', '会计', 0, NULL, NULL),
('sub-fje5', 'cat-ogjj', '出纳', 1, NULL, NULL),
('sub-lv6gbd', 'cat-ogjj', '财务分析', 2, NULL, NULL),
('sub-hcv6', 'cat-ogjj', '审计', 3, NULL, NULL),
('cat-pjoc', NULL, '金融', 9, NULL, '为客户提供专业的金融服务与方案
识别并控制业务风险，确保合规经营
持续维护客户关系，达成业务指标'),
('sub-rtkel8', 'cat-pjoc', '投资/理财顾问', 0, NULL, NULL),
('sub-s7qt', 'cat-pjoc', '风控', 1, NULL, NULL),
('sub-nb2kom', 'cat-pjoc', '银行柜员', 2, NULL, NULL),
('sub-cv71ci', 'cat-pjoc', '保险顾问', 3, NULL, NULL),
('cat-dbgutp', NULL, '教育培训', 10, NULL, '根据教学或培训目标设计课程、开展教学
跟进学员学习情况，及时反馈与辅导
参与教研与课程迭代，提升教学质量'),
('sub-ehd6bc', 'cat-dbgutp', '学科教师', 0, NULL, NULL),
('sub-lvu2b9', 'cat-dbgutp', '课程顾问', 1, NULL, NULL),
('sub-e1rbk0', 'cat-dbgutp', '培训讲师', 2, NULL, NULL),
('sub-twfcdi', 'cat-dbgutp', '早教/幼教', 3, NULL, NULL),
('cat-azja9a', NULL, '医疗健康', 11, NULL, '按规范开展本岗位专业工作，保障服务质量
与医生、患者或客户保持良好沟通
参与学习培训，遵守医疗与合规要求'),
('sub-icjz', 'cat-azja9a', '护士', 0, NULL, NULL),
('sub-da47cl', 'cat-azja9a', '医药代表', 1, NULL, NULL),
('sub-m8rll', 'cat-azja9a', '药剂师', 2, NULL, NULL),
('sub-copkzn', 'cat-azja9a', '健康管理', 3, NULL, NULL),
('cat-f3qdxu', NULL, '生产制造', 12, NULL, '按工艺与标准完成本岗位生产、检验或技术工作
保障产品质量与生产安全，配合改善
记录并反馈生产异常，协同处理'),
('sub-11gctc2', 'cat-f3qdxu', '机械工程师', 0, NULL, NULL),
('sub-114tsyp', 'cat-f3qdxu', '电气工程师', 1, NULL, NULL),
('sub-q4tk', 'cat-f3qdxu', '质检', 2, NULL, NULL),
('sub-14z6wq8', 'cat-f3qdxu', '普工/操作工', 3, NULL, NULL),
('cat-yyf6pp', NULL, '物流/采购', 13, NULL, '负责仓储、采购或供应链环节的日常运作
控制成本与库存，保障交付时效
协调供应商与内部团队，持续优化流程'),
('sub-cdrxpe', 'cat-yyf6pp', '仓储管理', 0, NULL, NULL),
('sub-dplyl', 'cat-yyf6pp', '供应链', 1, NULL, NULL),
('sub-mubau7', 'cat-yyf6pp', '采购专员', 2, NULL, NULL),
('sub-155o30v', 'cat-yyf6pp', '配送/司机', 3, NULL, NULL),
('cat-wrs1qt', NULL, '零售/餐饮', 14, NULL, '负责门店或后厨的日常运营与出品服务
保障服务质量与卫生标准，提升顾客满意度
管理并培训团队，完成经营目标'),
('sub-hy46', 'cat-wrs1qt', '店长', 0, NULL, NULL),
('sub-hfhl', 'cat-wrs1qt', '导购', 1, NULL, NULL),
('sub-fnw0', 'cat-wrs1qt', '厨师', 2, NULL, NULL),
('sub-hj6na', 'cat-wrs1qt', '服务员', 3, NULL, NULL),
('cat-1pg5yf2', NULL, '建筑/房地产', 15, NULL, '负责项目或客户的日常跟进与专业服务
按规范完成图纸、方案或合同相关工作
维护客户关系，推动项目按时落地'),
('sub-fbuavz', 'cat-1pg5yf2', '房产经纪', 0, NULL, NULL),
('sub-ec7ci0', 'cat-1pg5yf2', '室内设计', 1, NULL, NULL),
('sub-dqt1tj', 'cat-1pg5yf2', '土木工程', 2, NULL, NULL),
('sub-qj2v', 'cat-1pg5yf2', '造价', 3, NULL, NULL),
('cat-10ovu3w', NULL, '传媒/文化', 16, NULL, '策划并产出内容，覆盖选题、拍摄或编辑
与团队协作，保证内容质量和发布节奏
分析内容数据，持续优化产出'),
('sub-koa2zv', 'cat-10ovu3w', '编辑/记者', 0, NULL, NULL),
('sub-fpjsaw', 'cat-10ovu3w', '摄影摄像', 1, NULL, NULL),
('sub-1gyygp5', 'cat-10ovu3w', '主播/编导', 2, NULL, NULL);
