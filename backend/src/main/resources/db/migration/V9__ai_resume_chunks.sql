-- =====================================================================
-- V9 · AI 简历分块索引（Step 4 T-AI1）
--
-- 设计要点（详见 docs/step4-ai-plan.md §5.1 / §5.3 / §6.4）：
--   1) 一块一行：同一份简历按 section + ordinal 切分，块首自带上下文标题，便于回答引用定位；
--   2) 不冗余 company_id：resumes 是全局表（一份简历可投多家公司），若按公司复制块会成倍放大
--      存储与嵌入费用。检索时先用 applications.company_id 取出「对本公司可见」的 resume_id，
--      再按 resume_id IN (...) 前置过滤（不是先全局检索再后置过滤）；
--   3) content_hash = sha256(块正文)，与 model 共同用于「内容没变就复用旧向量」，避免重复计费；
--   4) embedding 存 JSON 数组（float），对应阶段 2「MySQL 向量存储」；
--      阶段 3 若换独立向量库，本表可原样充当外部索引的数据源与重建依据。
-- =====================================================================
CREATE TABLE resume_chunks (
    id           VARCHAR(64)  NOT NULL,
    resume_id    VARCHAR(64)  NOT NULL,
    user_id      VARCHAR(64)  NOT NULL COMMENT '简历归属求职者；检索前置过滤后仍保留，便于审计',
    section      VARCHAR(32)  NOT NULL COMMENT 'basics / educations / experiences / projects / skills',
    ordinal      INT          NOT NULL COMMENT '同一 section 内从 0 递增（长段续写也递增）',
    title        VARCHAR(256) NULL COMMENT '引用标题（含上下文，如「工作经历 · 某公司 · 前端开发」）',
    content      TEXT         NOT NULL COMMENT '送入嵌入模型的正文（标题 + 原文，不含手机号等敏感信息）',
    content_hash CHAR(64)     NOT NULL COMMENT 'sha256(content)；内容未变则复用旧向量',
    model        VARCHAR(64)  NOT NULL COMMENT '嵌入模型标识（如 text-embedding-v4）',
    dim          INT          NOT NULL COMMENT '向量维度（当前 1024）',
    embedding    MEDIUMTEXT   NULL COMMENT 'JSON 数组（float）；NULL 表示待重建',
    created_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_resume_chunks_slot (resume_id, section, ordinal, model),
    KEY idx_resume_chunks_hash_model (content_hash, model),
    KEY idx_resume_chunks_user (user_id),
    CONSTRAINT fk_resume_chunks_resume FOREIGN KEY (resume_id) REFERENCES resumes (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='AI 简历分块索引（Step 4 T-AI1）';
