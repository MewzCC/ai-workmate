package com.aiworkmate.service;

import com.aiworkmate.dto.AiIntentRequest;
import com.aiworkmate.dto.AiIntentResponse;
import com.aiworkmate.security.AuthenticatedUser;

public interface AiIntentService {
    AiIntentResponse classify(AiIntentRequest request, AuthenticatedUser user);
}
