package com.aiworkmate.controller;

import com.aiworkmate.common.Result;
import com.aiworkmate.dto.CaptchaResponse;
import com.aiworkmate.dto.EmailCodeRequest;
import com.aiworkmate.service.VerificationCodeService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class CaptchaController {
    private final VerificationCodeService verificationCodeService;

    @GetMapping("/captcha")
    public Result<CaptchaResponse> captcha() {
        return Result.ok(verificationCodeService.createImageCaptcha());
    }

    @PostMapping("/email-code/send")
    public Result<Void> sendEmailCode(@Valid @RequestBody EmailCodeRequest request,
                                      HttpServletRequest servletRequest) {
        verificationCodeService.sendEmailCode(request, clientIp(servletRequest));
        return Result.ok();
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        return forwarded == null || forwarded.isBlank()
                ? request.getRemoteAddr() : forwarded.split(",")[0].trim();
    }
}
