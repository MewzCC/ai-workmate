package com.aiworkmate.service.impl;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.aiworkmate.config.UploadProperties;
import com.aiworkmate.dto.SealUsageDocumentResponse;
import com.aiworkmate.entity.SealUsage;
import com.aiworkmate.entity.SealUsageDocument;
import com.aiworkmate.entity.User;
import com.aiworkmate.mapper.SealUsageDocumentMapper;
import com.aiworkmate.mapper.SealUsageMapper;
import com.aiworkmate.mapper.UserMapper;
import com.aiworkmate.security.AuthenticatedUser;
import com.aiworkmate.service.BusinessAuditService;
import com.aiworkmate.service.FileParserService;
import com.aiworkmate.service.ObjectStorageService;
import com.aiworkmate.service.SealUsageDocumentService;
import com.aiworkmate.service.UserAccessService;
import com.aiworkmate.service.model.ParsedFile;
import com.aiworkmate.service.model.ResolvedUserAccess;
import com.aiworkmate.service.model.SealUsageDocumentContent;
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
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SealUsageDocumentServiceImpl implements SealUsageDocumentService {
    private static final String RESOURCE_TYPE = "SEAL_USAGE_DOCUMENT";
    private static final Set<String> ARCHIVABLE_STATES = Set.of("APPROVED", "USED", "RETURNED");

    private final SealUsageDocumentMapper documentMapper;
    private final SealUsageMapper sealUsageMapper;
    private final UserMapper userMapper;
    private final UserAccessService userAccessService;
    private final FileParserService fileParserService;
    private final ObjectStorageService objectStorageService;
    private final UploadProperties uploadProperties;
    private final BusinessAuditService auditService;

    @Override
    @Transactional(readOnly = true)
    public List<SealUsageDocumentResponse> list(AuthenticatedUser actor, Long sealUsageId) {
        ResolvedUserAccess access = requireAccess(actor);
        requireRelatedUsage(access, sealUsageId);
        return documentMapper.selectList(new LambdaQueryWrapper<SealUsageDocument>()
                        .eq(SealUsageDocument::getTenantId, access.tenantId())
                        .eq(SealUsageDocument::getSealUsageId, sealUsageId)
                        .orderByDesc(SealUsageDocument::getCreatedAt))
                .stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional
    public SealUsageDocumentResponse upload(AuthenticatedUser actor, Long sealUsageId, MultipartFile file) {
        ResolvedUserAccess access = requireAccess(actor);
        SealUsage usage = requireRelatedUsage(access, sealUsageId);
        if (!ARCHIVABLE_STATES.contains(usage.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_STATE_INVALID, "oa.seal.document.state.invalid");
        }
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID, "oa.seal.document.empty");
        }
        if (file.getSize() > uploadProperties.getFileMaxBytes()) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID, "oa.seal.document.tooLarge");
        }

        String displayName = safeDisplayName(file);
        Path tempFile = createTempFile(file);
        String storageKey = null;
        try {
            ParsedFile parsed = fileParserService.parse(tempFile, displayName, actor.userId());
            if (parsed.image() && file.getSize() > uploadProperties.getImageMaxBytes()) {
                throw new BusinessException(ErrorCode.REQUEST_INVALID, "oa.seal.document.imageTooLarge");
            }
            storageKey = buildStorageKey(access.tenantId(), sealUsageId);
            store(storageKey, file, parsed.mimeType());

            SealUsageDocument document = new SealUsageDocument();
            document.setTenantId(access.tenantId());
            document.setSealUsageId(sealUsageId);
            document.setDisplayName(displayName);
            document.setStorageKey(storageKey);
            document.setMimeType(parsed.mimeType());
            document.setFileSize(file.getSize());
            document.setUploadedByUserId(access.userId());
            document.setCreatedAt(LocalDateTime.now());
            try {
                documentMapper.insert(document);
                auditService.recordTransactional(access.tenantId(), access.userId(), RESOURCE_TYPE,
                        String.valueOf(document.getId()), "UPLOAD", "SUCCESS",
                        "sealUsageId=" + sealUsageId + ",filename=" + displayName);
                return toResponse(document);
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
    public SealUsageDocumentContent download(AuthenticatedUser actor, Long sealUsageId, Long documentId) {
        ResolvedUserAccess access = requireAccess(actor);
        requireRelatedUsage(access, sealUsageId);
        SealUsageDocument document = documentMapper.selectOne(new LambdaQueryWrapper<SealUsageDocument>()
                .eq(SealUsageDocument::getId, documentId)
                .eq(SealUsageDocument::getTenantId, access.tenantId())
                .eq(SealUsageDocument::getSealUsageId, sealUsageId));
        if (document == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        auditService.record(access.tenantId(), access.userId(), RESOURCE_TYPE,
                String.valueOf(documentId), "DOWNLOAD", "SUCCESS", "sealUsageId=" + sealUsageId);
        return new SealUsageDocumentContent(objectStorageService.load(document.getStorageKey()),
                document.getMimeType(), document.getDisplayName());
    }

    private ResolvedUserAccess requireAccess(AuthenticatedUser actor) {
        ResolvedUserAccess access = userAccessService.resolveActiveUser(actor.userId());
        if (access == null || !access.tenantId().equals(actor.tenantId())) {
            throw new BusinessException(ErrorCode.AUTH_REQUIRED);
        }
        if (!access.permissions().contains("seal:register")) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED);
        }
        return access;
    }

    private SealUsage requireRelatedUsage(ResolvedUserAccess access, Long sealUsageId) {
        SealUsage usage = sealUsageMapper.selectById(sealUsageId);
        if (usage == null || !access.tenantId().equals(usage.getTenantId())) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        boolean related = access.userId().equals(usage.getApplicantUserId())
                || access.userId().equals(usage.getHandlerUserId());
        if (!related && !access.permissions().contains("seal:register:any")) {
            throw new BusinessException(ErrorCode.RESOURCE_FORBIDDEN);
        }
        return usage;
    }

    private SealUsageDocumentResponse toResponse(SealUsageDocument document) {
        User uploader = userMapper.selectById(document.getUploadedByUserId());
        String uploaderName = uploader == null ? null
                : uploader.getDisplayName() == null || uploader.getDisplayName().isBlank()
                ? uploader.getUsername() : uploader.getDisplayName();
        return new SealUsageDocumentResponse(document.getId(), document.getSealUsageId(),
                document.getDisplayName(), document.getMimeType(), document.getFileSize(),
                document.getUploadedByUserId(), uploaderName,
                "/api/admin-assets/seal-usages/" + document.getSealUsageId()
                        + "/documents/" + document.getId() + "/content",
                document.getCreatedAt());
    }

    private Path createTempFile(MultipartFile file) {
        try {
            Path path = Files.createTempFile("seal-document-", ".bin");
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

    private String buildStorageKey(Long tenantId, Long sealUsageId) {
        String prefix = uploadProperties.getSealDocumentStoragePrefix();
        if (!prefix.endsWith("/")) prefix += "/";
        return prefix + tenantId + "/" + sealUsageId + "/" + UUID.randomUUID();
    }

    private void deleteTempFile(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ex) {
            log.warn("Unable to remove seal document temp file");
        }
    }
}
