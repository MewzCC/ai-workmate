package com.aiworkmate.service.impl;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.aiworkmate.config.UploadProperties;
import com.aiworkmate.dto.AttachmentResponse;
import com.aiworkmate.entity.Attachment;
import com.aiworkmate.entity.Conversation;
import com.aiworkmate.mapper.AttachmentMapper;
import com.aiworkmate.mapper.ConversationMapper;
import com.aiworkmate.service.AttachmentService;
import com.aiworkmate.service.FileParserService;
import com.aiworkmate.service.ObjectStorageService;
import com.aiworkmate.service.model.AttachmentContent;
import com.aiworkmate.service.model.ParsedFile;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AttachmentServiceImpl implements AttachmentService {

    private final AttachmentMapper attachmentMapper;
    private final ConversationMapper conversationMapper;
    private final FileParserService fileParserService;
    private final UploadProperties properties;
    private final ObjectStorageService objectStorageService;

    @Override
    @Transactional
    public AttachmentResponse upload(Long tenantId, Long userId, Long conversationId, MultipartFile file) {
        requireConversationOwner(userId, conversationId);
        validateBasicFile(file);
        Path tempFile = createTempFile(file);
        try {
            ParsedFile parsed = fileParserService.parse(tempFile, safeDisplayName(file), userId);
            validateSize(file.getSize(), parsed.image());
            String storageName = buildStorageName();
            try {
                objectStorageService.store(storageName, file.getInputStream(), file.getSize(), parsed.mimeType());
            } catch (IOException ex) {
                log.error("Attachment stream read failed", ex);
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "附件保存失败");
            }
            Attachment attachment;
            try {
                attachment = createEntity(tenantId, userId, conversationId, file, storageName, parsed);
                attachmentMapper.insert(attachment);
            } catch (RuntimeException ex) {
                objectStorageService.delete(storageName);
                throw ex;
            }
            log.info("Attachment uploaded, userId={}, conversationId={}, attachmentId={}",
                    userId, conversationId, attachment.getId());
            return toResponse(attachment);
        } finally {
            deleteTempFile(tempFile);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public AttachmentContent loadContent(Long userId, Long attachmentId) {
        Attachment attachment = findOwned(userId, attachmentId);
        Resource resource = objectStorageService.load(attachment.getStorageName());
        return new AttachmentContent(resource, attachment.getMimeType(), attachment.getName());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Attachment> requireOwned(Long userId, Long conversationId, List<Long> attachmentIds) {
        if (attachmentIds == null || attachmentIds.isEmpty()) return List.of();
        List<Long> distinctIds = attachmentIds.stream().distinct().toList();
        List<Attachment> attachments = attachmentMapper.selectList(new LambdaQueryWrapper<Attachment>()
                .in(Attachment::getId, distinctIds)
                .eq(Attachment::getUserId, userId)
                .eq(Attachment::getConversationId, conversationId)
                .isNull(Attachment::getMessageId));
        if (attachments.size() != distinctIds.size()) {
            throw new BusinessException(ErrorCode.RESOURCE_FORBIDDEN, "附件不存在、已使用或不属于当前会话");
        }
        return attachments;
    }

    @Override
    public void bindToMessage(List<Attachment> attachments, Long messageId) {
        attachments.forEach(attachment -> {
            attachment.setMessageId(messageId);
            attachmentMapper.updateById(attachment);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public List<AttachmentResponse> listByMessageIds(List<Long> messageIds) {
        if (messageIds.isEmpty()) return List.of();
        return attachmentMapper.selectList(new LambdaQueryWrapper<Attachment>()
                        .in(Attachment::getMessageId, messageIds)
                        .orderByAsc(Attachment::getCreatedAt))
                .stream().map(this::toResponse).toList();
    }

    @Override
    public void deleteConversationAttachments(Long userId, Long conversationId) {
        List<Attachment> attachments = attachmentMapper.selectList(new LambdaQueryWrapper<Attachment>()
                .eq(Attachment::getUserId, userId)
                .eq(Attachment::getConversationId, conversationId));
        attachments.forEach(attachment -> objectStorageService.delete(attachment.getStorageName()));
        attachmentMapper.delete(new LambdaQueryWrapper<Attachment>()
                .eq(Attachment::getUserId, userId)
                .eq(Attachment::getConversationId, conversationId));
    }

    @Override
    public Resource resourceFor(Attachment attachment) {
        return objectStorageService.load(attachment.getStorageName());
    }

    private Attachment createEntity(Long tenantId, Long userId, Long conversationId, MultipartFile file,
                                    String storageName, ParsedFile parsed) {
        Attachment attachment = new Attachment();
        attachment.setTenantId(tenantId);
        attachment.setUserId(userId);
        attachment.setConversationId(conversationId);
        attachment.setType(parsed.image() ? "image" : "file");
        attachment.setName(safeDisplayName(file));
        attachment.setStorageName(storageName);
        attachment.setSize(file.getSize());
        attachment.setMimeType(parsed.mimeType());
        attachment.setExtractedText(parsed.extractedText());
        attachment.setCreatedAt(LocalDateTime.now());
        return attachment;
    }

    private Path createTempFile(MultipartFile file) {
        try {
            Path temp = Files.createTempFile("attachment-", ".bin");
            file.transferTo(temp);
            return temp;
        } catch (IOException ex) {
            log.error("Attachment temp storage failed", ex);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "附件保存失败");
        }
    }

    private void validateBasicFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID, "上传文件不能为空");
        }
        if (file.getSize() > properties.getFileMaxBytes()) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID, "文件不能超过 20MB");
        }
    }

    private void validateSize(long size, boolean image) {
        if (image && size > properties.getImageMaxBytes()) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID, "图片不能超过 10MB");
        }
    }

    private void requireConversationOwner(Long userId, Long conversationId) {
        Long count = conversationMapper.selectCount(new LambdaQueryWrapper<Conversation>()
                .eq(Conversation::getId, conversationId).eq(Conversation::getUserId, userId));
        if (count == 0) throw new BusinessException(ErrorCode.RESOURCE_FORBIDDEN, "无权访问该会话");
    }

    private Attachment findOwned(Long userId, Long attachmentId) {
        Attachment attachment = attachmentMapper.selectOne(new LambdaQueryWrapper<Attachment>()
                .eq(Attachment::getId, attachmentId).eq(Attachment::getUserId, userId));
        if (attachment == null) throw new BusinessException(ErrorCode.RESOURCE_FORBIDDEN, "无权访问该附件");
        return attachment;
    }

    private String safeDisplayName(MultipartFile file) {
        String original = file.getOriginalFilename() == null ? "attachment" : file.getOriginalFilename();
        String clean = original.replace('\\', '/');
        clean = clean.substring(clean.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "").strip();
        if (clean.isBlank()) return "attachment";
        return clean.length() > 255 ? clean.substring(clean.length() - 255) : clean;
    }

    private String buildStorageName() {
        String prefix = properties.getStoragePrefix() == null || properties.getStoragePrefix().isBlank()
                ? "" : properties.getStoragePrefix();
        return prefix + UUID.randomUUID();
    }

    private AttachmentResponse toResponse(Attachment attachment) {
        return new AttachmentResponse(attachment.getId(), attachment.getMessageId(), attachment.getType(), attachment.getName(),
                attachment.getSize(), attachment.getMimeType(),
                "/api/attachments/" + attachment.getId() + "/content",
                attachment.getType().equals("image") || attachment.getExtractedText() != null,
                attachment.getExtractedText() != null,
                attachment.getCreatedAt());
    }

    private void deleteTempFile(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ex) {
            log.warn("Unable to remove attachment temp file, path={}", path, ex);
        }
    }
}
