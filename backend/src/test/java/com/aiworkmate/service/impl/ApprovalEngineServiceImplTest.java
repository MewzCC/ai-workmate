package com.aiworkmate.service.impl;

import com.aiworkmate.common.PageResponse;
import com.aiworkmate.dto.ApprovalFormResponse;
import com.aiworkmate.dto.ApprovalFormAgentDraftRequest;
import com.aiworkmate.dto.ApprovalFormAgentDraftUpdateRequest;
import com.aiworkmate.dto.ApprovalProcessResponse;
import com.aiworkmate.dto.ApprovalProcessAgentDraftRequest;
import com.aiworkmate.dto.ApprovalProcessAgentDraftUpdateRequest;
import com.aiworkmate.dto.ApprovalRuleResponse;
import com.aiworkmate.dto.ApprovalRuleAgentDraftRequest;
import com.aiworkmate.dto.ApprovalRuleAgentDraftUpdateRequest;
import com.aiworkmate.entity.ApprovalForm;
import com.aiworkmate.entity.ApprovalProcess;
import com.aiworkmate.entity.ApprovalRule;
import com.aiworkmate.mapper.ApprovalFormMapper;
import com.aiworkmate.mapper.ApprovalProcessMapper;
import com.aiworkmate.mapper.ApprovalRuleMapper;
import com.aiworkmate.mapper.UserMapper;
import com.aiworkmate.service.BusinessAuditService;
import com.aiworkmate.service.UserAccessService;
import com.aiworkmate.service.model.ResolvedUserAccess;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 回归测试：种子数据 created_by / form_id 为 NULL 时列表接口不得抛 NPE
 * （不可变 Map.get(null) 曾导致模板列表加载失败，追踪号为系统内部错误）。
 */
@ExtendWith(MockitoExtension.class)
class ApprovalEngineServiceImplTest {

    private static final long TENANT_ID = 1L;
    private static final long USER_ID = 1001L;

    @Mock
    private ApprovalFormMapper formMapper;
    @Mock
    private ApprovalProcessMapper processMapper;
    @Mock
    private ApprovalRuleMapper ruleMapper;
    @Mock
    private UserMapper userMapper;
    @Mock
    private UserAccessService userAccessService;
    @Mock
    private BusinessAuditService auditService;

    private ApprovalEngineServiceImpl service;

    @BeforeEach
    void setUp() {
        initializeTableMetadata(ApprovalForm.class);
        initializeTableMetadata(ApprovalProcess.class);
        initializeTableMetadata(ApprovalRule.class);
        service = new ApprovalEngineServiceImpl(
                formMapper, processMapper, ruleMapper, userMapper,
                userAccessService, auditService, new com.fasterxml.jackson.databind.ObjectMapper());
    }

    private void initializeTableMetadata(Class<?> entityType) {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                entityType);
    }

    private ResolvedUserAccess readerAccess() {
        return new ResolvedUserAccess(USER_ID, "reader", "SUPER_ADMIN",
                List.of("approval:read", "approval:manage"));
    }

    @Test
    void listFormsShouldTolerateNullCreatedBy() {
        when(userAccessService.resolveActiveUser(USER_ID)).thenReturn(readerAccess());
        ApprovalForm seeded = form(null);
        when(formMapper.selectList(any())).thenReturn(List.of(seeded));
        when(formMapper.selectCount(any())).thenReturn(1L);

        PageResponse<ApprovalFormResponse> page = service.listForms(
                USER_ID, null, "ENABLED", 1, 20);

        assertThat(page.records()).hasSize(1);
        assertThat(page.records().get(0).creatorName()).isNull();
    }

    @Test
    void listProcessesShouldTolerateNullCreatedByAndFormId() {
        when(userAccessService.resolveActiveUser(USER_ID)).thenReturn(readerAccess());
        ApprovalProcess seeded = process(null, null);
        when(processMapper.selectList(any())).thenReturn(List.of(seeded));
        when(processMapper.selectCount(any())).thenReturn(1L);

        PageResponse<ApprovalProcessResponse> page = service.listProcesses(
                USER_ID, null, "ENABLED", 1, 20);

        assertThat(page.records()).hasSize(1);
        assertThat(page.records().get(0).formName()).isNull();
        assertThat(page.records().get(0).creatorName()).isNull();
    }

    @Test
    void listRulesShouldTolerateNullCreatedBy() {
        when(userAccessService.resolveActiveUser(USER_ID)).thenReturn(readerAccess());
        ApprovalRule seeded = rule(null);
        when(ruleMapper.selectList(any())).thenReturn(List.of(seeded));
        when(ruleMapper.selectCount(any())).thenReturn(1L);

        PageResponse<ApprovalRuleResponse> page = service.listRules(
                USER_ID, null, "ENABLED", 1, 20);

        assertThat(page.records()).hasSize(1);
        assertThat(page.records().get(0).creatorName()).isNull();
    }

    @Test
    void agentCreatesOnlyDisabledFormFromBoundedSemanticFields() {
        when(userAccessService.resolveActiveUser(USER_ID)).thenReturn(readerAccess());
        when(formMapper.selectOne(any())).thenReturn(null);
        doAnswer(invocation -> {
            ApprovalForm inserted = invocation.getArgument(0);
            inserted.setId(88L);
            return 1;
        }).when(formMapper).insert(any(ApprovalForm.class));

        var response = service.createFormDraftAgent(USER_ID, new ApprovalFormAgentDraftRequest(
                "travel", "出差申请", null, List.of(new ApprovalFormAgentDraftRequest.Field(
                "reason", "出差事由", "textarea", true, null, List.of(), "full"))));

        assertThat(response.id()).isEqualTo(88L);
        assertThat(response.status()).isEqualTo("DISABLED");
        var form = org.mockito.ArgumentCaptor.forClass(ApprovalForm.class);
        verify(formMapper).insert(form.capture());
        assertThat(form.getValue().getSchemaJson()).contains("\"name\":\"reason\"");
        assertThat(form.getValue().getStatus()).isEqualTo("DISABLED");
    }

    @Test
    void agentRejectsDuplicateFormFieldNamesBeforeWriting() {
        var duplicate = new ApprovalFormAgentDraftRequest.Field(
                "reason", "出差事由", "textarea", true, null, List.of(), "full");

        assertThatThrownBy(() -> service.createFormDraftAgent(USER_ID,
                new ApprovalFormAgentDraftRequest("travel", "出差申请", null,
                        List.of(duplicate, duplicate))))
                .isInstanceOf(com.aiworkmate.common.BusinessException.class);
        org.mockito.Mockito.verifyNoInteractions(formMapper, userAccessService);
    }

    @Test
    void agentUpdatesOnlyDisabledFormAtExpectedVersion() {
        when(userAccessService.resolveActiveUser(USER_ID)).thenReturn(readerAccess());
        ApprovalForm existing = form(USER_ID);
        existing.setStatus("DISABLED");
        existing.setVersion(2);
        existing.setSchemaJson("""
                {"fields":[{"name":"reason","label":"事由","type":"text","required":true,
                "placeholder":"请输入","options":[],"width":"full"}]}
                """);
        ApprovalForm updated = form(USER_ID);
        updated.setFormName("出差申请（新版）");
        updated.setStatus("DISABLED");
        updated.setVersion(3);
        when(formMapper.selectById(31L)).thenReturn(existing, updated);
        when(formMapper.update(any(), any())).thenReturn(1);

        var response = service.updateFormDraftAgent(USER_ID, 31L,
                new ApprovalFormAgentDraftUpdateRequest(2, "出差申请（新版）", null,
                        List.of(new ApprovalFormAgentDraftUpdateRequest.Field(
                                "reason", "出差事由", "textarea", true, null, List.of(), "full"))));

        assertThat(response.status()).isEqualTo("DISABLED");
        assertThat(response.version()).isEqualTo(3);
        verify(formMapper).update(org.mockito.ArgumentMatchers.isNull(), any());
        verify(auditService).record(TENANT_ID, USER_ID, "APPROVAL_FORM", "31",
                "UPDATE", "SUCCESS", "Agent 更新审批表单草稿");
    }

    @Test
    void agentRejectsUpdatingEnabledFormBeforeWriting() {
        when(userAccessService.resolveActiveUser(USER_ID)).thenReturn(readerAccess());
        ApprovalForm enabled = form(USER_ID);
        enabled.setStatus("ENABLED");
        when(formMapper.selectById(31L)).thenReturn(enabled);

        assertThatThrownBy(() -> service.updateFormDraftAgent(USER_ID, 31L,
                new ApprovalFormAgentDraftUpdateRequest(2, "已发布表单", null,
                        List.of(new ApprovalFormAgentDraftUpdateRequest.Field(
                                "reason", "事由", "text", true, null, List.of(), "full")))))
                .isInstanceOf(com.aiworkmate.common.BusinessException.class);
        org.mockito.Mockito.verify(formMapper, org.mockito.Mockito.never()).update(any(), any());
    }

    @Test
    void agentPublishesOnlyDisabledFormAtExpectedVersion() {
        when(userAccessService.resolveActiveUser(USER_ID)).thenReturn(readerAccess());
        ApprovalForm existing = form(USER_ID);
        existing.setStatus("DISABLED");
        existing.setVersion(2);
        existing.setSchemaJson("""
                {"fields":[{"name":"reason","label":"事由","type":"text","required":true,
                "placeholder":"请输入","options":[],"width":"full"}]}
                """);
        ApprovalForm published = form(USER_ID);
        published.setStatus("ENABLED");
        published.setVersion(3);
        when(formMapper.selectById(31L)).thenReturn(existing, published);
        when(formMapper.update(any(), any())).thenReturn(1);

        var response = service.publishFormDraftAgent(USER_ID, 31L, 2);

        assertThat(response.status()).isEqualTo("ENABLED");
        assertThat(response.version()).isEqualTo(3);
        verify(formMapper).update(org.mockito.ArgumentMatchers.isNull(), any());
        verify(auditService).record(TENANT_ID, USER_ID, "APPROVAL_FORM", "31",
                "PUBLISH", "SUCCESS", "Agent 发布审批表单草稿");
    }

    @Test
    void agentRejectsPublishingAnAlreadyEnabledForm() {
        when(userAccessService.resolveActiveUser(USER_ID)).thenReturn(readerAccess());
        ApprovalForm enabled = form(USER_ID);
        enabled.setStatus("ENABLED");
        when(formMapper.selectById(31L)).thenReturn(enabled);

        assertThatThrownBy(() -> service.publishFormDraftAgent(USER_ID, 31L, 2))
                .isInstanceOf(com.aiworkmate.common.BusinessException.class);
        org.mockito.Mockito.verify(formMapper, org.mockito.Mockito.never()).update(any(), any());
    }

    @Test
    void agentRejectsPublishingMalformedStoredFormSchema() {
        when(userAccessService.resolveActiveUser(USER_ID)).thenReturn(readerAccess());
        ApprovalForm invalid = form(USER_ID);
        invalid.setStatus("DISABLED");
        invalid.setVersion(2);
        invalid.setSchemaJson("{\"fields\":[]}");
        when(formMapper.selectById(31L)).thenReturn(invalid);

        assertThatThrownBy(() -> service.publishFormDraftAgent(USER_ID, 31L, 2))
                .isInstanceOf(com.aiworkmate.common.BusinessException.class);
        org.mockito.Mockito.verify(formMapper, org.mockito.Mockito.never()).update(any(), any());
    }

    @Test
    void agentCreatesOnlyDisabledProcessFromSemanticNodes() {
        when(userAccessService.resolveActiveUser(USER_ID)).thenReturn(readerAccess());
        when(processMapper.selectOne(any())).thenReturn(null);
        doAnswer(invocation -> {
            ApprovalProcess inserted = invocation.getArgument(0);
            inserted.setId(41L);
            return 1;
        }).when(processMapper).insert(any(ApprovalProcess.class));

        var response = service.createProcessDraftAgent(USER_ID, new ApprovalProcessAgentDraftRequest(
                "travel", "出差审批", null, null, List.of(
                new ApprovalProcessAgentDraftRequest.Node("START", "开始", null, null, null, null, null, null),
                new ApprovalProcessAgentDraftRequest.Node("APPROVAL", "主管审批", "DIRECT_MANAGER", "", "OR_SIGN", false, 48, "REMIND"),
                new ApprovalProcessAgentDraftRequest.Node("END", "结束", null, null, null, null, null, null))));

        assertThat(response.status()).isEqualTo("DISABLED");
        var process = org.mockito.ArgumentCaptor.forClass(ApprovalProcess.class);
        verify(processMapper).insert(process.capture());
        assertThat(process.getValue().getNodeJson()).contains("DIRECT_MANAGER");
        assertThat(process.getValue().getStatus()).isEqualTo("DISABLED");
    }

    @Test
    void agentUpdatesOnlyDisabledProcessAtExpectedVersion() {
        when(userAccessService.resolveActiveUser(USER_ID)).thenReturn(readerAccess());
        ApprovalProcess existing = process(USER_ID, null);
        existing.setStatus("DISABLED");
        existing.setVersion(2);
        ApprovalProcess updated = process(USER_ID, null);
        updated.setProcessName("出差审批（新版）");
        updated.setStatus("DISABLED");
        updated.setVersion(3);
        when(processMapper.selectById(41L)).thenReturn(existing, updated);
        when(processMapper.update(any(), any())).thenReturn(1);

        var response = service.updateProcessDraftAgent(USER_ID, 41L,
                new ApprovalProcessAgentDraftUpdateRequest(2, "出差审批（新版）", null, null, List.of(
                        new ApprovalProcessAgentDraftUpdateRequest.Node("START", "开始", null, null, null, null, null, null),
                        new ApprovalProcessAgentDraftUpdateRequest.Node("APPROVAL", "主管审批", "DIRECT_MANAGER", "", "OR_SIGN", false, 48, "REMIND"),
                        new ApprovalProcessAgentDraftUpdateRequest.Node("END", "结束", null, null, null, null, null, null))));

        assertThat(response.status()).isEqualTo("DISABLED");
        assertThat(response.version()).isEqualTo(3);
        verify(processMapper).update(org.mockito.ArgumentMatchers.isNull(), any());
        verify(auditService).record(TENANT_ID, USER_ID, "APPROVAL_PROCESS", "41",
                "UPDATE", "SUCCESS", "Agent 更新审批流程草稿");
    }

    @Test
    void agentRejectsUpdatingEnabledProcessBeforeWriting() {
        when(userAccessService.resolveActiveUser(USER_ID)).thenReturn(readerAccess());
        ApprovalProcess enabled = process(USER_ID, null);
        enabled.setStatus("ENABLED");
        when(processMapper.selectById(41L)).thenReturn(enabled);

        assertThatThrownBy(() -> service.updateProcessDraftAgent(USER_ID, 41L,
                new ApprovalProcessAgentDraftUpdateRequest(2, "已发布流程", null, null, List.of(
                        new ApprovalProcessAgentDraftUpdateRequest.Node("START", "开始", null, null, null, null, null, null),
                        new ApprovalProcessAgentDraftUpdateRequest.Node("APPROVAL", "主管审批", "DIRECT_MANAGER", "", "OR_SIGN", false, 48, "REMIND"),
                        new ApprovalProcessAgentDraftUpdateRequest.Node("END", "结束", null, null, null, null, null, null)))))
                .isInstanceOf(com.aiworkmate.common.BusinessException.class);
        org.mockito.Mockito.verify(processMapper, org.mockito.Mockito.never()).update(any(), any());
    }

    @Test
    void agentCreatesOnlyDisabledRuleFromSemanticConditions() {
        when(userAccessService.resolveActiveUser(USER_ID)).thenReturn(readerAccess());
        when(ruleMapper.selectOne(any())).thenReturn(null);
        doAnswer(invocation -> {
            ApprovalRule inserted = invocation.getArgument(0);
            inserted.setId(51L);
            return 1;
        }).when(ruleMapper).insert(any(ApprovalRule.class));

        var response = service.createRuleDraftAgent(USER_ID, new ApprovalRuleAgentDraftRequest(
                "large-expense", "大额费用复核", "AMOUNT_THRESHOLD", 10, null, "AND",
                List.of(new ApprovalRuleAgentDraftRequest.Condition("amount", "gte", "5000")),
                new ApprovalRuleAgentDraftRequest.Action("FINANCE_REVIEW", true, "OR_SIGN")));

        assertThat(response.status()).isEqualTo("DISABLED");
        var rule = org.mockito.ArgumentCaptor.forClass(ApprovalRule.class);
        verify(ruleMapper).insert(rule.capture());
        assertThat(rule.getValue().getConditionJson()).contains("\"field\":\"amount\"");
        assertThat(rule.getValue().getActionJson()).contains("FINANCE_REVIEW");
        assertThat(rule.getValue().getStatus()).isEqualTo("DISABLED");
    }

    @Test
    void agentUpdatesOnlyDisabledRuleAtExpectedVersion() {
        when(userAccessService.resolveActiveUser(USER_ID)).thenReturn(readerAccess());
        ApprovalRule existing = rule(USER_ID);
        existing.setStatus("DISABLED");
        existing.setVersion(2);
        ApprovalRule updated = rule(USER_ID);
        updated.setRuleName("大额费用复核（新版）");
        updated.setStatus("DISABLED");
        updated.setVersion(3);
        when(ruleMapper.selectById(51L)).thenReturn(existing, updated);
        when(ruleMapper.update(any(), any())).thenReturn(1);

        var response = service.updateRuleDraftAgent(USER_ID, 51L,
                new ApprovalRuleAgentDraftUpdateRequest(2, "大额费用复核（新版）",
                        "AMOUNT_THRESHOLD", 5, null, "AND",
                        List.of(new ApprovalRuleAgentDraftUpdateRequest.Condition("amount", "gte", "8000")),
                        new ApprovalRuleAgentDraftUpdateRequest.Action("FINANCE_REVIEW", true, "OR_SIGN")));

        assertThat(response.status()).isEqualTo("DISABLED");
        assertThat(response.version()).isEqualTo(3);
        verify(ruleMapper).update(org.mockito.ArgumentMatchers.isNull(), any());
        verify(auditService).record(TENANT_ID, USER_ID, "APPROVAL_RULE", "51",
                "UPDATE", "SUCCESS", "Agent 更新审批规则草稿");
    }

    @Test
    void agentRejectsUpdatingEnabledRuleBeforeWriting() {
        when(userAccessService.resolveActiveUser(USER_ID)).thenReturn(readerAccess());
        ApprovalRule enabled = rule(USER_ID);
        enabled.setStatus("ENABLED");
        when(ruleMapper.selectById(51L)).thenReturn(enabled);

        assertThatThrownBy(() -> service.updateRuleDraftAgent(USER_ID, 51L,
                new ApprovalRuleAgentDraftUpdateRequest(2, "已启用规则", "AMOUNT_THRESHOLD", 5,
                        null, "AND",
                        List.of(new ApprovalRuleAgentDraftUpdateRequest.Condition("amount", "gte", "8000")),
                        new ApprovalRuleAgentDraftUpdateRequest.Action("FINANCE_REVIEW", true, "OR_SIGN"))))
                .isInstanceOf(com.aiworkmate.common.BusinessException.class);
        org.mockito.Mockito.verify(ruleMapper, org.mockito.Mockito.never()).update(any(), any());
    }

    private ApprovalForm form(Long createdBy) {
        ApprovalForm form = new ApprovalForm();
        form.setId(1L);
        form.setTenantId(TENANT_ID);
        form.setFormKey("leave-application");
        form.setFormName("请假申请单");
        form.setSchemaJson("{}");
        form.setStatus("ENABLED");
        form.setVersion(1);
        form.setCreatedBy(createdBy);
        form.setCreatedAt(LocalDateTime.now());
        form.setUpdatedAt(LocalDateTime.now());
        form.setDeleted(false);
        return form;
    }

    private ApprovalProcess process(Long createdBy, Long formId) {
        ApprovalProcess process = new ApprovalProcess();
        process.setId(2L);
        process.setTenantId(TENANT_ID);
        process.setProcessKey("leave-single-approval");
        process.setProcessName("请假单级审批");
        process.setFormId(formId);
        process.setNodeJson("[]");
        process.setStatus("ENABLED");
        process.setVersion(1);
        process.setCreatedBy(createdBy);
        process.setCreatedAt(LocalDateTime.now());
        process.setUpdatedAt(LocalDateTime.now());
        process.setDeleted(false);
        return process;
    }

    private ApprovalRule rule(Long createdBy) {
        ApprovalRule rule = new ApprovalRule();
        rule.setId(3L);
        rule.setTenantId(TENANT_ID);
        rule.setRuleKey("leave-over-3-days");
        rule.setRuleName("请假超 3 天加签部门负责人");
        rule.setRuleType("LEAVE_TYPE");
        rule.setPriority(10);
        rule.setConditionJson("{}");
        rule.setActionJson("{}");
        rule.setStatus("ENABLED");
        rule.setVersion(1);
        rule.setCreatedBy(createdBy);
        rule.setCreatedAt(LocalDateTime.now());
        rule.setUpdatedAt(LocalDateTime.now());
        rule.setDeleted(false);
        return rule;
    }
}
