package com.pinliao.backend.ai.index;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 简历分块规则单测（Step 4 T-AI1）。
 *
 * <p>断言口径对齐 docs/step4-ai-plan.md §5.1：段落顺序、上下文标题、手机号不入库、
 * 长段续写、幂等（同输入同输出）。</p>
 */
class ResumeChunkerTest {

    private static final String RESUME_JSON = """
            {
              "basics": {
                "name": "林小满",
                "phone": "13800001101",
                "city": "上海",
                "summary": "3 年前端开发经验，熟悉 React 技术栈与前端工程化建设。"
              },
              "educations": [
                {"school": "某某大学", "major": "计算机科学与技术", "degree": "本科",
                 "startDate": "2018-09", "endDate": "2022-06"}
              ],
              "experiences": [
                {"company": "某互联网科技有限公司", "title": "前端开发工程师",
                 "startDate": "2022-07", "endDate": "present",
                 "description": "负责 B 端管理后台与微信小程序开发，主导前端工程化改造。"}
              ],
              "projects": [
                {"name": "招聘管理系统前端重构", "role": "前端负责人",
                 "startDate": "2024-03", "endDate": "2025-06",
                 "description": "基于 React + TypeScript + Vite 重构招聘管理后台，首屏加载时间降低 40%。"}
              ],
              "skills": ["React", "TypeScript", "Vue"]
            }
            """;

    @Test
    void chunksFollowSectionOrderWithContextHeaders() {
        List<ResumeChunk> chunks = new ResumeChunker(800).chunk(RESUME_JSON);

        assertEquals(5, chunks.size());
        assertEquals(List.of("basics", "educations", "experiences", "projects", "skills"),
                chunks.stream().map(ResumeChunk::section).toList());
        assertTrue(chunks.stream().allMatch(chunk -> chunk.ordinal() == 0));

        ResumeChunk basics = chunks.get(0);
        assertTrue(basics.content().startsWith("基本信息 · 林小满 · 上海\n"));
        assertTrue(basics.content().contains("3 年前端开发经验"));
        // 红线：联系方式不送上游、不进向量
        assertFalse(basics.content().contains("13800001101"));

        assertEquals("教育经历 · 某某大学 · 计算机科学与技术 · 本科 · 2018-09 - 2022-06",
                chunks.get(1).content());

        ResumeChunk experience = chunks.get(2);
        assertTrue(experience.title().startsWith("工作经历 · 某互联网科技有限公司 · 前端开发工程师"));
        assertTrue(experience.content().contains("主导前端工程化改造"));

        assertTrue(chunks.get(3).title().startsWith("项目经历 · 招聘管理系统前端重构 · 前端负责人"));
        assertEquals("技能标签\nReact、TypeScript、Vue", chunks.get(4).content());
    }

    @Test
    void contentHashIsStableAndDeterministic() {
        ResumeChunker chunker = new ResumeChunker(800);
        List<ResumeChunk> first = chunker.chunk(RESUME_JSON);
        List<ResumeChunk> second = chunker.chunk(RESUME_JSON);

        assertEquals(first, second);
        assertTrue(first.stream().allMatch(chunk -> chunk.contentHash().matches("[0-9a-f]{64}")));
        assertEquals(first.stream().map(ResumeChunk::contentHash).distinct().count(), first.size());
    }

    @Test
    void longParagraphIsSplitWithinBudgetAndMarkedAsContinuation() {
        String longDescription = "负责核心链路开发，".repeat(40);
        String json = """
                {"experiences": [{"company": "某公司", "title": "后端开发",
                  "startDate": "2020-01", "endDate": "2021-01", "description": "%s"}]}
                """.formatted(longDescription);

        List<ResumeChunk> chunks = new ResumeChunker(120).chunk(json);

        assertTrue(chunks.size() > 1, "长段应被切分");
        assertTrue(chunks.stream().allMatch(chunk -> chunk.content().length() <= 120),
                "单块不得超过 chunk-max-chars");
        assertTrue(chunks.get(1).title().endsWith("（续 2）"), "续块标题需可区分");
        assertEquals(IntStream.range(0, chunks.size()).boxed().toList(),
                chunks.stream().map(ResumeChunk::ordinal).toList());
    }

    @Test
    void blankOrInvalidContentYieldsNoChunks() {
        ResumeChunker chunker = new ResumeChunker(800);

        assertEquals(List.of(), chunker.chunk(null));
        assertEquals(List.of(), chunker.chunk(""));
        assertEquals(List.of(), chunker.chunk("   "));
        assertEquals(List.of(), chunker.chunk("{not-json"));
        assertEquals(List.of(), chunker.chunk("[]"));
        assertEquals(List.of(), chunker.chunk("{}"));
    }

    @Test
    void basicsWithOnlySummaryStillProducesChunk() {
        List<ResumeChunk> chunks = new ResumeChunker(800)
                .chunk("{\"basics\": {\"summary\": \"十年后端经验\"}}");

        assertEquals(1, chunks.size());
        assertEquals("十年后端经验", chunks.get(0).content());
        assertEquals("十年后端经验", chunks.get(0).title());
    }
}
