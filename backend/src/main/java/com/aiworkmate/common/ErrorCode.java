package com.aiworkmate.common;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    REQUEST_INVALID(40001, "REQUEST_INVALID", HttpStatus.BAD_REQUEST, "请求参数不合法"),
    AUTH_REQUIRED(40101, "AUTH_REQUIRED", HttpStatus.UNAUTHORIZED, "请先登录"),
    AUTH_TOKEN_INVALID(40102, "AUTH_TOKEN_INVALID", HttpStatus.UNAUTHORIZED, "登录凭证无效"),
    AUTH_TOKEN_EXPIRED(40103, "AUTH_TOKEN_EXPIRED", HttpStatus.UNAUTHORIZED, "登录已过期"),
    PERMISSION_DENIED(40301, "PERMISSION_DENIED", HttpStatus.FORBIDDEN, "权限不足"),
    RESOURCE_FORBIDDEN(40302, "RESOURCE_FORBIDDEN", HttpStatus.FORBIDDEN, "无权访问该资源"),
    AI_TASK_CONFIRMATION_REQUIRED(40901, "AI_TASK_CONFIRMATION_REQUIRED", HttpStatus.CONFLICT, "任务执行前必须确认"),
    AI_TASK_CAPABILITY_UNAVAILABLE(50301, "AI_TASK_CAPABILITY_UNAVAILABLE", HttpStatus.SERVICE_UNAVAILABLE,
            "AI 任务能力尚未接入真实业务工具，当前不可用"),
    AI_CHAT_UNAVAILABLE(50302, "AI_CHAT_UNAVAILABLE", HttpStatus.SERVICE_UNAVAILABLE,
            "AI 对话服务暂时不可用"),
    TOOL_CAPABILITY_UNAVAILABLE(50311, "TOOL_CAPABILITY_UNAVAILABLE", HttpStatus.SERVICE_UNAVAILABLE,
            "业务工具当前不可用"),
    RATE_LIMITED(42901, "RATE_LIMITED", HttpStatus.TOO_MANY_REQUESTS, "请求过于频繁，请稍后重试"),
    SYSTEM_ERROR(50001, "SYSTEM_ERROR", HttpStatus.INTERNAL_SERVER_ERROR, "系统内部错误");

    private final int code;
    private final String errorCode;
    private final HttpStatus status;
    private final String defaultMessage;
}
