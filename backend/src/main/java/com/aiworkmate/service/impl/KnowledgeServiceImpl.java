package com.aiworkmate.service.impl;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.aiworkmate.common.PageResponse;
import com.aiworkmate.config.EmbeddingProperties;
import com.aiworkmate.dto.EmbeddingStatusResponse;
import com.aiworkmate.dto.KnowledgeDocumentCreateRequest;
import com.aiworkmate.dto.KnowledgeDocumentResponse;
import com.aiworkmate.dto.KnowledgeSearchItemResponse;
import com.aiworkmate.dto.KnowledgeSearchRequest;
import com.aiworkmate.dto.KnowledgeSearchResponse;
import com.aiworkmate.entity.KnowledgeDocument;
import com.aiworkmate.mapper.KnowledgeDocumentMapper;
import com.aiworkmate.service.EmbeddingService;
import com.aiworkmate.service.KnowledgeChunker;
import com.aiworkmate.service.KnowledgeService;
import com.aiworkmate.service.UserAccessService;
import com.aiworkmate.service.model.EmbeddingDescriptor;
import com.aiworkmate.service.model.EmbeddingResult;
import com.aiworkmate.service.model.KnowledgeSearchRow;
import com.aiworkmate.service.model.ResolvedUserAccess;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;

@Service
@RequiredArgsConstructor
public class KnowledgeServiceImpl implements KnowledgeService {

    private static final String STATUS_PROCESSING = "PROCESSING";
    private static final String STATUS_READY = "READY";

    private final KnowledgeDocumentMapper documentMapper;
    private final EmbeddingService embeddingService;
    private final KnowledgeChunker knowledgeChunker;
    private final UserAccessService userAccessService;
    private final EmbeddingProperties properties;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public KnowledgeDocumentResponse create(Long userId, KnowledgeDocumentCreateRequest request) {
        ResolvedUserAccess access = requireAccess(userId);
        String filename = request.filename().strip();
        String content = request.content().strip();
        if (content.length() > properties.getMaxDocumentChars()) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID, "文档内容超过允许的最大长度");
        }

        EmbeddingDescriptor descriptor = embeddingService.current();
        String contentHash = sha256(content);
        KnowledgeDocument existing = findByHash(access, contentHash, descriptor);
        if (existing != null) {
            if (STATUS_READY.equals(existing.getStatus())) {
                return toResponse(existing);
            }
            throw new BusinessException(ErrorCode.BUSINESS_STATE_INVALID, "相同文档正在处理");
        }

        List<String> chunks = knowledgeChunker.split(content);
        if (chunks.isEmpty()) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID, "文档内容不能为空");
        }
        EmbeddingResult embeddings = embeddingService.embed(chunks);
        validateEmbeddingResult(descriptor, embeddings, chunks.size());

        LocalDateTime now = LocalDateTime.now();
        KnowledgeDocument document = new KnowledgeDocument();
        document.setTenantId(access.tenantId());
        document.setUserId(access.userId());
        document.setFilename(filename);
        document.setFileSize((long) content.getBytes(StandardCharsets.UTF_8).length);
        document.setFileType("TEXT");
        document.setChunkCount(0);
        document.setStatus(STATUS_PROCESSING);
        document.setContentHash(contentHash);
        document.setEmbeddingProvider(descriptor.provider());
        document.setEmbeddingModel(descriptor.model());
        document.setCreatedAt(now);
        document.setUpdatedAt(now);
        documentMapper.insert(document);

        for (int index = 0; index < chunks.size(); index++) {
            documentMapper.insertChunk(access.tenantId(), access.userId(), document.getId(), index,
                    chunks.get(index), vectorLiteral(embeddings.vectors().get(index)),
                    descriptor.provider(), descriptor.model(), metadata(filename, index));
        }
        document.setChunkCount(chunks.size());
        document.setStatus(STATUS_READY);
        document.setUpdatedAt(LocalDateTime.now());
        documentMapper.updateById(document);
        return toResponse(document);
    }

    @Override
    @Transactional
    public KnowledgeDocumentResponse reindex(Long userId, Long documentId) {
        ResolvedUserAccess access = requireAccess(userId);
        KnowledgeDocument document = requireOwned(access, documentId);
        EmbeddingDescriptor descriptor = embeddingService.current();
        KnowledgeDocument duplicate = findByHash(access, document.getContentHash(), descriptor);
        if (duplicate != null && !duplicate.getId().equals(documentId)) {
            throw new BusinessException(ErrorCode.BUSINESS_STATE_INVALID,
                    "当前向量模型下已存在相同文档，无需重复重建");
        }

        List<String> chunks = documentMapper.selectChunkContents(access.tenantId(), access.userId(), documentId);
        if (chunks.isEmpty()) {
            throw new BusinessException(ErrorCode.BUSINESS_STATE_INVALID, "文档没有可重建的分块");
        }
        EmbeddingResult embeddings = embeddingService.embed(chunks);
        validateEmbeddingResult(descriptor, embeddings, chunks.size());
        for (int index = 0; index < chunks.size(); index++) {
            int updated = documentMapper.updateChunkEmbedding(access.tenantId(), access.userId(), documentId,
                    index, vectorLiteral(embeddings.vectors().get(index)),
                    descriptor.provider(), descriptor.model());
            if (updated != 1) {
                throw new BusinessException(ErrorCode.BUSINESS_STATE_INVALID, "文档分块已发生变化，请重试");
            }
        }
        document.setEmbeddingProvider(descriptor.provider());
        document.setEmbeddingModel(descriptor.model());
        document.setStatus(STATUS_READY);
        document.setErrorMessage(null);
        document.setUpdatedAt(LocalDateTime.now());
        documentMapper.updateById(document);
        return toResponse(document);
    }

    @Override
    public PageResponse<KnowledgeDocumentResponse> list(Long userId, int page, int size) {
        ResolvedUserAccess access = requireAccess(userId);
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);
        Page<KnowledgeDocument> result = documentMapper.selectPage(
                Page.of(safePage, safeSize),
                new LambdaQueryWrapper<KnowledgeDocument>()
                        .eq(KnowledgeDocument::getTenantId, access.tenantId())
                        .eq(KnowledgeDocument::getUserId, access.userId())
                        .orderByDesc(KnowledgeDocument::getCreatedAt)
                        .orderByDesc(KnowledgeDocument::getId));
        return PageResponse.of(result.getRecords().stream().map(this::toResponse).toList(),
                result.getTotal(), safePage, safeSize);
    }

    @Override
    @Transactional
    public void delete(Long userId, Long documentId) {
        ResolvedUserAccess access = requireAccess(userId);
        KnowledgeDocument document = requireOwned(access, documentId);
        int deleted = documentMapper.delete(new LambdaQueryWrapper<KnowledgeDocument>()
                .eq(KnowledgeDocument::getId, document.getId())
                .eq(KnowledgeDocument::getTenantId, access.tenantId())
                .eq(KnowledgeDocument::getUserId, access.userId()));
        if (deleted != 1) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
    }

    @Override
    public KnowledgeSearchResponse search(Long userId, KnowledgeSearchRequest request) {
        ResolvedUserAccess access = requireAccess(userId);
        String query = request.query().strip();
        EmbeddingDescriptor descriptor = embeddingService.current();
        Long availableDocuments = documentMapper.selectCount(
                new LambdaQueryWrapper<KnowledgeDocument>()
                        .eq(KnowledgeDocument::getTenantId, access.tenantId())
                        .eq(KnowledgeDocument::getUserId, access.userId())
                        .eq(KnowledgeDocument::getStatus, STATUS_READY)
                        .eq(KnowledgeDocument::getEmbeddingProvider, descriptor.provider())
                        .eq(KnowledgeDocument::getEmbeddingModel, descriptor.model()));
        if (availableDocuments == null || availableDocuments == 0) {
            return new KnowledgeSearchResponse(descriptor.provider(), descriptor.model(),
                    descriptor.dimension(), List.of());
        }
        EmbeddingResult embedding = embeddingService.embed(List.of(query));
        validateEmbeddingResult(descriptor, embedding, 1);
        int topK = request.topK() == null ? properties.getRetrievalTopK() : request.topK();
        double minScore = request.minScore() == null
                ? properties.getRetrievalMinScore() : request.minScore();
        List<KnowledgeSearchRow> rows = documentMapper.search(
                access.tenantId(), access.userId(), vectorLiteral(embedding.vectors().get(0)),
                descriptor.provider(), descriptor.model(), minScore, topK);
        List<KnowledgeSearchItemResponse> records = rows.stream()
                .map(row -> new KnowledgeSearchItemResponse(row.getDocId(), row.getChunkId(),
                        row.getFilename(), row.getChunkIndex(), row.getContent(), row.getScore()))
                .toList();
        return new KnowledgeSearchResponse(descriptor.provider(), descriptor.model(),
                descriptor.dimension(), records);
    }

    @Override
    public EmbeddingStatusResponse embeddingStatus() {
        EmbeddingDescriptor descriptor = embeddingService.current();
        return new EmbeddingStatusResponse(properties.isEnabled(), descriptor.provider(),
                descriptor.model(), descriptor.dimension());
    }

    private ResolvedUserAccess requireAccess(Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.AUTH_REQUIRED);
        }
        return userAccessService.resolveActiveUser(userId);
    }

    private KnowledgeDocument requireOwned(ResolvedUserAccess access, Long documentId) {
        KnowledgeDocument document = documentMapper.selectOne(new LambdaQueryWrapper<KnowledgeDocument>()
                .eq(KnowledgeDocument::getId, documentId)
                .eq(KnowledgeDocument::getTenantId, access.tenantId())
                .eq(KnowledgeDocument::getUserId, access.userId()));
        if (document == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return document;
    }

    private KnowledgeDocument findByHash(ResolvedUserAccess access, String contentHash,
                                         EmbeddingDescriptor descriptor) {
        if (contentHash == null || contentHash.isBlank()) {
            return null;
        }
        return documentMapper.selectOne(new LambdaQueryWrapper<KnowledgeDocument>()
                .eq(KnowledgeDocument::getTenantId, access.tenantId())
                .eq(KnowledgeDocument::getUserId, access.userId())
                .eq(KnowledgeDocument::getContentHash, contentHash)
                .eq(KnowledgeDocument::getEmbeddingProvider, descriptor.provider())
                .eq(KnowledgeDocument::getEmbeddingModel, descriptor.model())
                .last("LIMIT 1"));
    }

    private void validateEmbeddingResult(EmbeddingDescriptor expected, EmbeddingResult actual, int count) {
        if (!expected.provider().equals(actual.provider())
                || !expected.model().equals(actual.model())
                || actual.vectors().size() != count) {
            throw new BusinessException(ErrorCode.EMBEDDING_RESPONSE_INVALID,
                    "向量模型返回结果与当前配置不一致");
        }
        for (float[] vector : actual.vectors()) {
            if (vector == null || vector.length != expected.dimension()) {
                throw new BusinessException(ErrorCode.EMBEDDING_RESPONSE_INVALID,
                        "向量模型返回维度与当前配置不一致");
            }
            for (float value : vector) {
                if (!Float.isFinite(value)) {
                    throw new BusinessException(ErrorCode.EMBEDDING_RESPONSE_INVALID,
                            "向量模型返回了非法数值");
                }
            }
        }
    }

    private KnowledgeDocumentResponse toResponse(KnowledgeDocument document) {
        return new KnowledgeDocumentResponse(document.getId(), document.getFilename(),
                document.getFileSize() == null ? 0 : document.getFileSize(), document.getFileType(),
                document.getChunkCount() == null ? 0 : document.getChunkCount(), document.getStatus(),
                document.getEmbeddingProvider(), document.getEmbeddingModel(),
                document.getCreatedAt(), document.getUpdatedAt());
    }

    private String metadata(String filename, int chunkIndex) {
        try {
            return objectMapper.writeValueAsString(java.util.Map.of(
                    "source", filename,
                    "chunkIndex", chunkIndex));
        } catch (JsonProcessingException ex) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "知识分块元数据序列化失败");
        }
    }

    private String sha256(String content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    static String vectorLiteral(float[] vector) {
        StringBuilder value = new StringBuilder(vector.length * 12).append('[');
        for (int index = 0; index < vector.length; index++) {
            if (index > 0) {
                value.append(',');
            }
            value.append(Float.toString(vector[index]));
        }
        return value.append(']').toString();
    }
}
