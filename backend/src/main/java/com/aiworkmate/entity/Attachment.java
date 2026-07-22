package com.aiworkmate.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("attachment")
public class Attachment {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private Long conversationId;
    private Long messageId;
    private String type;
    private String name;
    private String storageName;
    private Long size;
    private String mimeType;
    private String extractedText;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
