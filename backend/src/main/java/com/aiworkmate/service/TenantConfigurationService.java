package com.aiworkmate.service;

import com.aiworkmate.dto.TenantBusinessRequest;
import com.aiworkmate.dto.TenantConfigurationHistoryPageResponse;
import com.aiworkmate.dto.TenantConfigurationResponse;
import com.aiworkmate.dto.TenantFeaturesRequest;
import com.aiworkmate.dto.TenantProfileRequest;
import com.aiworkmate.dto.TenantSecurityRequest;

public interface TenantConfigurationService {
    TenantConfigurationResponse get(Long userId);
    TenantConfigurationResponse updateProfile(Long userId, TenantProfileRequest request);
    TenantConfigurationResponse updateFeatures(Long userId, TenantFeaturesRequest request);
    TenantConfigurationResponse updateBusiness(Long userId, TenantBusinessRequest request);
    TenantConfigurationResponse updateSecurity(Long userId, TenantSecurityRequest request);
    TenantConfigurationHistoryPageResponse history(Long userId, int page, int size);
}
