package com.aiworkmate.service.impl;

import com.aiworkmate.common.PageResponse;
import com.aiworkmate.dto.ApprovalFormResponse;
import com.aiworkmate.dto.ApprovalProcessResponse;
import com.aiworkmate.dto.ApprovalRuleResponse;
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
import static org.mockito.ArgumentMatchers.any;
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
