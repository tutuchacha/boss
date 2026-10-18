package com.pinliao.backend.application.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * conversations 表（契约 §8；T4 仅用于「投递时占位建会话」与约面试发送邀请消息）。
 *
 * <p>完整会话列表 / 消息 / 未读 / WebSocket 属于 T6。</p>
 */
@Data
@TableName("conversations")
public class ConversationEntity {

    @TableId(type = IdType.INPUT)
    private String id;

    private String jobId;
    private String userId;
    private String hrId;
    private String companyId;
    private LocalDateTime lastMessageAt;
    private Integer userUnreadCount;
    private Integer hrUnreadCount;
    private Boolean closedByHr;
    private LocalDateTime createdAt;
}
