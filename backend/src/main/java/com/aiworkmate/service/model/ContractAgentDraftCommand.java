package com.aiworkmate.service.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Transport-neutral editable fields accepted by the controlled Agent contract draft boundary. */
public record ContractAgentDraftCommand(
        String name, String contractType, String counterpartyName, Long supplierId,
        long ownerUserId, BigDecimal amount, String currency, LocalDate signedDate,
        LocalDate startDate, LocalDate endDate, String summary
) { }
