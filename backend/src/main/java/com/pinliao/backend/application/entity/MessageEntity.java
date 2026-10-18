package com.pinliao.backend.application.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** messages 表（契约 §8.7；T4 仅写入 INTERVIEW_INVITE 消息）。 */
@Data
@TableName("messages")
public class MessageEntity {

    @TableId(type = IdType.INPUT)
    private String id;

    private String conversationId;
    private String companyId;
    /** USER / HR / SYSTEM */
    private String fromType;
    private String fromUserId;
    private String fromHrId;
    /** TEXT / RESUME / INTERVIEW_INVITE / JOB_CARD / SYSTEM */
    private String type;
    private String content;
    private LocalDateTime createdAt;
    private Boolean readByUser;
    private Boolean readByHr;
}
