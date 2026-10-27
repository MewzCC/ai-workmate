package com.aiworkmate.service.impl;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.entity.ApprovalRule;
import com.aiworkmate.mapper.*;
import com.aiworkmate.service.BusinessAuditService;
import com.aiworkmate.service.UserAccessService;
import com.aiworkmate.service.model.ResolvedUserAccess;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ApprovalRuleDefinitionReadTest {
    private final ApprovalRuleMapper rules = mock(ApprovalRuleMapper.class);
    private final UserAccessService access = mock(UserAccessService.class);
    private final BusinessAuditService audit = mock(BusinessAuditService.class);
    private final UserMapper users = mock(UserMapper.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final ApprovalEngineServiceImpl service = new ApprovalEngineServiceImpl(mock(ApprovalFormMapper.class),
            mock(ApprovalProcessMapper.class), rules, users, access, audit, mapper);
    private ApprovalRule rule;
    @BeforeEach void setup() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), ApprovalRule.class);
        reader(99L, List.of("approval:read"));
        rule = new ApprovalRule(); rule.setId(11L); rule.setTenantId(99L); rule.setRuleKey("fixture");
        rule.setRuleName("Fixture"); rule.setRuleType("AMOUNT_THRESHOLD"); rule.setPriority(20);
        rule.setStatus("DISABLED"); rule.setVersion(3); rule.setCreatedBy(999L); rule.setDescription("private description");
        rule.setConditionJson("{\"field\":\"amount\",\"op\":\"gte\",\"value\":0.123456789012345678901}");
        rule.setActionJson("{\"appendNode\":\"FINANCE_REVIEW\",\"enabled\":true}");
        when(rules.selectOne(any(LambdaQueryWrapper.class))).thenReturn(rule);
    }
    private void reader(long tenant, List<String> permissions) {
        when(access.resolveActiveUser(7L)).thenReturn(new ResolvedUserAccess(7L, "fixture", tenant,
                "EMPLOYEE", List.of("EMPLOYEE"), permissions, List.of("TENANT"), 1L));
    }
    private JsonNode read() {
        return mapper.valueToTree(service.readRuleDefinitionAgent(7L, 99L, 11L));
    }
    @Test void readsPreciseTypedValuesWithoutPrivateMetadataOrWrites() {
        when(rules.selectOne(any(LambdaQueryWrapper.class))).thenAnswer(call -> {
            LambdaQueryWrapper<ApprovalRule> query = call.getArgument(0);
            assertThat(query.getSqlSegment()).contains("tenant_id", "id", "deleted");
            assertThat(query.getParamNameValuePairs().values()).contains(99L, 11L, false);
            return rule;
        });
        var result = read();
        assertThat(result.path("ruleId").asLong()).isEqualTo(11); assertThat(result.path("version").asInt()).isEqualTo(3);
        assertThat(result.path("logic").asText()).isEqualTo("AND");
        assertThat(result.at("/conditions/0/value").asText()).isEqualTo("0.123456789012345678901");
        assertThat(result.at("/action/mode").asText()).isEqualTo("OR_SIGN");
        assertThat(result.toString()).doesNotContain("conditionJson", "actionJson", "description", "createdBy", "tenantId");
        verify(rules).selectOne(any(LambdaQueryWrapper.class)); verifyNoMoreInteractions(rules); verifyNoInteractions(users, audit);
    }
    @Test void readsGroupedRulesAndActuallyDisabledActions() {
        rule.setConditionJson("{\"logic\":\"OR\",\"conditions\":[{\"field\":\"leaveType\",\"op\":\"eq\",\"value\":\"SICK\"}]}");
        rule.setActionJson("{\"appendNode\":\"DIRECT_MANAGER\",\"enabled\":false,\"mode\":\"SEQUENTIAL\"}");
        var result = read(); assertThat(result.path("logic").asText()).isEqualTo("OR");
        assertThat(result.at("/action/enabled").asBoolean()).isFalse();
        assertThat(result.at("/conditions/0/operator").asText()).isEqualTo("eq");
    }
    @ParameterizedTest @ValueSource(ints = {10001, Integer.MAX_VALUE})
    void readsValidPrioritiesBeyondTheWriteToolLimit(int priority) {
        rule.setPriority(priority); assertThat(read().path("priority").asInt()).isEqualTo(priority);
    }
    @Test void retainsTheExistingWriteOnlyPriorityLimit() {
        var request = new com.aiworkmate.dto.ApprovalRuleAgentDraftRequest("fixture", "Fixture", "AMOUNT_THRESHOLD", 10001,
                null, "AND", List.of(new com.aiworkmate.dto.ApprovalRuleAgentDraftRequest.Condition("amount", "gte", "5000")),
                new com.aiworkmate.dto.ApprovalRuleAgentDraftRequest.Action("FINANCE_REVIEW", true, "OR_SIGN"));
        assertThatThrownBy(() -> service.createRuleDraftAgent(7L, request)).isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo("REQUEST_INVALID");
        verifyNoInteractions(rules, audit);
    }
    @Test void refusesTenantChangesAndRevokedPermissionsBeforeReading() {
        reader(100L, List.of("approval:read"));
        assertThatThrownBy(this::read).isInstanceOf(BusinessException.class).extracting("errorCode").isEqualTo("PERMISSION_DENIED");
        reader(99L, List.of()); assertThatThrownBy(this::read).isInstanceOf(BusinessException.class);
        verifyNoInteractions(rules);
    }
    @Test void missingOrTenantFilteredResourceIsNotFound() {
        when(rules.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        assertThatThrownBy(this::read).isInstanceOf(BusinessException.class).extracting("errorCode").isEqualTo("RESOURCE_NOT_FOUND");
    }
    @Test void refusesMalformedIdentityBeforeAnyRead() {
        for (Long id : new Long[]{null, 0L, -1L}) {
            assertThatThrownBy(() -> service.readRuleDefinitionAgent(7L, 99L, id)).isInstanceOf(BusinessException.class);
        }
        verifyNoInteractions(rules);
    }
    @ParameterizedTest @ValueSource(strings = { "{}", "[]", "{\"field\":\"amount\",\"op\":\"gte\",\"value\":null}",
        "{\"field\":\"amount\",\"op\":\"gte\",\"value\":1,\"script\":\"private\"}",
        "{\"field\":\"amount\",\"op\":\"gte\",\"value\":1,\"value\":2}",
        "{\"field\":\"amount\",\"op\":\"gte\",\"value\":1} {}",
        "{\"logic\":\"AND\",\"conditions\":[]}", "{\"field\":\"secret\",\"op\":\"gte\",\"value\":1}" })
    void rejectsUnsupportedOrAmbiguousConditionsWithoutPartialDefinitions(String condition) {
        rule.setConditionJson(condition); assertThatThrownBy(this::read).isInstanceOf(BusinessException.class);
    }
    @ParameterizedTest @ValueSource(strings = { "{}", "{\"appendNode\":\"FINANCE_REVIEW\",\"enabled\":null}",
        "{\"appendNode\":\"FINANCE_REVIEW\",\"enabled\":true,\"mode\":null}",
        "{\"appendNode\":\"FINANCE_REVIEW\",\"enabled\":true,\"url\":\"private\"}" })
    void rejectsUnsupportedActionsInsteadOfSilentlyDroppingTheirBehavior(String action) {
        rule.setActionJson(action); assertThatThrownBy(this::read).isInstanceOf(BusinessException.class);
    }
    @Test void boundsTheSourceAndConditionCountBeforeReturningAnything() {
        rule.setConditionJson(" ".repeat(65537)); assertThatThrownBy(this::read).isInstanceOf(BusinessException.class);
        rule.setConditionJson("{\"logic\":\"AND\",\"conditions\":[" + String.join(",", java.util.Collections.nCopies(10,
                "{\"field\":\"amount\",\"op\":\"gte\",\"value\":1}")) + "]}");
        assertThat(read().path("conditions").size()).isEqualTo(10);
        rule.setConditionJson("{\"logic\":\"AND\",\"conditions\":[" + String.join(",", java.util.Collections.nCopies(11,
                "{\"field\":\"amount\",\"op\":\"gte\",\"value\":1}")) + "]}");
        assertThatThrownBy(this::read).isInstanceOf(BusinessException.class);
    }
}
