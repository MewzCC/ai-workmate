package com.aiworkmate.dto;

/** Internal single-field patch; null properties are omitted, never arbitrary JSON paths. */
public record ApprovalFormFieldPatchRequest(Integer version, String fieldName, String label,
                                            Boolean required, String placeholder, String width) { }
