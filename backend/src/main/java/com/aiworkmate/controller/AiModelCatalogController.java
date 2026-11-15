package com.aiworkmate.controller;
import com.aiworkmate.common.Result;
import com.aiworkmate.config.AiProviderRuntime;
import com.aiworkmate.dto.AiConfiguredModel;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/ai/models")
public class AiModelCatalogController {
    private final AiProviderRuntime runtime;
    @GetMapping public Result<List<AiConfiguredModel>> models() { return Result.ok(runtime.catalog()); }
}
