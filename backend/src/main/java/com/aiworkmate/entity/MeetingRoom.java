package com.aiworkmate.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会议室主数据。
 *
 * <p>当前版本仅维护会议室基础信息与开放状态，
 * 预订/审批能力在后续阶段补充。状态枚举 {@code OPEN / CLOSED}。
 * 删除采用软删除（{@code deleted = true}）。
 */
@Data
@TableName("meeting_room")
public class MeetingRoom {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private String code;
    private String name;
    private String location;
    private Integer capacity;
    private String facilities;
    private String status;
    private String remark;
    private Boolean deleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
