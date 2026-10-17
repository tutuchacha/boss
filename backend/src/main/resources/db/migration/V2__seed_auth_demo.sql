-- =====================================================================
-- V2 鉴权演示种子（演示账号 / 云杉科技 / 画像）
-- 与前端 mock（src/api/mock/auth.ts）对齐：
--   - 密码统一 123456（BCrypt 哈希与 PasswordHashTest.SEED_HASH 一致）
--   - id 沿用 mock 约定：u-* / up-* / co-*（云杉科技稳定哈希）/ hr-* / cm-*
--   - 时间固定为 UTC 的 2026-09-*（与 mock 展示一致，避免时间漂移影响断言）
-- =====================================================================

INSERT INTO users (id, phone, email, password_hash, role, status, last_login_at, created_at, updated_at) VALUES
('u-seeker-1', '13800000001', 'seeker@pinliao.dev', '$2a$10$.az91kSHYTOcUZerCHPwoe0jN3eUX8oQ.25Hps9C2jX0bZ1/8tr4m', 'JOB_SEEKER', 'ACTIVE', '2026-09-21 10:00:00.000', '2026-09-02 10:00:00.000', '2026-09-21 10:00:00.000'),
('u-hr-1', '13800000002', 'hr@yunshan.dev', '$2a$10$.az91kSHYTOcUZerCHPwoe0jN3eUX8oQ.25Hps9C2jX0bZ1/8tr4m', 'HR', 'ACTIVE', '2026-09-21 10:00:00.000', '2026-09-03 10:00:00.000', '2026-09-21 10:00:00.000');

INSERT INTO user_profiles (id, user_id, name, gender, work_years, edu, expect_cat, expect_sub, expect_city, expect_salary_min, expect_salary_max, seeking_status, created_at, updated_at) VALUES
('up-seeker-1', 'u-seeker-1', '林晓', 'female', 4, '本科', '互联网', '前端开发', '上海', 20, 30, 'ACTIVE', '2026-09-02 10:00:00.000', '2026-09-21 10:00:00.000');

INSERT INTO companies (id, name, stage, size, industry, verified, status, created_at, updated_at) VALUES
('co-acgiw7', '云杉科技', 'B轮', '500-999人', '企业服务', 1, 'ACTIVE', '2026-09-01 10:00:00.000', '2026-09-01 10:00:00.000');

INSERT INTO hr_profiles (id, user_id, company_id, name, title, intro, created_at, updated_at) VALUES
('hr-x2270n', 'u-hr-1', 'co-acgiw7', '陈女士', '技术招聘经理', '云杉科技技术招聘负责人，负责研发团队招聘。', '2026-09-03 10:00:00.000', '2026-09-03 10:00:00.000');

INSERT INTO company_members (id, user_id, company_id, role, joined_at) VALUES
('cm-1', 'u-hr-1', 'co-acgiw7', 'ADMIN', '2026-09-03 10:00:00.000');
