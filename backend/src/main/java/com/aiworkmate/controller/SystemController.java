package com.aiworkmate.controller;

import com.aiworkmate.common.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/system")
public class SystemController {

    @GetMapping("/health")
    public Result<Map<String, String>> health() {
        return Result.ok(Map.of(
                "status", "ok",
                "service", "oa-ai-platform"
        ));
    }

    /**
     * 服务器时间接口：返回 epoch 毫秒与 ISO 字符串，供前端校准本地时钟，
     * 确保打卡按钮显示的时间与后端落库时间使用同一时间源。
     */
    @GetMapping("/time")
    public Result<Map<String, Object>> time() {
        Instant now = Instant.now();
        return Result.ok(Map.of(
                "epochMillis", now.toEpochMilli(),
                "iso", now.toString()
        ));
    }
}
