package com.aiworkmate.service.impl;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.aiworkmate.config.EmbeddingProperties;
import com.aiworkmate.dto.KnowledgeBaseCreateRequest;
import com.aiworkmate.dto.KnowledgeBaseResponse;
import com.aiworkmate.dto.KnowledgeBaseUpdateRequest;
import com.aiworkmate.entity.KnowledgeBase;
import com.aiworkmate.mapper.KnowledgeBaseMapper;
import com.aiworkmate.service.EmbeddingService;
import com.aiworkmate.service.KnowledgeBaseService;
import com.aiworkmate.service.UserAccessService;
import com.aiworkmate.service.model.EmbeddingDescriptor;
import com.aiworkmate.service.model.ResolvedUserAccess;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class KnowledgeBaseServiceImpl implements KnowledgeBaseService {

    private static final int DEFAULT_SPARSE_TOP_K = 5;

    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final EmbeddingService embeddingService;
    private final EmbeddingProperties properties;
    private final UserAccessService userAccessService;

    @Override
    public List<KnowledgeBaseResponse> list(Long userId) {
        ResolvedUserAccess access = requireAccess(userId);
        return knowledgeBaseMapper.selectList(new LambdaQueryWrapper<KnowledgeBase>()
                        .eq(KnowledgeBase::getTenantId, access.tenantId())
                        .eq(KnowledgeBase::getUserId, access.userId())
                        .orderByDesc(KnowledgeBase::getCreatedAt)
                        .orderByDesc(KnowledgeBase::getId))
                .stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional
    public KnowledgeBaseResponse create(Long userId, KnowledgeBaseCreateRequest request) {
        ResolvedUserAccess access = requireAccess(userId);
        EmbeddingDescriptor descriptor = embeddingService.current();
        LocalDateTime now = LocalDateTime.now();
        KnowledgeBase knowledgeBase = new KnowledgeBase();
        knowledgeBase.setTenantId(access.tenantId());
        knowledgeBase.setUserId(access.userId());
        knowledgeBase.setName(request.name().strip());
        knowledgeBase.setIcon(blankToDefault(request.icon(), "knowledge-base"));
        knowledgeBase.setDescription(blankToNull(request.description()));
        knowledgeBase.setEmbeddingProvider(descriptor.provider());
        knowledgeBase.setEmbeddingModel(descriptor.model());
        knowledgeBase.setChunkSize(properties.getChunkMaxChars());
        knowledgeBase.setChunkOverlap(properties.getChunkOverlapChars());
        knowledgeBase.setDenseTopK(properties.getRetrievalTopK());
        knowledgeBase.setSparseTopK(DEFAULT_SPARSE_TOP_K);
        knowledgeBase.setCreatedAt(now);
        knowledgeBase.setUpdatedAt(now);
        knowledgeBaseMapper.insert(knowledgeBase);
        return toResponse(knowledgeBase);
    }

    @Override
    public KnowledgeBaseResponse detail(Long userId, Long kbId) {
        ResolvedUserAccess access = requireAccess(userId);
        KnowledgeBase knowledgeBase = requireOwned(access, kbId);
        return toResponse(knowledgeBase);
    }

    @Override
    @Transactional
    public KnowledgeBaseResponse update(Long userId, Long kbId, KnowledgeBaseUpdateRequest request) {
        ResolvedUserAccess access = requireAccess(userId);
        KnowledgeBase knowledgeBase = requireOwned(access, kbId);
        boolean changed = false;
        if (request.name() != null && !request.name().isBlank() && !request.name().strip().equals(knowledgeBase.getName())) {
            knowledgeBase.setName(request.name().strip());
            changed = true;
        }
        if (request.icon() != null) {
            knowledgeBase.setIcon(blankToDefault(request.icon(), "knowledge-base"));
            changed = true;
        }
        if (request.description() != null) {
            knowledgeBase.setDescription(blankToNull(request.description()));
            changed = true;
        }
        if (request.chunkSize() != null) {
            knowledgeBase.setChunkSize(request.chunkSize());
            changed = true;
        }
        if (request.chunkOverlap() != null) {
            knowledgeBase.setChunkOverlap(request.chunkOverlap());
            changed = true;
        }
        if (request.denseTopK() != null) {
            knowledgeBase.setDenseTopK(request.denseTopK());
            changed = true;
        }
        if (request.sparseTopK() != null) {
            knowledgeBase.setSparseTopK(request.sparseTopK());
            changed = true;
        }
        validateSettings(knowledgeBase);
        if (changed) {
            knowledgeBase.setUpdatedAt(LocalDateTime.now());
            knowledgeBaseMapper.updateById(knowledgeBase);
        }
        return toResponse(knowledgeBase);
    }

    @Override
    @Transactional
    public void delete(Long userId, Long kbId) {
        ResolvedUserAccess access = requireAccess(userId);
        requireOwned(access, kbId);
        knowledgeBaseMapper.delete(new LambdaQueryWrapper<KnowledgeBase>()
                .eq(KnowledgeBase::getId, kbId)
                .eq(KnowledgeBase::getTenantId, access.tenantId())
                .eq(KnowledgeBase::getUserId, access.userId()));
    }

    private void validateSettings(KnowledgeBase knowledgeBase) {
        if (knowledgeBase.getChunkOverlap() != null
                && knowledgeBase.getChunkSize() != null
                && knowledgeBase.getChunkOverlap() >= knowledgeBase.getChunkSize()) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID, "分块重叠必须小于分块大小");
        }
    }

    private ResolvedUserAccess requireAccess(Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.AUTH_REQUIRED);
        }
        return userAccessService.resolveActiveUser(userId);
    }

    private KnowledgeBase requireOwned(ResolvedUserAccess access, Long kbId) {
        KnowledgeBase knowledgeBase = knowledgeBaseMapper.selectOne(new LambdaQueryWrapper<KnowledgeBase>()
                .eq(KnowledgeBase::getId, kbId)
                .eq(KnowledgeBase::getTenantId, access.tenantId())
                .eq(KnowledgeBase::getUserId, access.userId()));
        if (knowledgeBase == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return knowledgeBase;
    }

    private KnowledgeBaseResponse toResponse(KnowledgeBase knowledgeBase) {
        long docCount = knowledgeBaseMapper.countDocuments(
                knowledgeBase.getTenantId(), knowledgeBase.getUserId(), knowledgeBase.getId());
        long chunkCount = knowledgeBaseMapper.countChunks(
                knowledgeBase.getTenantId(), knowledgeBase.getUserId(), knowledgeBase.getId());
        return new KnowledgeBaseResponse(
                knowledgeBase.getId(), knowledgeBase.getName(), knowledgeBase.getIcon(),
                knowledgeBase.getDescription(), docCount, chunkCount,
                knowledgeBase.getEmbeddingProvider(), knowledgeBase.getEmbeddingModel(),
                knowledgeBase.getRerankModel(),
                value(knowledgeBase.getChunkSize()), value(knowledgeBase.getChunkOverlap()),
                value(knowledgeBase.getDenseTopK()), value(knowledgeBase.getSparseTopK()),
                knowledgeBase.getCreatedAt(), knowledgeBase.getUpdatedAt());
    }

    private int value(Integer value) {
        return value == null ? 0 : value;
    }

    private String blankToNull(String value) {
        if (value == null) return null;
        String stripped = value.strip();
        return stripped.isBlank() ? null : stripped;
    }

    private String blankToDefault(String value, String fallback) {
        String stripped = blankToNull(value);
        return stripped == null ? fallback : stripped;
    }
}
