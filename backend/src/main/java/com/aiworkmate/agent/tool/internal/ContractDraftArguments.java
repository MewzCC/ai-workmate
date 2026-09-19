package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.tool.port.FinanceToolPort;
import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import java.util.regex.Pattern;

import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.*;

final class ContractDraftArguments {
    private static final Pattern CODE = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9_-]{1,63}$");
    private static final Set<String> TYPES = Set.of("PURCHASE", "SALES", "SERVICE", "LEASE", "OTHER");
    private static final Set<String> CURRENCIES = Set.of("CNY", "USD", "EUR", "HKD");
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("9999999999999999.99");

    private ContractDraftArguments() { }

    static FinanceToolPort.ContractDraft parse(JsonNode arguments) {
        String code = requiredText(arguments, "code");
        String name = requiredText(arguments, "name");
        String type = requiredText(arguments, "contractType");
        String counterparty = requiredText(arguments, "counterpartyName");
        String currency = requiredText(arguments, "currency");
        String summary = optionalText(arguments, "summary");
        BigDecimal amount = optionalDecimal(arguments, "amount", new BigDecimal("0.01"), MAX_AMOUNT, 2);
        LocalDate signedDate = optionalDate(arguments, "signedDate");
        LocalDate startDate = requiredDate(arguments, "startDate");
        LocalDate endDate = requiredDate(arguments, "endDate");
        if (!CODE.matcher(code).matches() || name.length() > 160 || counterparty.length() > 160
                || !TYPES.contains(type) || !CURRENCIES.contains(currency) || amount == null
                || summary != null && summary.length() > 2000 || endDate.isBefore(startDate)
                || signedDate != null && signedDate.isAfter(startDate)) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID);
        }
        return new FinanceToolPort.ContractDraft(
                code, name, type, counterparty, optionalPositiveLong(arguments, "supplierId"),
                requiredLong(arguments, "ownerUserId", 1), amount, currency,
                signedDate, startDate, endDate, summary);
    }
}
