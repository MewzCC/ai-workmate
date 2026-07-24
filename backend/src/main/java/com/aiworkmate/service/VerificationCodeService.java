package com.aiworkmate.service;

import com.aiworkmate.dto.CaptchaResponse;
import com.aiworkmate.dto.CodeScene;
import com.aiworkmate.dto.EmailCodeRequest;

public interface VerificationCodeService {

    CaptchaResponse createImageCaptcha();

    void sendEmailCode(EmailCodeRequest request, String clientIp);

    void verifyEmailCode(CodeScene scene, String email, String code);

    void verifyImageCaptcha(String captchaId, String code);
}
