package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.tool.port.UserSettingsToolPort;
import com.aiworkmate.common.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserSettingsUpdateToolHandlerTest {
    @Test
    void updatesOnlyTheGatewayAuthenticatedUsersPreferences() throws Exception {
        UserSettingsToolPort port = mock(UserSettingsToolPort.class);
        ObjectMapper mapper = new ObjectMapper();
        var context = new TrustedToolContext(1L, 7L, 10L, 30L, 1, "trace");
        var command = new UserSettingsToolPort.UpdateCommand(
                "deepseek-v4-pro", 12, false, true);
        var result = new UserSettingsToolPort.UpdateResult(
                "deepseek-v4-pro", 12, false, true);
        when(port.update(context.actor(), command)).thenReturn(result);

        var output = new UserSettingsUpdateToolHandler(port, mapper).execute(context,
                mapper.readTree("""
                        {"model":"deepseek-v4-pro","maxContextRounds":12,"stream":false,"forcePdfOcr":true}
                        """));

        assertThat(output.path("forcePdfOcr").asBoolean()).isTrue();
        verify(port).update(context.actor(), command);
    }

    @Test
    void rejectsUnsupportedModelBeforeCallingTheDomain() throws Exception {
        UserSettingsToolPort port = mock(UserSettingsToolPort.class);
        ObjectMapper mapper = new ObjectMapper();

        assertThatThrownBy(() -> new UserSettingsUpdateToolHandler(port, mapper).execute(
                new TrustedToolContext(1L, 7L, 10L, 30L, 1, "trace"), mapper.readTree("""
                        {"model":"unknown","maxContextRounds":12,"stream":false,"forcePdfOcr":true}
                        """))).isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo("REQUEST_INVALID");
    }
}
