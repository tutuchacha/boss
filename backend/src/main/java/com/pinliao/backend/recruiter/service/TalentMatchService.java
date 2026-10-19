package com.pinliao.backend.recruiter.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pinliao.backend.application.entity.ApplicationEntity;
import com.pinliao.backend.application.entity.ResumeEntity;
import com.pinliao.backend.application.mapper.ApplicationMapper;
import com.pinliao.backend.application.mapper.ResumeMapper;
import com.pinliao.backend.auth.context.Roles;
import com.pinliao.backend.auth.context.UserContext;
import com.pinliao.backend.auth.entity.UserProfileEntity;
import com.pinliao.backend.common.exception.BusinessException;
import com.pinliao.backend.job.entity.JobEntity;
import com.pinliao.backend.job.mapper.JobMapper;
import com.pinliao.backend.recruiter.RecruiterCodes;
import com.pinliao.backend.recruiter.dto.RecruiterRequests;
import com.pinliao.backend.recruiter.view.RecruiterViews;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * §4.5 / §4.6 人才筛选（HR 按技能需求匹配候选人）。
 *
 * <p><b>演示版为关键词匹配</b>：需求文本 + 标签 → 关键词提取（词表命中 / 英文技术词 /
 * 中文去停用词）→ 在公司收到的投递候选人范围内检索其简历内容 → 输出命中关键词与证据片段。
 * 与前端 mock {@code src/api/mock/talentMatch.ts} 逐条对齐（同词典、同权重、同证据文案、
 * 同排序），保证 T9 切换到真实后端后前端展示与 e2e 断言不变；后续可整体替换为语义检索
 * （embedding + 向量库），路径与出参结构不变。</p>
 *
 * <p><b>边界（诚实口径）</b>：这是关键词匹配而非语义检索——同义词（「React」与「前端框架」）
 * 不会被识别；词典命中为包含式（大小写不敏感），短词存在误报可能（演示数据已规避）。</p>
 *
 * <p><b>数据来源</b>：同一候选人多条投递时取最近一条（{@code updatedAt DESC}）；简历取该条
 * 投递的 {@code resume_id}（公司实际收到的简历）；画像取 {@code user_profiles}。缺简历内容者跳过。</p>
 */
@Service
public class TalentMatchService {

    /** §4.6 技能标签词表（筛选页 chips；同时作为关键词提取的主词典）。 */
    public static final List<String> TALENT_MATCH_TAGS = List.of(
            "前端",
            "React",
            "TypeScript",
            "Vue",
            "后端",
            "Java",
            "Spring Boot",
            "MySQL",
            "Redis",
            "高并发",
            "测试",
            "自动化测试",
            "性能测试",
            "运维",
            "Kubernetes",
            "Docker",
            "产品",
            "数据分析",
            "设计",
            "Figma");

    /**
     * 停用词：从中文片段中剔除，避免把「熟悉 / 以上 / 工程师」等无检索意义的词当成关键词。
     * 按长度倒序使用（先移除长词）。
     */
    private static final List<String> STOPWORDS = List.of(
            "工作经验", "相关经验", "年以上", "年以下", "经验", "熟悉", "掌握", "了解", "精通",
            "优先", "要求", "岗位", "负责", "具备", "能力", "不限", "相关", "开发", "工程师",
            "专员", "经理", "主管", "助理", "以及", "并且", "或者", "年", "的", "和", "与", "或",
            "及", "有");

    /** 词典：标签词表，长词优先（保证「自动化测试」先于「测试」命中）。 */
    private static final List<String> DICT = sortedByLengthDesc(TALENT_MATCH_TAGS);
    private static final List<String> STOPWORDS_SORTED = sortedByLengthDesc(STOPWORDS);

    private static final Pattern ENGLISH_TOKEN = Pattern.compile("[A-Za-z][A-Za-z0-9+#.-]*");
    private static final Pattern TRAILING_PUNCT = Pattern.compile("[.-]+$");
    private static final Pattern CHINESE_CHUNK = Pattern.compile("[\u4e00-\u9fa5]{2,}");

    /** 分值：技能精确命中 > 文本命中（简介 / 经历 / 项目 / 意向）。 */
    private static final int WEIGHT_SKILL = 3;
    private static final int WEIGHT_TEXT = 2;
    /** 单个关键词最多展示几条证据；单候选人证据总数上限（避免卡片过长）。 */
    private static final int MAX_EVIDENCES_PER_TOKEN = 3;
    private static final int MAX_EVIDENCES_PER_CANDIDATE = 6;

    private static final ObjectMapper JSON = new ObjectMapper();

    private final ApplicationMapper applicationMapper;
    private final ResumeMapper resumeMapper;
    private final JobMapper jobMapper;
    private final CandidateSummaryService candidateSummaryService;

    public TalentMatchService(ApplicationMapper applicationMapper,
                              ResumeMapper resumeMapper,
                              JobMapper jobMapper,
                              CandidateSummaryService candidateSummaryService) {
        this.applicationMapper = applicationMapper;
        this.resumeMapper = resumeMapper;
        this.jobMapper = jobMapper;
        this.candidateSummaryService = candidateSummaryService;
    }

    /** §4.6 技能标签词表。 */
    public List<String> tags() {
        Roles.requireManager();
        return TALENT_MATCH_TAGS;
    }

    /**
     * §4.5 按技能需求筛选候选人（按匹配度倒序；仅含有命中的候选人）。
     *
     * @param input query（自然语言技能需求）与 / 或 tags（技能标签）；两者至少一个非空，否则 42210
     */
    public List<RecruiterViews.CandidateMatch> match(RecruiterRequests.CandidateMatchInput input) {
        UserContext.CurrentUser user = Roles.requireManager();

        String query = input == null ? null : input.query();
        List<String> tags = input == null || input.tags() == null ? List.of() : input.tags();
        if (isBlank(query) && tags.stream().allMatch(TalentMatchService::isBlank)) {
            throw new BusinessException(RecruiterCodes.EMPTY_CRITERIA, "请填写技能需求或选择技能标签");
        }

        List<String> tokens = extractTokens(query, tags);
        if (tokens.isEmpty()) {
            // 条件非空但提不出关键词（纯停用词等）：不是错误，返回空结果（对齐 mock）
            return List.of();
        }

        List<RecruiterViews.CandidateMatch> results = new ArrayList<>();
        for (ApplicationEntity app : distinctLatestApplications(user.companyId())) {
            JsonNode content = resumeContent(app.getResumeId());
            if (content == null) {
                continue;
            }
            UserProfileEntity profile = candidateSummaryService.profileOf(app.getUserId());
            RecruiterViews.Candidate candidate = candidateSummaryService.summarise(app.getUserId(), profile);

            List<TokenHit> hits = new ArrayList<>();
            for (String token : tokens) {
                TokenHit hit = hitsOfToken(token, candidate.expect(), content);
                if (hit != null) {
                    hits.add(hit);
                }
            }
            if (hits.isEmpty()) {
                continue;
            }

            hits.sort(Comparator.comparingInt(TokenHit::weight).reversed());
            List<RecruiterViews.Evidence> evidences = new ArrayList<>();
            for (TokenHit hit : hits) {
                for (RecruiterViews.Evidence evidence : hit.evidences()) {
                    if (evidences.size() >= MAX_EVIDENCES_PER_CANDIDATE) {
                        break;
                    }
                    evidences.add(evidence);
                }
            }
            results.add(new RecruiterViews.CandidateMatch(
                    app.getId(),
                    candidate,
                    jobTitle(app.getJobId()),
                    hits.stream().mapToInt(TokenHit::weight).sum(),
                    hits.stream().map(TokenHit::token).toList(),
                    evidences));
        }

        results.sort(Comparator
                .comparingInt(RecruiterViews.CandidateMatch::score).reversed()
                .thenComparing(Comparator.comparingInt(TalentMatchService::completenessOf).reversed()));
        return results;
    }

    private static int completenessOf(RecruiterViews.CandidateMatch match) {
        return match.candidate().resumeCompleteness() == null ? 0 : match.candidate().resumeCompleteness();
    }

    // ------------------------------------------------------------------
    // 数据装配
    // ------------------------------------------------------------------

    /** 公司收到的投递，按更新时间倒序；同一候选人只保留最近一条。 */
    private List<ApplicationEntity> distinctLatestApplications(String companyId) {
        List<ApplicationEntity> rows = applicationMapper.selectList(new LambdaQueryWrapper<ApplicationEntity>()
                .eq(ApplicationEntity::getCompanyId, companyId)
                .isNull(ApplicationEntity::getDeletedAt));
        rows.sort(Comparator.comparing(ApplicationEntity::getUpdatedAt,
                        Comparator.nullsFirst(Comparator.naturalOrder()))
                .reversed()
                .thenComparing(ApplicationEntity::getId));

        Map<String, ApplicationEntity> byUser = new LinkedHashMap<>();
        for (ApplicationEntity app : rows) {
            byUser.putIfAbsent(app.getUserId(), app);
        }
        return new ArrayList<>(byUser.values());
    }

    /** 投递简历的 content（解析失败 / 缺失 / 已软删 → null，调用方跳过该候选人）。 */
    private JsonNode resumeContent(String resumeId) {
        if (isBlank(resumeId)) {
            return null;
        }
        ResumeEntity resume = resumeMapper.selectById(resumeId);
        if (resume == null || resume.getDeletedAt() != null || isBlank(resume.getContent())) {
            return null;
        }
        try {
            return JSON.readTree(resume.getContent());
        } catch (Exception e) {
            return null;
        }
    }

    private String jobTitle(String jobId) {
        JobEntity job = jobMapper.selectById(jobId);
        return job == null || job.getTitle() == null ? "职位已删除" : job.getTitle();
    }

    // ------------------------------------------------------------------
    // 关键词提取（对齐 mock extractRequirementTokens）
    // ------------------------------------------------------------------

    /**
     * 从「需求文本 + 标签」提取关键词（演示版，非语义分词）：
     * <ol>
     *   <li>词典命中：长词优先、大小写不敏感；命中后用空格占位避免子词重复计数；</li>
     *   <li>英文 / 数字技术词：词表未覆盖的（如 Node.js、Go、C++）；</li>
     *   <li>中文：去除停用词后剩余的连续中文片段（长度 ≥ 2）。</li>
     * </ol>
     */
    static List<String> extractTokens(String query, List<String> tags) {
        String raw = ((query == null ? "" : query) + " " + String.join(" ", tags == null ? List.of() : tags)).trim();
        if (raw.isEmpty()) {
            return List.of();
        }

        Set<String> seen = new LinkedHashSet<>();
        List<String> tokens = new ArrayList<>();

        StringBuilder working = new StringBuilder(raw);

        // 1) 词典命中
        for (String term : DICT) {
            int idx = working.toString().toLowerCase(Locale.ROOT).indexOf(term.toLowerCase(Locale.ROOT));
            if (idx < 0) {
                continue;
            }
            push(tokens, seen, term);
            for (int k = 0; k < term.length(); k++) {
                working.setCharAt(idx + k, ' ');
            }
        }

        // 2) 英文 / 数字技术词（去掉结尾的 . 与 -，保留 C++ 之类的中间符号）
        Matcher english = ENGLISH_TOKEN.matcher(working);
        while (english.find()) {
            push(tokens, seen, TRAILING_PUNCT.matcher(english.group()).replaceAll(""));
        }

        // 3) 中文片段：去停用词后剩余连续中文（≥2 字）
        String zh = working.toString();
        for (String stopword : STOPWORDS_SORTED) {
            zh = zh.replace(stopword, " ");
        }
        Matcher chinese = CHINESE_CHUNK.matcher(zh);
        while (chinese.find()) {
            push(tokens, seen, chinese.group());
        }

        return tokens;
    }

    private static void push(List<String> tokens, Set<String> seen, String raw) {
        String normalized = raw == null ? "" : raw.trim();
        if (normalized.isEmpty()) {
            return;
        }
        if (seen.add(normalized.toLowerCase(Locale.ROOT))) {
            tokens.add(normalized);
        }
    }

    // ------------------------------------------------------------------
    // 单关键词命中（对齐 mock hitsOfToken）
    // ------------------------------------------------------------------

    /** 关键词在简历中的命中（权重 + 证据）；未命中返回 null。 */
    private static TokenHit hitsOfToken(String token, String expect, JsonNode content) {
        String tk = token.toLowerCase(Locale.ROOT);
        HitBuilder builder = new HitBuilder(token);

        // 技能（精确命中权重最高）
        String skill = firstContaining(textList(content.path("skills")), tk);
        if (skill != null) {
            builder.add(WEIGHT_SKILL, "技能", skill);
        }

        // 求职意向
        String expectText = expect == null ? "" : expect;
        if (expectText.toLowerCase(Locale.ROOT).contains(tk)) {
            builder.add(WEIGHT_TEXT, "求职意向", expectText);
        }

        // 个人简介
        String summary = text(content.path("basics").path("summary"));
        if (summary != null && summary.toLowerCase(Locale.ROOT).contains(tk)) {
            builder.add(WEIGHT_TEXT, "个人简介", snippetAround(summary, token));
        }

        // 工作经历（合并为一行检索；命中即取首个匹配项）
        for (JsonNode exp : iterable(content.path("experiences"))) {
            String hay = joinNonNull(text(exp.path("company")), text(exp.path("title")), text(exp.path("description")));
            if (hay.toLowerCase(Locale.ROOT).contains(tk)) {
                builder.add(WEIGHT_TEXT, "工作经历 · " + nullToEmpty(text(exp.path("company"))), snippetAround(hay, token));
                break;
            }
        }

        // 项目经历
        for (JsonNode project : iterable(content.path("projects"))) {
            String hay = joinNonNull(text(project.path("name")), text(project.path("role")),
                    text(project.path("description")));
            if (hay.toLowerCase(Locale.ROOT).contains(tk)) {
                builder.add(WEIGHT_TEXT, "项目经历 · " + nullToEmpty(text(project.path("name"))), snippetAround(hay, token));
                break;
            }
        }

        return builder.isEmpty() ? null : builder.build();
    }

    /** 取 token 在文本中首次出现处的上下文片段（供前端高亮；对齐 mock snippetAround）。 */
    static String snippetAround(String text, String token) {
        int idx = text.toLowerCase(Locale.ROOT).indexOf(token.toLowerCase(Locale.ROOT));
        if (idx < 0) {
            return text;
        }
        int start = Math.max(0, idx - 10);
        int end = Math.min(text.length(), idx + token.length() + 46);
        return (start > 0 ? "…" : "") + text.substring(start, end) + (end < text.length() ? "…" : "");
    }

    // ------------------------------------------------------------------
    // 小工具
    // ------------------------------------------------------------------

    private static List<String> sortedByLengthDesc(List<String> source) {
        List<String> copy = new ArrayList<>(source);
        copy.sort(Comparator.comparingInt(String::length).reversed());
        return List.copyOf(copy);
    }

    private static List<String> textList(JsonNode node) {
        List<String> values = new ArrayList<>();
        for (JsonNode item : iterable(node)) {
            String value = text(item);
            if (value != null) {
                values.add(value);
            }
        }
        return values;
    }

    private static Iterable<JsonNode> iterable(JsonNode node) {
        return node != null && node.isArray() ? node : List.of();
    }

    private static String firstContaining(List<String> values, String loweredNeedle) {
        for (String value : values) {
            if (value.toLowerCase(Locale.ROOT).contains(loweredNeedle)) {
                return value;
            }
        }
        return null;
    }

    private static String text(JsonNode node) {
        return node != null && node.isTextual() ? node.asText() : null;
    }

    private static String joinNonNull(String... parts) {
        List<String> kept = new ArrayList<>();
        for (String part : parts) {
            if (part != null && !part.isEmpty()) {
                kept.add(part);
            }
        }
        return String.join(" · ", kept);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** 单个关键词的命中结果（权重 + 证据）。 */
    record TokenHit(String token, int weight, List<RecruiterViews.Evidence> evidences) {
    }

    /** 命中累积器（保持 mock 的「权重取最大、证据最多 3 条」语义）。 */
    private static final class HitBuilder {

        private final String token;
        private final List<RecruiterViews.Evidence> evidences = new ArrayList<>();
        private int weight;

        private HitBuilder(String token) {
            this.token = token;
        }

        private void add(int candidateWeight, String field, String snippet) {
            weight = Math.max(weight, candidateWeight);
            if (evidences.size() < MAX_EVIDENCES_PER_TOKEN) {
                evidences.add(new RecruiterViews.Evidence(token, field, snippet));
            }
        }

        private boolean isEmpty() {
            return weight == 0;
        }

        private TokenHit build() {
            return new TokenHit(token, weight, List.copyOf(evidences));
        }
    }
}
