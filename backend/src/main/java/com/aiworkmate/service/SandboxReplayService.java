package com.aiworkmate.service;

import com.aiworkmate.dto.SandboxReplayBaselineResponse;
import com.aiworkmate.dto.SandboxReplayDetailResponse;
import com.aiworkmate.dto.SandboxReplayPageResponse;
import com.aiworkmate.dto.SandboxReplayRequest;

import java.util.List;

public interface SandboxReplayService {
    SandboxReplayPageResponse list(Long userId, String keyword, String status, int page, int size);

    List<SandboxReplayBaselineResponse> baselines(Long userId, String keyword, int limit);

    SandboxReplayDetailResponse detail(Long userId, Long id);

    SandboxReplayDetailResponse execute(Long userId, SandboxReplayRequest request);
}
