package com.aiworkmate.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 站内通知。业务事件经 Redis 临时队列异步写入本表。
 */
@Data
@TableName("notification")
public class Notification {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private Long userId;

    /** approval / system / alert / todo */
    private String type;

    private String title;

    private String content;

    /** 关联业务类型，如 leave */
    private String bizType;

    private Long bizId;

    private Boolean readFlag;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
