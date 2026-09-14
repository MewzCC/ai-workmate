package com.aiworkmate.service;

import com.aiworkmate.dto.PageActionOverviewResponse;
import com.aiworkmate.dto.UpdatePageActionPolicyRequest;

public interface PageActionPolicyService {
    PageActionOverviewResponse overview(Long userId);

    PageActionOverviewResponse update(Long userId, String pageId, String toolCode,
                                      UpdatePageActionPolicyRequest request);
}
