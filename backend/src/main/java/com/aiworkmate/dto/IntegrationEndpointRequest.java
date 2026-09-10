package com.aiworkmate.dto;

import jakarta.validation.constraints.*;

public record IntegrationEndpointRequest(
        @NotBlank(message="{validation.integration.code.required}")
        @Pattern(regexp="^[A-Za-z0-9][A-Za-z0-9_-]{1,63}$",message="{validation.integration.code.invalid}") String code,
        @NotBlank(message="{validation.integration.name.required}") @Size(max=160) String name,
        @NotBlank(message="{validation.integration.upstream.required}") @Pattern(regexp="^[a-z][a-z0-9-]{1,39}$") String upstreamCode,
        @NotBlank @Pattern(regexp="GET|POST|PUT|PATCH|DELETE") String method,
        @NotBlank(message="{validation.integration.path.required}") @Size(max=500) String relativePath,
        @Size(max=16000) String requestTemplate,
        @Size(max=2000) String description,
        Integer version) {}
