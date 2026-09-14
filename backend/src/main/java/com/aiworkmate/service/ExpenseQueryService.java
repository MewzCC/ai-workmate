package com.aiworkmate.service;

import com.aiworkmate.common.PageResponse;
import com.aiworkmate.dto.ExpenseSummaryResponse;

public interface ExpenseQueryService {
    PageResponse<ExpenseSummaryResponse> mine(Long userId, String status, int page, int size);
    ExpenseSummaryResponse detail(Long userId, Long applicationId);
}
