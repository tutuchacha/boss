package com.pinliao.backend.ai.rag;

import com.pinliao.backend.ai.view.AiQaViews;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 引用装配（Step 4 T-AI2）。
 *
 * <p>把回答里的 {@code [n]} 映射回检索到的分块。两条兜底保证「回答必带引用」：</p>
 * <ul>
 *   <li>模型漏标引用但确实给出了有依据的回答 → 追加「来源：[1]」并用最相关分块兜底；</li>
 *   <li>回答是「简历中没有相关信息。」→ 不附任何引用（避免把无关片段包装成证据）。</li>
 * </ul>
 */
@Component
public class CitationAssembler {

    private static final Pattern MARKER = Pattern.compile("\\[(\\d{1,2})]");
    private static final int SNIPPET_MAX_CHARS = 120;

    public Assembly assemble(String answer, List<RetrievalService.Hit> hits) {
        String text = answer == null ? "" : answer.trim();
        if (text.contains(PromptBuilder.NO_EVIDENCE_ANSWER) || hits.isEmpty()) {
            return new Assembly(text, List.of());
        }

        Set<Integer> indices = new LinkedHashSet<>();
        Matcher matcher = MARKER.matcher(text);
        while (matcher.find()) {
            int index = Integer.parseInt(matcher.group(1));
            if (index >= 1 && index <= hits.size()) {
                indices.add(index);
            }
        }

        if (indices.isEmpty()) {
            // 模型没有标注来源：用最相关的一条兜底，保证前端始终有可核对的引用
            text = text + "\n\n来源：[1]";
            indices.add(1);
        }

        List<AiQaViews.Citation> citations = new ArrayList<>(indices.size());
        for (int index : indices) {
            RetrievalService.Hit hit = hits.get(index - 1);
            citations.add(new AiQaViews.Citation(
                    index,
                    hit.chunkId(),
                    hit.resumeId(),
                    hit.section(),
                    hit.title(),
                    snippetOf(hit.content()),
                    round(hit.score())));
        }
        return new Assembly(text, List.copyOf(citations));
    }

    /** 去掉上下文标题行并截断，避免把整段 800 字原文下发给前端。 */
    static String snippetOf(String content) {
        if (content == null) {
            return "";
        }
        String body = content;
        int newline = body.indexOf('\n');
        if (newline >= 0 && newline + 1 < body.length()) {
            body = body.substring(newline + 1);
        }
        body = body.replaceAll("\\s+", " ").trim();
        if (body.length() <= SNIPPET_MAX_CHARS) {
            return body;
        }
        return body.substring(0, SNIPPET_MAX_CHARS) + "…";
    }

    private static double round(double value) {
        return Math.round(value * 10_000d) / 10_000d;
    }

    /** 装配结果：可能被追加了兜底引用标记的回答 + 引用列表。 */
    public record Assembly(String answer, List<AiQaViews.Citation> citations) {
    }
}
