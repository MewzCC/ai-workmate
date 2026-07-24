package com.aiworkmate.common.validation;

public final class PasswordPolicy {

    public static final String PASSWORD_REGEX = "^(?=.*[A-Za-z])(?=.*\\d)\\S{8,32}$";
    public static final String PASSWORD_MESSAGE = "密码须为 8-32 位，并至少包含字母和数字";

    private PasswordPolicy() {
    }
}
