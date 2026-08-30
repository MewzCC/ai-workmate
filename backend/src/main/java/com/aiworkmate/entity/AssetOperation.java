package com.aiworkmate.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("asset_operation")
public class AssetOperation {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long assetId;
    private String operationType;
    private String fromStatus;
    private String toStatus;
    private Long fromDepartmentId;
    private Long toDepartmentId;
    private Long fromOwnerUserId;
    private Long toOwnerUserId;
    private Long operatorUserId;
    private String reason;
    private LocalDateTime createdAt;
}
