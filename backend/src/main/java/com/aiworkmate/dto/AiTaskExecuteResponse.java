package com.aiworkmate.dto;

public record AiTaskExecuteResponse(String taskId, String status, String statusUrl, String eventsUrl) { }
