package com.aiworkmate.service;

import com.aiworkmate.dto.PageCapabilityResponse;

public interface PageCapabilityService {
    PageCapabilityResponse resolve(Long userId, String pageId);
}
