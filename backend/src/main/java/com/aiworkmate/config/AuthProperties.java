package com.aiworkmate.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.auth")
public class AuthProperties {

    private String cookieName = "oa_session";
    private long sessionTtl = 86400;
    private long emailCodeTtl = 300;
    private long emailCodeCooldown = 60;
    private int emailCodeHourlyLimit = 5;
    private int emailCodeDailyLimit = 10;
    private int emailCodeMaxAttempts = 5;
    private long captchaTtl = 180;
    private int loginMaxFailures = 5;
    private long loginLockSeconds = 900;
    private boolean cookieSecure;
    private String mailFromName = "AI WorkMate";
    private String mailFromEmail = "";
}
