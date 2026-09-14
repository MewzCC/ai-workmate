package com.aiworkmate.service;

import com.aiworkmate.dto.SupplierDetailResponse;
import com.aiworkmate.dto.SupplierPageResponse;
import com.aiworkmate.dto.SupplierRequest;
import com.aiworkmate.dto.SupplierResponse;
import com.aiworkmate.dto.SupplierStatusRequest;

public interface SupplierService {
    SupplierPageResponse list(Long userId, String keyword, String status, String category, int page, int size);

    SupplierDetailResponse detail(Long userId, Long id);

    SupplierResponse create(Long userId, SupplierRequest request);

    SupplierResponse update(Long userId, Long id, SupplierRequest request);

    SupplierResponse updateStatus(Long userId, Long id, SupplierStatusRequest request);
}
