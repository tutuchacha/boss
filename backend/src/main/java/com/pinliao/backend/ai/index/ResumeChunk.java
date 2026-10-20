package com.pinliao.backend.ai.index;

/**
 * 简历分块（Step 4 T-AI1）：索引写入的最小单元。
 *
 * @param section     来源段落：basics / educations / experiences / projects / skills
 * @param ordinal     同一段内从 0 递增（长段切分后继续递增）
 * @param title       引用标题（内容首行，便于前端「跳转到原文位置」）
 * @param content     送入嵌入模型的正文（首行为带上下文的标题）
 * @param contentHash sha256(content)，内容未变即可复用旧向量
 */
public record ResumeChunk(String section, int ordinal, String title, String content, String contentHash) {
}
