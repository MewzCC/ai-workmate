package com.aiworkmate.dto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
public record AiConfigurationRequest(
        @NotBlank @Size(max=512) String baseUrl,
        @NotBlank @Pattern(regexp="[A-Za-z0-9][A-Za-z0-9._:/-]{0,127}", message="{validation.chat.model.invalid}") String model,
        @Size(max=4096) String apiKey,
        @NotNull @Min(0) Long version,
        @Size(min=1,max=200) List<@NotNull @Valid AiConfiguredModel> models) {
    public AiConfigurationRequest(String baseUrl,String model,String apiKey,Long version) { this(baseUrl,model,apiKey,version,null); }
    @Override public String toString() { return "AiConfigurationRequest[redacted]"; }
}
