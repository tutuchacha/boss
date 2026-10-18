-- =====================================================================
-- V3 T1 核心业务表建表（MySQL 8）
-- 契约：docs/api-endpoints.md §2~§12；表结构依据 prisma/schema.prisma 与 docs/data-model.md
-- 说明：V1 已建鉴权 5 表（users / user_profiles / companies / company_members / hr_profiles），
--       本迁移补齐其余全部业务表（字典 / 职位 / 简历 / 投递 / 面试 / 会话 / 消息 / 收藏 / 运营表）。
-- 时间约定：全部 DATETIME(3) 存 UTC（连接层已 SET time_zone='+00:00'）
-- 与 prisma 的差异（演示简化，均有注释标注）：
--   1) jobs.hr_id / conversations.hr_id / messages.from_hr_id 不做外键：演示种子中除云杉科技陈女士
--      外，其余公司 HR 无账号（无 users 行）；职位冗余存储 hr_name / hr_title 供列表与详情直接展示
--   2) messages 依 data-model.md §2.2 带 company_id（prisma 未列，属多租户冗余列）
--   3) sys_configs 列名用 config_key / config_value（key / value 在 MySQL 属保留字风险位）
-- =====================================================================

-- 城市字典（跨租户共享）
CREATE TABLE cities (
    id       VARCHAR(64) NOT NULL,
    code     VARCHAR(16) NOT NULL,
    name     VARCHAR(64) NOT NULL,
    province VARCHAR(64) NULL,
    hot      BOOLEAN     NOT NULL DEFAULT FALSE COMMENT '是否热门城市',
    sort     INT         NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_cities_code (code),
    KEY idx_cities_sort (sort)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='城市字典';

-- 分类树（跨租户共享；一级分类 parent_id 为 NULL）
CREATE TABLE categories (
    id                        VARCHAR(64)  NOT NULL,
    parent_id                 VARCHAR(64)  NULL,
    name                      VARCHAR(64)  NOT NULL,
    sort                      INT          NOT NULL DEFAULT 0,
    icon                      VARCHAR(256) NULL,
    responsibilities_template TEXT         NULL COMMENT '一级分类职责模板（发布职位用，原型 RESP 字典）',
    PRIMARY KEY (id),
    KEY idx_categories_parent_sort (parent_id, sort),
    CONSTRAINT fk_categories_parent FOREIGN KEY (parent_id) REFERENCES categories (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='分类树';

-- 系统配置（跨租户共享）
CREATE TABLE sys_configs (
    id           VARCHAR(64)  NOT NULL,
    config_key   VARCHAR(128) NOT NULL COMMENT '对应 prisma SysConfig.key',
    config_value TEXT         NOT NULL COMMENT '对应 prisma SysConfig.value',
    description  VARCHAR(256) NULL COMMENT '对应 prisma SysConfig.desc',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_configs_key (config_key)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='系统配置';

-- 职位（租户私有表，带 company_id；求职者侧跨租户可见 OPEN 职位）
CREATE TABLE jobs (
    id               VARCHAR(64)  NOT NULL,
    company_id       VARCHAR(64)  NOT NULL,
    hr_id            VARCHAR(64)  NOT NULL COMMENT '发布者展示 id（演示中非全量外键，见文件头说明）',
    hr_name          VARCHAR(64)  NOT NULL COMMENT '冗余展示：招聘者姓名',
    hr_title         VARCHAR(64)  NOT NULL COMMENT '冗余展示：招聘者职位',
    title            VARCHAR(128) NOT NULL,
    salary_min       INT          NOT NULL COMMENT 'K',
    salary_max       INT          NOT NULL COMMENT 'K',
    city             VARCHAR(64)  NOT NULL,
    area             VARCHAR(64)  NULL,
    exp              VARCHAR(32)  NOT NULL COMMENT '不限 / 应届 / 1-3年 / 3-5年 / 5-10年',
    edu              VARCHAR(32)  NOT NULL COMMENT '学历不限 / 大专 / 本科 / 硕士',
    cat              VARCHAR(64)  NOT NULL COMMENT '一级分类',
    sub              VARCHAR(64)  NOT NULL COMMENT '二级岗位',
    tags             JSON         NULL COMMENT '标签数组',
    responsibilities TEXT         NULL,
    requirements     TEXT         NULL,
    welfare          JSON         NULL COMMENT '福利数组',
    status           VARCHAR(16)  NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN / CLOSED / DRAFT',
    published_at     DATETIME(3)  NOT NULL,
    view_count       INT          NOT NULL DEFAULT 0,
    apply_count      INT          NOT NULL DEFAULT 0,
    created_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted_at       DATETIME(3)  NULL COMMENT '软删除',
    PRIMARY KEY (id),
    KEY idx_jobs_company_status (company_id, status),
    KEY idx_jobs_hr_status_pub (hr_id, status, published_at),
    KEY idx_jobs_list (city, cat, status, published_at),
    KEY idx_jobs_sub_status (sub, status),
    CONSTRAINT fk_jobs_company FOREIGN KEY (company_id) REFERENCES companies (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='职位';

-- 简历（跨租户共享；content 为 JSON：{basics, educations, experiences, projects, skills}）
CREATE TABLE resumes (
    id             VARCHAR(64)  NOT NULL,
    user_id        VARCHAR(64)  NOT NULL,
    name           VARCHAR(64)  NOT NULL COMMENT '“我的简历” / “PM版” 等',
    content        JSON         NULL,
    attachment_url VARCHAR(512) NULL,
    is_default     BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted_at     DATETIME(3)  NULL COMMENT '软删除',
    PRIMARY KEY (id),
    KEY idx_resumes_user_default (user_id, is_default),
    CONSTRAINT fk_resumes_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='简历';

-- 收藏（跨租户共享，求职者视角）
CREATE TABLE favorites (
    id         VARCHAR(64) NOT NULL,
    user_id    VARCHAR(64) NOT NULL,
    job_id     VARCHAR(64) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_favorites_user_job (user_id, job_id),
    KEY idx_favorites_user_created (user_id, created_at),
    CONSTRAINT fk_favorites_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_favorites_job FOREIGN KEY (job_id) REFERENCES jobs (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='收藏';

-- 投递（租户私有表带 company_id；同一求职者对同一职位唯一）
CREATE TABLE applications (
    id           VARCHAR(64)  NOT NULL,
    user_id      VARCHAR(64)  NOT NULL,
    job_id       VARCHAR(64)  NOT NULL,
    company_id   VARCHAR(64)  NOT NULL COMMENT '多租户冗余（与 jobs.company_id 一致，便于拦截器注入）',
    resume_id    VARCHAR(64)  NOT NULL,
    status       VARCHAR(16)  NOT NULL DEFAULT 'SUBMITTED' COMMENT 'SUBMITTED / VIEWED / INTERVIEW / OFFER / REJECTED / WITHDRAWN',
    cover_letter VARCHAR(500) NULL COMMENT '打招呼语 / 求职信',
    created_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted_at   DATETIME(3)  NULL COMMENT '软删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_applications_user_job (user_id, job_id),
    KEY idx_applications_user_created (user_id, created_at),
    KEY idx_applications_company_status (company_id, status, created_at),
    KEY idx_applications_job_status (job_id, status),
    CONSTRAINT fk_applications_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_applications_job FOREIGN KEY (job_id) REFERENCES jobs (id),
    CONSTRAINT fk_applications_company FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT fk_applications_resume FOREIGN KEY (resume_id) REFERENCES resumes (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='投递';

-- 面试邀请（租户私有表带 company_id）
CREATE TABLE interviews (
    id             VARCHAR(64)  NOT NULL,
    application_id VARCHAR(64)  NOT NULL,
    company_id     VARCHAR(64)  NOT NULL,
    scheduled_at   DATETIME(3)  NOT NULL,
    format         VARCHAR(16)  NOT NULL COMMENT 'ONSITE / VIDEO / PHONE',
    location       VARCHAR(256) NULL,
    online_url     VARCHAR(512) NULL,
    note           VARCHAR(500) NULL,
    status         VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING / ACCEPTED / DECLINED / COMPLETED / CANCELLED',
    created_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_interviews_company_status (company_id, status),
    KEY idx_interviews_application_status (application_id, status),
    CONSTRAINT fk_interviews_application FOREIGN KEY (application_id) REFERENCES applications (id) ON DELETE CASCADE,
    CONSTRAINT fk_interviews_company FOREIGN KEY (company_id) REFERENCES companies (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='面试邀请';

-- 会话（租户私有表带 company_id；同一求职者对同一职位唯一）
CREATE TABLE conversations (
    id                VARCHAR(64) NOT NULL,
    job_id            VARCHAR(64) NOT NULL,
    user_id           VARCHAR(64) NOT NULL COMMENT '求职者',
    hr_id             VARCHAR(64) NOT NULL COMMENT '招聘者展示 id（演示中非全量外键，见文件头说明）',
    company_id        VARCHAR(64) NOT NULL,
    last_message_at   DATETIME(3) NOT NULL,
    user_unread_count INT         NOT NULL DEFAULT 0,
    hr_unread_count   INT         NOT NULL DEFAULT 0,
    closed_by_hr      BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at        DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_conversations_job_user (job_id, user_id),
    KEY idx_conversations_user_last (user_id, last_message_at),
    KEY idx_conversations_hr_last (hr_id, last_message_at),
    KEY idx_conversations_company_last (company_id, last_message_at),
    CONSTRAINT fk_conversations_job FOREIGN KEY (job_id) REFERENCES jobs (id),
    CONSTRAINT fk_conversations_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_conversations_company FOREIGN KEY (company_id) REFERENCES companies (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='会话';

-- 消息（租户私有表带 company_id；内容为文本或 JSON 字符串）
CREATE TABLE messages (
    id              VARCHAR(64) NOT NULL,
    conversation_id VARCHAR(64) NOT NULL,
    company_id      VARCHAR(64) NOT NULL COMMENT '多租户冗余（data-model.md §2.2）',
    from_type       VARCHAR(16) NOT NULL COMMENT 'USER / HR / SYSTEM',
    from_user_id    VARCHAR(64) NULL,
    from_hr_id      VARCHAR(64) NULL COMMENT '招聘者展示 id（非全量外键）',
    type            VARCHAR(32) NOT NULL COMMENT 'TEXT / RESUME / INTERVIEW_INVITE / JOB_CARD / SYSTEM',
    content         TEXT        NOT NULL,
    created_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    read_by_user    BOOLEAN     NOT NULL DEFAULT FALSE,
    read_by_hr      BOOLEAN     NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    KEY idx_messages_conv_created (conversation_id, created_at),
    KEY idx_messages_from_created (from_type, created_at),
    KEY idx_messages_company (company_id),
    CONSTRAINT fk_messages_conversation FOREIGN KEY (conversation_id) REFERENCES conversations (id) ON DELETE CASCADE,
    CONSTRAINT fk_messages_user FOREIGN KEY (from_user_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='消息';

-- 简历查看记录（租户私有表带 company_id；计费与隐私审计）
CREATE TABLE resume_view_logs (
    id         VARCHAR(64) NOT NULL,
    company_id VARCHAR(64) NOT NULL,
    hr_id      VARCHAR(64) NOT NULL,
    user_id    VARCHAR(64) NOT NULL,
    resume_id  VARCHAR(64) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_resume_view_logs_company_hr (company_id, hr_id, created_at),
    KEY idx_resume_view_logs_user (user_id, created_at),
    CONSTRAINT fk_resume_view_logs_company FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT fk_resume_view_logs_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_resume_view_logs_resume FOREIGN KEY (resume_id) REFERENCES resumes (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='简历查看记录';

-- 租户配置（配额与功能开关）
CREATE TABLE tenant_configs (
    id                VARCHAR(64) NOT NULL,
    company_id        VARCHAR(64) NOT NULL,
    max_jobs          INT         NOT NULL DEFAULT 50,
    max_hr_accounts   INT         NOT NULL DEFAULT 5,
    max_monthly_reach INT         NOT NULL DEFAULT 1000,
    features          JSON        NULL COMMENT '{ publishJob, videoInterview, ... }',
    expires_at        DATETIME(3) NULL,
    created_at        DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_configs_company (company_id),
    CONSTRAINT fk_tenant_configs_company FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='租户配置';

-- 用量埋点（按天聚合，计费与限流）
CREATE TABLE usage_logs (
    id         VARCHAR(64) NOT NULL,
    company_id VARCHAR(64) NOT NULL,
    type       VARCHAR(32) NOT NULL COMMENT 'JOB_PUBLISH / RESUME_VIEW / MESSAGE_SEND 等',
    day        DATE        NOT NULL,
    count      INT         NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_usage_logs_company_type_day (company_id, type, day),
    KEY idx_usage_logs_company_day (company_id, day),
    CONSTRAINT fk_usage_logs_company FOREIGN KEY (company_id) REFERENCES companies (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='用量埋点';

-- 审计日志（全局表，只追加不修改）
CREATE TABLE audit_logs (
    id          VARCHAR(64) NOT NULL,
    actor_id    VARCHAR(64) NOT NULL,
    actor_type  VARCHAR(16) NOT NULL COMMENT 'USER / HR / SYSTEM',
    action      VARCHAR(64) NOT NULL COMMENT 'JOB.PUBLISH / APPLICATION.WITHDRAW 等',
    target_type VARCHAR(32) NULL,
    target_id   VARCHAR(64) NULL,
    metadata    JSON        NULL,
    ip          VARCHAR(64) NULL,
    created_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_audit_logs_actor_created (actor_id, created_at),
    KEY idx_audit_logs_action_created (action, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='审计日志';
