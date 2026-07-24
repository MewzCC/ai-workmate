package com.aiworkmate.service.impl;

import com.aiworkmate.entity.User;
import com.aiworkmate.mapper.AccessControlMapper;
import com.aiworkmate.mapper.UserMapper;
import com.aiworkmate.service.UserAccessService;
import com.aiworkmate.service.model.ResolvedUserAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserAccessServiceImpl implements UserAccessService {

    private static final String SUPER_ADMIN = "SUPER_ADMIN";

    private final UserMapper userMapper;
    private final AccessControlMapper accessControlMapper;

    @Override
    @Transactional(readOnly = true)
    public ResolvedUserAccess resolveActiveUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null || !Integer.valueOf(1).equals(user.getStatus())) {
            return null;
        }
        return new ResolvedUserAccess(
                user.getId(),
                user.getEmail(),
                user.getRole(),
                permissionsForRole(user.getRole())
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> permissionsForRole(String roleCode) {
        if (SUPER_ADMIN.equals(roleCode)) {
            return List.copyOf(accessControlMapper.selectAllPermissionCodes());
        }
        return List.copyOf(accessControlMapper.selectPermissionCodes(roleCode));
    }
}
