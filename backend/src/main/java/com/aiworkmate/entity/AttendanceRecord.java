package com.aiworkmate.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 考勤打卡记录。
 *
 * <p>每个用户每天最多一条记录，由 {@code uk_attendance_user_date} 唯一约束保证。
 * 上班/下班打卡分别更新 {@code clock_in_time} 与 {@code clock_out_time}，
 * 每次打卡后重新计算 {@code status} / {@code late_minutes} / {@code early_leave_minutes}。
 */
@Data
@TableName("attendance_record")
public class AttendanceRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private Long userId;
    private LocalDate clockDate;
    private LocalDateTime clockInTime;
    private LocalDateTime clockOutTime;
    private String clockInIp;
    private String clockOutIp;
    private String status;
    private Integer lateMinutes;
    private Integer earlyLeaveMinutes;
    private String source;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
