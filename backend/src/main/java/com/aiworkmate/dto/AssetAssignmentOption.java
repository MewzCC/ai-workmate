package com.aiworkmate.dto;

/** Minimum assignment metadata; never exposes employee profiles or contact details. */
public record AssetAssignmentOption(Long id, String name, Long departmentId) {}
