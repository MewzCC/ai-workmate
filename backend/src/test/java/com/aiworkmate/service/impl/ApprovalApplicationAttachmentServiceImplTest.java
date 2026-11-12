package com.aiworkmate.service.impl;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.config.UploadProperties;
import com.aiworkmate.dto.ApprovalExecutionSnapshot;
import com.aiworkmate.entity.ApprovalApplication;
import com.aiworkmate.entity.ApprovalApplicationAttachment;
import com.aiworkmate.entity.WorkflowTask;
import com.aiworkmate.mapper.*;
import com.aiworkmate.service.*;
import com.aiworkmate.service.model.ResolvedUserAccess;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ApprovalApplicationAttachmentServiceImplTest {
    private final ApprovalApplicationAttachmentMapper attachments = mock(ApprovalApplicationAttachmentMapper.class);
    private final ApprovalApplicationMapper applications = mock(ApprovalApplicationMapper.class);
    private final WorkflowTaskMapper tasks = mock(WorkflowTaskMapper.class);
    private final UserAccessService access = mock(UserAccessService.class);
    private final ObjectStorageService storage = mock(ObjectStorageService.class);
    private ApprovalApplicationAttachmentServiceImpl service;

    @BeforeEach void setup() {
        service = new ApprovalApplicationAttachmentServiceImpl(attachments, applications, mock(ApprovalFormMapper.class),
                tasks, mock(WorkflowActionLogMapper.class), access, mock(FileParserService.class), storage,
                new UploadProperties(), mock(BusinessAuditService.class), new ObjectMapper());
        when(attachments.selectById(1L)).thenReturn(attachment());
    }

    @Test void validatesIdentifiersAgainstTheExactTenantApplicationAndField() {
        service.validateReferences(7L, 8L, "proof", "file", List.of("1"));
        for (var scope : List.of(new Object[]{6L, 8L, "proof", "file"}, new Object[]{7L, 9L, "proof", "file"},
                new Object[]{7L, 8L, "other", "file"}, new Object[]{7L, 8L, "proof", "image"})) {
            assertThatThrownBy(() -> service.validateReferences((Long)scope[0], (Long)scope[1], (String)scope[2], (String)scope[3], List.of("1")))
                    .isInstanceOf(BusinessException.class).extracting("errorCode").isEqualTo("REQUEST_INVALID");
        }
        Mockito.verifyNoInteractions(storage);
    }

    @Test void rejectsUnknownRepeatedAndUnboundReferencesWithoutReadingStorage() {
        for (List<?> references : List.<List<?>>of(List.of("https://example.invalid/file"), List.of(Map.of("path", "private")),
                List.of("1", "1"), List.of("9223372036854775808"), List.of("2"))) {
            assertThatThrownBy(() -> service.validateReferences(7L, 8L, "proof", "file", references)).isInstanceOf(BusinessException.class);
        }
        assertThatThrownBy(() -> service.validateReferences(7L, null, "proof", "file", List.of("1"))).isInstanceOf(BusinessException.class);
        verifyNoInteractions(storage);
    }

    @Test void currentDraftAttachmentsArePrivateToTheApplicantAndAuditor() {
        when(access.resolveActiveUser(10L)).thenReturn(actor(10L, List.of("todo:read")));
        when(applications.selectById(8L)).thenReturn(application("DRAFT"));
        assertThatThrownBy(() -> service.metadata(10L, 8L, 1L, null)).isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo("RESOURCE_FORBIDDEN");
        when(access.resolveActiveUser(9L)).thenReturn(actor(9L, List.of()));
        assertThat(service.metadata(9L, 8L, 1L, null).displayName()).isEqualTo("proof.txt");
        verifyNoInteractions(storage);
    }

    @Test void historicalTaskReadsRequireItsOwnSnapshotAndCannotReadUnreferencedUploads() {
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(new org.apache.ibatis.builder.MapperBuilderAssistant(
                new com.baomidou.mybatisplus.core.MybatisConfiguration(), "test"), WorkflowTask.class);
        when(access.resolveActiveUser(10L)).thenReturn(actor(10L, List.of("todo:read")));
        when(applications.selectById(8L)).thenReturn(application("DRAFT"));
        var task = new WorkflowTask(); task.setId(45L); task.setTenantId(7L); task.setBusinessType("GENERIC_APPROVAL");
        task.setBusinessId(8L); task.setInstanceId(30L);
        when(tasks.selectById(45L)).thenReturn(task);
        when(tasks.selectCount(any())).thenReturn(1L);
        when(applications.selectExecution(7L, 30L)).thenReturn(snapshot("{\"proof\":[\"1\"]}"));
        assertThat(service.metadata(10L, 8L, 1L, 45L).id()).isEqualTo("1");
        when(applications.selectExecution(7L, 30L)).thenReturn(snapshot("{\"proof\":[\"2\"]}"));
        assertThatThrownBy(() -> service.content(10L, 8L, 1L, 45L)).isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo("RESOURCE_NOT_FOUND");
        verifyNoInteractions(storage);
    }

    private ApprovalApplicationAttachment attachment() {
        var attachment = new ApprovalApplicationAttachment(); attachment.setId(1L); attachment.setTenantId(7L);
        attachment.setApplicationId(8L); attachment.setOwnerUserId(9L); attachment.setFieldName("proof");
        attachment.setFieldType("file"); attachment.setDisplayName("proof.txt"); attachment.setStorageKey("PRIVATE_STORAGE_KEY");
        attachment.setMimeType("text/plain"); attachment.setFileSize(20L); attachment.setCreatedAt(LocalDateTime.now()); return attachment;
    }

    private ApprovalApplication application(String status) {
        var application = new ApprovalApplication(); application.setId(8L); application.setTenantId(7L);
        application.setApplicantUserId(9L); application.setWorkflowInstanceId(40L); application.setStatus(status); return application;
    }

    private ResolvedUserAccess actor(long id, List<String> permissions) {
        return new ResolvedUserAccess(id, "fixture", 7L, "EMPLOYEE", List.of("EMPLOYEE"), permissions, List.of("SELF"), 1L);
    }

    private ApprovalExecutionSnapshot snapshot(String data) {
        return new ApprovalExecutionSnapshot(30L, 7L, 8L, 9L, "fixture", "Fixture", "Fixture", data,
                "{}", 1, "[]", 1, "[]", null, 0, 0, LocalDateTime.now());
    }
}
