package com.aiworkmate.service.impl;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.aiworkmate.config.AuthProperties;
import com.aiworkmate.dto.CodeScene;
import com.aiworkmate.service.MailDeliveryService;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Slf4j
@Service
@RequiredArgsConstructor
public class MailDeliveryServiceImpl implements MailDeliveryService {

    private final JavaMailSender mailSender;
    private final AuthProperties properties;

    @Override
    public void sendVerificationCode(String email, CodeScene scene, String code, long ttlSeconds) {
        if (properties.getMailFromEmail().isBlank()) {
            throw new BusinessException(ErrorCode.AUTH_SERVICE_UNAVAILABLE, "SMTP 发件人尚未配置");
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(new InternetAddress(properties.getMailFromEmail(), properties.getMailFromName(), StandardCharsets.UTF_8.name()));
            helper.setTo(email);
            helper.setSubject("AI WorkMate 邮箱验证码");
            helper.setText(buildContent(scene, code, ttlSeconds), false);
            mailSender.send(message);
            log.info("Verification email sent, scene={}, emailDomain={}", scene.value(), domainOf(email));
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Verification email delivery failed, scene={}, emailDomain={}", scene.value(), domainOf(email), ex);
            throw new BusinessException(ErrorCode.AUTH_SERVICE_UNAVAILABLE, "验证码邮件发送失败，请稍后重试");
        }
    }

    private String buildContent(CodeScene scene, String code, long ttlSeconds) {
        String action = switch (scene) {
            case REGISTER -> "注册企业账号";
            case LOGIN -> "登录企业工作台";
            case RESET_PASSWORD -> "重置登录密码";
        };
        return "您正在" + action + "，验证码为：" + code + "。验证码将在 "
                + Math.max(1, ttlSeconds / 60) + " 分钟后失效，请勿向他人泄露。";
    }

    private String domainOf(String email) {
        int index = email.indexOf('@');
        return index < 0 ? "invalid" : email.substring(index + 1);
    }
}
