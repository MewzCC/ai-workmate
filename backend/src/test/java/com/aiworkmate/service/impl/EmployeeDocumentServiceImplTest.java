package com.aiworkmate.service.impl;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.config.UploadProperties;
import com.aiworkmate.dto.AccessUserRow;
import com.aiworkmate.dto.EmployeeDocumentResponse;
import com.aiworkmate.entity.EmployeeDocument;
import com.aiworkmate.mapper.AccessControlMapper;
import com.aiworkmate.mapper.EmployeeDocumentMapper;
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
import java.time.LocalDateTime;
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
class EmployeeDocumentServiceImplTest {
    private static final long TENANT_ID = 1L;
    private static final long ACTOR_ID = 1001L;
    private static final long EMPLOYEE_ID = 2002L;

    @Mock private EmployeeDocumentMapper documentMapper;
    @Mock private AccessControlMapper accessControlMapper;
    @Mock private UserAccessService userAccessService;
    @Mock private FileParserService fileParserService;
    @Mock private ObjectStorageService objectStorageService;
    @Mock private BusinessAuditService auditService;

    private EmployeeDocumentServiceImpl service;
    private UploadProperties properties;

    @BeforeEach
    void setUp() {
        initializeTableMetadata(EmployeeDocument.class);
        properties = new UploadProperties();
        properties.setEmployeeDocumentStoragePrefix("employee-documents/");
        service = new EmployeeDocumentServiceImpl(documentMapper, accessControlMapper,
                userAccessService, fileParserService, objectStorageService, properties, auditService);
    }

    @Test
    void uploadStoresObjectKeyOnlyAndReturnsControlledContentUrl() {
        when(userAccessService.resolveActiveUser(ACTOR_ID)).thenReturn(access(ACTOR_ID, List.of("hr:manage")));
        when(accessControlMapper.selectUsers(TENANT_ID)).thenReturn(users());
        when(fileParserService.parse(any(), anyString(), anyLong()))
                .thenReturn(new ParsedFile("application/pdf", "contract", false));
        when(documentMapper.insert(any(EmployeeDocument.class))).thenAnswer(invocation -> {
            EmployeeDocument document = invocation.getArgument(0);
            document.setId(9L);
            return 1;
        });

        EmployeeDocumentResponse response = service.upload(actor(), EMPLOYEE_ID, "CONTRACT", pdf());

        ArgumentCaptor<EmployeeDocument> captor = ArgumentCaptor.forClass(EmployeeDocument.class);
        verify(documentMapper).insert(captor.capture());
        assertThat(captor.getValue().getStorageKey()).startsWith("employee-documents/1/2002/");
        assertThat(response.contentUrl()).isEqualTo("/api/hr/employees/2002/documents/9/content");
        assertThat(response.toString()).doesNotContain(captor.getValue().getStorageKey());
        verify(auditService).recordTransactional(TENANT_ID, ACTOR_ID, "EMPLOYEE_DOCUMENT",
                "9", "UPLOAD", "SUCCESS", "employeeUserId=2002,type=CONTRACT");
    }

    @Test
    void uploadDeletesStoredObjectWhenMetadataInsertFails() {
        when(userAccessService.resolveActiveUser(ACTOR_ID)).thenReturn(access(ACTOR_ID, List.of("hr:manage")));
        when(accessControlMapper.selectUsers(TENANT_ID)).thenReturn(users());
        when(fileParserService.parse(any(), anyString(), anyLong()))
                .thenReturn(new ParsedFile("application/pdf", "contract", false));
        doThrow(new IllegalStateException("db unavailable")).when(documentMapper)
                .insert(any(EmployeeDocument.class));

        assertThatThrownBy(() -> service.upload(actor(), EMPLOYEE_ID, "CONTRACT", pdf()))
                .isInstanceOf(IllegalStateException.class);

        verify(objectStorageService).delete(anyString());
    }

    @Test
    void uploadDeletesStoredObjectWhenTransactionalAuditFails() {
        when(userAccessService.resolveActiveUser(ACTOR_ID)).thenReturn(access(ACTOR_ID, List.of("hr:manage")));
        when(accessControlMapper.selectUsers(TENANT_ID)).thenReturn(users());
        when(fileParserService.parse(any(), anyString(), anyLong()))
                .thenReturn(new ParsedFile("application/pdf", "contract", false));
        when(documentMapper.insert(any(EmployeeDocument.class))).thenAnswer(invocation -> {
            EmployeeDocument document = invocation.getArgument(0);
            document.setId(10L);
            return 1;
        });
        doThrow(new IllegalStateException("audit unavailable")).when(auditService)
                .recordTransactional(TENANT_ID, ACTOR_ID, "EMPLOYEE_DOCUMENT", "10",
                        "UPLOAD", "SUCCESS", "employeeUserId=2002,type=PROFILE");

        assertThatThrownBy(() -> service.upload(actor(), EMPLOYEE_ID, "PROFILE", pdf()))
                .isInstanceOf(IllegalStateException.class);

        verify(objectStorageService).delete(anyString());
    }

    @Test
    void uploadRequiresRealtimeManagePermission() {
        when(userAccessService.resolveActiveUser(ACTOR_ID)).thenReturn(access(ACTOR_ID, List.of("hr:read")));

        assertThatThrownBy(() -> service.upload(actor(), EMPLOYEE_ID, "PROFILE", pdf()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo("PERMISSION_DENIED");

        verify(objectStorageService, never()).store(anyString(), any(InputStream.class), anyLong(), anyString());
    }

    @Test
    void selfCanListOwnDocumentsWithoutHrReadPermission() {
        when(userAccessService.resolveActiveUser(ACTOR_ID)).thenReturn(access(ACTOR_ID, List.of()));
        when(accessControlMapper.selectUsers(TENANT_ID)).thenReturn(users());
        when(documentMapper.selectList(any())).thenReturn(List.of());

        assertThat(service.list(actor(), ACTOR_ID)).isEmpty();
    }

    @Test
    void crossTenantSessionIsRejectedBeforeMetadataLookup() {
        when(userAccessService.resolveActiveUser(ACTOR_ID))
                .thenReturn(new ResolvedUserAccess(ACTOR_ID, "actor@example.com", 2L,
                        "EMPLOYEE", List.of("EMPLOYEE"), List.of("hr:read"), List.of("SELF"), 1L));

        assertThatThrownBy(() -> service.list(actor(), EMPLOYEE_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo("AUTH_REQUIRED");

        verify(documentMapper, never()).selectList(any());
    }

    private AuthenticatedUser actor() {
        return new AuthenticatedUser(ACTOR_ID, "actor@example.com", TENANT_ID, "SYSTEM_ADMIN",
                List.of("SYSTEM_ADMIN"), List.of(), List.of("ALL"), 1L);
    }

    private ResolvedUserAccess access(long userId, List<String> permissions) {
        return new ResolvedUserAccess(userId, "actor@example.com", TENANT_ID, "SYSTEM_ADMIN",
                List.of("SYSTEM_ADMIN"), permissions, List.of("ALL"), 1L);
    }

    private List<AccessUserRow> users() {
        LocalDateTime now = LocalDateTime.now();
        return List.of(
                new AccessUserRow(ACTOR_ID, "人事管理员", "actor@example.com", "SYSTEM_ADMIN", 1,
                        TENANT_ID, 10L, 11L, null, 1L, now, null),
                new AccessUserRow(EMPLOYEE_ID, "员工甲", "employee@example.com", "EMPLOYEE", 1,
                        TENANT_ID, 10L, 11L, ACTOR_ID, 1L, now, null));
    }

    private MockMultipartFile pdf() {
        return new MockMultipartFile("file", "contract.pdf", "application/pdf", "%PDF-content".getBytes());
    }

    private static void initializeTableMetadata(Class<?> entityClass) {
        if (TableInfoHelper.getTableInfo(entityClass) != null) return;
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "test");
        assistant.setCurrentNamespace(EmployeeDocumentMapper.class.getName());
        TableInfoHelper.initTableInfo(assistant, entityClass);
    }
}
