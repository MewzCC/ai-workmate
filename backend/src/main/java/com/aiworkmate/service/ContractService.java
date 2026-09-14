package com.aiworkmate.service;

import com.aiworkmate.dto.ContractDetailResponse;
import com.aiworkmate.dto.ContractFulfillmentRequest;
import com.aiworkmate.dto.ContractOptionsResponse;
import com.aiworkmate.dto.ContractPageResponse;
import com.aiworkmate.dto.ContractPaymentRequest;
import com.aiworkmate.dto.ContractReminderRequest;
import com.aiworkmate.dto.ContractRequest;
import com.aiworkmate.dto.ContractResponse;
import com.aiworkmate.dto.ContractStatusRequest;

public interface ContractService {
    ContractPageResponse list(Long userId, String keyword, String status, String contractType,
                              String expiryState, int page, int size);
    ContractDetailResponse detail(Long userId, Long id);
    ContractOptionsResponse options(Long userId);
    ContractResponse create(Long userId, ContractRequest request);
    ContractResponse update(Long userId, Long id, ContractRequest request);
    ContractResponse updateStatus(Long userId, Long id, ContractStatusRequest request);
    ContractResponse updateFulfillment(Long userId, Long id, ContractFulfillmentRequest request);
    ContractResponse recordPayment(Long userId, Long id, ContractPaymentRequest request);
    ContractResponse remind(Long userId, Long id, ContractReminderRequest request);
}
