package com.aiworkmate.dto;

import java.time.LocalDateTime;

/** Immutable submitted payload and resolved plan per workflow attempt; only progress fields advance. */
public record ApprovalExecutionSnapshot(Long instanceId, Long tenantId, Long applicationId, Long applicantUserId,
        String formKey, String formName, String title, String dataJson, String formSchema, Integer formVersion,
        String processNodes, Integer processVersion, String rules, String planJson, int currentNodeIndex,
        int version, LocalDateTime submittedAt) { }
