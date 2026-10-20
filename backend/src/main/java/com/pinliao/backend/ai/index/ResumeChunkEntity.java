package com.pinliao.backend.ai.index;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * resume_chunks 表（Step 4 T-AI1；DDL 见 V9）。
 *
 * <p>不冗余 company_id：简历是全局表，检索时用 applications.company_id 先取可见 resume_id，
 * 再按 resume_id 前置过滤（详见 V9 注释与 docs/step4-ai-plan.md §5.3）。</p>
 *
 * <p>created_at / updated_at 由 service 显式写入并截到毫秒，与项目其他写路径一致。</p>
 */
@Data
@TableName("resume_chunks")
public class ResumeChunkEntity {

    @TableId(type = IdType.INPUT)
    private String id;

    private String resumeId;
    private String userId;
    private String section;
    private Integer ordinal;
    private String title;
    private String content;
    private String contentHash;
    private String model;
    private Integer dim;
    /** JSON 数组（float）；null 表示待重建。 */
    private String embedding;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
