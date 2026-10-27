package com.aiworkmate.service.impl;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.dto.ApprovalFormFieldPatchResponse;
import com.aiworkmate.dto.ApprovalFormFieldPatchRequest;
import com.aiworkmate.entity.ApprovalForm;
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

class ApprovalFormFieldPatchTest {
    private final ApprovalFormMapper forms = mock(ApprovalFormMapper.class);
    private final UserAccessService access = mock(UserAccessService.class);
    private final BusinessAuditService audit = mock(BusinessAuditService.class);
    private final UserMapper users = mock(UserMapper.class);
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final ApprovalEngineServiceImpl service = new ApprovalEngineServiceImpl(forms,
            mock(ApprovalProcessMapper.class), mock(ApprovalRuleMapper.class), users, access, audit, mapper);
    private ApprovalForm form;
    private static final String SCHEMA = """
        {"fields":[{"name":"amount","label":"Amount","type":"money","min":0.123456789012345678901},
        {"key":"reason","label":"Reason","type":"text","required":false}]}
        """;
    @BeforeEach void setup() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), ApprovalForm.class);
        when(access.resolveActiveUser(7L)).thenReturn(new ResolvedUserAccess(7L, "fixture", 99L,
                "EMPLOYEE", List.of("EMPLOYEE"), List.of("approval:manage"), List.of("TENANT"), 1L));
        form = new ApprovalForm(); form.setId(11L); form.setTenantId(99L); form.setFormKey("fixture");
        form.setFormName("Fixture"); form.setDescription("Keep description"); form.setVersion(3);
        form.setStatus("DISABLED"); form.setDeleted(false); form.setSchemaJson(SCHEMA);
        when(forms.selectOne(any(LambdaQueryWrapper.class))).thenReturn(form);
    }
    @Test void changesOnlyTheNamedFieldAndPreservesExactConstraintsAndLegacyStructure() throws Exception {
        when(forms.update(isNull(), any(LambdaUpdateWrapper.class))).thenAnswer(call -> {
            LambdaUpdateWrapper<ApprovalForm> update = call.getArgument(1);
            assertThat(update.getSqlSegment()).contains("tenant_id", "id", "deleted", "status", "version");
            assertThat(update.getSqlSet()).contains("schema_json", "version = version + 1")
                    .doesNotContain("form_name", "description", "form_key");
            String json = update.getParamNameValuePairs().values().stream().filter(String.class::isInstance)
                    .map(String.class::cast).filter(value -> value.startsWith("{\"fields\"")).findFirst().orElseThrow();
            assertThat(json).contains("0.123456789012345678901");
            var before = mapper.reader().with(com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).readTree(SCHEMA);
            var after = mapper.reader().with(com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).readTree(json);
            assertThat(after.path("fields").get(0)).isEqualTo(before.path("fields").get(0));
            assertThat(after.path("fields").get(1).path("key").asText()).isEqualTo("reason");
            assertThat(after.path("fields").get(1).path("label").asText()).isEqualTo("New reason");
            assertThat(after.path("fields").get(1).path("required").asBoolean()).isTrue();
            assertThat(after.path("fields").get(1).has("options")).isFalse();
            return 1;
        });
        var result = patch(3, "reason", "New reason", true, null, null);
        assertThat(result.formId()).isEqualTo(11L); assertThat(result.version()).isEqualTo(4);
        assertThat(result.status()).isEqualTo("DISABLED");
        verify(audit).recordTransactional(eq(99L), eq(7L), eq("APPROVAL_FORM"), eq("11"), eq("PATCH_FIELD"), eq("SUCCESS"), anyString());
        verify(forms, never()).selectById(any());
    }
    @Test void deniesAChangedTrustedTenantBeforeReading() {
        when(access.resolveActiveUser(7L)).thenReturn(new ResolvedUserAccess(7L, "fixture", 100L,
                "EMPLOYEE", List.of("EMPLOYEE"), List.of("approval:manage"), List.of("TENANT"), 1L));
        assertThatThrownBy(() -> patch(3, "reason", "New", null, null, null)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(forms, audit);
    }
    @Test void returnsOnlyTheActualFieldReceiptWithoutResolvingCreatorOrExposingTheSchema() {
        form.setCreatedBy(42L);
        var foreignCreator = new com.aiworkmate.entity.User(); foreignCreator.setId(42L);
        foreignCreator.setTenantId(100L); foreignCreator.setDisplayName("Foreign private creator");
        when(users.selectById(42L)).thenReturn(foreignCreator);
        when(forms.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(1);
        var result = mapper.valueToTree(patch(3, "reason", "New reason", true, "", "half"));
        assertThat(result.toString()).doesNotContain("schemaJson", "creatorName", "description", "Foreign private creator");
        assertThat(result.path("formId").asLong()).isEqualTo(11);
        assertThat(result.path("fieldName").asText()).isEqualTo("reason");
        assertThat(result.path("label").asText()).isEqualTo("New reason");
        assertThat(result.path("required").asBoolean()).isTrue();
        assertThat(result.path("placeholder").asText()).isEmpty();
        assertThat(result.path("width").asText()).isEqualTo("half");
        verifyNoInteractions(users);
    }
    @Test void deniesRevokedManagePermission() {
        when(access.resolveActiveUser(7L)).thenReturn(new ResolvedUserAccess(7L, "fixture", 99L,
                "EMPLOYEE", List.of("EMPLOYEE"), List.of("approval:read"), List.of("TENANT"), 1L));
        assertThatThrownBy(() -> patch(3, "reason", "New", null, null, null)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(forms, audit);
    }
    @Test void refusesUnsupportedReceiptIdentityBeforeWriting() {
        form.setFormKey("invalid identity");
        when(forms.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(1);
        assertThatThrownBy(() -> patch(3, "reason", "New", null, null, null)).isInstanceOf(BusinessException.class);
        verify(forms, never()).update(any(), any()); verifyNoInteractions(audit);
    }
    @Test void rejectsPublishedFormsWithoutUpdating() {
        form.setStatus("ENABLED");
        assertThatThrownBy(() -> patch(3, "reason", "New", null, null, null)).isInstanceOf(BusinessException.class);
        verify(forms, never()).update(any(), any()); verifyNoInteractions(audit);
    }
    @Test void rejectsStaleVersionsBeforeModifyingFields() {
        assertThatThrownBy(() -> patch(2, "reason", "New", null, null, null)).isInstanceOf(BusinessException.class);
        verify(forms, never()).update(any(), any()); verifyNoInteractions(audit);
    }
    @Test void missingResourceCannotBePatched() {
        when(forms.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        assertThatThrownBy(() -> patch(3, "reason", "New", null, null, null)).isInstanceOf(BusinessException.class);
        verify(forms, never()).update(any(), any()); verifyNoInteractions(audit);
    }
    @Test void unknownFieldCannotBeCreatedByThePatch() {
        assertThatThrownBy(() -> patch(3, "newField", "New", null, null, null)).isInstanceOf(BusinessException.class);
        verify(forms, never()).update(any(), any()); verifyNoInteractions(audit);
    }
    @Test void emptyPatchCannotPretendToChangeAForm() {
        assertThatThrownBy(() -> patch(3, "reason", null, null, null, null)).isInstanceOf(BusinessException.class);
        verify(forms, never()).update(any(), any()); verifyNoInteractions(audit);
    }
    @Test void identicalPatchDoesNotAdvanceTheVersionOrWriteAnAudit() {
        assertThatThrownBy(() -> patch(3, "reason", "Reason", null, null, null)).isInstanceOf(BusinessException.class);
        verify(forms, never()).update(any(), any()); verifyNoInteractions(audit);
    }
    @Test void explicitFalseAndEmptyPlaceholderAreNotMistakenForOmittedProperties() {
        form.setSchemaJson(SCHEMA.replace("\"required\":false", "\"required\":true,\"placeholder\":\"Old hint\""));
        when(forms.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(1);
        var result = patch(3, "reason", null, false, "", "half");
        assertThat(result.required()).isFalse(); assertThat(result.placeholder()).isEmpty(); assertThat(result.width()).isEqualTo("half");
    }
    @ParameterizedTest @ValueSource(strings = { "trailing", "duplicate" })
    void ambiguousHistoricalJsonCannotBeSilentlyRewritten(String kind) {
        form.setSchemaJson(kind.equals("trailing") ? SCHEMA + " {}"
                : SCHEMA.replace("\"label\":\"Reason\"", "\"label\":\"Private original\",\"label\":\"Reason\""));
        when(forms.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(1);
        assertThatThrownBy(() -> patch(3, "reason", "New", null, null, null)).isInstanceOf(BusinessException.class);
        verify(forms, never()).update(any(), any()); verifyNoInteractions(audit);
    }
    @ParameterizedTest @ValueSource(ints = { 0, -1, Integer.MAX_VALUE })
    void invalidVersionsAreRejectedBeforeReading(int version) {
        assertThatThrownBy(() -> patch(version, "reason", "New", null, null, null)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(forms, audit);
    }
    @Test void invalidLabelsAndWidthsCannotReachPersistence() {
        assertThatThrownBy(() -> patch(3, "reason", " ", null, null, null)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> patch(3, "reason", "x".repeat(41), null, null, null)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> patch(3, "reason", null, null, "x".repeat(81), null)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> patch(3, "reason", null, null, null, "arbitrary")).isInstanceOf(BusinessException.class);
        verifyNoInteractions(forms, audit);
    }
    @Test void malformedHistoricalExtensionsFailClosedWithoutRewritingThem() {
        form.setSchemaJson(SCHEMA.replace("\"min\":", "\"script\":\"unsafe\",\"min\":"));
        assertThatThrownBy(() -> patch(3, "reason", "New", null, null, null)).isInstanceOf(BusinessException.class);
        verify(forms, never()).update(any(), any()); verifyNoInteractions(audit);
    }
    @Test void concurrentUpdateFailsWithoutSuccessAudit() {
        when(forms.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(0);
        assertThatThrownBy(() -> patch(3, "reason", "New", null, null, null)).isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo("VERSION_CONFLICT");
        verifyNoInteractions(audit);
    }
    private ApprovalFormFieldPatchResponse patch(int version, String fieldName, String label, Boolean required, String placeholder, String width) {
        return service.patchFormDraftFieldAgent(7L, 99L, 11L,
                new ApprovalFormFieldPatchRequest(version, fieldName, label, required, placeholder, width));
    }
}
