package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.tool.port.DocumentMetadataToolModels;
import com.aiworkmate.agent.tool.port.HrEmployeeToolPort;
import com.aiworkmate.agent.tool.port.SealToolPort;
import com.aiworkmate.agent.tool.port.ToolPage;
import com.aiworkmate.common.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DocumentMetadataToolHandlersTest {
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    private final TrustedToolContext context = new TrustedToolContext(9L, 7L, 11L, 13L, 1, "metadata-test");
    private final HrEmployeeToolPort employees = mock(HrEmployeeToolPort.class);
    private final SealToolPort seals = mock(SealToolPort.class);

    @Test void sendsOnlyTrustedActorAndTheFixedResourceToItsOwnPort() throws Exception {
        var query = new DocumentMetadataToolModels.Query(17, 2, 3);
        var item = new DocumentMetadataToolModels.Item("9007199254740993", "PROFILE", "profile.txt", "text/plain", 12,
                LocalDateTime.of(2026, 11, 13, 12, 0));
        when(employees.documents(context.actor(), query)).thenReturn(new ToolPage<>(List.of(item), 4, 2, 3));
        var result = new HrEmployeeDocumentsQueryToolHandler(employees, json).execute(context,
                json.readTree("{\"employeeId\":17,\"page\":2,\"size\":3}"));
        assertThat(result.at("/items/0/documentId").asText()).isEqualTo("9007199254740993");
        assertThat(result.toString()).doesNotContain("storageKey", "contentUrl", "uploadedBy", "tenantId");
        verify(employees).documents(context.actor(), query);
        verifyNoInteractions(seals);
        when(seals.documents(context.actor(), new DocumentMetadataToolModels.Query(19, 1, 20)))
                .thenReturn(new ToolPage<>(List.of(), 0, 1, 20));
        assertThat(new SealDocumentsQueryToolHandler(seals, json).execute(context, json.readTree("{\"usageId\":19}"))
                .path("items")).isEmpty();
    }

    @Test void rejectsInvalidResourcesAndPagingBeforeCallingEitherDomain() throws Exception {
        var handler = new HrEmployeeDocumentsQueryToolHandler(employees, json);
        for (String arguments : List.of("{}", "{\"employeeId\":0}", "{\"employeeId\":1,\"size\":21}",
                "{\"employeeId\":1,\"page\":0}", "{\"employeeId\":1,\"page\":10001}", "{\"employeeId\":1.5}")) {
            assertThatThrownBy(() -> handler.execute(context, json.readTree(arguments))).isInstanceOf(BusinessException.class);
        }
        verifyNoInteractions(employees, seals);
    }
}
