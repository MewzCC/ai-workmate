package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.common.PageResponse;
import com.aiworkmate.dto.NotificationResponse;
import com.aiworkmate.service.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationMineToolHandlerTest {
    private final NotificationService service = mock(NotificationService.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final NotificationMineToolHandler handler = new NotificationMineToolHandler(service, mapper);

    @Test
    void usesTrustedUserCapsSizeAndOmitsInternalBusinessId() throws Exception {
        var response = new NotificationResponse(5L, "approval", "审批提醒", "请及时处理",
                "leave", 999L, false, LocalDateTime.of(2026, 8, 25, 9, 30));
        when(service.list(7L, 2, 50)).thenReturn(PageResponse.of(List.of(response), 1, 2, 50));

        var output = handler.execute(new TrustedToolContext(99L, 7L, 1L, 2L, 1, "trace"),
                mapper.readTree("{\"page\":2,\"size\":500}"));

        assertThat(output.at("/items/0/title").asText()).isEqualTo("审批提醒");
        assertThat(output.at("/items/0/businessType").asText()).isEqualTo("leave");
        assertThat(output.at("/items/0/businessId").isMissingNode()).isTrue();
        assertThat(output.at("/items/0/userId").isMissingNode()).isTrue();
        verify(service).list(7L, 2, 50);
    }

    @Test
    void returnsEmptyListWithoutFabrication() throws Exception {
        when(service.list(7L, 1, 20)).thenReturn(PageResponse.of(List.of(), 0, 1, 20));
        var output = handler.execute(new TrustedToolContext(99L, 7L, 1L, 2L, 1, "trace"),
                mapper.readTree("{}"));
        assertThat(output.path("items")).isEmpty();
        assertThat(output.path("total").asLong()).isZero();
    }
}
