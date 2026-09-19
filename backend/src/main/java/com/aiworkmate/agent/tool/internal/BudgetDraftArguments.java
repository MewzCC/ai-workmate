package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.tool.port.FinanceToolPort;
import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.Set;
import java.util.regex.Pattern;

import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.*;

final class BudgetDraftArguments {
    private static final Pattern CODE = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9_-]{1,63}$");
    private static final Set<String> CURRENCIES = Set.of("CNY", "USD", "EUR", "HKD");
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("9999999999999999.99");

    private BudgetDraftArguments() { }

    static FinanceToolPort.BudgetDraft parse(JsonNode arguments) {
        String code = requiredText(arguments, "code");
        Fields fields = parseFields(arguments);
        if (!CODE.matcher(code).matches()) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID);
        }
        return new FinanceToolPort.BudgetDraft(
                code, fields.name(), fields.fiscalYear(), fields.ownerUserId(), fields.totalAmount(),
                fields.currency(), fields.warningThreshold(), fields.summary());
    }

    static FinanceToolPort.BudgetDraftUpdate parseUpdate(JsonNode arguments) {
        Fields fields = parseFields(arguments);
        return new FinanceToolPort.BudgetDraftUpdate(
                requiredLong(arguments, "budgetId", 1),
                requiredInt(arguments, "version", 0, Integer.MAX_VALUE - 1),
                fields.name(), fields.fiscalYear(), fields.ownerUserId(), fields.totalAmount(),
                fields.currency(), fields.warningThreshold(), fields.summary());
    }

    private static Fields parseFields(JsonNode arguments) {
        String name = requiredText(arguments, "name");
        String currency = requiredText(arguments, "currency");
        String summary = optionalText(arguments, "summary");
        BigDecimal amount = optionalDecimal(
                arguments, "totalAmount", new BigDecimal("0.01"), MAX_AMOUNT, 2);
        if (name.length() > 160 || amount == null
                || !CURRENCIES.contains(currency) || summary != null && summary.length() > 2000) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID);
        }
        return new Fields(
                name, requiredInt(arguments, "fiscalYear", 2000, 2200),
                requiredLong(arguments, "ownerUserId", 1), amount, currency,
                requiredInt(arguments, "warningThreshold", 1, 100), summary);
    }

    private record Fields(String name, int fiscalYear, long ownerUserId, BigDecimal totalAmount,
                          String currency, int warningThreshold, String summary) { }
}
