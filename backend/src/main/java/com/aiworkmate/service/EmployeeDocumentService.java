package com.aiworkmate.service;

import com.aiworkmate.dto.EmployeeDocumentResponse;
import com.aiworkmate.security.AuthenticatedUser;
import com.aiworkmate.service.model.EmployeeDocumentContent;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface EmployeeDocumentService {
    List<EmployeeDocumentResponse> list(AuthenticatedUser actor, Long employeeUserId);

    EmployeeDocumentResponse upload(AuthenticatedUser actor, Long employeeUserId,
                                    String documentType, MultipartFile file);

    EmployeeDocumentContent download(AuthenticatedUser actor, Long employeeUserId, Long documentId);
}
