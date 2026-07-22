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
import com.aiworkmate.service.model.AttachmentContent;
import com.aiworkmate.service.model.ParsedFile;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
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

    @Override
    @Transactional
    public AttachmentResponse upload(Long userId, Long conversationId, MultipartFile file) {
        requireConversationOwner(userId, conversationId);
        validateBasicFile(file);
        Path storedPath = store(file);
        try {
            ParsedFile parsed = fileParserService.parse(storedPath, safeDisplayName(file));
            validateSize(file.getSize(), parsed.image());
            Attachment attachment = createEntity(userId, conversationId, file, storedPath, parsed);
            attachmentMapper.insert(attachment);
            log.info("Attachment uploaded, userId={}, conversationId={}, attachmentId={}",
                    userId, conversationId, attachment.getId());
            return toResponse(attachment);
        } catch (RuntimeException ex) {
            deleteQuietly(storedPath);
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public AttachmentContent loadContent(Long userId, Long attachmentId) {
        Attachment attachment = findOwned(userId, attachmentId);
        Path path = storageRoot().resolve(attachment.getStorageName()).normalize();
        if (!path.startsWith(storageRoot()) || !Files.isRegularFile(path)) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID, "附件文件不存在");
        }
        return new AttachmentContent(new FileSystemResource(path), attachment.getMimeType(), attachment.getName());
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
        attachments.forEach(attachment -> deleteQuietly(storageRoot().resolve(attachment.getStorageName())));
        attachmentMapper.delete(new LambdaQueryWrapper<Attachment>()
                .eq(Attachment::getUserId, userId)
                .eq(Attachment::getConversationId, conversationId));
    }

    @Override
    public org.springframework.core.io.Resource resourceFor(Attachment attachment) {
        Path path = storageRoot().resolve(attachment.getStorageName()).normalize();
        if (!path.startsWith(storageRoot()) || !Files.isRegularFile(path)) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID, "附件文件不存在");
        }
        return new FileSystemResource(path);
    }

    private Attachment createEntity(Long userId, Long conversationId, MultipartFile file,
                                    Path storedPath, ParsedFile parsed) {
        Attachment attachment = new Attachment();
        attachment.setUserId(userId);
        attachment.setConversationId(conversationId);
        attachment.setType(parsed.image() ? "image" : "file");
        attachment.setName(safeDisplayName(file));
        attachment.setStorageName(storedPath.getFileName().toString());
        attachment.setSize(file.getSize());
        attachment.setMimeType(parsed.mimeType());
        attachment.setExtractedText(parsed.extractedText());
        attachment.setCreatedAt(LocalDateTime.now());
        return attachment;
    }

    private Path store(MultipartFile file) {
        try {
            Path root = storageRoot();
            Files.createDirectories(root);
            Path target = root.resolve(UUID.randomUUID().toString()).normalize();
            file.transferTo(target);
            return target;
        } catch (IOException ex) {
            log.error("Attachment storage failed", ex);
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

    private Path storageRoot() {
        return Path.of(properties.getDirectory()).toAbsolutePath().normalize();
    }

    private AttachmentResponse toResponse(Attachment attachment) {
        return new AttachmentResponse(attachment.getId(), attachment.getMessageId(), attachment.getType(), attachment.getName(),
                attachment.getSize(), attachment.getMimeType(),
                "/api/attachments/" + attachment.getId() + "/content",
                attachment.getType().equals("image") || attachment.getExtractedText() != null,
                attachment.getCreatedAt());
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ex) {
            log.warn("Unable to remove failed attachment file, path={}", path, ex);
        }
    }
}
