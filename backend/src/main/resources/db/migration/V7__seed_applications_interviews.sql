-- =====================================================================
-- V7 种子：投递（9）与面试（3）
-- ⚠ 本文件由 backend/scripts/seed-gen/generate.mjs 生成，请勿手改；
--   改数据请改 src/api/mock/* 后重跑生成脚本（见脚本头部说明）。
-- 来源：src/api/mock/applications.ts（ensureSeed）
-- HR 侧 6 条覆盖全部状态枚举；求职者 3 条（job-9 / job-18 / job-10，均非云杉）；
-- 面试：int-hr-01（赵之衡·待确认 VIDEO）/ int-hr-02（何知遥·已完成 ONSITE）/ int-seek-01（林晓·待确认 VIDEO）
-- =====================================================================

INSERT INTO applications (id, user_id, job_id, company_id, resume_id, status, cover_letter, created_at, updated_at) VALUES
('app-hr-01', 'u-cand-1', 'job-1', 'co-acgiw7', 'resume-u-cand-1', 'SUBMITTED', '您好，我对该岗位非常感兴趣，期待与您沟通。', DATE_SUB(NOW(3), INTERVAL 8 HOUR), DATE_SUB(NOW(3), INTERVAL 4 HOUR)),
('app-hr-02', 'u-cand-2', 'job-21', 'co-acgiw7', 'resume-u-cand-2', 'VIEWED', NULL, DATE_SUB(NOW(3), INTERVAL 16 HOUR), DATE_SUB(NOW(3), INTERVAL 8 HOUR)),
('app-hr-03', 'u-cand-3', 'job-6', 'co-acgiw7', 'resume-u-cand-3', 'SUBMITTED', NULL, DATE_SUB(NOW(3), INTERVAL 24 HOUR), DATE_SUB(NOW(3), INTERVAL 12 HOUR)),
('app-hr-04', 'u-cand-4', 'job-12', 'co-acgiw7', 'resume-u-cand-4', 'INTERVIEW', NULL, DATE_SUB(NOW(3), INTERVAL 32 HOUR), DATE_SUB(NOW(3), INTERVAL 16 HOUR)),
('app-hr-05', 'u-cand-5', 'job-16', 'co-acgiw7', 'resume-u-cand-5', 'REJECTED', NULL, DATE_SUB(NOW(3), INTERVAL 40 HOUR), DATE_SUB(NOW(3), INTERVAL 20 HOUR)),
('app-hr-06', 'u-cand-6', 'job-17', 'co-acgiw7', 'resume-u-cand-6', 'OFFER', NULL, DATE_SUB(NOW(3), INTERVAL 48 HOUR), DATE_SUB(NOW(3), INTERVAL 24 HOUR)),
('app-seek-01', 'u-seeker-1', 'job-9', 'co-ki0wn7', 'resume-seeker', 'INTERVIEW', NULL, DATE_SUB(NOW(3), INTERVAL 48 HOUR), DATE_SUB(NOW(3), INTERVAL 6 HOUR)),
('app-seek-02', 'u-seeker-1', 'job-18', 'co-jivah0', 'resume-seeker', 'SUBMITTED', NULL, DATE_SUB(NOW(3), INTERVAL 72 HOUR), DATE_SUB(NOW(3), INTERVAL 12 HOUR)),
('app-seek-03', 'u-seeker-1', 'job-10', 'co-fm70w', 'resume-seeker', 'VIEWED', NULL, DATE_SUB(NOW(3), INTERVAL 96 HOUR), DATE_SUB(NOW(3), INTERVAL 18 HOUR));

INSERT INTO interviews (id, application_id, company_id, scheduled_at, format, location, online_url, note, status, created_at, updated_at) VALUES
('int-hr-01', 'app-hr-04', 'co-acgiw7', DATE_ADD(NOW(3), INTERVAL 2 DAY), 'VIDEO', NULL, 'https://meeting.pinliao.dev/demo-room', '请提前 5 分钟进入会议，准备好项目介绍。', 'PENDING', DATE_SUB(NOW(3), INTERVAL 2 HOUR), DATE_SUB(NOW(3), INTERVAL 2 HOUR)),
('int-hr-02', 'app-hr-06', 'co-acgiw7', DATE_SUB(NOW(3), INTERVAL 3 DAY), 'ONSITE', '上海市浦东新区云杉大厦 12F 会议室 A', NULL, NULL, 'COMPLETED', DATE_SUB(NOW(3), INTERVAL 6 DAY), DATE_SUB(NOW(3), INTERVAL 3 DAY)),
('int-seek-01', 'app-seek-01', 'co-ki0wn7', DATE_ADD(NOW(3), INTERVAL 74 HOUR), 'VIDEO', NULL, 'https://meeting.pinliao.dev/room-6088', '一面预计 45 分钟，请准备一段自我介绍。', 'PENDING', DATE_SUB(NOW(3), INTERVAL 6 HOUR), DATE_SUB(NOW(3), INTERVAL 6 HOUR));
