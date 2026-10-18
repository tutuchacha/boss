package com.pinliao.backend.message.web;

import com.pinliao.backend.common.api.PageViews;
import com.pinliao.backend.message.dto.MessageRequests;
import com.pinliao.backend.message.service.ConversationCommandService;
import com.pinliao.backend.message.service.ConversationQueryService;
import com.pinliao.backend.message.view.MessageViews;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 消息模块接口（契约 docs/api-endpoints.md §8.1~§8.8）。
 *
 * <p>鉴权由 {@code AuthInterceptor} 统一处理；角色与归属在 service 层守卫。
 * 另提供 {@code GET /conversations/{id}} 供 HR 聊天页读取顶栏信息（扩展读取，
 * 不改变 §8 的八条接口计账）。响应统一由 {@code ResponseAdvice} 包装为包络。</p>
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "8. 消息模块", description = "会话列表 / 消息 / 发送 / 已读 / typing / 简历卡片 / 关闭（§8）")
public class ConversationController {

    private final ConversationQueryService queryService;
    private final ConversationCommandService commandService;

    public ConversationController(ConversationQueryService queryService,
                                  ConversationCommandService commandService) {
        this.queryService = queryService;
        this.commandService = commandService;
    }

    /** §8.1 会话列表（求职者本人 / 本公司 HR；游标分页）。 */
    @GetMapping("/conversations")
    public PageViews.Page<MessageViews.ListItem> list(@RequestParam(required = false) String cursor,
                                                      @RequestParam(required = false) Integer limit) {
        return queryService.list(cursor, limit);
    }

    /** 会话详情（HR 聊天页顶栏；扩展读取）。 */
    @GetMapping("/conversations/{conversationId}")
    public MessageViews.ListItem detail(@PathVariable String conversationId) {
        return queryService.detail(conversationId);
    }

    /** §8.2 会话消息列表（参与方 / 本公司 HR；createdAt 降序游标）。 */
    @GetMapping("/conversations/{conversationId}/messages")
    public PageViews.Page<MessageViews.Message> messages(@PathVariable String conversationId,
                                                         @RequestParam(required = false) String cursor,
                                                         @RequestParam(required = false) Integer limit) {
        return queryService.messages(conversationId, cursor, limit);
    }

    /** §8.3 发起会话（JOB_SEEKER；已存在 40912）。 */
    @PostMapping("/conversations")
    public MessageViews.Conversation start(@RequestBody MessageRequests.Start body) {
        return commandService.start(body);
    }

    /** §8.4 发送消息（参与方；30 次/分钟/用户）。 */
    @PostMapping("/conversations/{conversationId}/messages")
    public MessageViews.Message send(@PathVariable String conversationId,
                                     @RequestBody MessageRequests.Send body) {
        return commandService.send(conversationId, body);
    }

    /** §8.5 标记已读（参与方；返回 unreadCount=0）。 */
    @PostMapping("/conversations/{conversationId}/read")
    public MessageViews.ReadResult read(@PathVariable String conversationId) {
        return commandService.markRead(conversationId);
    }

    /** §8.6 正在输入（参与方；同时推 user queue 与 conversation topic）。 */
    @PostMapping("/conversations/{conversationId}/typing")
    public Map<String, Object> typing(@PathVariable String conversationId,
                                      @RequestBody MessageRequests.Typing body) {
        commandService.typing(conversationId, body);
        return Map.of();
    }

    /** §8.7 发送简历快捷操作（JOB_SEEKER；默认简历兜底）。 */
    @PostMapping("/conversations/{conversationId}/actions/send-resume")
    public MessageViews.Message sendResume(@PathVariable String conversationId,
                                           @RequestBody(required = false) MessageRequests.SendResume body) {
        return commandService.sendResume(conversationId, body == null
                ? new MessageRequests.SendResume(null) : body);
    }

    /** §8.8 关闭会话（HR / COMPANY_ADMIN 本公司）。 */
    @PostMapping("/conversations/{conversationId}/close")
    public MessageViews.Conversation close(@PathVariable String conversationId) {
        return commandService.close(conversationId);
    }
}
