package com.aiworkmate.service.impl;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.aiworkmate.config.EmbeddingProperties;
import com.aiworkmate.config.UploadProperties;
import com.aiworkmate.dto.KnowledgeSearchRequest;
import com.aiworkmate.entity.KnowledgeBase;
import com.aiworkmate.entity.KnowledgeDocument;
import com.aiworkmate.mapper.KnowledgeBaseMapper;
import com.aiworkmate.mapper.KnowledgeDocumentMapper;
import com.aiworkmate.service.EmbeddingService;
import com.aiworkmate.service.FileParserService;
import com.aiworkmate.service.KnowledgeChunker;
import com.aiworkmate.service.RerankService;
import com.aiworkmate.service.UserAccessService;
import com.aiworkmate.service.model.EmbeddingDescriptor;
import com.aiworkmate.service.model.EmbeddingResult;
import com.aiworkmate.service.model.KnowledgeSearchRow;
import com.aiworkmate.service.model.ParsedFile;
import com.aiworkmate.service.model.ResolvedUserAccess;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnowledgeServiceImplTest {

    private static final ResolvedUserAccess ACCESS = new ResolvedUserAccess(
            7L, "alice", 99L, "EMPLOYEE", List.of("EMPLOYEE"),
            List.of("knowledge:search"), List.of("SELF"), 1L);

    private static KnowledgeBase ownedKnowledgeBase() {
        KnowledgeBase knowledgeBase = new KnowledgeBase();
        knowledgeBase.setId(5L);
        knowledgeBase.setTenantId(99L);
        knowledgeBase.setUserId(7L);
        knowledgeBase.setName("公司制度");
        knowledgeBase.setChunkSize(1000);
        knowledgeBase.setChunkOverlap(120);
        knowledgeBase.setDenseTopK(5);
        knowledgeBase.setSparseTopK(5);
        return knowledgeBase;
    }

    private static KnowledgeServiceImpl service(KnowledgeDocumentMapper mapper,
                                                KnowledgeBaseMapper kbMapper,
                                                EmbeddingService embeddingService,
                                                UserAccessService accessService,
                                                FileParserService parser,
                                                EmbeddingProperties properties) {
        return service(mapper, kbMapper, embeddingService, accessService, parser, properties,
                mock(RerankService.class));
    }

    private static KnowledgeServiceImpl service(KnowledgeDocumentMapper mapper,
                                                KnowledgeBaseMapper kbMapper,
                                                EmbeddingService embeddingService,
                                                UserAccessService accessService,
                                                FileParserService parser,
                                                EmbeddingProperties properties,
                                                RerankService rerankService) {
        return new KnowledgeServiceImpl(mapper, kbMapper, embeddingService,
                new KnowledgeChunker(), accessService, parser,
                new UploadProperties(), properties, new ObjectMapper(), rerankService);
    }

    @Test
    void searchShouldAlwaysScopeByResolvedTenantAndUserAndCurrentModel() {
        KnowledgeDocumentMapper mapper = mock(KnowledgeDocumentMapper.class);
        EmbeddingService embeddingService = mock(EmbeddingService.class);
        UserAccessService accessService = mock(UserAccessService.class);
        EmbeddingProperties properties = new EmbeddingProperties();
        properties.setDimension(3);
        when(accessService.resolveActiveUser(7L)).thenReturn(ACCESS);
        when(embeddingService.current()).thenReturn(new EmbeddingDescriptor("api", "model-a", 3));
        when(embeddingService.embed(List.of("policy")))
                .thenReturn(new EmbeddingResult("api", "model-a",
                        List.of(new float[]{0.1F, 0.2F, 0.3F})));
        when(mapper.selectCount(any())).thenReturn(1L);
        KnowledgeSearchRow row = new KnowledgeSearchRow();
        row.setDocId(1L);
        row.setChunkId(2L);
        row.setFilename("policy.txt");
        row.setChunkIndex(0);
        row.setContent("policy content");
        row.setScore(0.9);
        when(mapper.search(eq(99L), eq(7L), anyString(), eq("api"), eq("model-a"),
                eq(0.4), eq(3))).thenReturn(List.of(row));
        KnowledgeServiceImpl service = service(mapper, mock(KnowledgeBaseMapper.class),
                embeddingService, accessService, mock(FileParserService.class), properties);

        var result = service.search(7L, new KnowledgeSearchRequest("policy", 3, 0.4));

        assertThat(result.records()).hasSize(1);
        assertThat(result.provider()).isEqualTo("api");
        assertThat(result.records().get(0).matchType()).isEqualTo("DENSE");
        verify(mapper).search(eq(99L), eq(7L), anyString(), eq("api"), eq("model-a"),
                eq(0.4), eq(3));
    }

    @Test
    void searchShouldFailClosedWithoutRealtimePermission() {
        UserAccessService accessService = mock(UserAccessService.class);
        when(accessService.resolveActiveUser(7L)).thenReturn(new ResolvedUserAccess(
                7L, "alice", 99L, "EMPLOYEE", List.of("EMPLOYEE"),
                List.of(), List.of("SELF"), 1L));
        KnowledgeDocumentMapper mapper = mock(KnowledgeDocumentMapper.class);
        EmbeddingService embeddingService = mock(EmbeddingService.class);
        KnowledgeServiceImpl service = service(mapper, mock(KnowledgeBaseMapper.class),
                embeddingService, accessService, mock(FileParserService.class), new EmbeddingProperties());

        assertThatThrownBy(() -> service.search(7L,
                new KnowledgeSearchRequest("policy", 5, 0.4)))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo("PERMISSION_DENIED"));

        org.mockito.Mockito.verifyNoInteractions(mapper, embeddingService);
    }

    @Test
    void searchShouldEnforceHardTopKTenInDomainLayer() {
        KnowledgeDocumentMapper mapper = mock(KnowledgeDocumentMapper.class);
        EmbeddingService embeddingService = mock(EmbeddingService.class);
        UserAccessService accessService = mock(UserAccessService.class);
        EmbeddingProperties properties = new EmbeddingProperties();
        properties.setDimension(3);
        when(accessService.resolveActiveUser(7L)).thenReturn(ACCESS);
        when(embeddingService.current()).thenReturn(new EmbeddingDescriptor("api", "model-a", 3));
        when(embeddingService.embed(List.of("policy"))).thenReturn(
                new EmbeddingResult("api", "model-a", List.of(new float[]{0.1F, 0.2F, 0.3F})));
        when(mapper.selectCount(any())).thenReturn(1L);
        when(mapper.search(eq(99L), eq(7L), anyString(), eq("api"), eq("model-a"),
                eq(0.4), eq(10))).thenReturn(List.of());
        KnowledgeServiceImpl service = service(mapper, mock(KnowledgeBaseMapper.class),
                embeddingService, accessService, mock(FileParserService.class), properties);

        service.search(7L, new KnowledgeSearchRequest("policy", 20, 0.4));

        verify(mapper).search(eq(99L), eq(7L), anyString(), eq("api"), eq("model-a"),
                eq(0.4), eq(10));
    }

    @Test
    void uploadShouldParseFileAndCreateVectorizedDocument() {
        KnowledgeDocumentMapper mapper = mock(KnowledgeDocumentMapper.class);
        KnowledgeBaseMapper kbMapper = mock(KnowledgeBaseMapper.class);
        EmbeddingService embeddingService = mock(EmbeddingService.class);
        UserAccessService accessService = mock(UserAccessService.class);
        FileParserService parser = mock(FileParserService.class);
        EmbeddingProperties properties = new EmbeddingProperties();
        properties.setDimension(3);
        when(accessService.resolveActiveUser(7L)).thenReturn(ACCESS);
        when(kbMapper.selectOne(any())).thenReturn(ownedKnowledgeBase());
        when(embeddingService.current()).thenReturn(new EmbeddingDescriptor("local", "model-a", 3));
        when(parser.parse(any(), eq("policy.pdf"), any(Long.class)))
                .thenReturn(new ParsedFile("application/pdf",
                        "Annual leave must be approved by the direct manager.", false));
        when(embeddingService.embed(any()))
                .thenReturn(new EmbeddingResult("local", "model-a",
                        List.of(new float[]{0.1F, 0.2F, 0.3F})));
        when(mapper.insert(any(KnowledgeDocument.class))).thenAnswer(invocation -> {
            KnowledgeDocument doc = invocation.getArgument(0);
            doc.setId(42L);
            return 1;
        });
        KnowledgeServiceImpl service = service(mapper, kbMapper, embeddingService,
                accessService, parser, properties);

        MockMultipartFile file = new MockMultipartFile("file", "policy.pdf",
                "application/pdf", "dummy bytes".getBytes());
        var result = service.upload(7L, 5L, file);

        assertThat(result.id()).isEqualTo(42L);
        assertThat(result.filename()).isEqualTo("policy.pdf");
        assertThat(result.fileType()).isEqualTo("PDF");
        assertThat(result.status()).isEqualTo("READY");
        verify(mapper).insertChunk(eq(99L), eq(7L), eq(42L), anyInt(),
                anyString(), anyString(), eq("local"), eq("model-a"), anyString());
    }

    @Test
    void uploadShouldAcceptImageWithOcrText() {
        KnowledgeDocumentMapper mapper = mock(KnowledgeDocumentMapper.class);
        KnowledgeBaseMapper kbMapper = mock(KnowledgeBaseMapper.class);
        EmbeddingService embeddingService = mock(EmbeddingService.class);
        UserAccessService accessService = mock(UserAccessService.class);
        FileParserService parser = mock(FileParserService.class);
        EmbeddingProperties properties = new EmbeddingProperties();
        properties.setDimension(3);
        when(accessService.resolveActiveUser(7L)).thenReturn(ACCESS);
        when(kbMapper.selectOne(any())).thenReturn(ownedKnowledgeBase());
        when(embeddingService.current()).thenReturn(new EmbeddingDescriptor("local", "model-a", 3));
        when(parser.parse(any(), eq("scan.png"), any(Long.class)))
                .thenReturn(new ParsedFile("image/png", "发票金额 1000 元", true));
        when(embeddingService.embed(any()))
                .thenReturn(new EmbeddingResult("local", "model-a",
                        List.of(new float[]{0.1F, 0.2F, 0.3F})));
        when(mapper.insert(any(KnowledgeDocument.class))).thenAnswer(invocation -> {
            KnowledgeDocument doc = invocation.getArgument(0);
            doc.setId(43L);
            return 1;
        });
        KnowledgeServiceImpl service = service(mapper, kbMapper, embeddingService,
                accessService, parser, properties);

        MockMultipartFile file = new MockMultipartFile("file", "scan.png",
                "image/png", "png".getBytes());
        var result = service.upload(7L, 5L, file);

        assertThat(result.id()).isEqualTo(43L);
        assertThat(result.fileType()).isEqualTo("PNG");
        assertThat(result.status()).isEqualTo("READY");
    }

    @Test
    void uploadShouldRejectImageWithoutOcrText() {
        KnowledgeDocumentMapper mapper = mock(KnowledgeDocumentMapper.class);
        KnowledgeBaseMapper kbMapper = mock(KnowledgeBaseMapper.class);
        UserAccessService accessService = mock(UserAccessService.class);
        FileParserService parser = mock(FileParserService.class);
        when(accessService.resolveActiveUser(7L)).thenReturn(ACCESS);
        when(kbMapper.selectOne(any())).thenReturn(ownedKnowledgeBase());
        when(parser.parse(any(), anyString(), any(Long.class)))
                .thenReturn(new ParsedFile("image/png", null, true));
        KnowledgeServiceImpl service = service(mapper, kbMapper,
                mock(EmbeddingService.class), accessService, parser, new EmbeddingProperties());

        MockMultipartFile file = new MockMultipartFile("file", "photo.png",
                "image/png", "png".getBytes());

        assertThatThrownBy(() -> service.upload(7L, 5L, file))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.REQUEST_INVALID.getErrorCode()));
    }

    @Test
    void batchDeleteShouldReturnMapperDeletedCount() {
        KnowledgeDocumentMapper mapper = mock(KnowledgeDocumentMapper.class);
        UserAccessService accessService = mock(UserAccessService.class);
        when(accessService.resolveActiveUser(7L)).thenReturn(ACCESS);
        when(mapper.delete(any())).thenReturn(2);
        KnowledgeServiceImpl service = service(mapper, mock(KnowledgeBaseMapper.class),
                mock(EmbeddingService.class), accessService, mock(FileParserService.class),
                new EmbeddingProperties());

        int deleted = service.batchDelete(7L, List.of(1L, 2L, 2L));

        assertThat(deleted).isEqualTo(2);
        verify(mapper).delete(any());
    }

    @Test
    void batchReindexShouldReindexAllDocuments() {
        KnowledgeDocumentMapper mapper = mock(KnowledgeDocumentMapper.class);
        EmbeddingService embeddingService = mock(EmbeddingService.class);
        UserAccessService accessService = mock(UserAccessService.class);
        EmbeddingProperties properties = new EmbeddingProperties();
        properties.setDimension(3);
        when(accessService.resolveActiveUser(7L)).thenReturn(ACCESS);
        when(embeddingService.current()).thenReturn(new EmbeddingDescriptor("local", "model-a", 3));
        KnowledgeDocument doc = new KnowledgeDocument();
        doc.setId(42L);
        doc.setKbId(5L);
        doc.setFilename("policy.txt");
        doc.setFileType("TEXT");
        doc.setStatus("READY");
        when(mapper.selectOne(any())).thenReturn(doc);
        when(mapper.selectChunkContents(eq(99L), eq(7L), eq(42L))).thenReturn(List.of("chunk a"));
        when(embeddingService.embed(List.of("chunk a")))
                .thenReturn(new EmbeddingResult("local", "model-a",
                        List.of(new float[]{0.1F, 0.2F, 0.3F})));
        when(mapper.updateChunkEmbedding(eq(99L), eq(7L), eq(42L), eq(0),
                anyString(), eq("local"), eq("model-a"))).thenReturn(1);
        KnowledgeServiceImpl service = service(mapper, mock(KnowledgeBaseMapper.class),
                embeddingService, accessService, mock(FileParserService.class), properties);

        var results = service.batchReindex(7L, List.of(42L));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).id()).isEqualTo(42L);
        verify(mapper).updateChunkEmbedding(eq(99L), eq(7L), eq(42L), eq(0),
                anyString(), eq("local"), eq("model-a"));
    }

    @Test
    void searchInKnowledgeBaseShouldMergeDenseAndSparse() {
        KnowledgeDocumentMapper mapper = mock(KnowledgeDocumentMapper.class);
        KnowledgeBaseMapper kbMapper = mock(KnowledgeBaseMapper.class);
        EmbeddingService embeddingService = mock(EmbeddingService.class);
        UserAccessService accessService = mock(UserAccessService.class);
        EmbeddingProperties properties = new EmbeddingProperties();
        properties.setDimension(3);
        when(accessService.resolveActiveUser(7L)).thenReturn(ACCESS);
        when(kbMapper.selectOne(any())).thenReturn(ownedKnowledgeBase());
        when(embeddingService.current()).thenReturn(new EmbeddingDescriptor("local", "model-a", 3));
        when(mapper.selectCount(any())).thenReturn(1L);
        when(embeddingService.embed(any()))
                .thenReturn(new EmbeddingResult("local", "model-a",
                        List.of(new float[]{0.1F, 0.2F, 0.3F})));

        KnowledgeSearchRow dense = row(1L, 10L, "policy.txt", 0.8);
        when(mapper.searchDense(eq(99L), eq(7L), eq(5L), anyString(), eq("local"),
                eq("model-a"), eq(0.35), eq(5))).thenReturn(List.of(dense));
        KnowledgeSearchRow sparse = row(2L, 11L, "handbook.txt", 0.6);
        when(mapper.searchSparse(eq(99L), eq(7L), eq(5L), eq("假期"), eq(0.05), eq(5)))
                .thenReturn(List.of(sparse));

        KnowledgeServiceImpl service = service(mapper, kbMapper, embeddingService,
                accessService, mock(FileParserService.class), properties);

        var result = service.searchInKnowledgeBase(7L, 5L,
                new KnowledgeSearchRequest("假期", null, null));

        assertThat(result.records()).hasSize(2);
        assertThat(result.records().get(0).matchType()).isEqualTo("DENSE");
        assertThat(result.records().get(0).score()).isEqualTo(0.4);
        assertThat(result.records().get(1).matchType()).isEqualTo("SPARSE");
        assertThat(result.records().get(1).score()).isEqualTo(0.3);
    }

    @Test
    void searchInKnowledgeBaseShouldCapMergedResultsByTopK() {
        KnowledgeDocumentMapper mapper = mock(KnowledgeDocumentMapper.class);
        KnowledgeBaseMapper kbMapper = mock(KnowledgeBaseMapper.class);
        EmbeddingService embeddingService = mock(EmbeddingService.class);
        UserAccessService accessService = mock(UserAccessService.class);
        EmbeddingProperties properties = new EmbeddingProperties();
        properties.setDimension(3);
        when(accessService.resolveActiveUser(7L)).thenReturn(ACCESS);
        when(kbMapper.selectOne(any())).thenReturn(ownedKnowledgeBase());
        when(embeddingService.current()).thenReturn(new EmbeddingDescriptor("local", "model-a", 3));
        when(mapper.selectCount(any())).thenReturn(1L);
        when(embeddingService.embed(any()))
                .thenReturn(new EmbeddingResult("local", "model-a",
                        List.of(new float[]{0.1F, 0.2F, 0.3F})));
        when(mapper.searchDense(eq(99L), eq(7L), eq(5L), anyString(), eq("local"),
                eq("model-a"), eq(0.35), eq(5))).thenReturn(List.of(row(1L, 10L, "a.txt", 0.9)));
        when(mapper.searchSparse(eq(99L), eq(7L), eq(5L), eq("假期"), eq(0.05), eq(5)))
                .thenReturn(List.of(
                        row(2L, 11L, "b.txt", 0.6),
                        row(3L, 12L, "c.txt", 0.5)));

        KnowledgeServiceImpl service = service(mapper, kbMapper, embeddingService,
                accessService, mock(FileParserService.class), properties);

        var result = service.searchInKnowledgeBase(7L, 5L,
                new KnowledgeSearchRequest("假期", 2, null));

        assertThat(result.records()).hasSize(2);
    }

    @Test
    void searchShouldFilterTocLikeChunks() {
        KnowledgeDocumentMapper mapper = mock(KnowledgeDocumentMapper.class);
        EmbeddingService embeddingService = mock(EmbeddingService.class);
        UserAccessService accessService = mock(UserAccessService.class);
        EmbeddingProperties properties = new EmbeddingProperties();
        properties.setDimension(3);
        when(accessService.resolveActiveUser(7L)).thenReturn(ACCESS);
        when(embeddingService.current()).thenReturn(new EmbeddingDescriptor("api", "model-a", 3));
        when(embeddingService.embed(List.of("policy")))
                .thenReturn(new EmbeddingResult("api", "model-a",
                        List.of(new float[]{0.1F, 0.2F, 0.3F})));
        when(mapper.selectCount(any())).thenReturn(1L);
        // 目录分块相似度更高，但必须被过滤掉，正文分块保留
        when(mapper.search(eq(99L), eq(7L), anyString(), eq("api"), eq("model-a"),
                eq(0.4), eq(3))).thenReturn(List.of(tocRow(1L, 10L, 0.9), row(1L, 42L, "book.pdf", 0.6)));

        KnowledgeServiceImpl service = service(mapper, mock(KnowledgeBaseMapper.class),
                embeddingService, accessService, mock(FileParserService.class), properties);

        var result = service.search(7L, new KnowledgeSearchRequest("policy", 3, 0.4));

        assertThat(result.records()).hasSize(1);
        assertThat(result.records().get(0).chunkId()).isEqualTo(42L);
    }

    @Test
    void searchInKnowledgeBaseShouldFilterTocLikeChunks() {
        KnowledgeDocumentMapper mapper = mock(KnowledgeDocumentMapper.class);
        KnowledgeBaseMapper kbMapper = mock(KnowledgeBaseMapper.class);
        EmbeddingService embeddingService = mock(EmbeddingService.class);
        UserAccessService accessService = mock(UserAccessService.class);
        EmbeddingProperties properties = new EmbeddingProperties();
        properties.setDimension(3);
        when(accessService.resolveActiveUser(7L)).thenReturn(ACCESS);
        when(kbMapper.selectOne(any())).thenReturn(ownedKnowledgeBase());
        when(embeddingService.current()).thenReturn(new EmbeddingDescriptor("local", "model-a", 3));
        when(mapper.selectCount(any())).thenReturn(1L);
        when(embeddingService.embed(any()))
                .thenReturn(new EmbeddingResult("local", "model-a",
                        List.of(new float[]{0.1F, 0.2F, 0.3F})));
        when(mapper.searchDense(eq(99L), eq(7L), eq(5L), anyString(), eq("local"),
                eq("model-a"), eq(0.35), eq(5))).thenReturn(List.of(tocRow(1L, 10L, 0.9)));
        when(mapper.searchSparse(eq(99L), eq(7L), eq(5L), eq("假期"), eq(0.05), eq(5)))
                .thenReturn(List.of(row(2L, 11L, "body.txt", 0.6)));

        KnowledgeServiceImpl service = service(mapper, kbMapper, embeddingService,
                accessService, mock(FileParserService.class), properties);

        var result = service.searchInKnowledgeBase(7L, 5L,
                new KnowledgeSearchRequest("假期", null, null));

        assertThat(result.records()).hasSize(1);
        assertThat(result.records().get(0).chunkId()).isEqualTo(11L);
        assertThat(result.records().get(0).matchType()).isEqualTo("SPARSE");
    }

    @Test
    void searchShouldRerankWhenConfigured() {
        KnowledgeDocumentMapper mapper = mock(KnowledgeDocumentMapper.class);
        EmbeddingService embeddingService = mock(EmbeddingService.class);
        UserAccessService accessService = mock(UserAccessService.class);
        RerankService rerankService = mock(RerankService.class);
        EmbeddingProperties properties = new EmbeddingProperties();
        properties.setDimension(3);
        when(accessService.resolveActiveUser(7L)).thenReturn(ACCESS);
        when(embeddingService.current()).thenReturn(new EmbeddingDescriptor("api", "model-a", 3));
        when(embeddingService.embed(List.of("policy")))
                .thenReturn(new EmbeddingResult("api", "model-a",
                        List.of(new float[]{0.1F, 0.2F, 0.3F})));
        when(mapper.selectCount(any())).thenReturn(1L);
        KnowledgeSearchRow first = row(1L, 10L, "a.txt", 0.9);
        KnowledgeSearchRow second = row(1L, 11L, "b.txt", 0.6);
        when(mapper.search(eq(99L), eq(7L), anyString(), eq("api"), eq("model-a"),
                eq(0.4), eq(3))).thenReturn(List.of(first, second));
        // rerank 把第 2 条（b.txt）排到第 1
        when(rerankService.configured()).thenReturn(true);
        when(rerankService.rerank(eq("policy"), eq(List.of("a.txt content", "b.txt content")), eq(2)))
                .thenReturn(List.of(
                        new RerankService.RankedItem(1, 0.95),
                        new RerankService.RankedItem(0, 0.4)));

        KnowledgeServiceImpl service = service(mapper, mock(KnowledgeBaseMapper.class),
                embeddingService, accessService, mock(FileParserService.class), properties, rerankService);

        var result = service.search(7L, new KnowledgeSearchRequest("policy", 3, 0.4));

        assertThat(result.records()).hasSize(2);
        assertThat(result.records().get(0).chunkId()).isEqualTo(11L);
        assertThat(result.records().get(0).score()).isEqualTo(0.95);
        assertThat(result.records().get(1).chunkId()).isEqualTo(10L);
    }

    @Test
    void searchShouldFallBackToOriginalOrderWhenRerankFails() {
        KnowledgeDocumentMapper mapper = mock(KnowledgeDocumentMapper.class);
        EmbeddingService embeddingService = mock(EmbeddingService.class);
        UserAccessService accessService = mock(UserAccessService.class);
        RerankService rerankService = mock(RerankService.class);
        EmbeddingProperties properties = new EmbeddingProperties();
        properties.setDimension(3);
        when(accessService.resolveActiveUser(7L)).thenReturn(ACCESS);
        when(embeddingService.current()).thenReturn(new EmbeddingDescriptor("api", "model-a", 3));
        when(embeddingService.embed(List.of("policy")))
                .thenReturn(new EmbeddingResult("api", "model-a",
                        List.of(new float[]{0.1F, 0.2F, 0.3F})));
        when(mapper.selectCount(any())).thenReturn(1L);
        when(mapper.search(eq(99L), eq(7L), anyString(), eq("api"), eq("model-a"),
                eq(0.4), eq(3))).thenReturn(List.of(row(1L, 10L, "a.txt", 0.9), row(1L, 11L, "b.txt", 0.6)));
        when(rerankService.configured()).thenReturn(true);
        when(rerankService.rerank(anyString(), any(), anyInt()))
                .thenThrow(new RuntimeException("rerank service down"));

        KnowledgeServiceImpl service = service(mapper, mock(KnowledgeBaseMapper.class),
                embeddingService, accessService, mock(FileParserService.class), properties, rerankService);

        var result = service.search(7L, new KnowledgeSearchRequest("policy", 3, 0.4));

        assertThat(result.records()).hasSize(2);
        assertThat(result.records().get(0).chunkId()).isEqualTo(10L);
        assertThat(result.records().get(1).chunkId()).isEqualTo(11L);
    }

    private static KnowledgeSearchRow tocRow(Long docId, Long chunkId, double score) {
        KnowledgeSearchRow row = new KnowledgeSearchRow();
        row.setDocId(docId);
        row.setChunkId(chunkId);
        row.setFilename("book.pdf");
        row.setChunkIndex(0);
        row.setContent("""
                1. 前言 ................................ ix
                2. 语言特性 ................................ 8
                3. 对设计的影响 ................................ 20
                """);
        row.setScore(score);
        return row;
    }

    private static KnowledgeSearchRow row(Long docId, Long chunkId, String filename, double score) {
        KnowledgeSearchRow row = new KnowledgeSearchRow();
        row.setDocId(docId);
        row.setChunkId(chunkId);
        row.setFilename(filename);
        row.setChunkIndex(0);
        row.setContent(filename + " content");
        row.setScore(score);
        return row;
    }

    @Test
    void documentDetailShouldReturnDocumentWithChunks() {
        KnowledgeDocumentMapper mapper = mock(KnowledgeDocumentMapper.class);
        UserAccessService accessService = mock(UserAccessService.class);
        when(accessService.resolveActiveUser(7L)).thenReturn(ACCESS);
        KnowledgeDocument doc = new KnowledgeDocument();
        doc.setId(42L);
        doc.setFilename("policy.txt");
        doc.setFileType("TEXT");
        doc.setFileSize(128L);
        doc.setChunkCount(2);
        doc.setStatus("READY");
        doc.setEmbeddingProvider("local");
        doc.setEmbeddingModel("model-a");
        when(mapper.selectOne(any())).thenReturn(doc);

        KnowledgeSearchRow first = new KnowledgeSearchRow();
        first.setChunkId(10L);
        first.setChunkIndex(0);
        first.setContent("first chunk");
        KnowledgeSearchRow second = new KnowledgeSearchRow();
        second.setChunkId(11L);
        second.setChunkIndex(1);
        second.setContent("second chunk body");
        when(mapper.selectChunks(eq(99L), eq(7L), eq(42L))).thenReturn(List.of(first, second));

        KnowledgeServiceImpl service = service(mapper, mock(KnowledgeBaseMapper.class),
                mock(EmbeddingService.class), accessService, mock(FileParserService.class),
                new EmbeddingProperties());

        var result = service.documentDetail(7L, 42L);

        assertThat(result.id()).isEqualTo(42L);
        assertThat(result.chunks()).hasSize(2);
        assertThat(result.chunks().get(0).vectorId()).isEqualTo(10L);
        assertThat(result.chunks().get(0).charCount()).isEqualTo("first chunk".length());
        assertThat(result.chunks().get(1).charCount()).isEqualTo("second chunk body".length());
    }

    @Test
    void deleteChunkShouldRemoveAndRenumberFollowingChunks() {
        KnowledgeDocumentMapper mapper = mock(KnowledgeDocumentMapper.class);
        UserAccessService accessService = mock(UserAccessService.class);
        when(accessService.resolveActiveUser(7L)).thenReturn(ACCESS);
        KnowledgeDocument doc = new KnowledgeDocument();
        doc.setId(42L);
        doc.setFilename("policy.txt");
        doc.setChunkCount(5);
        when(mapper.selectOne(any())).thenReturn(doc);

        KnowledgeSearchRow removed = new KnowledgeSearchRow();
        removed.setChunkId(10L);
        removed.setDocId(42L);
        removed.setChunkIndex(2);
        when(mapper.selectChunkOwned(eq(99L), eq(7L), eq(10L))).thenReturn(removed);
        when(mapper.deleteChunkById(eq(99L), eq(7L), eq(10L))).thenReturn(1);
        KnowledgeSearchRow idx0 = chunkRow(0);
        KnowledgeSearchRow idx1 = chunkRow(1);
        KnowledgeSearchRow idx3 = chunkRow(3);
        KnowledgeSearchRow idx4 = chunkRow(4);
        when(mapper.selectChunks(eq(99L), eq(7L), eq(42L)))
                .thenReturn(List.of(idx0, idx1, idx3, idx4));
        when(mapper.updateChunkIndex(eq(99L), eq(7L), eq(42L), eq(3), eq(2))).thenReturn(1);
        when(mapper.updateChunkIndex(eq(99L), eq(7L), eq(42L), eq(4), eq(3))).thenReturn(1);

        KnowledgeServiceImpl service = service(mapper, mock(KnowledgeBaseMapper.class),
                mock(EmbeddingService.class), accessService, mock(FileParserService.class),
                new EmbeddingProperties());

        service.deleteChunk(7L, 42L, 10L);

        verify(mapper).deleteChunkById(eq(99L), eq(7L), eq(10L));
        verify(mapper).updateChunkIndex(eq(99L), eq(7L), eq(42L), eq(3), eq(2));
        verify(mapper).updateChunkIndex(eq(99L), eq(7L), eq(42L), eq(4), eq(3));
        verify(mapper).updateById(any(KnowledgeDocument.class));
        assertThat(doc.getChunkCount()).isEqualTo(4);
    }

    private static KnowledgeSearchRow chunkRow(int index) {
        KnowledgeSearchRow row = new KnowledgeSearchRow();
        row.setChunkId(100L + index);
        row.setDocId(42L);
        row.setChunkIndex(index);
        row.setContent("chunk " + index);
        return row;
    }
}
