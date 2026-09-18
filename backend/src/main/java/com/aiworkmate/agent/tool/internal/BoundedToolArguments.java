package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Shared fail-closed parser for the fixed, schema-validated Agent argument surface. */
final class BoundedToolArguments {
    private BoundedToolArguments() { }

    static int pageNumber(JsonNode arguments) {
        return pageNumber(arguments, 10000);
    }

    /** Legacy contracts without a page maximum retain their frozen schema semantics. */
    static int pageNumber(JsonNode arguments, int maximum) {
        return positiveInt(arguments, "page", 1, maximum);
    }

    static int pageSize(JsonNode arguments) {
        return positiveInt(arguments, "size", 20, 50);
    }

    static String optionalText(JsonNode arguments, String field) {
        JsonNode value = arguments.get(field);
        if (value == null || value.isNull()) return null;
        if (!value.isTextual()) throw invalid();
        String text = value.asText().strip();
        return text.isEmpty() ? null : text;
    }

    static String requiredText(JsonNode arguments, String field) {
        String value = optionalText(arguments, field);
        if (value == null) throw invalid();
        return value;
    }

    static int positiveInt(JsonNode arguments, String field, int fallback, int maximum) {
        JsonNode value = arguments.get(field);
        if (value == null || value.isNull()) return fallback;
        if (!value.isIntegralNumber() || !value.canConvertToInt() || value.asInt() < 1) throw invalid();
        return Math.min(maximum, value.asInt());
    }

    static int requiredInt(JsonNode arguments, String field, int minimum, int maximum) {
        JsonNode value = arguments.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToInt()
                || value.asInt() < minimum || value.asInt() > maximum) throw invalid();
        return value.asInt();
    }

    static Long optionalPositiveLong(JsonNode arguments, String field) {
        JsonNode value = arguments.get(field);
        if (value == null || value.isNull()) return null;
        if (!value.isIntegralNumber() || !value.canConvertToLong() || value.asLong() < 1) throw invalid();
        return value.asLong();
    }

    static long requiredLong(JsonNode arguments, String field, long minimum) {
        JsonNode value = arguments.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong()
                || value.asLong() < minimum) throw invalid();
        return value.asLong();
    }

    static LocalDate requiredDate(JsonNode arguments, String field) {
        try {
            return LocalDate.parse(requiredText(arguments, field));
        } catch (DateTimeException exception) {
            throw invalid();
        }
    }

    static LocalDate optionalDate(JsonNode arguments, String field) {
        String value = optionalText(arguments, field);
        if (value == null) return null;
        try {
            return LocalDate.parse(value);
        } catch (DateTimeException exception) {
            throw invalid();
        }
    }

    static Integer optionalInt(JsonNode arguments, String field, int minimum, int maximum) {
        JsonNode value = arguments.get(field);
        if (value == null || value.isNull()) return null;
        if (!value.isIntegralNumber() || !value.canConvertToInt()
                || value.asInt() < minimum || value.asInt() > maximum) throw invalid();
        return value.asInt();
    }

    static Boolean optionalBoolean(JsonNode arguments, String field) {
        JsonNode value = arguments.get(field);
        if (value == null || value.isNull()) return null;
        if (!value.isBoolean()) throw invalid();
        return value.booleanValue();
    }

    static LocalDateTime optionalDateTime(JsonNode arguments, String field) {
        String value = optionalText(arguments, field);
        if (value == null) return null;
        try {
            return LocalDateTime.parse(value);
        } catch (DateTimeException exception) {
            throw invalid();
        }
    }

    static BigDecimal optionalDecimal(
            JsonNode arguments, String field, BigDecimal minimum,
            BigDecimal maximum, int maximumScale) {
        JsonNode value = arguments.get(field);
        if (value == null || value.isNull()) return null;
        if (!value.isNumber()) throw invalid();
        BigDecimal decimal = value.decimalValue();
        if (decimal.stripTrailingZeros().scale() > maximumScale
                || decimal.compareTo(minimum) < 0 || decimal.compareTo(maximum) > 0) {
            throw invalid();
        }
        return decimal;
    }

    static LocalDateTime requiredDateTime(JsonNode arguments, String field) {
        LocalDateTime value = optionalDateTime(arguments, field);
        if (value == null) throw invalid();
        return value;
    }

    static <E extends Enum<E>> E requiredEnum(JsonNode arguments, String field, Class<E> type) {
        try {
            return Enum.valueOf(type, requiredText(arguments, field));
        } catch (IllegalArgumentException exception) {
            throw invalid();
        }
    }

    private static BusinessException invalid() {
        return new BusinessException(ErrorCode.REQUEST_INVALID);
    }
}
