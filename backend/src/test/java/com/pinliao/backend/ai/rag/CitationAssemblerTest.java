package com.pinliao.backend.ai.rag;

import com.pinliao.backend.ai.view.AiQaViews;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T-AI2：引用装配（标记映射 / 去重 / 兜底 / 无依据不附引用 / 片段截断）。
 */
class CitationAssemblerTest {

    private final CitationAssembler assembler = new CitationAssembler();

    @Test
    void 按回答首次出现顺序映射引用并忽略越界编号() {
        CitationAssembler.Assembly result = assembler.assemble(
                "他做过高并发系统 [2]，也做过缓存优化 [1][2][9]。", hits());

        assertEquals("他做过高并发系统 [2]，也做过缓存优化 [1][2][9]。", result.answer());
        assertEquals(List.of(2, 1), result.citations().stream().map(AiQaViews.Citation::index).toList());
        AiQaViews.Citation second = result.citations().get(0);
        assertEquals("项目经历 · 秒杀系统", second.title());
        assertEquals("基于 Redis 预扣库存。", second.snippet());
        assertEquals(0.8123, second.score(), 0.00001);
    }

    @Test
    void 模型漏标引用时用第一条兜底并追加标记() {
        CitationAssembler.Assembly result = assembler.assemble("他有 6 年后端经验。", hits());

        assertTrue(result.answer().endsWith("来源：[1]"));
        assertEquals(1, result.citations().size());
        assertEquals(1, result.citations().get(0).index());
    }

    @Test
    void 无依据回答不附任何引用() {
        CitationAssembler.Assembly result = assembler.assemble(PromptBuilder.NO_EVIDENCE_ANSWER, hits());

        assertEquals(PromptBuilder.NO_EVIDENCE_ANSWER, result.answer());
        assertTrue(result.citations().isEmpty());
    }

    @Test
    void 没有检索结果时不附引用也不改回答() {
        CitationAssembler.Assembly result = assembler.assemble("随便回答", List.of());

        assertEquals("随便回答", result.answer());
        assertTrue(result.citations().isEmpty());
    }

    @Test
    void 片段去掉上下文标题并截断() {
        String snippet = CitationAssembler.snippetOf("技能标签\n" + "Java ".repeat(60));

        assertFalse(snippet.contains("技能标签"));
        assertTrue(snippet.endsWith("…"));
        assertTrue(snippet.length() <= 121);
    }

    private static List<RetrievalService.Hit> hits() {
        return List.of(
                new RetrievalService.Hit("rc_1", "resume-1", "experiences", "工作经历 · 某电商平台",
                        "工作经历 · 某电商平台\n负责订单与营销核心链路，QPS 从 3000 提升至 12000。", 0.73456),
                new RetrievalService.Hit("rc_2", "resume-1", "projects", "项目经历 · 秒杀系统",
                        "项目经历 · 秒杀系统\n基于 Redis 预扣库存。", 0.81234));
    }
}
