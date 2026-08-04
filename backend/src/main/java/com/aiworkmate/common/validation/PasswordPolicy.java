package com.aiworkmate.common.validation;

public final class PasswordPolicy {

    public static final String PASSWORD_REGEX = "^(?=.*[A-Za-z])(?=.*\\d)\\S{8,32}$";
    public static final String PASSWORD_MESSAGE = "{validation.password.policy}";

    private PasswordPolicy() {
    }
}
