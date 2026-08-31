package com.aiworkmate.service;

import com.aiworkmate.dto.SealUsageDocumentResponse;
import com.aiworkmate.security.AuthenticatedUser;
import com.aiworkmate.service.model.SealUsageDocumentContent;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface SealUsageDocumentService {
    List<SealUsageDocumentResponse> list(AuthenticatedUser actor, Long sealUsageId);
    SealUsageDocumentResponse upload(AuthenticatedUser actor, Long sealUsageId, MultipartFile file);
    SealUsageDocumentContent download(AuthenticatedUser actor, Long sealUsageId, Long documentId);
}
