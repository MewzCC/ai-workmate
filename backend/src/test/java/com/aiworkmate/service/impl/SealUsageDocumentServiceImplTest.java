package com.aiworkmate.service.impl;

import com.aiworkmate.common.BusinessException;
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
import com.aiworkmate.service.UserAccessService;
import com.aiworkmate.service.model.ParsedFile;
import com.aiworkmate.service.model.ResolvedUserAccess;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.InputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SealUsageDocumentServiceImplTest {
    private static final long TENANT_ID = 1L;
    private static final long ACTOR_ID = 1001L;
    private static final long USAGE_ID = 66L;

    @Mock private SealUsageDocumentMapper documentMapper;
    @Mock private SealUsageMapper sealUsageMapper;
    @Mock private UserMapper userMapper;
    @Mock private UserAccessService userAccessService;
    @Mock private FileParserService fileParserService;
    @Mock private ObjectStorageService objectStorageService;
    @Mock private BusinessAuditService auditService;

    private SealUsageDocumentServiceImpl service;

    @BeforeEach
    void setUp() {
        initializeTableMetadata();
        UploadProperties properties = new UploadProperties();
        properties.setSealDocumentStoragePrefix("seal-documents/");
        service = new SealUsageDocumentServiceImpl(documentMapper, sealUsageMapper, userMapper,
                userAccessService, fileParserService, objectStorageService, properties, auditService);
    }

    @Test
    void uploadReturnsControlledUrlWithoutStorageKey() {
        stubAccess(TENANT_ID, List.of("seal:register"));
        when(sealUsageMapper.selectById(USAGE_ID)).thenReturn(usage("APPROVED", TENANT_ID, ACTOR_ID));
        when(fileParserService.parse(any(), anyString(), anyLong()))
                .thenReturn(new ParsedFile("application/pdf", "contract", false));
        when(documentMapper.insert(any(SealUsageDocument.class))).thenAnswer(invocation -> {
            SealUsageDocument document = invocation.getArgument(0);
            document.setId(9L);
            return 1;
        });
        when(userMapper.selectById(ACTOR_ID)).thenReturn(user());

        SealUsageDocumentResponse response = service.upload(actor(), USAGE_ID, pdf());

        ArgumentCaptor<SealUsageDocument> captor = ArgumentCaptor.forClass(SealUsageDocument.class);
        verify(documentMapper).insert(captor.capture());
        assertThat(captor.getValue().getStorageKey()).startsWith("seal-documents/1/66/");
        assertThat(response.contentUrl())
                .isEqualTo("/api/admin-assets/seal-usages/66/documents/9/content");
        assertThat(response.toString()).doesNotContain(captor.getValue().getStorageKey());
    }

    @Test
    void uploadDeletesObjectWhenMetadataInsertFails() {
        stubAccess(TENANT_ID, List.of("seal:register"));
        when(sealUsageMapper.selectById(USAGE_ID)).thenReturn(usage("USED", TENANT_ID, ACTOR_ID));
        when(fileParserService.parse(any(), anyString(), anyLong()))
                .thenReturn(new ParsedFile("application/pdf", "contract", false));
        doThrow(new IllegalStateException("db unavailable")).when(documentMapper)
                .insert(any(SealUsageDocument.class));

        assertThatThrownBy(() -> service.upload(actor(), USAGE_ID, pdf()))
                .isInstanceOf(IllegalStateException.class);

        verify(objectStorageService).delete(anyString());
    }

    @Test
    void pendingRequestCannotArchiveDocument() {
        stubAccess(TENANT_ID, List.of("seal:register"));
        when(sealUsageMapper.selectById(USAGE_ID)).thenReturn(usage("PENDING", TENANT_ID, ACTOR_ID));

        assertThatThrownBy(() -> service.upload(actor(), USAGE_ID, pdf()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo("BUSINESS_STATE_INVALID");

        verify(objectStorageService, never()).store(anyString(), any(InputStream.class), anyLong(), anyString());
    }

    @Test
    void crossTenantUsageIsHiddenBeforeDocumentLookup() {
        stubAccess(TENANT_ID, List.of("seal:register"));
        when(sealUsageMapper.selectById(USAGE_ID)).thenReturn(usage("USED", 2L, ACTOR_ID));

        assertThatThrownBy(() -> service.list(actor(), USAGE_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo("RESOURCE_NOT_FOUND");

        verify(documentMapper, never()).selectList(any());
    }

    private void stubAccess(Long tenantId, List<String> permissions) {
        when(userAccessService.resolveActiveUser(ACTOR_ID)).thenReturn(new ResolvedUserAccess(
                ACTOR_ID, "actor@example.com", tenantId, "EMPLOYEE", List.of("EMPLOYEE"),
                permissions, List.of("SELF"), 1L));
    }

    private AuthenticatedUser actor() {
        return new AuthenticatedUser(ACTOR_ID, "actor@example.com", TENANT_ID, "EMPLOYEE",
                List.of("EMPLOYEE"), List.of(), List.of("SELF"), 1L);
    }

    private SealUsage usage(String status, Long tenantId, Long applicantId) {
        SealUsage usage = new SealUsage();
        usage.setId(USAGE_ID);
        usage.setTenantId(tenantId);
        usage.setApplicantUserId(applicantId);
        usage.setStatus(status);
        return usage;
    }

    private User user() {
        User user = new User();
        user.setId(ACTOR_ID);
        user.setDisplayName("经办人");
        user.setUsername("actor");
        return user;
    }

    private MockMultipartFile pdf() {
        return new MockMultipartFile("file", "sealed-contract.pdf", "application/pdf",
                "%PDF-content".getBytes());
    }

    private static void initializeTableMetadata() {
        if (TableInfoHelper.getTableInfo(SealUsageDocument.class) != null) return;
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "test");
        assistant.setCurrentNamespace(SealUsageDocumentMapper.class.getName());
        TableInfoHelper.initTableInfo(assistant, SealUsageDocument.class);
    }
}
