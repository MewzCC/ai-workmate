package com.aiworkmate.agent.registry;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.Set;

@Configuration(proxyBeanMethods = false)
public class AgentMeetingRoomToolDefinitions {
    public static final String QUERY_INPUT = """
            {"type":"object","additionalProperties":false,"properties":{"keyword":{"type":"string","maxLength":200},"status":{"type":"string","enum":["OPEN","CLOSED"]},"page":{"type":"integer","minimum":1,"maximum":10000},"size":{"type":"integer","minimum":1,"maximum":50}}}
            """.strip();
    public static final String CREATE_INPUT = """
            {"type":"object","additionalProperties":false,"required":["code","name","capacity","status"],"properties":{"code":{"type":"string","minLength":1,"maxLength":64},"name":{"type":"string","minLength":1,"maxLength":120},"location":{"type":"string","maxLength":200},"capacity":{"type":"integer","minimum":0,"maximum":10000},"facilities":{"type":"string","maxLength":200},"status":{"type":"string","enum":["OPEN","CLOSED"]},"remark":{"type":"string","maxLength":500}}}
            """.strip();
    public static final String UPDATE_INPUT = """
            {"type":"object","additionalProperties":false,"required":["roomId","version"],"properties":{"roomId":{"type":"integer","minimum":1},"version":{"type":"integer","minimum":0,"maximum":2147483646},"code":{"type":"string","minLength":1,"maxLength":64},"name":{"type":"string","minLength":1,"maxLength":120},"location":{"type":"string","maxLength":200},"capacity":{"type":"integer","minimum":0,"maximum":10000},"facilities":{"type":"string","maxLength":200},"status":{"type":"string","enum":["OPEN","CLOSED"]},"remark":{"type":"string","maxLength":500}},"minProperties":3}
            """.strip();
    public static final String QUERY_OUTPUT = """
            {"type":"object","additionalProperties":false,"required":["items","total","page","size"],"properties":{"items":{"type":"array","maxItems":50,"items":{"type":"object","additionalProperties":false,"required":["roomId","code","name","capacity","status","version"],"properties":{"roomId":{"type":"integer","minimum":1},"code":{"type":"string","minLength":1,"maxLength":64},"name":{"type":"string","minLength":1,"maxLength":120},"location":{"type":"string","maxLength":200},"capacity":{"type":"integer","minimum":0},"facilities":{"type":"string","maxLength":200},"status":{"type":"string","enum":["OPEN","CLOSED"]},"remark":{"type":"string","maxLength":500},"version":{"type":"integer","minimum":0}}}},"total":{"type":"integer","minimum":0},"page":{"type":"integer","minimum":1},"size":{"type":"integer","minimum":1,"maximum":50}}}
            """.strip();
    public static final String WRITE_OUTPUT = """
            {"type":"object","additionalProperties":false,"required":["roomId","code","name","status","version"],"properties":{"roomId":{"type":"integer","minimum":1},"code":{"type":"string","minLength":1,"maxLength":64},"name":{"type":"string","minLength":1,"maxLength":120},"status":{"type":"string","enum":["OPEN","CLOSED"]},"version":{"type":"integer","minimum":0}}}
            """.strip();

    @Bean
    public ToolDefinition meetingRoomQueryToolDefinition(ObjectMapper mapper) throws JsonProcessingException {
        return ToolDefinitionFactory.read(ToolCode.MEETING_ROOM_QUERY, "Query meeting room metadata",
                "Pages through tenant room metadata including the current version.",
                "Read room metadata, not booking availability. Use keyword, status and bounded pagination. No identities or deletion.",
                mapper.readTree(QUERY_INPUT), mapper.readTree(QUERY_OUTPUT), Set.of("assets:read", "meeting:read:self"),
                PermissionMode.ANY, OwnershipPolicy.TENANT_SCOPED, 50, 262144, 15000);
    }

    @Bean
    public ToolDefinition meetingRoomCreateToolDefinition(ObjectMapper mapper) throws JsonProcessingException {
        return ToolDefinitionFactory.singleWrite(ToolCode.MEETING_ROOM_CREATE, "Create one meeting room",
                "Creates one tenant-scoped room and atomic business audit after confirmation.",
                "Create one room only. Code, name, capacity and status are required. Never retry an uncertain write; verify using room query first. No booking or deletion.",
                mapper.readTree(CREATE_INPUT), mapper.readTree(WRITE_OUTPUT), ToolWriteProfile.NON_RETRYABLE_L1,
                Set.of("meeting:write"), OwnershipPolicy.TENANT_SCOPED, 1, 4096, 10000);
    }

    @Bean
    public ToolDefinition meetingRoomUpdateToolDefinition(ObjectMapper mapper) throws JsonProcessingException {
        return ToolDefinitionFactory.singleWrite(ToolCode.MEETING_ROOM_UPDATE, "Update one meeting room",
                "Patches one tenant-scoped room at its expected version with atomic audit.",
                "Change only explicitly supplied fields of one room after confirmation. Omitted fields stay unchanged; empty optional text clears it. No booking or deletion. Closing fails when active bookings exist. Never retry an uncertain write.",
                mapper.readTree(UPDATE_INPUT), mapper.readTree(WRITE_OUTPUT), ToolWriteProfile.NON_RETRYABLE_L1,
                Set.of("meeting:write"), OwnershipPolicy.TENANT_SCOPED, 1, 4096, 10000);
    }
}
