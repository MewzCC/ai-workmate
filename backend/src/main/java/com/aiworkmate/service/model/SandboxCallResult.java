package com.aiworkmate.service.model;
public record SandboxCallResult(String outcome,Integer httpStatus,long durationMs,String responsePreview,String errorCode) {}
