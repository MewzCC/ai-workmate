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
    void dispatchesReadCreateAndUpdateWithGatewayActor() throws Exception {
        when(port.dictionaries(context.actor(), new DictionaryToolPort.DictionaryQuery(null, null)))
                .thenReturn(new DictionaryToolPort.DictionaryOverview(List.of(), true));
        var createdAt = LocalDateTime.of(2026, 10, 2, 1, 5);
        var command = new DictionaryToolPort.CreateTypeCommand(
                "PROJECT_STAGE", "项目阶段", "项目阶段字典", 20);
        when(port.createType(context.actor(), command)).thenReturn(new DictionaryToolPort.CreateTypeResult(
                91L, "PROJECT_STAGE", "项目阶段", "项目阶段字典", "ACTIVE", 20, 0, createdAt));
        var updateCommand = new DictionaryToolPort.UpdateTypeCommand(
                "PROJECT_STAGE", 0, "项目阶段新版", "", 30);
        when(port.updateType(context.actor(), updateCommand)).thenReturn(new DictionaryToolPort.UpdateTypeResult(
                91L, "PROJECT_STAGE", "项目阶段新版", null, "ACTIVE", 30, 1, createdAt));

        var query = new DictionaryQueryToolHandler(port, mapper);
        var create = new DictionaryTypeCreateToolHandler(port, mapper);
        var update = new DictionaryTypeUpdateToolHandler(port, mapper);
        var result = create.execute(context, mapper.readTree("""
                {"code":"PROJECT_STAGE","name":"项目阶段","description":"项目阶段字典","sortOrder":20}
                """));
        var updated = update.execute(context, mapper.readTree("""
                {"code":"PROJECT_STAGE","version":0,"name":"项目阶段新版","description":"","sortOrder":30}
                """));

        assertThat(query.toolCode()).isEqualTo(ToolCode.DICTIONARY_QUERY.code());
        query.execute(context, mapper.createObjectNode());
        assertThat(create.toolCode()).isEqualTo(ToolCode.DICTIONARY_TYPE_CREATE.code());
        assertThat(create.executionTemplate()).isEqualTo(ToolExecutionTemplate.DIRECT_WRITE);
        assertThat(result.path("dictionaryTypeId").asLong()).isEqualTo(91L);
        assertThat(update.toolCode()).isEqualTo(ToolCode.DICTIONARY_TYPE_UPDATE.code());
        assertThat(update.executionTemplate()).isEqualTo(ToolExecutionTemplate.DIRECT_WRITE);
        assertThat(updated.path("version").asInt()).isEqualTo(1);
        verify(port).createType(context.actor(), command);
        verify(port).updateType(context.actor(), updateCommand);
        verify(port).dictionaries(context.actor(), new DictionaryToolPort.DictionaryQuery(null, null));
    }
}
