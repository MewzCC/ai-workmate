package com.aiworkmate.service.impl;

import com.aiworkmate.dto.AiTaskExecuteRequest;
import com.aiworkmate.dto.AiTaskExecuteResponse;
import com.aiworkmate.dto.AiTaskPlanRequest;
import com.aiworkmate.dto.AiTaskPlanResponse;
import com.aiworkmate.service.AiTaskService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

@Service
public class MockAiTaskServiceImpl implements AiTaskService {

    @Override
    public AiTaskPlanResponse plan(AiTaskPlanRequest request) {
        String type = inferType(request.getInput());
        boolean highRisk = List.of("approve", "export", "update").contains(type);

        return new AiTaskPlanResponse(
                "task_mock_" + Instant.now().toEpochMilli(),
                type,
                highRisk ? "high" : "medium",
                highRisk,
                "已生成 OA AI mock 执行计划，当前不会写入真实业务数据。",
                List.of(
                        new AiTaskPlanResponse.Step("读取当前页面上下文", "识别页面、筛选条件、表格数据和按钮权限。"),
                        new AiTaskPlanResponse.Step("风险分级", "按金额、SLA、供应商、资料完整度和角色权限分级。"),
                        new AiTaskPlanResponse.Step("生成执行意见", "生成通过、退回、补充材料或联调排查建议。"),
                        new AiTaskPlanResponse.Step("确认执行", "高风险动作需要人工确认，并生成审计记录。")
                )
        );
    }

    @Override
    public AiTaskExecuteResponse execute(AiTaskExecuteRequest request) {
        if (!request.isConfirm()) {
            throw new IllegalArgumentException("confirm is required");
        }

        return new AiTaskExecuteResponse(
                true,
                "AUDIT-MOCK-" + Instant.now().toEpochMilli(),
                "AI 任务已模拟执行完成",
                new AiTaskExecuteResponse.ExecuteResult(
                        2,
                        "approve".equals(request.getType()) ? 2 : 1,
                        "approve".equals(request.getType()) ? 1 : 0
                )
        );
    }

    private String inferType(String input) {
        String value = input == null ? "" : input.toLowerCase(Locale.ROOT);
        if (containsAny(value, "新建", "创建", "采购", "入职")) return "create";
        if (containsAny(value, "修改", "调整", "改为", "同步")) return "update";
        if (containsAny(value, "审批", "预审", "通过", "驳回", "退回", "催办")) return "approve";
        if (containsAny(value, "接口", "联调", "失败", "报错", "排查", "回放")) return "debug";
        if (containsAny(value, "导出", "汇总", "报表", "下载")) return "export";
        return "general";
    }

    private boolean containsAny(String value, String... keywords) {
        for (String keyword : keywords) {
            if (value.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
