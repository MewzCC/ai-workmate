package com.aiworkmate.security;

import com.aiworkmate.config.AuthProperties;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class AuthCookieManager {

    private final AuthProperties properties;

    public void write(HttpServletResponse response, String token, boolean remember) {
        ResponseCookie.ResponseCookieBuilder builder = baseCookie(token);
        if (remember) {
            builder.maxAge(Duration.ofSeconds(properties.getSessionTtl()));
        }
        response.addHeader(HttpHeaders.SET_COOKIE, builder.build().toString());
    }

    public void clear(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE,
                baseCookie("").maxAge(Duration.ZERO).build().toString());
    }

    private ResponseCookie.ResponseCookieBuilder baseCookie(String value) {
        return ResponseCookie.from(properties.getCookieName(), value)
                .httpOnly(true)
                .secure(properties.isCookieSecure())
                .sameSite("Strict")
                .path("/");
    }
}
