package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.tool.port.NotificationToolPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationMarkReadToolHandlerTest {
    private final NotificationToolPort port = mock(NotificationToolPort.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final NotificationMarkReadToolHandler handler = new NotificationMarkReadToolHandler(port, mapper);

    @Test
    void forwardsOnlyTheResourceIdWithTrustedActorIdentity() throws Exception {
        var context = new TrustedToolContext(99L, 7L, 11L, 12L, 1, "trace");
        when(port.markRead(context.actor(), 9L)).thenReturn(new NotificationToolPort.ReadResult(9L, true));

        var output = handler.execute(context, mapper.readTree("{\"notificationId\":9}"));

        assertThat(output.path("notificationId").asLong()).isEqualTo(9L);
        assertThat(output.path("read").asBoolean()).isTrue();
        assertThat(output.has("userId")).isFalse();
        assertThat(output.has("tenantId")).isFalse();
        verify(port).markRead(context.actor(), 9L);
    }
}
