package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.ContractToolPort;
import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.optionalDecimal;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.optionalText;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.requiredDate;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.requiredInt;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.requiredLong;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.requiredText;

@Component
public final class ContractRecordPaymentToolHandler extends TypedWriteToolHandler<
        ContractToolPort.ContractPayment, ContractToolPort.ContractPaymentResult> {
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("9999999999999999.99");
    private final ContractToolPort port;

    public ContractRecordPaymentToolHandler(ContractToolPort port, ObjectMapper mapper) {
        super(ToolCode.CONTRACT_RECORD_PAYMENT, mapper);
        this.port = port;
    }

    @Override protected ContractToolPort.ContractPayment parseArguments(JsonNode arguments) {
        BigDecimal amount = optionalDecimal(arguments, "amount", new BigDecimal("0.01"), MAX_AMOUNT, 2);
        String reference = requiredText(arguments, "reference");
        String note = optionalText(arguments, "note");
        if (amount == null || reference.length() > 100 || note != null && note.length() > 500) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID);
        }
        return new ContractToolPort.ContractPayment(
                requiredLong(arguments, "contractId", 1),
                requiredInt(arguments, "version", 0, Integer.MAX_VALUE - 1), amount,
                requiredDate(arguments, "paymentDate"), reference, note);
    }

    @Override protected ContractToolPort.ContractPaymentResult invoke(
            TrustedToolContext context, ContractToolPort.ContractPayment command) {
        return port.recordContractPayment(context.actor(), command);
    }
}
