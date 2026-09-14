package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.tool.port.ApprovalConfigurationToolPort;
import com.aiworkmate.common.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BoundedToolArgumentsTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void parsesOnlyFixedScalarTypesAndAppliesDefensiveBounds() throws Exception {
        var arguments = mapper.readTree("""
                {"text":" value ","page":2,"size":500,"id":9,
                 "date":"2026-09-12","time":"2026-09-12T09:30:00","resource":"FORM"}
                """);

        assertThat(BoundedToolArguments.optionalText(arguments, "text")).isEqualTo("value");
        assertThat(BoundedToolArguments.positiveInt(arguments, "page", 1, 100)).isEqualTo(2);
        assertThat(BoundedToolArguments.positiveInt(arguments, "size", 20, 50)).isEqualTo(50);
        assertThat(BoundedToolArguments.optionalPositiveLong(arguments, "id")).isEqualTo(9L);
        assertThat(BoundedToolArguments.requiredDate(arguments, "date")).isEqualTo(LocalDate.of(2026, 9, 12));
        assertThat(BoundedToolArguments.optionalDateTime(arguments, "time"))
                .isEqualTo(LocalDateTime.of(2026, 9, 12, 9, 30));
        assertThat(BoundedToolArguments.requiredEnum(arguments, "resource",
                ApprovalConfigurationToolPort.Resource.class))
                .isEqualTo(ApprovalConfigurationToolPort.Resource.FORM);
    }

    @Test
    void rejectsTypeCoercionMalformedDatesAndUnknownEnums() throws Exception {
        assertThatThrownBy(() -> BoundedToolArguments.optionalText(
                mapper.readTree("{\"value\":1}"), "value")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> BoundedToolArguments.positiveInt(
                mapper.readTree("{\"page\":\"1\"}"), "page", 1, 50)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> BoundedToolArguments.requiredDate(
                mapper.readTree("{\"date\":\"invalid\"}"), "date")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> BoundedToolArguments.requiredEnum(
                mapper.readTree("{\"resource\":\"UNKNOWN\"}"), "resource",
                ApprovalConfigurationToolPort.Resource.class)).isInstanceOf(BusinessException.class);
    }
}
