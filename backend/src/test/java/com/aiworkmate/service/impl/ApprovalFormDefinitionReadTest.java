package com.aiworkmate.service.impl;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.entity.ApprovalForm;
import com.aiworkmate.mapper.*;
import com.aiworkmate.service.BusinessAuditService;
import com.aiworkmate.service.UserAccessService;
import com.aiworkmate.service.model.ResolvedUserAccess;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
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

class ApprovalFormDefinitionReadTest {
    private final ApprovalFormMapper forms = mock(ApprovalFormMapper.class);
    private final UserAccessService access = mock(UserAccessService.class);
    private final BusinessAuditService audit = mock(BusinessAuditService.class);
    private final ApprovalEngineServiceImpl service = new ApprovalEngineServiceImpl(forms,
            mock(ApprovalProcessMapper.class), mock(ApprovalRuleMapper.class), mock(UserMapper.class), access, audit, new ObjectMapper());
    private static final String SCHEMA = """
        {"fields":[{"name":"reason","label":"Reason","type":"text","required":true,"options":[],"width":"full"}]}
        """;
    @BeforeEach void metadata() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), ApprovalForm.class);
    }
    private void reader(long tenant) {
        when(access.resolveActiveUser(7L)).thenReturn(new ResolvedUserAccess(7L, "fixture", tenant,
                "EMPLOYEE", List.of("EMPLOYEE"), List.of("approval:read"), List.of("TENANT"), 1L));
    }
    private ApprovalForm form(String schema) {
        var form = new ApprovalForm(); form.setId(11L); form.setTenantId(99L); form.setFormKey("fixture");
        form.setFormName("Fixture"); form.setStatus("DISABLED"); form.setVersion(3); form.setSchemaJson(schema);
        return form;
    }
    @Test void readsOnlyTypedFieldsWithTenantAndIdInTheActualQuery() {
        reader(99L);
        when(forms.selectOne(any(LambdaQueryWrapper.class))).thenAnswer(call -> {
            LambdaQueryWrapper<ApprovalForm> query = call.getArgument(0);
            assertThat(query.getSqlSegment()).contains("tenant_id", "id", "deleted");
            assertThat(query.getParamNameValuePairs().values()).contains(99L, 11L, false);
            return form(SCHEMA);
        });
        var result = service.readFormDefinitionAgent(7L, 99L, 11L);
        assertThat(result.formId()).isEqualTo(11L); assertThat(result.version()).isEqualTo(3);
        assertThat(result.fields()).singleElement().satisfies(field -> {
            assertThat(field.name()).isEqualTo("reason"); assertThat(field.required()).isTrue();
        });
        verify(forms, never()).selectById(any()); verifyNoInteractions(audit);
    }
    @Test void deniesAChangedTenantBeforeAnyRead() {
        reader(100L);
        assertThatThrownBy(() -> service.readFormDefinitionAgent(7L, 99L, 11L))
                .isInstanceOf(BusinessException.class).extracting("errorCode").isEqualTo("PERMISSION_DENIED");
        verifyNoInteractions(forms);
    }
    @Test void deniesRevokedBusinessReadBeforeAnyRead() {
        when(access.resolveActiveUser(7L)).thenReturn(new ResolvedUserAccess(7L, "fixture", 99L,
                "EMPLOYEE", List.of("EMPLOYEE"), List.of(), List.of("TENANT"), 2L));
        assertThatThrownBy(() -> service.readFormDefinitionAgent(7L, 99L, 11L)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(forms);
    }
    @Test void missingOrFilteredResourceIsNotFound() {
        reader(99L);
        assertThatThrownBy(() -> service.readFormDefinitionAgent(7L, 99L, 11L))
                .isInstanceOf(BusinessException.class).extracting("errorCode").isEqualTo("RESOURCE_NOT_FOUND");
    }
    @Test void acceptsTwentyFieldsButRejectsTwentyOneWithoutPartialOutput() throws Exception {
        reader(99L);
        var mapper = new ObjectMapper(); var root = mapper.createObjectNode(); var fields = root.putArray("fields");
        for (int i = 0; i < 20; i++) fields.addObject().put("name", "field" + i).put("label", "Field")
                .put("type", "text").put("required", false).put("width", "full").putArray("options");
        when(forms.selectOne(any(LambdaQueryWrapper.class))).thenReturn(form(root.toString()));
        assertThat(service.readFormDefinitionAgent(7L, 99L, 11L).fields()).hasSize(20);
        fields.addObject().put("name", "field20").put("label", "Field").put("type", "text")
                .put("required", false).put("width", "full").putArray("options");
        when(forms.selectOne(any(LambdaQueryWrapper.class))).thenReturn(form(root.toString()));
        assertThatThrownBy(() -> service.readFormDefinitionAgent(7L, 99L, 11L)).isInstanceOf(BusinessException.class);
    }
    @Test void acceptsTenOptionsButRejectsEleven() throws Exception {
        reader(99L); var root = new ObjectMapper().readTree(SCHEMA);
        var options = (com.fasterxml.jackson.databind.node.ArrayNode) root.at("/fields/0/options");
        for (int i = 0; i < 10; i++) options.add("Option" + i);
        when(forms.selectOne(any(LambdaQueryWrapper.class))).thenReturn(form(root.toString()));
        assertThat(service.readFormDefinitionAgent(7L, 99L, 11L).fields().get(0).options()).hasSize(10);
        options.add("Too many"); when(forms.selectOne(any(LambdaQueryWrapper.class))).thenReturn(form(root.toString()));
        assertThatThrownBy(() -> service.readFormDefinitionAgent(7L, 99L, 11L)).isInstanceOf(BusinessException.class);
    }
    @Test void boundsSourceBytesBeforeParsingEvenWhenTheSchemaWouldOtherwiseBeValid() {
        reader(99L);
        String unicodeSchema = SCHEMA.replace("Reason", "字段名称");
        int bytes = unicodeSchema.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        when(forms.selectOne(any(LambdaQueryWrapper.class))).thenReturn(form(unicodeSchema + " ".repeat(65536 - bytes)));
        assertThat(service.readFormDefinitionAgent(7L, 99L, 11L).fields()).hasSize(1);
        when(forms.selectOne(any(LambdaQueryWrapper.class))).thenReturn(form(unicodeSchema + " ".repeat(65537 - bytes)));
        assertThatThrownBy(() -> service.readFormDefinitionAgent(7L, 99L, 11L)).isInstanceOf(BusinessException.class);
    }
    @Test void readsTheExistingSeedShapeAndPreservesItsNumericMinimum() {
        reader(99L);
        when(forms.selectOne(any(LambdaQueryWrapper.class))).thenReturn(form(
                "{\"fields\":[{\"name\":\"amount\",\"label\":\"Amount\",\"type\":\"number\",\"required\":true,\"min\":0.01}]}"));
        var result = new ObjectMapper().valueToTree(service.readFormDefinitionAgent(7L, 99L, 11L));
        assertThat(result.at("/fields/0/name").asText()).isEqualTo("amount");
        assertThat(result.at("/fields/0/min").decimalValue()).isEqualByComparingTo("0.01");
        assertThat(result.at("/fields/0/width").asText()).isEqualTo("full");
        assertThat(result.at("/fields/0/options").isArray()).isTrue();
    }
    @Test void readsTheLegacyDesignerKeyWithoutChangingItsBusinessIdentity() {
        reader(99L); when(forms.selectOne(any(LambdaQueryWrapper.class))).thenReturn(form(
                "{\"fields\":[{\"key\":\"legacyReason\",\"label\":\"Reason\",\"type\":\"text\"}]}"));
        var result = service.readFormDefinitionAgent(7L, 99L, 11L);
        assertThat(result.fields().get(0).name()).isEqualTo("legacyReason");
        assertThat(result.fields().get(0).required()).isFalse();
    }
    @Test void rejectsAnActuallyOutOfRangeDecimalRatherThanRoundingItOntoTheBoundary() {
        reader(99L); when(forms.selectOne(any(LambdaQueryWrapper.class))).thenReturn(form(
                "{\"fields\":[{\"name\":\"amount\",\"label\":\"Amount\",\"type\":\"number\",\"min\":1000000000000000.01}]}"));
        assertThatThrownBy(() -> service.readFormDefinitionAgent(7L, 99L, 11L)).isInstanceOf(BusinessException.class);
    }
    @Test void preservesDecimalPrecisionWhenReadingAnExistingConstraint() {
        reader(99L); when(forms.selectOne(any(LambdaQueryWrapper.class))).thenReturn(form(
                "{\"fields\":[{\"name\":\"amount\",\"label\":\"Amount\",\"type\":\"number\",\"min\":0.123456789012345678901}]}"));
        assertThat(service.readFormDefinitionAgent(7L, 99L, 11L).fields().get(0).min())
                .isEqualByComparingTo("0.123456789012345678901");
    }
    @ParameterizedTest @ValueSource(strings = {
        "{}", "{\"fields\":[]}", "{\"fields\":[{\"name\":\"reason\",\"type\":\"script\"}]}",
        "{\"fields\":[{\"name\":\"reason\",\"key\":\"other\",\"label\":\"Reason\",\"type\":\"text\"}]}",
        "{\"fields\":[],\"url\":\"https://invalid.example\"}",
        "{\"fields\":[{\"name\":\"reason\",\"label\":\"Reason\",\"type\":\"text\",\"required\":true,\"options\":[],\"width\":\"full\",\"script\":\"private\"}]}"
    }) void unsupportedDefinitionsFailClosedRatherThanReturningRawOrPartialSchema(String schema) {
        reader(99L); when(forms.selectOne(any(LambdaQueryWrapper.class))).thenReturn(form(schema));
        assertThatThrownBy(() -> service.readFormDefinitionAgent(7L, 99L, 11L)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(audit);
    }
}
