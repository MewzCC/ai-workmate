package com.aiworkmate.service;

import com.aiworkmate.common.PageResponse;
import com.aiworkmate.dto.EmployeeChangeDecisionRequest;
import com.aiworkmate.dto.EmployeeChangeRequest;
import com.aiworkmate.dto.EmployeeChangeResponse;
import com.aiworkmate.dto.VersionRequest;
import com.aiworkmate.service.model.EmployeeChangeAgentApplicationCommand;
import com.aiworkmate.service.model.EmployeeChangeAgentApplicationReceipt;

import java.util.Optional;

public interface EmployeeChangeService {
    PageResponse<EmployeeChangeResponse> list(Long userId, String status, String changeType,
                                              String keyword, int page, int size);
    EmployeeChangeResponse detail(Long userId, Long id);
    EmployeeChangeResponse create(Long userId, EmployeeChangeRequest request);
    EmployeeChangeAgentApplicationReceipt createAgent(
            Long userId, EmployeeChangeAgentApplicationCommand command, String operationKey);
    Optional<EmployeeChangeAgentApplicationReceipt> findAgentApplication(
            Long userId, EmployeeChangeAgentApplicationCommand command, String operationKey);
    EmployeeChangeResponse approve(Long userId, Long id, EmployeeChangeDecisionRequest request);
    EmployeeChangeResponse reject(Long userId, Long id, EmployeeChangeDecisionRequest request);
    EmployeeChangeResponse withdraw(Long userId, Long id, VersionRequest request);
}
