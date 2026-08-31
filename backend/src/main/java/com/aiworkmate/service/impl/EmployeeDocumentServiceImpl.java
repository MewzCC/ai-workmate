package com.aiworkmate.service.impl;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.aiworkmate.config.UploadProperties;
import com.aiworkmate.dto.AccessUserRow;
import com.aiworkmate.dto.EmployeeDocumentResponse;
import com.aiworkmate.entity.EmployeeDocument;
import com.aiworkmate.mapper.AccessControlMapper;
import com.aiworkmate.mapper.EmployeeDocumentMapper;
import com.aiworkmate.security.AuthenticatedUser;
import com.aiworkmate.service.BusinessAuditService;
import com.aiworkmate.service.EmployeeDocumentService;
import com.aiworkmate.service.FileParserService;
import com.aiworkmate.service.ObjectStorageService;
import com.aiworkmate.service.UserAccessService;
import com.aiworkmate.service.model.EmployeeDocumentContent;
import com.aiworkmate.service.model.ParsedFile;
import com.aiworkmate.service.model.ResolvedUserAccess;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmployeeDocumentServiceImpl implements EmployeeDocumentService {
    private static final String RESOURCE_TYPE = "EMPLOYEE_DOCUMENT";
    private static final Set<String> DOCUMENT_TYPES = Set.of("CONTRACT", "PROFILE");

    private final EmployeeDocumentMapper documentMapper;
    private final AccessControlMapper accessControlMapper;
    private final UserAccessService userAccessService;
    private final FileParserService fileParserService;
    private final ObjectStorageService objectStorageService;
    private final UploadProperties uploadProperties;
    private final BusinessAuditService auditService;

    @Override
    @Transactional(readOnly = true)
    public List<EmployeeDocumentResponse> list(AuthenticatedUser actor, Long employeeUserId) {
        ResolvedUserAccess access = requireReadAccess(actor, employeeUserId);
        requireEmployee(access.tenantId(), employeeUserId);
        Map<Long, String> userNames = accessControlMapper.selectUsers(access.tenantId()).stream()
                .collect(Collectors.toMap(AccessUserRow::id, AccessUserRow::name, (a, b) -> a));
        return documentMapper.selectList(new LambdaQueryWrapper<EmployeeDocument>()
                        .eq(EmployeeDocument::getTenantId, access.tenantId())
                        .eq(EmployeeDocument::getEmployeeUserId, employeeUserId)
                        .orderByDesc(EmployeeDocument::getCreatedAt))
                .stream().map(item -> toResponse(item, userNames.get(item.getUploadedByUserId()))).toList();
    }

    @Override
    @Transactional
    public EmployeeDocumentResponse upload(AuthenticatedUser actor, Long employeeUserId,
                                           String documentType, MultipartFile file) {
        ResolvedUserAccess access = requirePermission(actor, "hr:manage");
        requireEmployee(access.tenantId(), employeeUserId);
        if (!DOCUMENT_TYPES.contains(documentType)) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID, "oa.employeeDocument.typeInvalid");
        }
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID, "oa.employeeDocument.empty");
        }
        if (file.getSize() > uploadProperties.getFileMaxBytes()) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID, "oa.employeeDocument.tooLarge");
        }

        String displayName = safeDisplayName(file);
        Path tempFile = createTempFile(file);
        String storageKey = null;
        try {
            ParsedFile parsed = fileParserService.parse(tempFile, displayName, actor.userId());
            if (parsed.image() && file.getSize() > uploadProperties.getImageMaxBytes()) {
                throw new BusinessException(ErrorCode.REQUEST_INVALID, "oa.employeeDocument.imageTooLarge");
            }
            storageKey = buildStorageKey(access.tenantId(), employeeUserId);
            store(storageKey, file, parsed.mimeType());

            EmployeeDocument document = new EmployeeDocument();
            document.setTenantId(access.tenantId());
            document.setEmployeeUserId(employeeUserId);
            document.setDocumentType(documentType);
            document.setDisplayName(displayName);
            document.setStorageKey(storageKey);
            document.setMimeType(parsed.mimeType());
            document.setFileSize(file.getSize());
            document.setUploadedByUserId(actor.userId());
            document.setVersion(0);
            document.setCreatedAt(LocalDateTime.now());
            try {
                documentMapper.insert(document);
                auditService.recordTransactional(access.tenantId(), actor.userId(), RESOURCE_TYPE,
                        String.valueOf(document.getId()), "UPLOAD", "SUCCESS",
                        "employeeUserId=" + employeeUserId + ",type=" + documentType);
                String uploaderName = accessControlMapper.selectUsers(access.tenantId()).stream()
                        .filter(item -> item.id().equals(actor.userId()))
                        .map(AccessUserRow::name)
                        .findFirst()
                        .orElse(access.username());
                return toResponse(document, uploaderName);
            } catch (RuntimeException ex) {
                objectStorageService.delete(storageKey);
                throw ex;
            }
        } finally {
            deleteTempFile(tempFile);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public EmployeeDocumentContent download(AuthenticatedUser actor, Long employeeUserId, Long documentId) {
        ResolvedUserAccess access = requireReadAccess(actor, employeeUserId);
        EmployeeDocument document = documentMapper.selectOne(new LambdaQueryWrapper<EmployeeDocument>()
                .eq(EmployeeDocument::getId, documentId)
                .eq(EmployeeDocument::getTenantId, access.tenantId())
                .eq(EmployeeDocument::getEmployeeUserId, employeeUserId));
        if (document == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        auditService.record(access.tenantId(), actor.userId(), RESOURCE_TYPE,
                String.valueOf(documentId), "DOWNLOAD", "SUCCESS", "employeeUserId=" + employeeUserId);
        return new EmployeeDocumentContent(objectStorageService.load(document.getStorageKey()),
                document.getMimeType(), document.getDisplayName());
    }

    private ResolvedUserAccess requireReadAccess(AuthenticatedUser actor, Long employeeUserId) {
        ResolvedUserAccess access = userAccessService.resolveActiveUser(actor.userId());
        if (access == null || !access.tenantId().equals(actor.tenantId())) {
            throw new BusinessException(ErrorCode.AUTH_REQUIRED);
        }
        if (!access.userId().equals(employeeUserId) && !access.permissions().contains("hr:read")) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED);
        }
        return access;
    }

    private ResolvedUserAccess requirePermission(AuthenticatedUser actor, String permission) {
        ResolvedUserAccess access = userAccessService.resolveActiveUser(actor.userId());
        if (access == null || !access.tenantId().equals(actor.tenantId())) {
            throw new BusinessException(ErrorCode.AUTH_REQUIRED);
        }
        if (!access.permissions().contains(permission)) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED);
        }
        return access;
    }

    private AccessUserRow requireEmployee(Long tenantId, Long employeeUserId) {
        return accessControlMapper.selectUsers(tenantId).stream()
                .filter(item -> item.id().equals(employeeUserId))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private EmployeeDocumentResponse toResponse(EmployeeDocument document, String uploaderName) {
        return new EmployeeDocumentResponse(document.getId(), document.getEmployeeUserId(),
                document.getDocumentType(), document.getDisplayName(), document.getMimeType(),
                document.getFileSize(), document.getUploadedByUserId(), uploaderName,
                "/api/hr/employees/" + document.getEmployeeUserId() + "/documents/"
                        + document.getId() + "/content",
                document.getCreatedAt());
    }

    private Path createTempFile(MultipartFile file) {
        try {
            Path path = Files.createTempFile("employee-document-", ".bin");
            file.transferTo(path);
            return path;
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR);
        }
    }

    private void store(String storageKey, MultipartFile file, String mimeType) {
        try {
            objectStorageService.store(storageKey, file.getInputStream(), file.getSize(), mimeType);
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR);
        }
    }

    private String safeDisplayName(MultipartFile file) {
        String original = file.getOriginalFilename() == null ? "document" : file.getOriginalFilename();
        String clean = original.replace('\\', '/');
        clean = clean.substring(clean.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "").strip();
        if (clean.isBlank()) return "document";
        return clean.length() > 255 ? clean.substring(clean.length() - 255) : clean;
    }

    private String buildStorageKey(Long tenantId, Long employeeUserId) {
        String prefix = uploadProperties.getEmployeeDocumentStoragePrefix();
        if (!prefix.endsWith("/")) prefix += "/";
        return prefix + tenantId + "/" + employeeUserId + "/" + UUID.randomUUID();
    }

    private void deleteTempFile(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ex) {
            log.warn("Unable to remove employee document temp file");
        }
    }
}
