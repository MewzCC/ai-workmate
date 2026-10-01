package com.aiworkmate.agent.tool.adapter;

import com.aiworkmate.agent.tool.port.ToolActorContext;
import com.aiworkmate.agent.tool.port.UserSettingsToolPort;
import com.aiworkmate.dto.ChatPreferencesRequest;
import com.aiworkmate.dto.ChatPreferencesResponse;
import com.aiworkmate.service.UserSettingsService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserSettingsAgentDomainToolAdapterTest {
    @Test
    void forwardsOnlyTrustedActorAndBoundedPreferenceFields() {
        UserSettingsService service = mock(UserSettingsService.class);
        var adapter = new UserSettingsAgentDomainToolAdapter(service);
        var actor = new ToolActorContext(7L, 1001L, 20L, 30L, 1, "trace");
        var command = new UserSettingsToolPort.UpdateCommand(
                "deepseek-v4-pro", 6, false, true);
        var request = new ChatPreferencesRequest("deepseek-v4-pro", 6, false, true);
        when(service.updateChatPreferencesByAgent(1001L, request)).thenReturn(
                new ChatPreferencesResponse("deepseek-v4-pro", 6, false, true, true));

        var result = adapter.update(actor, command);

        assertThat(result.maxContextRounds()).isEqualTo(6);
        verify(service).updateChatPreferencesByAgent(1001L, request);
    }
}
