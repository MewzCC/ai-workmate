package com.aiworkmate.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * 全局异常处理器。
 *
 * <p>所有响应文案均通过 {@link MessageSource} 按 {@code Accept-Language} 解析，
 * 禁止在 controller/service 直接拼中文。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private final MessageSource messageSource;

    public GlobalExceptionHandler(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleValidation(MethodArgumentNotValidException ex) {
        Locale locale = LocaleContextHolder.getLocale();
        List<FieldError> fieldErrors = ex.getBindingResult().getFieldErrors();
        String msg = fieldErrors.stream()
                .map(error -> {
                    String code = error.getDefaultMessage();
                    if (code == null || code.isBlank()) return "";
                    // defaultMessage 现在是 i18n key，走 MessageSource 解析
                    return messageSource.getMessage(code, null, code, locale);
                })
                .filter(s -> !s.isBlank())
                .collect(Collectors.joining(", "));
        return Result.error(ErrorCode.REQUEST_INVALID, msg);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleIllegalArgument(IllegalArgumentException ex) {
        return Result.error(ErrorCode.REQUEST_INVALID, ex.getMessage());
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusiness(BusinessException ex) {
        // BusinessException.getMessage() 已按当前 locale 解析
        String message = ex.getMessage();
        Result<Void> result = ex.getErrorCode() == null
                ? Result.error(ex.getCode(), message)
                : new Result<>(ex.getCode(), ex.getErrorCode(), message, null);
        return ResponseEntity.status(ex.getStatus()).body(result);
    }

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Result<Void> handleAccessDenied(AccessDeniedException ex) {
        return Result.error(ErrorCode.PERMISSION_DENIED);
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleException(Exception ex) {
        log.error("Unexpected error", ex);
        return Result.error(ErrorCode.SYSTEM_ERROR);
    }
}
