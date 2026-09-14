package com.aiworkmate.dto;

import java.util.List;

public record BudgetOptionsResponse(List<Option> owners) {
    public record Option(Long id, String label, String detail) {}
}
