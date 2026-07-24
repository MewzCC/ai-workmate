package com.aiworkmate.service;

public interface LoginProtectionService {

    void assertLoginAllowed(String account, String clientIp);

    boolean isCaptchaRequired(String account, String clientIp);

    void recordFailure(String account, String clientIp);

    void clearFailures(String account, String clientIp);

    void claimRegistration(String requestId);
}
