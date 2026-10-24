package com.aiworkmate.service.impl;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.dto.ApprovalRuleConditionPatchRequest;
import com.aiworkmate.entity.ApprovalRule;
import com.aiworkmate.mapper.*;
import com.aiworkmate.service.BusinessAuditService;
import com.aiworkmate.service.UserAccessService;
import com.aiworkmate.service.model.ResolvedUserAccess;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ApprovalRuleConditionPatchTest {
    private final ApprovalRuleMapper rules = mock(ApprovalRuleMapper.class);
    private final UserAccessService access = mock(UserAccessService.class);
    private final BusinessAuditService audit = mock(BusinessAuditService.class);
    private final UserMapper users = mock(UserMapper.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final ApprovalEngineServiceImpl service = new ApprovalEngineServiceImpl(mock(ApprovalFormMapper.class),
            mock(ApprovalProcessMapper.class), rules, users, access, audit, mapper);
    private ApprovalRule rule;
    private static final String CONDITIONS = """
            {"logic":"OR","conditions":[{"field":"amount","op":"gte","value":0.123456789012345678901},
            {"field":"leaveType","op":"eq","value":"SICK"}]}
            """;

    @BeforeEach void setup() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), ApprovalRule.class);
        actor(99L, List.of("approval:manage"));
        rule = new ApprovalRule(); rule.setId(11L); rule.setTenantId(99L); rule.setRuleKey("fixture");
        rule.setRuleName("Keep name"); rule.setDescription("Keep description"); rule.setRuleType("AMOUNT_THRESHOLD");
        rule.setPriority(Integer.MAX_VALUE); rule.setStatus("DISABLED"); rule.setVersion(3);
        rule.setConditionJson(CONDITIONS); rule.setActionJson("{\"appendNode\":\"FINANCE_REVIEW\",\"enabled\":false}");
        when(rules.selectOne(any(LambdaQueryWrapper.class))).thenAnswer(call -> {
            LambdaQueryWrapper<ApprovalRule> query = call.getArgument(0);
            assertThat(query.getSqlSegment()).contains("tenant_id", "id", "deleted");
            assertThat(query.getParamNameValuePairs().values()).contains(99L, 11L, false);
            return rule;
        });
    }
    private void actor(long tenant, List<String> permissions) {
        when(access.resolveActiveUser(7L)).thenReturn(new ResolvedUserAccess(7L, "fixture", tenant,
                "EMPLOYEE", List.of("EMPLOYEE"), permissions, List.of("TENANT"), 1L));
    }
    private Object patch(Integer version, Integer number, String value) {
        return service.patchRuleDraftConditionAgent(7L, 99L, 11L,
                new ApprovalRuleConditionPatchRequest(version, number, value));
    }
    private String captureChange() {
        var update = org.mockito.ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(rules).update(isNull(), update.capture());
        assertThat(update.getValue().getSqlSegment()).contains("tenant_id", "id", "deleted", "status", "version");
        assertThat(update.getValue().getSqlSet()).contains("condition_json", "version = version + 1")
                .doesNotContain("action_json", "rule_name", "priority", "description", "rule_key");
        return (String) update.getValue().getParamNameValuePairs().values().stream()
                .filter(value -> value instanceof String text && text.startsWith("{" )).findFirst().orElseThrow();
    }
    @Test void preservesOtherConditionsAndMetadataAndReturnsOnlyTheChangedLiteral() throws Exception {
        when(rules.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(1);
        String value = "Annual [literal](https://example.invalid) 中文";
        var receipt = mapper.valueToTree(patch(3, 2, value));
        var saved = mapper.reader().with(com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                .readTree(captureChange());
        assertThat(saved.path("logic").asText()).isEqualTo("OR");
        assertThat(saved.at("/conditions/0/value").asText()).isEqualTo("0.123456789012345678901");
        assertThat(saved.at("/conditions/1/value").asText()).isEqualTo(value);
        assertThat(receipt.size()).isEqualTo(7);
        assertThat(receipt.path("value").asText()).isEqualTo(value);
        assertThat(receipt.path("version").asInt()).isEqualTo(4);
        assertThat(receipt.path("conditionNumber").asInt()).isEqualTo(2);
        assertThat(receipt.path("field").asText()).isEqualTo("leaveType");
        assertThat(receipt.path("operator").asText()).isEqualTo("eq");
        verify(audit).recordTransactional(eq(99L), eq(7L), eq("APPROVAL_RULE"), eq("11"),
                eq("PATCH_CONDITION"), eq("SUCCESS"), eq("conditionNumber=2"));
        verifyNoInteractions(users);
        verify(rules, never()).selectById(any());
    }
    @Test void preservesFlatNumericRepresentationAndExactDecimalValue() throws Exception {
        rule.setConditionJson("{\"field\":\"amount\",\"op\":\"gte\",\"value\":5000}");
        when(rules.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(1);
        var receipt = mapper.valueToTree(patch(3, 1, "0.987654321098765432109"));
        String changed = captureChange();
        assertThat(changed).contains("0.987654321098765432109").doesNotContain("conditions", "logic");
        assertThat(mapper.readTree(changed).path("value").isNumber()).isTrue();
        assertThat(receipt.path("value").asText()).isEqualTo("0.987654321098765432109");
    }
    @ParameterizedTest @ValueSource(strings = {"NaN", "Infinity", "01", "1e2147483647", "1e-2147483647", "not numeric"})
    void rejectsNonJsonOrUnboundedNumericReplacement(String value) {
        assertThatThrownBy(() -> patch(3, 1, value)).isInstanceOf(BusinessException.class);
        verify(rules, never()).update(any(), any()); verifyNoInteractions(audit);
    }
    @Test void preservesBooleanScalarTypeAndRejectsArbitraryTextForIt() throws Exception {
        rule.setConditionJson("{\"field\":\"employeeLevel\",\"op\":\"eq\",\"value\":true}");
        when(rules.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(1);
        patch(3, 1, "false");
        assertThat(mapper.readTree(captureChange()).path("value").isBoolean()).isTrue();
        expect("REQUEST_INVALID", () -> patch(3, 1, "not boolean"));
    }
    @Test void rejectsNumericNormalizationThatWouldExceedTheReadableValueLimit() {
        when(rules.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(1);
        expect("REQUEST_INVALID", () -> patch(3, 1, "1".repeat(116) + "e120"));
        verify(rules, never()).update(any(), any()); verifyNoInteractions(audit);
    }
    @Test void refusesRevokedPermissionAndChangedTenantBeforeReading() {
        actor(99L, List.of()); assertThatThrownBy(() -> patch(3, 1, "1")).isInstanceOf(BusinessException.class);
        actor(100L, List.of("approval:manage")); assertThatThrownBy(() -> patch(3, 1, "1")).isInstanceOf(BusinessException.class);
        verifyNoInteractions(rules, audit);
    }
    @Test void rejectsMissingEnabledStaleAndOutOfRangeTargets() {
        rule.setStatus("ENABLED"); expect("BUSINESS_STATE_INVALID", () -> patch(3, 1, "1"));
        rule.setStatus("DISABLED"); expect("VERSION_CONFLICT", () -> patch(2, 1, "1"));
        expect("RESOURCE_NOT_FOUND", () -> patch(3, 3, "1"));
        when(rules.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        expect("RESOURCE_NOT_FOUND", () -> patch(3, 1, "1"));
        verify(rules, never()).update(any(), any()); verifyNoInteractions(audit);
    }
    @Test void rejectsBadArgumentsBeforeReading() {
        for (Integer version : new Integer[]{null, 0, Integer.MAX_VALUE}) {
            expect("REQUEST_INVALID", () -> patch(version, 1, "1"));
        }
        for (Integer number : new Integer[]{null, 0, 11}) expect("REQUEST_INVALID", () -> patch(3, number, "1"));
        for (String value : new String[]{null, " ", "a".repeat(121)}) expect("REQUEST_INVALID", () -> patch(3, 1, value));
        verifyNoInteractions(rules, audit);
    }
    @Test void refusesUnknownDuplicateAndTrailingSourceInsteadOfDroppingIt() {
        for (String condition : new String[]{CONDITIONS.replace("\"logic\"", "\"script\""),
                "{\"field\":\"amount\",\"op\":\"gte\",\"value\":1,\"value\":2}", CONDITIONS + " {}"}) {
            rule.setConditionJson(condition); expect("REQUEST_INVALID", () -> patch(3, 1, "1"));
        }
        rule.setConditionJson(CONDITIONS); rule.setActionJson("{\"appendNode\":\"FINANCE_REVIEW\",\"enabled\":false,\"url\":\"private\"}");
        expect("REQUEST_INVALID", () -> patch(3, 1, "1"));
        verify(rules, never()).update(any(), any()); verifyNoInteractions(audit);
    }
    @Test void failedCompareAndSetDoesNotAuditSuccess() {
        when(rules.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(0);
        expect("VERSION_CONFLICT", () -> patch(3, 2, "ANNUAL")); verifyNoInteractions(audit);
    }
    private void expect(String code, org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOf(BusinessException.class).extracting("errorCode").isEqualTo(code);
    }
}
