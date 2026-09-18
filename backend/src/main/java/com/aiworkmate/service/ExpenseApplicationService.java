package com.aiworkmate.service;

import com.aiworkmate.service.model.ExpenseAgentDraftCommand;
import com.aiworkmate.service.model.ExpenseAgentDraftReceipt;

import java.util.Optional;

/** Expense-specific application boundary backed by the generic approval domain. */
public interface ExpenseApplicationService {
    ExpenseAgentDraftReceipt createAgentDraft(
            Long userId, ExpenseAgentDraftCommand command, String operationKey);

    Optional<ExpenseAgentDraftReceipt> findAgentDraft(
            Long userId, ExpenseAgentDraftCommand command, String operationKey);
}
