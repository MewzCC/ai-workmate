package com.aiworkmate.agent.tool.adapter;

import com.aiworkmate.agent.tool.port.DictionaryToolPort;
import com.aiworkmate.agent.tool.port.ToolActorContext;
import com.aiworkmate.dto.DictionaryTypeListResponse;
import com.aiworkmate.dto.DictionaryTypeResponse;
import com.aiworkmate.dto.DictionaryTypeAgentUpdateRequest;
import com.aiworkmate.dto.DictionaryItemAgentCreateRequest;
import com.aiworkmate.dto.DictionaryItemAgentUpdateRequest;
import com.aiworkmate.dto.DictionaryItemResponse;
import com.aiworkmate.dto.DictionaryItemPageResponse;
import com.aiworkmate.dto.DictionaryStatusRequest;
import com.aiworkmate.service.DataDictionaryService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DictionaryAgentDomainToolAdapterTest {
    private final DataDictionaryService service = mock(DataDictionaryService.class);
    private final DictionaryAgentDomainToolAdapter adapter = new DictionaryAgentDomainToolAdapter(service);
    private final ToolActorContext actor = new ToolActorContext(1L, 2L, 3L, 4L, 0, "trace");

    @Test
    void mapsDictionaryQueryWithoutInternalIdentifiers() {
        var updatedAt = LocalDateTime.of(2026, 10, 2, 1, 0);
        when(service.listTypes(2L, "employee", "ACTIVE")).thenReturn(new DictionaryTypeListResponse(
                List.of(new DictionaryTypeResponse(81L, "EMPLOYEE_STATUS", "员工状态", null,
                        "ACTIVE", 10, 2, 2, 0, updatedAt, true)), true));

        var result = adapter.dictionaries(actor, new DictionaryToolPort.DictionaryQuery("employee", "ACTIVE"));

        assertThat(result.records()).singleElement().satisfies(type -> {
            assertThat(type.code()).isEqualTo("EMPLOYEE_STATUS");
            assertThat(type.toString()).doesNotContain("81");
        });
    }

    @Test
    void mapsDictionaryItemsWithoutInternalIdentifiers() {
        var updatedAt = LocalDateTime.of(2026, 10, 2, 2, 5);
        when(service.listItemsAgent(2L, "PROJECT_STAGE", "progress", "ACTIVE", 1, 20))
                .thenReturn(new DictionaryItemPageResponse(List.of(
                        new DictionaryItemResponse(101L, 91L, "IN_PROGRESS", "进行中", null,
                                "ACTIVE", 10, 2L, 3, updatedAt, true, false)), 1, 1, 20, true));

        var result = adapter.dictionaryItems(actor, new DictionaryToolPort.DictionaryItemQuery(
                "PROJECT_STAGE", "progress", "ACTIVE", 1, 20));

        assertThat(result.typeCode()).isEqualTo("PROJECT_STAGE");
        assertThat(result.records()).singleElement().satisfies(item -> {
            assertThat(item.value()).isEqualTo("IN_PROGRESS");
            assertThat(item.version()).isEqualTo(3);
            assertThat(item.toString()).doesNotContain("101", "91");
        });
    }

    @Test
    void mapsTrustedActorAndBoundedCreateCommand() {
        var updatedAt = LocalDateTime.of(2026, 10, 2, 1, 5);
        when(service.createTypeAgent(2L, new com.aiworkmate.dto.DictionaryTypeRequest(
                "PROJECT_STAGE", "项目阶段", "项目阶段字典", 20, null)))
                .thenReturn(new DictionaryTypeResponse(91L, "PROJECT_STAGE", "项目阶段", "项目阶段字典",
                        "ACTIVE", 20, 0, 0, 0, updatedAt, true));

        var result = adapter.createType(actor, new DictionaryToolPort.CreateTypeCommand(
                "PROJECT_STAGE", "项目阶段", "项目阶段字典", 20));

        assertThat(result.dictionaryTypeId()).isEqualTo(91L);
        assertThat(result.status()).isEqualTo("ACTIVE");
        verify(service).createTypeAgent(2L, new com.aiworkmate.dto.DictionaryTypeRequest(
                "PROJECT_STAGE", "项目阶段", "项目阶段字典", 20, null));
    }

    @Test
    void mapsImmutableCodeAndOptimisticVersionForUpdate() {
        var updatedAt = LocalDateTime.of(2026, 10, 2, 1, 25);
        when(service.updateTypeAgent(2L, "PROJECT_STAGE",
                new DictionaryTypeAgentUpdateRequest(0, "项目阶段新版", "", 30)))
                .thenReturn(new DictionaryTypeResponse(91L, "PROJECT_STAGE", "项目阶段新版", null,
                        "ACTIVE", 30, 0, 0, 1, updatedAt, true));

        var result = adapter.updateType(actor, new DictionaryToolPort.UpdateTypeCommand(
                "PROJECT_STAGE", 0, "项目阶段新版", "", 30));

        assertThat(result.dictionaryTypeId()).isEqualTo(91L);
        assertThat(result.version()).isEqualTo(1);
        verify(service).updateTypeAgent(2L, "PROJECT_STAGE",
                new DictionaryTypeAgentUpdateRequest(0, "项目阶段新版", "", 30));
    }

    @Test
    void mapsTypeCodeAndBoundedItemCreate() {
        var updatedAt = LocalDateTime.of(2026, 10, 2, 1, 45);
        when(service.createItemAgent(2L, "PROJECT_STAGE",
                new DictionaryItemAgentCreateRequest("IN_PROGRESS", "进行中", "处理中", 10)))
                .thenReturn(new DictionaryItemResponse(101L, 91L, "IN_PROGRESS", "进行中", "处理中",
                        "ACTIVE", 10, 0L, 0, updatedAt, true, true));

        var result = adapter.createItem(actor, new DictionaryToolPort.CreateItemCommand(
                "PROJECT_STAGE", "IN_PROGRESS", "进行中", "处理中", 10));

        assertThat(result.dictionaryItemId()).isEqualTo(101L);
        assertThat(result.typeCode()).isEqualTo("PROJECT_STAGE");
        verify(service).createItemAgent(2L, "PROJECT_STAGE",
                new DictionaryItemAgentCreateRequest("IN_PROGRESS", "进行中", "处理中", 10));
    }

    @Test
    void mapsImmutableTypeCodeValueAndOptimisticVersionForItemUpdate() {
        var updatedAt = LocalDateTime.of(2026, 10, 2, 2, 25);
        when(service.updateItemAgent(2L, "PROJECT_STAGE", "IN_PROGRESS",
                new DictionaryItemAgentUpdateRequest(3, "处理中", "执行中", 20)))
                .thenReturn(new DictionaryItemResponse(101L, 91L, "IN_PROGRESS", "处理中", "执行中",
                        "ACTIVE", 20, 2L, 4, updatedAt, true, false));

        var result = adapter.updateItem(actor, new DictionaryToolPort.UpdateItemCommand(
                "PROJECT_STAGE", "IN_PROGRESS", 3, "处理中", "执行中", 20));

        assertThat(result.dictionaryItemId()).isEqualTo(101L);
        assertThat(result.typeCode()).isEqualTo("PROJECT_STAGE");
        assertThat(result.value()).isEqualTo("IN_PROGRESS");
        assertThat(result.version()).isEqualTo(4);
        verify(service).updateItemAgent(2L, "PROJECT_STAGE", "IN_PROGRESS",
                new DictionaryItemAgentUpdateRequest(3, "处理中", "执行中", 20));
    }

    @Test
    void mapsImmutableTypeCodeAndVersionForStatusChange() {
        var updatedAt = LocalDateTime.of(2026, 10, 2, 2, 45);
        when(service.updateTypeStatusAgent(2L, "PROJECT_STAGE",
                new DictionaryStatusRequest("DISABLED", 4)))
                .thenReturn(new DictionaryTypeResponse(91L, "PROJECT_STAGE", "项目阶段", null,
                        "DISABLED", 20, 2, 0, 5, updatedAt, true));

        var result = adapter.updateTypeStatus(actor, new DictionaryToolPort.UpdateTypeStatusCommand(
                "PROJECT_STAGE", 4, "DISABLED"));

        assertThat(result.dictionaryTypeId()).isEqualTo(91L);
        assertThat(result.status()).isEqualTo("DISABLED");
        assertThat(result.version()).isEqualTo(5);
        verify(service).updateTypeStatusAgent(2L, "PROJECT_STAGE",
                new DictionaryStatusRequest("DISABLED", 4));
    }

    @Test
    void mapsImmutableItemCoordinatesAndVersionForStatusChange() {
        var updatedAt = LocalDateTime.of(2026, 10, 2, 3, 5);
        when(service.updateItemStatusAgent(2L, "PROJECT_STAGE", "IN_PROGRESS",
                new DictionaryStatusRequest("DISABLED", 4)))
                .thenReturn(new DictionaryItemResponse(101L, 91L, "IN_PROGRESS", "进行中", null,
                        "DISABLED", 20, 2L, 5, updatedAt, true, false));

        var result = adapter.updateItemStatus(actor, new DictionaryToolPort.UpdateItemStatusCommand(
                "PROJECT_STAGE", "IN_PROGRESS", 4, "DISABLED"));

        assertThat(result.dictionaryItemId()).isEqualTo(101L);
        assertThat(result.typeCode()).isEqualTo("PROJECT_STAGE");
        assertThat(result.value()).isEqualTo("IN_PROGRESS");
        assertThat(result.status()).isEqualTo("DISABLED");
        assertThat(result.version()).isEqualTo(5);
        verify(service).updateItemStatusAgent(2L, "PROJECT_STAGE", "IN_PROGRESS",
                new DictionaryStatusRequest("DISABLED", 4));
    }
}
