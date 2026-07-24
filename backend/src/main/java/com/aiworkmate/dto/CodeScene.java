package com.aiworkmate.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum CodeScene {
    REGISTER("register"),
    LOGIN("login"),
    RESET_PASSWORD("reset_password");

    private final String value;

    CodeScene(String value) {
        this.value = value;
    }

    @JsonCreator
    public static CodeScene fromValue(String value) {
        for (CodeScene scene : values()) {
            if (scene.value.equalsIgnoreCase(value)) {
                return scene;
            }
        }
        throw new IllegalArgumentException("不支持的验证码场景");
    }

    @JsonValue
    public String value() {
        return value;
    }
}
