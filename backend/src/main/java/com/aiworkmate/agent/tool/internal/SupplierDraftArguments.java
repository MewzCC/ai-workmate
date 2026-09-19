package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.tool.port.FinanceToolPort;
import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Set;
import java.util.regex.Pattern;

import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.*;

final class SupplierDraftArguments {
    private static final Pattern CODE = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9_-]{1,63}$");
    private static final Set<String> CATEGORIES = Set.of("MATERIAL", "SERVICE", "LOGISTICS", "CONSULTING", "OTHER");
    private static final Set<String> LEVELS = Set.of("STRATEGIC", "PREFERRED", "STANDARD", "RESTRICTED");

    private SupplierDraftArguments() { }

    static FinanceToolPort.SupplierDraft parse(JsonNode arguments) {
        String code = requiredText(arguments, "code");
        String name = requiredText(arguments, "name");
        String shortName = optionalText(arguments, "shortName");
        String category = requiredText(arguments, "category");
        String level = requiredText(arguments, "supplierLevel");
        String paymentTerms = optionalText(arguments, "paymentTerms");
        if (!CODE.matcher(code).matches() || name.length() > 160
                || shortName != null && shortName.length() > 80
                || !CATEGORIES.contains(category) || !LEVELS.contains(level)
                || paymentTerms != null && paymentTerms.length() > 120) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID);
        }
        return new FinanceToolPort.SupplierDraft(code, name, shortName, category, level, paymentTerms);
    }
}
