package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.DictionaryToolPort;
import com.aiworkmate.agent.tool.port.ToolActorContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DictionaryToolHandlerTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final DictionaryToolPort port = mock(DictionaryToolPort.class);
    private final TrustedToolContext context = new TrustedToolContext(1L, 2L, 3L, 4L, 0, "trace");

    @Test
    void dispatchesReadAndCreateWithGatewayActor() throws Exception {
        when(port.dictionaries(context.actor(), new DictionaryToolPort.DictionaryQuery(null, null)))
                .thenReturn(new DictionaryToolPort.DictionaryOverview(List.of(), true));
        var createdAt = LocalDateTime.of(2026, 10, 2, 1, 5);
        var command = new DictionaryToolPort.CreateTypeCommand(
                "PROJECT_STAGE", "项目阶段", "项目阶段字典", 20);
        when(port.createType(context.actor(), command)).thenReturn(new DictionaryToolPort.CreateTypeResult(
                91L, "PROJECT_STAGE", "项目阶段", "项目阶段字典", "ACTIVE", 20, 0, createdAt));

        var query = new DictionaryQueryToolHandler(port, mapper);
        var create = new DictionaryTypeCreateToolHandler(port, mapper);
        var result = create.execute(context, mapper.readTree("""
                {"code":"PROJECT_STAGE","name":"项目阶段","description":"项目阶段字典","sortOrder":20}
                """));

        assertThat(query.toolCode()).isEqualTo(ToolCode.DICTIONARY_QUERY.code());
        query.execute(context, mapper.createObjectNode());
        assertThat(create.toolCode()).isEqualTo(ToolCode.DICTIONARY_TYPE_CREATE.code());
        assertThat(create.executionTemplate()).isEqualTo(ToolExecutionTemplate.DIRECT_WRITE);
        assertThat(result.path("dictionaryTypeId").asLong()).isEqualTo(91L);
        verify(port).createType(context.actor(), command);
        verify(port).dictionaries(context.actor(), new DictionaryToolPort.DictionaryQuery(null, null));
    }
}
