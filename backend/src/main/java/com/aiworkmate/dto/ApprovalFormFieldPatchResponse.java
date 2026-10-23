package com.aiworkmate.dto;

/** Actual single-field values only; excludes schema, creator identities and unrelated content. */
public record ApprovalFormFieldPatchResponse(long formId, String formKey, String status, int version,
                                             String fieldName, String label, boolean required,
                                             String placeholder, String width) { }
