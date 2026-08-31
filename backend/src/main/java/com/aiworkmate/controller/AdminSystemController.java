package com.aiworkmate.controller;

import com.aiworkmate.common.Result;
import com.aiworkmate.dto.SystemCapabilitiesResponse;
import com.aiworkmate.service.SystemCapabilityService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/system")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('access:manage')")
public class AdminSystemController {

    private final SystemCapabilityService systemCapabilityService;

    @GetMapping("/capabilities")
    public Result<SystemCapabilitiesResponse> capabilities() {
        return Result.ok(systemCapabilityService.inspect());
    }
}
