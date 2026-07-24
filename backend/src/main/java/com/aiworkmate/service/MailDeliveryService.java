package com.aiworkmate.service;

import com.aiworkmate.dto.CodeScene;

public interface MailDeliveryService {

    void sendVerificationCode(String email, CodeScene scene, String code, long ttlSeconds);
}
