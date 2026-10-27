package com.aiworkmate.agent.registry;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

@Configuration(proxyBeanMethods = false)
public class AgentAttendanceWriteToolDefinitions {
    public static final String CLOCK_INPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"required":["clockType"],"properties":{"clockType":{"type":"string","enum":["CLOCK_IN","CLOCK_OUT"]}}}
            """.strip();
    public static final String CLOCK_OUTPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"required":["recordId","clockDate","status","lateMinutes","earlyLeaveMinutes"],"properties":{"recordId":{"type":"integer","minimum":1},"clockDate":{"type":"string","format":"date"},"clockInTime":{"type":["string","null"],"format":"date-time"},"clockOutTime":{"type":["string","null"],"format":"date-time"},"status":{"type":"string","enum":["NORMAL","LATE","EARLY_LEAVE","LATE_AND_EARLY","MISSING_CLOCK"]},"lateMinutes":{"type":"integer","minimum":0},"earlyLeaveMinutes":{"type":"integer","minimum":0}}}
            """.strip();
    public static final String REISSUE_INPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"required":["clockDate","clockType","reason"],"properties":{"clockDate":{"type":"string","format":"date"},"clockType":{"type":"string","enum":["CLOCK_IN","CLOCK_OUT"]},"reason":{"type":"string","minLength":1,"maxLength":500}}}
            """.strip();
    public static final String REISSUE_OUTPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"required":["reissueId","status","clockDate","clockType","submittedAt"],"properties":{"reissueId":{"type":"integer","minimum":1},"status":{"type":"string","const":"PENDING"},"clockDate":{"type":"string","format":"date"},"clockType":{"type":"string","enum":["CLOCK_IN","CLOCK_OUT"]},"submittedAt":{"type":"string","format":"date-time"}}}
            """.strip();
    public static final String REISSUE_DECISION_INPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"required":["reissueId","version","decision"],"properties":{"reissueId":{"type":"integer","minimum":1},"version":{"type":"integer","minimum":0},"decision":{"type":"string","enum":["APPROVED","REJECTED"]},"comment":{"type":"string","maxLength":500}}}
            """.strip();
    public static final String REISSUE_DECISION_OUTPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"required":["reissueId","status","version","decidedAt"],"properties":{"reissueId":{"type":"integer","minimum":1},"status":{"type":"string","enum":["APPROVED","REJECTED"]},"version":{"type":"integer","minimum":1},"decidedAt":{"type":"string","format":"date-time"}}}
            """.strip();
    public static final String SETTINGS_UPDATE_INPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"required":["version","workStartTime","workEndTime","startFlexMinutes","endFlexMinutes","flexLinked"],"properties":{"version":{"type":"integer","minimum":0},"workStartTime":{"type":"string","pattern":"^(?:[01]\\\\d|2[0-3]):[0-5]\\\\d$"},"workEndTime":{"type":"string","pattern":"^(?:[01]\\\\d|2[0-3]):[0-5]\\\\d$"},"startFlexMinutes":{"type":"integer","minimum":0,"maximum":480},"endFlexMinutes":{"type":"integer","minimum":0,"maximum":480},"flexLinked":{"type":"boolean"}}}
            """.strip();
    public static final String SETTINGS_UPDATE_OUTPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"required":["version","workStartTime","workEndTime","startFlexMinutes","endFlexMinutes","flexLinked","updatedAt"],"properties":{"version":{"type":"integer","minimum":1},"workStartTime":{"type":"string"},"workEndTime":{"type":"string"},"startFlexMinutes":{"type":"integer","minimum":0,"maximum":480},"endFlexMinutes":{"type":"integer","minimum":0,"maximum":480},"flexLinked":{"type":"boolean"},"updatedAt":{"type":"string","format":"date-time"}}}
            """.strip();

    @Bean
    public ToolDefinition attendanceClockToolDefinition(ObjectMapper objectMapper)
            throws JsonProcessingException {
        return ToolDefinitionFactory.singleWrite(
                ToolCode.ATTENDANCE_CLOCK, "Clock my attendance",
                "Records one current-time clock-in or clock-out for the authenticated user.",
                "Clock only the current user at server time after explicit confirmation; never accept another identity or timestamp.",
                objectMapper.readTree(CLOCK_INPUT_SCHEMA), objectMapper.readTree(CLOCK_OUTPUT_SCHEMA),
                ToolWriteProfile.NON_RETRYABLE_L1, Set.of("attendance:clock"), OwnershipPolicy.SELF,
                1, 4096, 10000);
    }

    @Bean
    public ToolDefinition attendanceReissueApplyToolDefinition(ObjectMapper objectMapper)
            throws JsonProcessingException {
        return ToolDefinitionFactory.singleWrite(
                ToolCode.ATTENDANCE_REISSUE_APPLY, "Apply for my attendance correction",
                "Creates exactly one attendance correction request for the authenticated user.",
                "Submit one personal correction request for approval without modifying attendance records directly.",
                objectMapper.readTree(REISSUE_INPUT_SCHEMA),
                objectMapper.readTree(REISSUE_OUTPUT_SCHEMA), ToolWriteProfile.IDEMPOTENT_L1,
                Set.of("attendance:reissue:apply"), OwnershipPolicy.SELF,
                1, 8192, 10000);
    }

    @Bean
    public ToolDefinition attendanceReissueDecideToolDefinition(ObjectMapper objectMapper)
            throws JsonProcessingException {
        return ToolDefinitionFactory.singleWrite(
                ToolCode.ATTENDANCE_REISSUE_DECIDE, "Decide an assigned attendance correction",
                "Approves or rejects exactly one pending attendance correction assigned to the authenticated user.",
                "Use the current resource version and require a rejection comment; never decide an unassigned request.",
                objectMapper.readTree(REISSUE_DECISION_INPUT_SCHEMA),
                objectMapper.readTree(REISSUE_DECISION_OUTPUT_SCHEMA), ToolWriteProfile.SECONDARY_L2,
                Set.of("attendance:reissue:decide"), OwnershipPolicy.ASSIGNED_TO_SELF,
                1, 4096, 10000);
    }

    @Bean
    public ToolDefinition attendanceSettingsUpdateToolDefinition(ObjectMapper objectMapper)
            throws JsonProcessingException {
        return ToolDefinitionFactory.singleWrite(
                ToolCode.ATTENDANCE_SETTINGS_UPDATE, "Update tenant attendance settings",
                "Updates one tenant's work hours and flex rules using the current configuration version.",
                "Require secondary confirmation and the current version; never accept a tenant or user identity.",
                objectMapper.readTree(SETTINGS_UPDATE_INPUT_SCHEMA),
                objectMapper.readTree(SETTINGS_UPDATE_OUTPUT_SCHEMA), ToolWriteProfile.SECONDARY_L2,
                Set.of("attendance:settings:manage"), OwnershipPolicy.TENANT_SCOPED,
                1, 4096, 10000);
    }
}
