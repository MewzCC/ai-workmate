package com.aiworkmate.service.model;

/** Committed domain decision, independent of Agent transport and resource list DTOs. */
public record WorkflowDecisionReceipt(long resourceId, String resourceType, String resourceStatus, int resourceVersion,
                                      long taskId, String status, int version, String action) { }
