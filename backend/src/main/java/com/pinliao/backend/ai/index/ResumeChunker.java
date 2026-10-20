package com.pinliao.backend.ai.index;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pinliao.backend.ai.config.AiProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * 简历结构化分块（Step 4 T-AI1）。
 *
 * <p>为什么不按固定长度硬切：RAG 回答要能「引用到简历的哪一段」，所以按业务段落切，
 * 并给每块加一行上下文标题（如「工作经历 · 某公司 · 前端开发工程师 · 2022-07 - present」）。
 * 这样即使只看单块，也能判断它在讲哪段经历；引用回前端时也有可读的标题。</p>
 *
 * <p>切分规则（对齐 {@code docs/step4-ai-plan.md} §5.1）：</p>
 * <ul>
 *   <li>basics 1 块：姓名 / 城市 + 个人简介；<b>手机号等联系方式一律不入库</b>（不送上游、不进向量）；</li>
 *   <li>educations / experiences / projects 每条 1 块（标题带机构、职位、起止时间，正文为描述）；</li>
 *   <li>skills 1 块：技能标签合集；</li>
 *   <li>单块超过 {@code pinliao.ai.chunk-max-chars}（默认 800）时按句号 / 分号 / 换行切分，
 *       续块标题追加「（续 n）」，保证引用可区分。</li>
 * </ul>
 *
 * <p>纯函数：不读库、不调模型，便于单测（用假向量模型跑索引链路）。</p>
 */
@Component
public class ResumeChunker {

    public static final String SECTION_BASICS = "basics";
    public static final String SECTION_EDUCATIONS = "educations";
    public static final String SECTION_EXPERIENCES = "experiences";
    public static final String SECTION_PROJECTS = "projects";
    public static final String SECTION_SKILLS = "skills";

    /** 找不到句读时的最小切分预算，避免头部过长导致空转。 */
    private static final int MIN_BUDGET = 40;
    /** 续块标题「（续 n）」与换行的预留长度。 */
    private static final int CONTINUATION_RESERVE = 9;
    private static final int TITLE_MAX_CHARS = 250;
    private static final String SENTENCE_ENDS = "。！？；;!?\n";

    private static final ObjectMapper JSON = new ObjectMapper();

    private final int maxChars;

    @Autowired
    public ResumeChunker(AiProperties properties) {
        this(properties.chunkMaxChars());
    }

    ResumeChunker(int maxChars) {
        this.maxChars = maxChars > 0 ? maxChars : 800;
    }

    /**
     * 把简历 content JSON 切成有序块。
     *
     * @param contentJson {@code resumes.content} 原始 JSON；null / 空 / 非对象 / 解析失败均返回空列表
     * @return 按 basics → educations → experiences → projects → skills 排序的块；不可变
     */
    public List<ResumeChunk> chunk(String contentJson) {
        if (contentJson == null || contentJson.isBlank()) {
            return List.of();
        }
        JsonNode root;
        try {
            root = JSON.readTree(contentJson);
        } catch (Exception e) {
            return List.of();
        }
        if (root == null || !root.isObject()) {
            return List.of();
        }

        List<ResumeChunk> chunks = new ArrayList<>();
        addBasics(chunks, root.path("basics"));
        addEntries(chunks, SECTION_EDUCATIONS, root.path("educations"), item -> joinHeader("教育经历",
                text(item, "school"), text(item, "major"), text(item, "degree"), period(item)), item -> "");
        addEntries(chunks, SECTION_EXPERIENCES, root.path("experiences"), item -> joinHeader("工作经历",
                text(item, "company"), text(item, "title"), period(item)), item -> text(item, "description"));
        addEntries(chunks, SECTION_PROJECTS, root.path("projects"), item -> joinHeader("项目经历",
                text(item, "name"), text(item, "role"), period(item)), item -> text(item, "description"));
        addSkills(chunks, root.path("skills"));
        return List.copyOf(chunks);
    }

    private void addBasics(List<ResumeChunk> chunks, JsonNode basics) {
        if (!basics.isObject()) {
            return;
        }
        String header = joinHeader("基本信息", text(basics, "name"), text(basics, "city"));
        String summary = text(basics, "summary");
        if (header.isEmpty() && summary.isEmpty()) {
            return;
        }
        emit(chunks, SECTION_BASICS, List.of(new Entry(header, summary)));
    }

    private void addEntries(List<ResumeChunk> chunks, String section, JsonNode array,
                            Function<JsonNode, String> headerOf, Function<JsonNode, String> bodyOf) {
        if (!array.isArray()) {
            return;
        }
        List<Entry> entries = new ArrayList<>();
        for (JsonNode item : array) {
            if (!item.isObject()) {
                continue;
            }
            String header = headerOf.apply(item);
            String body = bodyOf.apply(item);
            if (!header.isEmpty() || !body.isEmpty()) {
                entries.add(new Entry(header, body));
            }
        }
        emit(chunks, section, entries);
    }

    private void addSkills(List<ResumeChunk> chunks, JsonNode skills) {
        if (!skills.isArray()) {
            return;
        }
        List<String> labels = new ArrayList<>();
        for (JsonNode skill : skills) {
            String value = skill.asText("").trim();
            if (!value.isEmpty()) {
                labels.add(value);
            }
        }
        if (labels.isEmpty()) {
            return;
        }
        emit(chunks, SECTION_SKILLS, List.of(new Entry("技能标签", String.join("、", labels))));
    }

    private void emit(List<ResumeChunk> chunks, String section, List<Entry> entries) {
        int ordinal = 0;
        for (Entry entry : entries) {
            for (String content : compose(entry.header(), entry.body())) {
                chunks.add(new ResumeChunk(section, ordinal++, titleOf(content), content, sha256Hex(content)));
            }
        }
    }

    /** 标题 + 正文；正文按预算切分，续块标题追加「（续 n）」。 */
    private List<String> compose(String header, String body) {
        String head = header == null ? "" : header.trim();
        String text = body == null ? "" : body.trim();
        if (text.isEmpty()) {
            return head.isEmpty() ? List.of() : List.of(head);
        }
        int budget = maxChars - head.length() - CONTINUATION_RESERVE;
        if (budget < MIN_BUDGET) {
            // 头部过长时截断标题，保证「单块不超过上限」这一硬约束不被破坏
            int keep = Math.max(0, maxChars - MIN_BUDGET - CONTINUATION_RESERVE);
            head = head.length() <= keep ? head : head.substring(0, keep);
            budget = Math.max(1, maxChars - head.length() - CONTINUATION_RESERVE);
        }
        List<String> parts = splitText(text, budget);
        List<String> contents = new ArrayList<>(parts.size());
        for (int i = 0; i < parts.size(); i++) {
            String partHeader = i == 0 ? head : (head.isEmpty() ? "" : head + "（续 " + (i + 1) + "）");
            contents.add(partHeader.isEmpty() ? parts.get(i) : partHeader + "\n" + parts.get(i));
        }
        return contents;
    }

    /**
     * 按句读切分，单段不超过 budget；找不到句读时在 budget 处硬切。
     * 不会产生空段，也不会丢字（空白由 trim 收口）。
     */
    private static List<String> splitText(String text, int budget) {
        List<String> parts = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int hardEnd = Math.min(text.length(), start + budget);
            int end = hardEnd;
            if (hardEnd < text.length()) {
                int boundary = lastSentenceEnd(text, start, hardEnd);
                if (boundary > start) {
                    end = boundary;
                }
            }
            String part = text.substring(start, end).trim();
            if (!part.isEmpty()) {
                parts.add(part);
            }
            start = end;
            while (start < text.length() && Character.isWhitespace(text.charAt(start))) {
                start++;
            }
        }
        return parts;
    }

    private static int lastSentenceEnd(String text, int from, int to) {
        for (int i = to; i > from; i--) {
            if (SENTENCE_ENDS.indexOf(text.charAt(i - 1)) >= 0) {
                return i;
            }
        }
        return -1;
    }

    private static String titleOf(String content) {
        int lineBreak = content.indexOf('\n');
        String title = lineBreak >= 0 ? content.substring(0, lineBreak) : content;
        return title.length() <= TITLE_MAX_CHARS ? title : title.substring(0, TITLE_MAX_CHARS);
    }

    private static String joinHeader(String sectionLabel, String... parts) {
        StringBuilder header = new StringBuilder(sectionLabel);
        for (String part : parts) {
            if (part != null && !part.isBlank()) {
                header.append(" · ").append(part.trim());
            }
        }
        // 只有段名、没有任何业务字段时视为空标题（正文自带段名即可）
        return header.length() == sectionLabel.length() ? "" : header.toString();
    }

    private static String period(JsonNode item) {
        String start = text(item, "startDate");
        String end = text(item, "endDate");
        if (start.isEmpty() && end.isEmpty()) {
            return "";
        }
        return (start.isEmpty() ? "?" : start) + " - " + (end.isEmpty() ? "?" : end);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? "" : value.asText("").trim();
    }

    static String sha256Hex(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", e);
        }
    }

    private record Entry(String header, String body) {
    }
}
