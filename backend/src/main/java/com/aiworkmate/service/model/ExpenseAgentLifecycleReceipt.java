package com.aiworkmate.service.model;

/** Bounded receipt for one expense approval lifecycle transition. */
public record ExpenseAgentLifecycleReceipt(
        long applicationId, String formKey, String status, int version) { }
