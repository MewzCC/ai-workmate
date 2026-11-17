package com.aiworkmate.service.model;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;

/** Shared pagination only; the domain and resource are selected by fixed service methods. */
public record DocumentMetadataQuery(int page, int size) {
    public DocumentMetadataQuery {
        if (page < 1 || page > 10000 || size < 1 || size > 20) throw new BusinessException(ErrorCode.REQUEST_INVALID);
    }

    public void requireResource(Long resourceId) {
        if (resourceId == null || resourceId < 1) throw new BusinessException(ErrorCode.REQUEST_INVALID);
    }

    public long offset() { return (long) (page - 1) * size; }
}
