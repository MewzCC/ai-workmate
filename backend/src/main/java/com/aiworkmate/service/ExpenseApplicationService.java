package com.aiworkmate.service;

import com.aiworkmate.service.model.ExpenseAgentDraftCommand;
import com.aiworkmate.service.model.ExpenseAgentDraftReceipt;
import com.aiworkmate.service.model.ExpenseAgentLifecycleReceipt;

import java.util.Optional;

/** Expense-specific application boundary backed by the generic approval domain. */
public interface ExpenseApplicationService {
    ExpenseAgentDraftReceipt createAgentDraft(
            Long userId, ExpenseAgentDraftCommand command, String operationKey);

    Optional<ExpenseAgentDraftReceipt> findAgentDraft(
            Long userId, ExpenseAgentDraftCommand command, String operationKey);

    ExpenseAgentLifecycleReceipt updateAgentDraft(
            Long userId, Long applicationId, int version, ExpenseAgentDraftCommand patch);

    ExpenseAgentLifecycleReceipt submitAgentDraft(Long userId, Long applicationId, int version);

    ExpenseAgentLifecycleReceipt withdrawAgentApplication(Long userId, Long applicationId, int version);

    ExpenseAgentLifecycleReceipt reopenAgentApplication(Long userId, Long applicationId, int version);
}
