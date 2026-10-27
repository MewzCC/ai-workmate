package com.aiworkmate.controller;

import com.aiworkmate.common.GlobalExceptionHandler;
import com.aiworkmate.config.I18nConfig;
import com.aiworkmate.config.RequestTraceFilter;
import com.aiworkmate.config.SecurityConfig;
import com.aiworkmate.dto.DictionaryTypeResponse;
import com.aiworkmate.dto.DictionaryItemResponse;
import com.aiworkmate.dto.DictionaryItemDetailResponse;
import com.aiworkmate.security.JwtAuthenticationFilter;
import com.aiworkmate.security.JwtValidationStatus;
import com.aiworkmate.service.DataDictionaryService;
import com.aiworkmate.service.UserAccessService;
import com.aiworkmate.service.model.ResolvedUserAccess;
import com.aiworkmate.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@WebMvcTest(DataDictionaryController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, RequestTraceFilter.class, GlobalExceptionHandler.class, I18nConfig.class})
class DataDictionaryDetailControllerSecurityTest {
    @Autowired MockMvc mvc;
    @MockBean DataDictionaryService service;
    @MockBean JwtUtil jwtUtil;
    @MockBean UserAccessService accessService;

    @BeforeEach void setup() {
        when(jwtUtil.validateTokenStatus("valid")).thenReturn(JwtValidationStatus.VALID);
        when(jwtUtil.getUserIdFromToken("valid")).thenReturn(42L);
        when(accessService.resolveActiveUser(42L)).thenReturn(access(List.of("route:dictionary")));
    }

    @Test void detailsRequireAuthentication() throws Exception {
        mvc.perform(get("/api/admin/dictionaries/11")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/dictionaries/items/21")).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test void aReadonlyDictionaryUserCanReachBothDetailRoutes() throws Exception {
        var type = new DictionaryTypeResponse(11L, "FIXTURE", "类型", null, "DISABLED", 0, 1, 0, 3, null, false);
        var item = new DictionaryItemResponse(21L, 11L, "FIRST", "第一项", null, "DISABLED", 0, 0L, 4, null, false, false);
        when(service.typeDetail(42L, 11L)).thenReturn(type);
        when(service.itemDetail(42L, 21L)).thenReturn(new DictionaryItemDetailResponse(type, item));
        mvc.perform(get("/api/admin/dictionaries/11").header("Authorization", "Bearer valid").param("userId", "999"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.version").value(3)).andExpect(jsonPath("$.data.canManage").value(false));
        mvc.perform(get("/api/admin/dictionaries/items/21").header("Authorization", "Bearer valid").param("tenantId", "999"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.dictionaryType.id").value(11))
                .andExpect(jsonPath("$.data.item.version").value(4)).andExpect(jsonPath("$.data.item.canDelete").value(false));
        verify(service).typeDetail(42L, 11L); verify(service).itemDetail(42L, 21L);
    }

    @Test void anAdministratorLabelDoesNotReplaceTheRealtimeReadPermission() throws Exception {
        when(accessService.resolveActiveUser(42L)).thenReturn(access(List.of()));
        mvc.perform(get("/api/admin/dictionaries/11").header("Authorization", "Bearer valid")).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/dictionaries/items/21").header("Authorization", "Bearer valid")).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test void nonPositiveIdsAreRejectedBeforeTheDomainCallWithLocalizedErrors() throws Exception {
        for (String path : List.of("/api/admin/dictionaries/0", "/api/admin/dictionaries/items/-1")) {
            mvc.perform(get(path).header("Authorization", "Bearer valid").header("Accept-Language", "en-US"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("REQUEST_INVALID"))
                    .andExpect(jsonPath("$.message").value("Dictionary record ID must be a positive integer"));
        }
        verifyNoInteractions(service);
    }

    @Test void malformedIdsAreClientErrorsNotInternalFailures() throws Exception {
        mvc.perform(get("/api/admin/dictionaries/items/not-a-number").header("Authorization", "Bearer valid"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("REQUEST_INVALID"));
        verifyNoInteractions(service);
    }

    @Test void revokedPermissionIsRecheckedWithoutRelogin() throws Exception {
        mvc.perform(get("/api/admin/dictionaries/11").header("Authorization", "Bearer valid")).andExpect(status().isOk());
        when(accessService.resolveActiveUser(42L)).thenReturn(access(List.of()));
        mvc.perform(get("/api/admin/dictionaries/11").header("Authorization", "Bearer valid")).andExpect(status().isForbidden());
        verify(service, times(1)).typeDetail(42L, 11L);
    }

    private ResolvedUserAccess access(List<String> permissions) {
        return new ResolvedUserAccess(42L, "fixture", 9L, "SUPER_ADMIN", List.of("SUPER_ADMIN"), permissions, List.of("TENANT"), 1L);
    }
}
