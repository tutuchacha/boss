-- =====================================================================
-- V1 鉴权模块建表（MySQL 8）
-- 契约：docs/api-endpoints.md §1；表结构依据 prisma/schema.prisma 与 docs/data-model.md
-- 说明：本迁移只包含鉴权所需的表（users / user_profiles / companies /
--       company_members / hr_profiles）；其余业务表由后续迁移补齐（T1）。
-- 时间约定：全部 DATETIME(3) 存 UTC（连接层已 SET time_zone='+00:00'）
-- =====================================================================

-- 账号（跨租户共享表，不带 companyId）
CREATE TABLE users (
    id            VARCHAR(64)  NOT NULL COMMENT '业务侧字符串主键（演示沿用 u-* 约定）',
    phone         VARCHAR(20)  NOT NULL COMMENT '手机号（登录主标识）',
    email         VARCHAR(128) NULL COMMENT '邮箱（辅助登录 / 联系方式）',
    password_hash VARCHAR(100) NULL COMMENT 'BCrypt 哈希；OAuth 用户可空',
    role          VARCHAR(32)  NOT NULL COMMENT 'JOB_SEEKER / HR / COMPANY_ADMIN / PLATFORM_ADMIN',
    status        VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE / SUSPENDED / DELETED',
    last_login_at DATETIME(3)  NULL,
    created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted_at    DATETIME(3)  NULL COMMENT '软删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_phone (phone),
    KEY idx_users_role_status (role, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='账号';

-- 求职者画像（与 users 一对一）
CREATE TABLE user_profiles (
    id                VARCHAR(64)  NOT NULL,
    user_id           VARCHAR(64)  NOT NULL,
    name              VARCHAR(64)  NOT NULL,
    avatar_url        VARCHAR(512) NULL,
    gender            VARCHAR(16)  NULL COMMENT 'male / female / other',
    birth_date        DATE         NULL,
    work_years        INT          NULL,
    edu               VARCHAR(32)  NULL,
    expect_cat        VARCHAR(64)  NULL COMMENT '期望一级分类',
    expect_sub        VARCHAR(64)  NULL COMMENT '期望二级岗位',
    expect_city       VARCHAR(64)  NULL,
    expect_salary_min INT          NULL COMMENT '期望薪资下限（K）',
    expect_salary_max INT          NULL COMMENT '期望薪资上限（K）',
    seeking_status    VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE / PASSIVE / NOT_LOOKING',
    default_resume_id VARCHAR(64)  NULL,
    created_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_profiles_user (user_id),
    KEY idx_user_profiles_cat_sub (expect_cat, expect_sub),
    KEY idx_user_profiles_city (expect_city),
    CONSTRAINT fk_user_profiles_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='求职者画像';

-- 企业（租户主表）
CREATE TABLE companies (
    id            VARCHAR(64)  NOT NULL,
    name          VARCHAR(128) NOT NULL,
    logo_url      VARCHAR(512) NULL,
    intro         TEXT         NULL,
    industry      VARCHAR(64)  NULL,
    stage         VARCHAR(32)  NULL COMMENT '融资阶段：天使轮 / B轮 / 已上市 等',
    size          VARCHAR(32)  NULL COMMENT '规模：20-99人 等',
    website       VARCHAR(256) NULL,
    verified      BOOLEAN      NOT NULL DEFAULT FALSE COMMENT '是否已通过企业认证',
    status        VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'ACTIVE / SUSPENDED / PENDING',
    custom_domain VARCHAR(128) NULL COMMENT '白标预留',
    theme_color   VARCHAR(32)  NULL COMMENT '白标预留',
    created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_companies_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='企业（租户主表）';

-- 企业成员（租户私有表，带 companyId）
CREATE TABLE company_members (
    id         VARCHAR(64) NOT NULL,
    user_id    VARCHAR(64) NOT NULL,
    company_id VARCHAR(64) NOT NULL,
    role       VARCHAR(16) NOT NULL COMMENT 'ADMIN / HR',
    joined_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_company_members_user_company (user_id, company_id),
    KEY idx_company_members_company_role (company_id, role),
    CONSTRAINT fk_company_members_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_company_members_company FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='企业成员';

-- 招聘者画像（租户私有表，带 companyId）
CREATE TABLE hr_profiles (
    id         VARCHAR(64)  NOT NULL,
    user_id    VARCHAR(64)  NOT NULL,
    company_id VARCHAR(64)  NOT NULL,
    name       VARCHAR(64)  NOT NULL,
    title      VARCHAR(64)  NOT NULL COMMENT '招聘专员 / HRBP / 技术总监 等',
    avatar_url VARCHAR(512) NULL,
    intro      VARCHAR(512) NULL,
    created_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_hr_profiles_user (user_id),
    KEY idx_hr_profiles_company (company_id),
    CONSTRAINT fk_hr_profiles_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_hr_profiles_company FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='招聘者画像';
