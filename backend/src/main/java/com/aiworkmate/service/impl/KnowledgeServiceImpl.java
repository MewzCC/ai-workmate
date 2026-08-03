package com.aiworkmate.service.impl;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.aiworkmate.common.PageResponse;
import com.aiworkmate.config.EmbeddingProperties;
import com.aiworkmate.config.UploadProperties;
import com.aiworkmate.dto.EmbeddingStatusResponse;
import com.aiworkmate.dto.KnowledgeChunkResponse;
import com.aiworkmate.dto.KnowledgeDocumentCreateRequest;
import com.aiworkmate.dto.KnowledgeDocumentDetailResponse;
import com.aiworkmate.dto.KnowledgeDocumentResponse;
import com.aiworkmate.dto.KnowledgeSearchItemResponse;
import com.aiworkmate.dto.KnowledgeSearchRequest;
import com.aiworkmate.dto.KnowledgeSearchResponse;
import com.aiworkmate.entity.KnowledgeBase;
import com.aiworkmate.entity.KnowledgeDocument;
import com.aiworkmate.mapper.KnowledgeBaseMapper;
import com.aiworkmate.mapper.KnowledgeDocumentMapper;
import com.aiworkmate.service.EmbeddingService;
import com.aiworkmate.service.FileParserService;
import com.aiworkmate.service.KnowledgeChunker;
import com.aiworkmate.service.KnowledgeService;
import com.aiworkmate.service.RerankService;
import com.aiworkmate.service.UserAccessService;
import com.aiworkmate.service.model.EmbeddingDescriptor;
import com.aiworkmate.service.model.EmbeddingResult;
import com.aiworkmate.service.model.KnowledgeSearchRow;
import com.aiworkmate.service.model.ParsedFile;
import com.aiworkmate.service.model.ResolvedUserAccess;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeServiceImpl implements KnowledgeService {

    private static final String STATUS_PROCESSING = "PROCESSING";
    private static final String STATUS_READY = "READY";

    /** 目录页特征：连续 4+ 个点/中点/省略号（如“章节标题 ………… 42”） */
    private static final Pattern TOC_DOT_LINE = Pattern.compile(".*[.·…．]{4,}.*");

    private static final Map<String, String> FILE_TYPE_BY_MIME = Map.ofEntries(
            Map.entry("application/pdf", "PDF"),
            Map.entry("application/msword", "DOC"),
            Map.entry("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "DOCX"),
            Map.entry("application/vnd.ms-excel", "XLS"),
            Map.entry("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "XLSX"),
            Map.entry("text/plain", "TXT"),
            Map.entry("text/markdown", "MD"),
            Map.entry("text/csv", "CSV"),
            Map.entry("application/csv", "CSV")
    );

    private final KnowledgeDocumentMapper documentMapper;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final EmbeddingService embeddingService;
    private final KnowledgeChunker knowledgeChunker;
    private final UserAccessService userAccessService;
    private final FileParserService fileParserService;
    private final UploadProperties uploadProperties;
    private final EmbeddingProperties properties;
    private final ObjectMapper objectMapper;
    private final RerankService rerankService;

    @Override
    @Transactional
    public KnowledgeDocumentResponse create(Long userId, KnowledgeDocumentCreateRequest request) {
        String filename = request.filename().strip();
        String content = request.content().strip();
        if (content.length() > properties.getMaxDocumentChars()) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID, "文档内容超过允许的最大长度");
        }
        return createFromContent(userId, request.kbId(), filename, content, "TEXT",
                (long) content.getBytes(StandardCharsets.UTF_8).length);
    }

    @Override
    @Transactional
    public KnowledgeDocumentResponse upload(Long userId, Long kbId, MultipartFile file) {
        validateUploadFile(file);
        Path tempFile = createTempFile(file);
        try {
            String filename = safeDisplayName(file);
            ParsedFile parsed = fileParserService.parse(tempFile, filename);
            if (parsed.image()) {
                throw new BusinessException(ErrorCode.REQUEST_INVALID,
                        "不支持图片文件，请上传 TXT、PDF 或 Word 文档");
            }
            String content = parsed.extractedText() == null ? "" : parsed.extractedText().strip();
            if (content.isBlank()) {
                throw new BusinessException(ErrorCode.REQUEST_INVALID, "无法从文件中提取文本内容");
            }
            if (content.length() > properties.getMaxDocumentChars()) {
                content = content.substring(0, properties.getMaxDocumentChars());
            }
            String fileType = FILE_TYPE_BY_MIME.getOrDefault(parsed.mimeType(),
                    extensionLabel(filename));
            return createFromContent(userId, kbId, filename, content, fileType, file.getSize());
        } finally {
            deleteTempFile(tempFile);
        }
    }

    private KnowledgeDocumentResponse createFromContent(Long userId, Long kbId, String filename,
                                                        String content, String fileType, long fileSize) {
        ResolvedUserAccess access = requireAccess(userId);
        KnowledgeBase knowledgeBase = requireKnowledgeBase(access, kbId);

        EmbeddingDescriptor descriptor = embeddingService.current();
        String contentHash = sha256(content);
        KnowledgeDocument existing = findByHash(access, kbId, contentHash, descriptor);
        if (existing != null) {
            if (STATUS_READY.equals(existing.getStatus())) {
                return toResponse(existing);
            }
            throw new BusinessException(ErrorCode.BUSINESS_STATE_INVALID, "相同文档正在处理");
        }

        List<String> chunks = knowledgeChunker.split(content,
                knowledgeBase.getChunkSize() == null ? 1000 : knowledgeBase.getChunkSize(),
                knowledgeBase.getChunkOverlap() == null ? 120 : knowledgeBase.getChunkOverlap());
        if (chunks.isEmpty()) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID, "文档内容不能为空");
        }
        EmbeddingResult embeddings = embeddingService.embed(chunks);
        validateEmbeddingResult(descriptor, embeddings, chunks.size());

        LocalDateTime now = LocalDateTime.now();
        KnowledgeDocument document = new KnowledgeDocument();
        document.setTenantId(access.tenantId());
        document.setUserId(access.userId());
        document.setKbId(kbId);
        document.setFilename(filename);
        document.setFileSize(fileSize);
        document.setFileType(fileType);
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
        KnowledgeDocument duplicate = findByHash(access, document.getKbId(), document.getContentHash(), descriptor);
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
    public KnowledgeDocumentDetailResponse documentDetail(Long userId, Long documentId) {
        ResolvedUserAccess access = requireAccess(userId);
        KnowledgeDocument document = requireOwned(access, documentId);
        List<KnowledgeChunkResponse> chunks = documentMapper
                .selectChunks(access.tenantId(), access.userId(), documentId)
                .stream()
                .map(row -> new KnowledgeChunkResponse(row.getChunkId(), row.getChunkIndex(),
                        row.getContent(), row.getContent() == null ? 0 : row.getContent().length()))
                .toList();
        return new KnowledgeDocumentDetailResponse(document.getId(), document.getFilename(),
                document.getFileSize() == null ? 0 : document.getFileSize(), document.getFileType(),
                document.getChunkCount() == null ? 0 : document.getChunkCount(), document.getStatus(),
                document.getEmbeddingProvider(), document.getEmbeddingModel(),
                document.getCreatedAt(), document.getUpdatedAt(), chunks);
    }

    @Override
    @Transactional
    public void deleteChunk(Long userId, Long documentId, Long chunkId) {
        ResolvedUserAccess access = requireAccess(userId);
        KnowledgeDocument document = requireOwned(access, documentId);
        KnowledgeSearchRow chunk = documentMapper.selectChunkOwned(
                access.tenantId(), access.userId(), chunkId);
        if (chunk == null || !documentId.equals(chunk.getDocId())) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        int deleted = documentMapper.deleteChunkById(access.tenantId(), access.userId(), chunkId);
        if (deleted != 1) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        int removedIndex = chunk.getChunkIndex();
        List<Integer> remaining = documentMapper
                .selectChunks(access.tenantId(), access.userId(), documentId)
                .stream()
                .map(KnowledgeSearchRow::getChunkIndex)
                .filter(index -> index > removedIndex)
                .sorted()
                .toList();
        for (int index : remaining) {
            documentMapper.updateChunkIndex(access.tenantId(), access.userId(), documentId,
                    index, index - 1);
        }

        document.setChunkCount(document.getChunkCount() == null
                ? 0 : Math.max(0, document.getChunkCount() - 1));
        document.setUpdatedAt(LocalDateTime.now());
        documentMapper.updateById(document);
    }

    @Override
    @Transactional
    public int batchDelete(Long userId, List<Long> ids) {
        ResolvedUserAccess access = requireAccess(userId);
        List<Long> distinctIds = ids.stream().distinct().toList();
        return documentMapper.delete(new LambdaQueryWrapper<KnowledgeDocument>()
                .eq(KnowledgeDocument::getTenantId, access.tenantId())
                .eq(KnowledgeDocument::getUserId, access.userId())
                .in(KnowledgeDocument::getId, distinctIds));
    }

    @Override
    @Transactional
    public List<KnowledgeDocumentResponse> batchReindex(Long userId, List<Long> ids) {
        List<Long> distinctIds = ids.stream().distinct().toList();
        return distinctIds.stream().map(id -> reindex(userId, id)).toList();
    }

    @Override
    public PageResponse<KnowledgeDocumentResponse> list(Long userId, Long kbId, int page, int size) {
        ResolvedUserAccess access = requireAccess(userId);
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);
        LambdaQueryWrapper<KnowledgeDocument> wrapper = new LambdaQueryWrapper<KnowledgeDocument>()
                .eq(KnowledgeDocument::getTenantId, access.tenantId())
                .eq(KnowledgeDocument::getUserId, access.userId())
                .eq(kbId != null, KnowledgeDocument::getKbId, kbId)
                .orderByDesc(KnowledgeDocument::getCreatedAt)
                .orderByDesc(KnowledgeDocument::getId);
        Page<KnowledgeDocument> result = documentMapper.selectPage(Page.of(safePage, safeSize), wrapper);
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
                        row.getFilename(), row.getChunkIndex(), row.getContent(), row.getScore(), "DENSE"))
                .filter(record -> !isTocLikeChunk(record.content()))
                .toList();
        records = applyRerank(query, records);
        return new KnowledgeSearchResponse(descriptor.provider(), descriptor.model(),
                descriptor.dimension(), records);
    }

    @Override
    public KnowledgeSearchResponse searchInKnowledgeBase(Long userId, Long kbId,
                                                         KnowledgeSearchRequest request) {
        ResolvedUserAccess access = requireAccess(userId);
        KnowledgeBase knowledgeBase = requireKnowledgeBase(access, kbId);
        String query = request.query().strip();
        EmbeddingDescriptor descriptor = embeddingService.current();

        int denseTopK = knowledgeBase.getDenseTopK() == null ? 5 : knowledgeBase.getDenseTopK();
        int sparseTopK = knowledgeBase.getSparseTopK() == null ? 0 : knowledgeBase.getSparseTopK();
        int resultCap = request.topK() == null
                ? denseTopK + sparseTopK
                : Math.min(request.topK(), denseTopK + sparseTopK);

        Map<Long, KnowledgeSearchRow> rowsByChunk = new LinkedHashMap<>();
        Map<Long, Double> denseScores = new HashMap<>();
        Map<Long, Double> sparseScores = new HashMap<>();

        if (denseTopK > 0) {
            Long available = documentMapper.selectCount(new LambdaQueryWrapper<KnowledgeDocument>()
                    .eq(KnowledgeDocument::getTenantId, access.tenantId())
                    .eq(KnowledgeDocument::getUserId, access.userId())
                    .eq(KnowledgeDocument::getKbId, kbId)
                    .eq(KnowledgeDocument::getStatus, STATUS_READY)
                    .eq(KnowledgeDocument::getEmbeddingProvider, descriptor.provider())
                    .eq(KnowledgeDocument::getEmbeddingModel, descriptor.model()));
            if (available != null && available > 0) {
                EmbeddingResult embedding = embeddingService.embed(List.of(query));
                validateEmbeddingResult(descriptor, embedding, 1);
                double minScore = request.minScore() == null
                        ? properties.getRetrievalMinScore() : request.minScore();
                for (KnowledgeSearchRow row : documentMapper.searchDense(
                        access.tenantId(), access.userId(), kbId,
                        vectorLiteral(embedding.vectors().get(0)),
                        descriptor.provider(), descriptor.model(), minScore, denseTopK)) {
                    rowsByChunk.putIfAbsent(row.getChunkId(), row);
                    denseScores.put(row.getChunkId(), row.getScore());
                }
            }
        }

        if (sparseTopK > 0) {
            for (KnowledgeSearchRow row : documentMapper.searchSparse(
                    access.tenantId(), access.userId(), kbId, query,
                    properties.getSparseMinScore(), sparseTopK)) {
                rowsByChunk.putIfAbsent(row.getChunkId(), row);
                sparseScores.put(row.getChunkId(), row.getScore());
            }
        }

        List<KnowledgeSearchItemResponse> records = filterTocChunks(rowsByChunk.values().stream()
                .map(row -> {
                    double dense = denseScores.getOrDefault(row.getChunkId(), 0d);
                    double sparse = sparseScores.getOrDefault(row.getChunkId(), 0d);
                    String matchType = dense > 0 && sparse > 0 ? "HYBRID"
                            : (dense > 0 ? "DENSE" : "SPARSE");
                    return new KnowledgeSearchItemResponse(row.getDocId(), row.getChunkId(),
                            row.getFilename(), row.getChunkIndex(), row.getContent(),
                            (dense + sparse) / 2.0, matchType);
                })
                .sorted(Comparator.comparingDouble(KnowledgeSearchItemResponse::score).reversed())
                .toList());
        records = applyRerank(query, records);
        records = records.stream().limit(resultCap).toList();

        return new KnowledgeSearchResponse(descriptor.provider(), descriptor.model(),
                descriptor.dimension(), records);
    }

    @Override
    public EmbeddingStatusResponse embeddingStatus() {
        EmbeddingDescriptor descriptor = embeddingService.current();
        boolean rerankEnabled = rerankService != null && rerankService.configured();
        String rerankModel = rerankEnabled ? rerankService.model() : null;
        return new EmbeddingStatusResponse(properties.isEnabled(), descriptor.provider(),
                descriptor.model(), descriptor.dimension(), rerankEnabled, rerankModel);
    }

    /**
     * 对检索候选做 rerank 重排（若已配置）。失败时降级为原检索顺序，不阻断检索。
     */
    private List<KnowledgeSearchItemResponse> applyRerank(String query,
                                                          List<KnowledgeSearchItemResponse> records) {
        if (rerankService == null || !rerankService.configured() || records.size() < 2) {
            return records;
        }
        try {
            List<String> documents = records.stream()
                    .map(KnowledgeSearchItemResponse::content).toList();
            List<RerankService.RankedItem> ranked = rerankService.rerank(query, documents, records.size());
            List<KnowledgeSearchItemResponse> reranked = ranked.stream()
                    .filter(item -> item.index() >= 0 && item.index() < records.size())
                    .map(item -> {
                        KnowledgeSearchItemResponse record = records.get(item.index());
                        return new KnowledgeSearchItemResponse(record.docId(), record.chunkId(),
                                record.filename(), record.chunkIndex(), record.content(),
                                item.score(), record.matchType());
                    })
                    .toList();
            return reranked.isEmpty() ? records : reranked;
        } catch (RuntimeException ex) {
            log.warn("Rerank failed, fallback to retrieval order, query={}", query, ex);
            return records;
        }
    }

    /**
     * 过滤目录/索引类分块：整块中超过一半非空行包含连续点线（如“标题 …… 42”）时
     * 判定为目录页，对 RAG 回答没有内容价值，直接剔除，避免挤占正文引用位置。
     */
    private List<KnowledgeSearchItemResponse> filterTocChunks(List<KnowledgeSearchItemResponse> records) {
        return records.stream()
                .filter(record -> !isTocLikeChunk(record.content()))
                .toList();
    }

    private boolean isTocLikeChunk(String content) {
        if (content == null || content.isBlank()) return false;
        String[] lines = content.split("\\R");
        if (lines.length < 3) return false;
        int total = 0;
        int dotLines = 0;
        for (String line : lines) {
            String trimmed = line.strip();
            if (trimmed.isEmpty()) continue;
            total++;
            if (TOC_DOT_LINE.matcher(trimmed).matches()) dotLines++;
        }
        return total > 0 && (double) dotLines / total >= 0.5;
    }

    private ResolvedUserAccess requireAccess(Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.AUTH_REQUIRED);
        }
        return userAccessService.resolveActiveUser(userId);
    }

    private KnowledgeBase requireKnowledgeBase(ResolvedUserAccess access, Long kbId) {
        KnowledgeBase knowledgeBase = knowledgeBaseMapper.selectOne(new LambdaQueryWrapper<KnowledgeBase>()
                .eq(KnowledgeBase::getId, kbId)
                .eq(KnowledgeBase::getTenantId, access.tenantId())
                .eq(KnowledgeBase::getUserId, access.userId()));
        if (knowledgeBase == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return knowledgeBase;
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

    private void validateUploadFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID, "上传文件不能为空");
        }
        if (file.getSize() > uploadProperties.getFileMaxBytes()) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID, "文件不能超过 20MB");
        }
    }

    private Path createTempFile(MultipartFile file) {
        try {
            Path temp = Files.createTempFile("knowledge-", ".bin");
            file.transferTo(temp);
            return temp;
        } catch (IOException ex) {
            log.error("Knowledge document temp storage failed", ex);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "文件保存失败，请稍后重试");
        }
    }

    private void deleteTempFile(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ex) {
            log.warn("Unable to remove knowledge document temp file, path={}", path, ex);
        }
    }

    private String safeDisplayName(MultipartFile file) {
        String original = file.getOriginalFilename() == null ? "document" : file.getOriginalFilename();
        String clean = original.replace('\\', '/');
        clean = clean.substring(clean.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "").strip();
        if (clean.isBlank()) return "document";
        return clean.length() > 255 ? clean.substring(clean.length() - 255) : clean;
    }

    private String extensionLabel(String filename) {
        String lower = filename.toLowerCase(Locale.ROOT);
        int dot = lower.lastIndexOf('.');
        if (dot < 0 || dot == lower.length() - 1) return "FILE";
        String extension = lower.substring(dot + 1);
        return extension.length() <= 10 ? extension.toUpperCase(Locale.ROOT) : "FILE";
    }

    private KnowledgeDocument findByHash(ResolvedUserAccess access, Long kbId, String contentHash,
                                         EmbeddingDescriptor descriptor) {
        if (contentHash == null || contentHash.isBlank()) {
            return null;
        }
        return documentMapper.selectOne(new LambdaQueryWrapper<KnowledgeDocument>()
                .eq(KnowledgeDocument::getTenantId, access.tenantId())
                .eq(KnowledgeDocument::getUserId, access.userId())
                .eq(KnowledgeDocument::getKbId, kbId)
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
            return objectMapper.writeValueAsString(Map.of(
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
