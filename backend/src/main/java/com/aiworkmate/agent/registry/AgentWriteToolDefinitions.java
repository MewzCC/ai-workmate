package com.aiworkmate.agent.registry;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

@Configuration(proxyBeanMethods = false)
public class AgentWriteToolDefinitions {

    public static final String LEAVE_CREATE_DRAFT_INPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"required":["leaveType","startDate","startPeriod","endDate","endPeriod","reason"],"properties":{"leaveType":{"type":"string","enum":["ANNUAL","PERSONAL","SICK","MARRIAGE","MATERNITY","PATERNITY","BEREAVEMENT","COMPENSATORY","OTHER"]},"approverUserId":{"type":"integer","minimum":1},"startDate":{"type":"string","format":"date","maxLength":10},"startPeriod":{"type":"string","enum":["AM","PM"]},"endDate":{"type":"string","format":"date","maxLength":10},"endPeriod":{"type":"string","enum":["AM","PM"]},"reason":{"type":"string","minLength":1,"maxLength":500}}}
            """.strip();

    public static final String LEAVE_CREATE_DRAFT_OUTPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"required":["applicationId","status","version"],"properties":{"applicationId":{"type":"integer","minimum":1},"status":{"type":"string","const":"DRAFT"},"version":{"type":"integer","minimum":0}}}
            """.strip();

    public static final String LEAVE_SUBMIT_INPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"required":["applicationId","version"],"properties":{"applicationId":{"type":"integer","minimum":1},"version":{"type":"integer","minimum":0}}}
            """.strip();

    public static final String LEAVE_SUBMIT_OUTPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"required":["applicationId","status","version"],"properties":{"applicationId":{"type":"integer","minimum":1},"status":{"type":"string","const":"PENDING"},"version":{"type":"integer","minimum":1}}}
            """.strip();

    public static final String LEAVE_APPLY_INPUT_SCHEMA = LEAVE_CREATE_DRAFT_INPUT_SCHEMA;

    public static final String LEAVE_UPDATE_DRAFT_INPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"required":["applicationId","version","leaveType","startDate","startPeriod","endDate","endPeriod","reason"],"properties":{"applicationId":{"type":"integer","minimum":1},"version":{"type":"integer","minimum":0,"maximum":2147483646},"leaveType":{"type":"string","enum":["ANNUAL","PERSONAL","SICK","MARRIAGE","MATERNITY","PATERNITY","BEREAVEMENT","COMPENSATORY","OTHER"]},"approverUserId":{"type":"integer","minimum":1},"startDate":{"type":"string","format":"date","maxLength":10},"startPeriod":{"type":"string","enum":["AM","PM"]},"endDate":{"type":"string","format":"date","maxLength":10},"endPeriod":{"type":"string","enum":["AM","PM"]},"reason":{"type":"string","minLength":1,"maxLength":500}}}
            """.strip();

    public static final String LEAVE_UPDATE_DRAFT_OUTPUT_SCHEMA = LEAVE_CREATE_DRAFT_OUTPUT_SCHEMA;

    public static final String LEAVE_APPLY_OUTPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"required":["applicationId","status","version","approvalTaskId"],"properties":{"applicationId":{"type":"integer","minimum":1},"status":{"type":"string","const":"PENDING"},"version":{"type":"integer","minimum":1},"approvalTaskId":{"type":"integer","minimum":1}}}
            """.strip();

    @Bean
    public ToolDefinition leaveCreateDraftToolDefinition(ObjectMapper objectMapper) throws JsonProcessingException {
        return ToolDefinitionFactory.singleWrite(
                ToolCode.LEAVE_CREATE_DRAFT, "Create my leave draft",
                "Creates exactly one draft owned by the authenticated user in the authenticated tenant.",
                "用于用户明确要求暂存、保存草稿、不提交的请假。只保存本人草稿，不启动审批；用户要求直接申请并提交时应选择原子申请操作。必填类型、起止日期和半天、理由；可选审批人未提供时不追问。",
                objectMapper.readTree(LEAVE_CREATE_DRAFT_INPUT_SCHEMA),
                objectMapper.readTree(LEAVE_CREATE_DRAFT_OUTPUT_SCHEMA), ToolWriteProfile.IDEMPOTENT_L1,
                Set.of("leave:create"), OwnershipPolicy.SELF,
                1, 16384, 15000);
    }

    @Bean
    public ToolDefinition leaveSubmitToolDefinition(ObjectMapper objectMapper) throws JsonProcessingException {
        return ToolDefinitionFactory.singleWrite(
                ToolCode.LEAVE_SUBMIT, "Submit my existing leave draft",
                "Submits one pre-existing draft owned by the authenticated user in the authenticated tenant.",
                "仅提交执行前已经存在的本人请假草稿，必须有真实申请编号applicationId和当前version；不可用于尚未创建的请假，也不可和创建草稿拼成两个写步骤。返回进入审批的申请编号、状态和新版本。",
                objectMapper.readTree(LEAVE_SUBMIT_INPUT_SCHEMA),
                objectMapper.readTree(LEAVE_SUBMIT_OUTPUT_SCHEMA), ToolWriteProfile.SECONDARY_L2,
                Set.of("leave:create"), OwnershipPolicy.SELF,
                1, 16384, 15000);
    }

    @Bean
    public ToolDefinition leaveApplyToolDefinition(ObjectMapper objectMapper) throws JsonProcessingException {
        return ToolDefinitionFactory.singleWrite(
                ToolCode.LEAVE_APPLY, "Apply for leave",
                "Atomically creates and submits exactly one leave application owned by the authenticated user.",
                "用于用户要求请假、申请并提交、走请假审批流程；在一个事务中创建本人申请并启动审批。不是只存草稿，也不是提交已有草稿。必填请假类型、起止日期和半天、用户理由；可选审批人未指定时省略。返回申请编号、PENDING状态、新版本和审批任务编号；执行前必须二次确认。",
                objectMapper.readTree(LEAVE_APPLY_INPUT_SCHEMA),
                objectMapper.readTree(LEAVE_APPLY_OUTPUT_SCHEMA), ToolWriteProfile.SECONDARY_L2,
                Set.of("leave:create"), OwnershipPolicy.SELF,
                1, 16384, 15000);
    }

    @Bean
    public ToolDefinition leaveUpdateDraftToolDefinition(ObjectMapper objectMapper) throws JsonProcessingException {
        return ToolDefinitionFactory.singleWrite(
                ToolCode.LEAVE_UPDATE_DRAFT, "Update my leave draft",
                "Replaces the bounded form fields on exactly one leave draft owned by the authenticated user.",
                "仅修改已有本人草稿的表单内容，必须有真实申请编号及当前version，同时提供完整替换表单字段。不提交、不启动审批；用户要求提交草稿时应使用提交操作。",
                objectMapper.readTree(LEAVE_UPDATE_DRAFT_INPUT_SCHEMA),
                objectMapper.readTree(LEAVE_UPDATE_DRAFT_OUTPUT_SCHEMA), ToolWriteProfile.NON_RETRYABLE_L1,
                Set.of("leave:create"), OwnershipPolicy.SELF,
                1, 16384, 15000);
    }
}
