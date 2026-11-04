package com.aiworkmate.service.impl;

import com.aiworkmate.common.AvatarUrls;
import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.aiworkmate.dto.TodoQueryResult;
import com.aiworkmate.dto.UnifiedTodoResponse;
import com.aiworkmate.mapper.UnifiedTodoMapper;
import com.aiworkmate.service.TodoQueryService;
import com.aiworkmate.service.UserAccessService;
import com.aiworkmate.service.model.BusinessCandidateQuery;
import com.aiworkmate.service.model.ResolvedUserAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
public class TodoQueryServiceImpl implements TodoQueryService {
    private final UnifiedTodoMapper mapper;
    private final UserAccessService access;
    @Override public TodoQueryResult mine(Long userId,Long expectedTenantId,String businessType,
            String status,LocalDateTime from,LocalDateTime to,int page,int size) {
        ResolvedUserAccess actor=actor(userId,expectedTenantId); new BusinessCandidateQuery(null,page,size);
        String type=businessType==null || businessType.isBlank() ? "ALL" : businessType;
        String state=status==null ? "PENDING" : status.isBlank() ? "ALL" : status;
        if(size>50 || !Set.of("ALL","LEAVE_APPLICATION","GENERIC_APPROVAL").contains(type)
                || !Set.of("ALL","PENDING","APPROVED","REJECTED","CANCELLED").contains(state)
                || from!=null && to!=null && from.isAfter(to)) throw new BusinessException(ErrorCode.REQUEST_INVALID);
        return new TodoQueryResult(mapper.mine(actor.tenantId(),actor.userId(),type,state,from,to,size,(page-1L)*size).stream().map(this::avatar).toList(),
                mapper.count(actor.tenantId(),actor.userId(),type,state,from,to),page,size,type,state,from,to);
    }
    @Override public UnifiedTodoResponse summary(Long userId,Long expectedTenantId,Long taskId) {
        ResolvedUserAccess actor=actor(userId,expectedTenantId);
        if(taskId==null || taskId<1) throw new BusinessException(ErrorCode.REQUEST_INVALID);
        var item=mapper.summary(actor.tenantId(),actor.userId(),taskId);
        if(item==null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        return avatar(item);
    }
    private UnifiedTodoResponse avatar(UnifiedTodoResponse item) {
        return item.withAvatarUrl(AvatarUrls.build(item.applicantUserId(),item.applicantAvatar(),item.applicantUpdatedAt()));
    }
    private ResolvedUserAccess actor(Long userId,Long tenant) {
        ResolvedUserAccess actor=access.resolveActiveUser(userId);
        if(actor==null) throw new BusinessException(ErrorCode.AUTH_REQUIRED);
        if(tenant!=null && !tenant.equals(actor.tenantId())) throw new BusinessException(ErrorCode.RESOURCE_FORBIDDEN);
        if(!actor.permissions().contains("todo:read")) throw new BusinessException(ErrorCode.PERMISSION_DENIED);
        return actor;
    }
}
