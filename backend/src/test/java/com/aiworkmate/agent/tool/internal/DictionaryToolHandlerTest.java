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
        var itemQueryCommand = new DictionaryToolPort.DictionaryItemQuery(
                "PROJECT_STAGE", "progress", "ACTIVE", 2, 10);
        when(port.dictionaryItems(context.actor(), itemQueryCommand)).thenReturn(
                new DictionaryToolPort.DictionaryItems("PROJECT_STAGE", List.of(
                        new DictionaryToolPort.DictionaryItem("IN_PROGRESS", "进行中", null,
                                "ACTIVE", 10, 0, 1, createdAt)), 1, 2, 10, true));
        var command = new DictionaryToolPort.CreateTypeCommand(
                "PROJECT_STAGE", "项目阶段", "项目阶段字典", 20);
        when(port.createType(context.actor(), command)).thenReturn(new DictionaryToolPort.CreateTypeResult(
                91L, "PROJECT_STAGE", "项目阶段", "项目阶段字典", "ACTIVE", 20, 0, createdAt));
        var updateCommand = new DictionaryToolPort.UpdateTypeCommand(
                "PROJECT_STAGE", 0, "项目阶段新版", "", 30);
        when(port.updateType(context.actor(), updateCommand)).thenReturn(new DictionaryToolPort.UpdateTypeResult(
                91L, "PROJECT_STAGE", "项目阶段新版", null, "ACTIVE", 30, 1, createdAt));
        var createItemCommand = new DictionaryToolPort.CreateItemCommand(
                "PROJECT_STAGE", "IN_PROGRESS", "进行中", "处理中", 10);
        when(port.createItem(context.actor(), createItemCommand)).thenReturn(new DictionaryToolPort.CreateItemResult(
                101L, "PROJECT_STAGE", "IN_PROGRESS", "进行中", "处理中",
                "ACTIVE", 10, 0, 0, createdAt));
        var updateItemCommand = new DictionaryToolPort.UpdateItemCommand(
                "PROJECT_STAGE", "IN_PROGRESS", 0, "处理中", "执行中", 20);
        when(port.updateItem(context.actor(), updateItemCommand)).thenReturn(new DictionaryToolPort.UpdateItemResult(
                101L, "PROJECT_STAGE", "IN_PROGRESS", "处理中", "执行中",
                "ACTIVE", 20, 2, 1, createdAt));
        var updateTypeStatusCommand = new DictionaryToolPort.UpdateTypeStatusCommand(
                "PROJECT_STAGE", 1, "DISABLED");
        when(port.updateTypeStatus(context.actor(), updateTypeStatusCommand))
                .thenReturn(new DictionaryToolPort.UpdateTypeResult(
                        91L, "PROJECT_STAGE", "项目阶段新版", null, "DISABLED", 30, 2, createdAt));

        var query = new DictionaryQueryToolHandler(port, mapper);
        var itemQuery = new DictionaryItemQueryToolHandler(port, mapper);
        var create = new DictionaryTypeCreateToolHandler(port, mapper);
        var update = new DictionaryTypeUpdateToolHandler(port, mapper);
        var createItem = new DictionaryItemCreateToolHandler(port, mapper);
        var updateItem = new DictionaryItemUpdateToolHandler(port, mapper);
        var updateTypeStatus = new DictionaryTypeUpdateStatusToolHandler(port, mapper);
        var result = create.execute(context, mapper.readTree("""
                {"code":"PROJECT_STAGE","name":"项目阶段","description":"项目阶段字典","sortOrder":20}
                """));
        var updated = update.execute(context, mapper.readTree("""
                {"code":"PROJECT_STAGE","version":0,"name":"项目阶段新版","description":"","sortOrder":30}
                """));
        var item = createItem.execute(context, mapper.readTree("""
                {"typeCode":"PROJECT_STAGE","value":"IN_PROGRESS","label":"进行中","description":"处理中","sortOrder":10}
                """));
        var itemPage = itemQuery.execute(context, mapper.readTree("""
                {"typeCode":"PROJECT_STAGE","keyword":"progress","status":"ACTIVE","page":2,"size":10}
                """));
        var updatedItem = updateItem.execute(context, mapper.readTree("""
                {"typeCode":"PROJECT_STAGE","value":"IN_PROGRESS","version":0,"label":"处理中","description":"执行中","sortOrder":20}
                """));
        var updatedTypeStatus = updateTypeStatus.execute(context, mapper.readTree("""
                {"code":"PROJECT_STAGE","version":1,"status":"DISABLED"}
                """));

        assertThat(query.toolCode()).isEqualTo(ToolCode.DICTIONARY_QUERY.code());
        query.execute(context, mapper.createObjectNode());
        assertThat(itemQuery.toolCode()).isEqualTo(ToolCode.DICTIONARY_ITEM_QUERY.code());
        assertThat(itemPage.path("records").get(0).path("value").asText()).isEqualTo("IN_PROGRESS");
        assertThat(create.toolCode()).isEqualTo(ToolCode.DICTIONARY_TYPE_CREATE.code());
        assertThat(create.executionTemplate()).isEqualTo(ToolExecutionTemplate.DIRECT_WRITE);
        assertThat(result.path("dictionaryTypeId").asLong()).isEqualTo(91L);
        assertThat(update.toolCode()).isEqualTo(ToolCode.DICTIONARY_TYPE_UPDATE.code());
        assertThat(update.executionTemplate()).isEqualTo(ToolExecutionTemplate.DIRECT_WRITE);
        assertThat(updated.path("version").asInt()).isEqualTo(1);
        assertThat(createItem.toolCode()).isEqualTo(ToolCode.DICTIONARY_ITEM_CREATE.code());
        assertThat(item.path("dictionaryItemId").asLong()).isEqualTo(101L);
        assertThat(updateItem.toolCode()).isEqualTo(ToolCode.DICTIONARY_ITEM_UPDATE.code());
        assertThat(updateItem.executionTemplate()).isEqualTo(ToolExecutionTemplate.DIRECT_WRITE);
        assertThat(updatedItem.path("version").asInt()).isEqualTo(1);
        assertThat(updateTypeStatus.toolCode()).isEqualTo(ToolCode.DICTIONARY_TYPE_UPDATE_STATUS.code());
        assertThat(updatedTypeStatus.path("status").asText()).isEqualTo("DISABLED");
        verify(port).createType(context.actor(), command);
        verify(port).updateType(context.actor(), updateCommand);
        verify(port).createItem(context.actor(), createItemCommand);
        verify(port).updateItem(context.actor(), updateItemCommand);
        verify(port).updateTypeStatus(context.actor(), updateTypeStatusCommand);
        verify(port).dictionaries(context.actor(), new DictionaryToolPort.DictionaryQuery(null, null));
        verify(port).dictionaryItems(context.actor(), itemQueryCommand);
    }
}
