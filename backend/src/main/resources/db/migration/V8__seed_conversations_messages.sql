-- =====================================================================
-- V8 种子：会话（8）与消息（16）
-- ⚠ 本文件由 backend/scripts/seed-gen/generate.mjs 生成，请勿手改；
--   改数据请改 src/api/mock/* 后重跑生成脚本（见脚本头部说明）。
-- 来源：src/api/mock/hrConversations.ts（HR 侧 6 会话 / 未读 4：2+1+1；id 序列与 mock 一致）
--       + conversations.ts（求职者侧 2 会话；companyId / hrId 由 mock 占位值替换为稳定哈希 id）
-- 时间：HR 侧沿用 mock 固定基准 2026-09-21T10:00:00Z；求职者侧相对 NOW(3)
-- =====================================================================

INSERT INTO conversations (id, job_id, user_id, hr_id, company_id, last_message_at, user_unread_count, hr_unread_count, closed_by_hr, created_at) VALUES
('conv-hr-1', 'job-1', 'u-cand-1', 'hr-x2270n', 'co-acgiw7', '2026-09-21 09:55:00.000', 0, 2, 0, '2026-09-21 09:25:00.000'),
('conv-hr-4', 'job-21', 'u-cand-2', 'hr-x2270n', 'co-acgiw7', '2026-09-21 09:08:00.000', 0, 1, 0, '2026-09-21 08:38:00.000'),
('conv-hr-8', 'job-6', 'u-cand-3', 'hr-x2270n', 'co-acgiw7', '2026-09-21 08:21:00.000', 0, 1, 0, '2026-09-21 07:51:00.000'),
('conv-hr-11', 'job-12', 'u-cand-4', 'hr-x2270n', 'co-acgiw7', '2026-09-21 07:34:00.000', 0, 0, 0, '2026-09-21 07:04:00.000'),
('conv-hr-14', 'job-16', 'u-cand-5', 'hr-x2270n', 'co-acgiw7', '2026-09-21 06:47:00.000', 0, 0, 0, '2026-09-21 06:17:00.000'),
('conv-hr-16', 'job-17', 'u-cand-6', 'hr-x2270n', 'co-acgiw7', '2026-09-21 06:00:00.000', 0, 0, 0, '2026-09-21 05:30:00.000'),
('conv-seed-003', 'job-3', 'u-seeker-1', 'hr-2o4rqu', 'co-fm70w', DATE_SUB(NOW(3), INTERVAL 25 MINUTE), 1, 0, 0, DATE_SUB(NOW(3), INTERVAL 25 MINUTE)),
('conv-seed-008', 'job-8', 'u-seeker-1', 'hr-1f2x4s9', 'co-dekwg9', DATE_SUB(NOW(3), INTERVAL 3 HOUR), 1, 0, 0, DATE_SUB(NOW(3), INTERVAL 3 HOUR));

INSERT INTO messages (id, conversation_id, company_id, from_type, from_user_id, from_hr_id, type, content, created_at, read_by_user, read_by_hr) VALUES
('msg-hr-2', 'conv-hr-1', 'co-acgiw7', 'USER', 'u-cand-1', NULL, 'TEXT', '您好，我在招聘网站上看到这个岗位，我的经历比较匹配，方便聊聊吗？', '2026-09-21 09:35:00.000', 1, 0),
('msg-hr-3', 'conv-hr-1', 'co-acgiw7', 'USER', 'u-cand-1', NULL, 'TEXT', '请问方便的话，今天下午可以电话吗？', '2026-09-21 09:55:00.000', 1, 0),
('msg-hr-5', 'conv-hr-4', 'co-acgiw7', 'USER', 'u-cand-2', NULL, 'TEXT', '您好！我对贵司这个职位很感兴趣，请问目前还在招吗？', '2026-09-21 08:48:00.000', 1, 0),
('msg-hr-6', 'conv-hr-4', 'co-acgiw7', 'HR', NULL, 'hr-x2270n', 'TEXT', '您好，您的背景我们比较感兴趣，方便约个时间电话沟通吗？', '2026-09-21 08:58:00.000', 1, 1),
('msg-hr-7', 'conv-hr-4', 'co-acgiw7', 'USER', 'u-cand-2', NULL, 'TEXT', '请问方便的话，今天下午可以电话吗？', '2026-09-21 09:08:00.000', 1, 0),
('msg-hr-9', 'conv-hr-8', 'co-acgiw7', 'USER', 'u-cand-3', NULL, 'TEXT', '您好，我有相关项目经验，想了解一下岗位的具体业务方向。', '2026-09-21 08:01:00.000', 1, 0),
('msg-hr-10', 'conv-hr-8', 'co-acgiw7', 'USER', 'u-cand-3', NULL, 'TEXT', '请问方便的话，今天下午可以电话吗？', '2026-09-21 08:21:00.000', 1, 0),
('msg-hr-12', 'conv-hr-11', 'co-acgiw7', 'USER', 'u-cand-4', NULL, 'TEXT', '您好，请问这个岗位对技术栈有硬性要求吗？', '2026-09-21 07:14:00.000', 1, 1),
('msg-hr-13', 'conv-hr-11', 'co-acgiw7', 'HR', NULL, 'hr-x2270n', 'TEXT', '您好，您的背景我们比较感兴趣，方便约个时间电话沟通吗？', '2026-09-21 07:24:00.000', 1, 1),
('msg-hr-15', 'conv-hr-14', 'co-acgiw7', 'USER', 'u-cand-5', NULL, 'TEXT', '您好，我已经投递了简历，期待您的回复。', '2026-09-21 06:27:00.000', 1, 1),
('msg-hr-17', 'conv-hr-16', 'co-acgiw7', 'USER', 'u-cand-6', NULL, 'TEXT', '您好，想确认一下工作地点和到岗时间的要求。', '2026-09-21 05:40:00.000', 1, 1),
('msg-hr-18', 'conv-hr-16', 'co-acgiw7', 'HR', NULL, 'hr-x2270n', 'TEXT', '您好，您的背景我们比较感兴趣，方便约个时间电话沟通吗？', '2026-09-21 05:50:00.000', 1, 1),
('msg-3-1', 'conv-seed-003', 'co-fm70w', 'SYSTEM', NULL, NULL, 'SYSTEM', '你已向 李女士 发起沟通', DATE_SUB(NOW(3), INTERVAL 25 MINUTE), 1, 1),
('msg-3-2', 'conv-seed-003', 'co-fm70w', 'USER', 'u-seeker-1', NULL, 'TEXT', '您好，我对「产品经理（B端）」很感兴趣，方便聊聊吗？', DATE_SUB(NOW(3), INTERVAL 24 MINUTE), 1, 1),
('msg-3-3', 'conv-seed-003', 'co-fm70w', 'HR', NULL, 'hr-2o4rqu', 'TEXT', '您好！看了您的简历，B 端经验很匹配，方便这周找个时间电话聊聊吗？', DATE_SUB(NOW(3), INTERVAL 23 MINUTE), 0, 1),
('msg-8-1', 'conv-seed-008', 'co-dekwg9', 'HR', NULL, 'hr-1f2x4s9', 'TEXT', '您好，我是极光量子的孙先生，看到您的简历，想和您聊聊 Go 后端的机会。', DATE_SUB(NOW(3), INTERVAL 3 HOUR), 0, 1);
