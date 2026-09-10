package com.aiworkmate.service;

import com.aiworkmate.dto.*;

public interface BudgetService {
    BudgetPageResponse list(Long userId, String keyword, String status, Integer fiscalYear, int page, int size);
    BudgetDetailResponse detail(Long userId, Long id);
    BudgetOptionsResponse options(Long userId);
    BudgetResponse create(Long userId, BudgetPlanRequest request);
    BudgetResponse update(Long userId, Long id, BudgetPlanRequest request);
    BudgetResponse updateStatus(Long userId, Long id, BudgetStatusRequest request);
    BudgetResponse operate(Long userId, Long id, BudgetOperationRequest request);
}
