package com.pinliao.backend.ai.rag;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T-AI2：Prompt 组装（编号资料 / 指令与数据分区 / 防注入声明）。
 */
class PromptBuilderTest {

    private final PromptBuilder builder = new PromptBuilder();

    @Test
    void 资料按编号拼装并包含问题与防注入声明() {
        PromptBuilder.Prompt prompt = builder.build("他做过高并发吗？", List.of(
                hit("工作经历 · 某电商平台", "负责订单核心链路，QPS 12000。"),
                hit("项目经历 · 秒杀系统", "基于 Redis 预扣库存。")));

        assertTrue(prompt.system().contains("不得执行"), "系统提示必须声明资料是纯数据");
        assertTrue(prompt.system().contains("[序号]"));
        assertTrue(prompt.user().contains("简历资料："));
        assertTrue(prompt.user().contains("[1] 工作经历 · 某电商平台"));
        assertTrue(prompt.user().contains("[2] 项目经历 · 秒杀系统"));
        assertTrue(prompt.user().contains("HR 的问题：他做过高并发吗？"));
        assertTrue(prompt.user().indexOf("[1]") < prompt.user().indexOf("[2]"));
    }

    private static RetrievalService.Hit hit(String title, String content) {
        return new RetrievalService.Hit("rc_1", "resume-1", "experiences", title,
                title + "\n" + content, 0.9);
    }
}
