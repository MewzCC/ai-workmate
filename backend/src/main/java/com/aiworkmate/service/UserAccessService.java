package com.aiworkmate.service;

import com.aiworkmate.service.model.ResolvedUserAccess;

import java.util.List;

public interface UserAccessService {

    ResolvedUserAccess resolveActiveUser(Long userId);

    List<String> permissionsForRole(String roleCode);
}
