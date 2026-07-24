package com.aiworkmate.dto;

public record CaptchaResponse(String captchaId, String image, long expiresIn) {
}
