package com.aiworkmate.agent.tool.adapter;

import com.aiworkmate.agent.tool.port.DictionaryToolPort;
import com.aiworkmate.agent.tool.port.ToolActorContext;
import com.aiworkmate.dto.DictionaryTypeListResponse;
import com.aiworkmate.dto.DictionaryTypeResponse;
import com.aiworkmate.dto.DictionaryTypeAgentUpdateRequest;
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
}
