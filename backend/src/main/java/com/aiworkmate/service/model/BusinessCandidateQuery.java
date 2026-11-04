package com.aiworkmate.service.model;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;

/** Bounded search input shared by domain-owned candidate queries; contains no authorization data. */
public record BusinessCandidateQuery(String keyword, int page, int size) {
    public BusinessCandidateQuery {
        if (page < 1 || page > 10000 || size < 1 || size > 100 || keyword != null && keyword.length() > 120) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID);
        }
        keyword = keyword == null || keyword.isBlank() ? null : keyword.trim();
    }

    public long offset() { return (page - 1L) * size; }
}
