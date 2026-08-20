package com.aiworkmate.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 考勤上下班时间配置（每租户一行）。
 *
 * <p>配置标准上班/下班时间与弹性宽限分钟，迟到/早退判定及补卡“视为准时”时间均以此为基准。
 * 未配置租户在读取时回落到 {@code workStartTime=09:00 / workEndTime=18:00 / 宽限 0 分钟}。
 */
@Data
@TableName("attendance_setting")
public class AttendanceSetting {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private LocalTime workStartTime;

    private LocalTime workEndTime;

    /** 弹性上班宽限（分钟）：上班打卡晚于 work_start_time 但在该宽限内不记迟到。 */
    private Integer startFlexMinutes;

    /** 弹性下班宽限（分钟）：下班打卡早于预期下班但不超过该宽限时不记早退。 */
    private Integer endFlexMinutes;

    /**
     * 弹性联动下班：开启后预计下班时间随实际打卡顺延
     * （9:00 上班 → 18:00 下班；9:30 上班 → 18:30 下班）；关闭则按固定 {@code workEndTime} 判定。
     */
    private Boolean flexLinked;

    private Long updatedBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
