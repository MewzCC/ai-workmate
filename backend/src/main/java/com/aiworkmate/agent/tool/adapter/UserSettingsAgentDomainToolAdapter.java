package com.aiworkmate.agent.tool.adapter;

import com.aiworkmate.agent.tool.port.ToolActorContext;
import com.aiworkmate.agent.tool.port.UserSettingsToolPort;
import com.aiworkmate.dto.ChatPreferencesRequest;
import com.aiworkmate.service.UserSettingsService;
import lombok.RequiredArgsConstructor;

@LocalAgentDomainAdapter
@RequiredArgsConstructor
public final class UserSettingsAgentDomainToolAdapter implements UserSettingsToolPort {
    private final UserSettingsService userSettingsService;

    @Override
    public UpdateResult update(ToolActorContext context, UpdateCommand command) {
        var response = userSettingsService.updateChatPreferencesByAgent(context.userId(),
                new ChatPreferencesRequest(command.model(), command.maxContextRounds(),
                        command.stream(), command.forcePdfOcr()));
        return new UpdateResult(response.model(), response.maxContextRounds(),
                response.stream(), response.forcePdfOcr());
    }
}
