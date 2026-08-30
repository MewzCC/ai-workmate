package com.aiworkmate.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("meeting_booking")
public class MeetingBooking {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long roomId;
    private Long organizerUserId;
    private String title;
    private String agenda;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private Integer attendeeCount;
    private String status;
    private Integer version;
    private Long cancelledByUserId;
    private LocalDateTime cancelledAt;
    private String cancelReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
