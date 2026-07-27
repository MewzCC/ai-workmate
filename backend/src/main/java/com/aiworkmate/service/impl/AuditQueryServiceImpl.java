package com.aiworkmate.service.impl;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.aiworkmate.common.PageResponse;
import com.aiworkmate.dto.AuditRecordResponse;
import com.aiworkmate.mapper.BusinessAuditLogMapper;
import com.aiworkmate.service.AuditQueryService;
import com.aiworkmate.service.UserAccessService;
import com.aiworkmate.service.model.ResolvedUserAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AuditQueryServiceImpl implements AuditQueryService {

    private final BusinessAuditLogMapper mapper;
    private final UserAccessService userAccessService;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AuditRecordResponse> query(Long userId,
                                                   Long actorUserId,
                                                   String action,
                                                   String resourceType,
                                                   String result,
                                                   LocalDateTime from,
                                                   LocalDateTime to,
                                                   int page,
                                                   int size) {
        ResolvedUserAccess access = requireAccess(userId);
        if (!access.permissions().contains("audit:read")) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED);
        }
        int safePage = Math.max(1, page);
        int safeSize = Math.min(100, Math.max(1, size));
        int offset = (safePage - 1) * safeSize;
        List<AuditRecordResponse> records = mapper.selectRecords(
                access.tenantId(), actorUserId, action, resourceType, result,
                from, to, safeSize, offset);
        long total = mapper.countRecords(
                access.tenantId(), actorUserId, action, resourceType, result, from, to);
        return PageResponse.of(records, total, safePage, safeSize);
    }

    private ResolvedUserAccess requireAccess(Long userId) {
        ResolvedUserAccess access = userAccessService.resolveActiveUser(userId);
        if (access == null) {
            throw new BusinessException(ErrorCode.AUTH_REQUIRED);
        }
        return access;
    }
}
