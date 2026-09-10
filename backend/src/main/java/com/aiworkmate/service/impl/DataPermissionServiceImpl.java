package com.aiworkmate.service.impl;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.aiworkmate.dto.*;
import com.aiworkmate.mapper.AccessControlMapper;
import com.aiworkmate.mapper.DataPermissionMapper;
import com.aiworkmate.service.AccessControlService;
import com.aiworkmate.service.BusinessAuditService;
import com.aiworkmate.service.DataPermissionService;
import com.aiworkmate.service.UserAccessService;
import com.aiworkmate.service.model.ResolvedUserAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DataPermissionServiceImpl implements DataPermissionService {
    private static final String MANAGE = "data-scope:manage";
    private final DataPermissionMapper mapper;
    private final AccessControlMapper accessMapper;
    private final AccessControlService accessControlService;
    private final UserAccessService userAccessService;
    private final BusinessAuditService auditService;

    @Override @Transactional(readOnly = true)
    public DataPermissionOverviewResponse overview(Long operatorUserId) {
        ResolvedUserAccess actor = requireManager(operatorUserId);
        AccessControlOverviewResponse access = accessControlService.overview(actor.tenantId());
        return new DataPermissionOverviewResponse(policies(actor.tenantId()), access.roles(), access.users(),
                access.departments(), mapper.selectRoleBindings(actor.tenantId()), mapper.selectUserExceptions(actor.tenantId()));
    }

    @Override @Transactional
    public DataPermissionPolicyResponse create(Long operatorUserId, SaveDataPermissionPolicyRequest request) {
        ResolvedUserAccess actor = requireManager(operatorUserId);
        validateDepartments(actor.tenantId(), request.scopeType(), request.departmentIds());
        DataPermissionMapper.DataPermissionPolicyWrite row = write(actor, null, request);
        mapper.insertPolicy(row);
        replaceDepartments(actor.tenantId(), row.getId(), request.scopeType(), request.departmentIds());
        audit(actor, "CREATE", row.getId(), request.name());
        return findPolicy(actor.tenantId(), row.getId());
    }

    @Override @Transactional
    public DataPermissionPolicyResponse update(Long operatorUserId, Long id, SaveDataPermissionPolicyRequest request) {
        ResolvedUserAccess actor = requireManager(operatorUserId);
        if (request.version() == null) throw new BusinessException(ErrorCode.REQUEST_INVALID);
        requirePolicy(actor.tenantId(), id);
        if (!request.enabled() && (mapper.countRoleBindings(actor.tenantId(), id) > 0
                || mapper.countUserBindings(actor.tenantId(), id) > 0)) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID, "error.data_permission.policy_in_use");
        }
        validateDepartments(actor.tenantId(), request.scopeType(), request.departmentIds());
        DataPermissionMapper.DataPermissionPolicyWrite row = write(actor, id, request);
        if (mapper.updatePolicy(row) == 0) throw new BusinessException(ErrorCode.VERSION_CONFLICT);
        replaceDepartments(actor.tenantId(), id, request.scopeType(), request.departmentIds());
        audit(actor, "UPDATE", id, request.name());
        return findPolicy(actor.tenantId(), id);
    }

    @Override @Transactional
    public void delete(Long operatorUserId, Long id, long version) {
        ResolvedUserAccess actor = requireManager(operatorUserId);
        requirePolicy(actor.tenantId(), id);
        if (mapper.countRoleBindings(actor.tenantId(), id) > 0 || mapper.countUserBindings(actor.tenantId(), id) > 0)
            throw new BusinessException(ErrorCode.REQUEST_INVALID, "error.data_permission.policy_in_use");
        if (mapper.deletePolicy(actor.tenantId(), id, version) == 0) throw new BusinessException(ErrorCode.VERSION_CONFLICT);
        audit(actor, "DELETE", id, null);
    }

    @Override @Transactional
    public void bindRole(Long operatorUserId, String roleCode, Long policyId) {
        ResolvedUserAccess actor = requireManager(operatorUserId);
        String role = roleCode.trim().toUpperCase();
        if (accessMapper.countRoleForTenant(actor.tenantId(), role) == 0) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        DataPermissionPolicyResponse policy = requireEnabledPolicy(actor.tenantId(), policyId);
        if ("SUPER_ADMIN".equals(role) && !"ALL".equals(policy.scopeType()))
            throw new BusinessException(ErrorCode.REQUEST_INVALID, "error.data_permission.super_admin_all");
        mapper.bindRole(actor.tenantId(), role, policyId, actor.userId());
        accessMapper.incrementPermissionVersionForRole(actor.tenantId(), role);
        audit(actor, "BIND_ROLE", policyId, role);
    }

    @Override @Transactional
    public void bindUserException(Long operatorUserId, Long userId, Long policyId) {
        ResolvedUserAccess actor = requireManager(operatorUserId);
        if (!actor.tenantId().equals(accessMapper.selectUserTenantId(userId))) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        DataPermissionPolicyResponse policy = requireEnabledPolicy(actor.tenantId(), policyId);
        if (accessMapper.selectUserRoleCodes(actor.tenantId(), userId).contains("SUPER_ADMIN")
                && !"ALL".equals(policy.scopeType())) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID, "error.data_permission.super_admin_all");
        }
        mapper.bindUser(actor.tenantId(), userId, policyId, actor.userId());
        mapper.incrementUserPermissionVersion(actor.tenantId(), userId);
        audit(actor, "BIND_USER_EXCEPTION", policyId, String.valueOf(userId));
    }

    @Override @Transactional
    public void clearUserException(Long operatorUserId, Long userId) {
        ResolvedUserAccess actor = requireManager(operatorUserId);
        if (!actor.tenantId().equals(accessMapper.selectUserTenantId(userId))) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        mapper.deleteUserException(actor.tenantId(), userId);
        mapper.incrementUserPermissionVersion(actor.tenantId(), userId);
        audit(actor, "CLEAR_USER_EXCEPTION", userId, null);
    }

    @Override @Transactional(readOnly = true)
    public DataPermissionPreviewResponse preview(Long operatorUserId, Long userId) {
        ResolvedUserAccess actor = requireManager(operatorUserId);
        if (!actor.tenantId().equals(accessMapper.selectUserTenantId(userId))) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        ResolvedDataPermission resolved = resolve(actor.tenantId(), userId);
        return new DataPermissionPreviewResponse(userId, resolved.source(), sortedStrings(resolved.scopeTypes()),
                sortedLongs(resolved.departmentIds()), sortedLongs(resolved.visibleUserIds()));
    }

    @Override @Transactional(readOnly = true)
    public ResolvedDataPermission resolve(Long tenantId, Long userId) {
        Long ownDepartment = mapper.selectActiveUserDepartment(tenantId, userId);
        if (ownDepartment == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        List<String> values = mapper.selectEffectiveScopes(tenantId, userId);
        Set<String> scopes = values.isEmpty() ? Set.of("SELF") : new HashSet<>(values);
        boolean exception = mapper.selectUserExceptions(tenantId).stream().anyMatch(item -> item.userId().equals(userId));
        Set<Long> departments = new HashSet<>();
        if (scopes.contains("ALL")) {
            departments.addAll(accessMapper.selectDepartments(tenantId).stream().map(DepartmentResponse::id).toList());
        } else {
            if (scopes.contains("DEPARTMENT")) departments.add(ownDepartment);
            if (scopes.contains("DEPARTMENT_AND_CHILDREN")) departments.addAll(descendants(tenantId, ownDepartment));
            if (scopes.contains("CUSTOM_DEPARTMENTS")) departments.addAll(mapper.selectEffectiveCustomDepartments(tenantId, userId));
        }
        Set<Long> visible = accessMapper.selectUsers(tenantId).stream()
                .filter(user -> scopes.contains("ALL") || user.id().equals(userId) || departments.contains(user.departmentId()))
                .map(AccessUserRow::id).collect(Collectors.toCollection(TreeSet::new));
        return new ResolvedDataPermission(exception ? "USER_EXCEPTION" : "ROLE", Set.copyOf(scopes), Set.copyOf(departments), Set.copyOf(visible));
    }

    private Set<Long> descendants(Long tenantId, Long root) {
        List<DepartmentResponse> all = accessMapper.selectDepartments(tenantId);
        Set<Long> result = new HashSet<>(Set.of(root));
        boolean changed;
        do { changed = false; for (DepartmentResponse d : all) if (d.parentId() != null && result.contains(d.parentId())) changed |= result.add(d.id()); }
        while (changed);
        return result;
    }
    private List<DataPermissionPolicyResponse> policies(Long tenantId) { return mapper.selectPolicies(tenantId).stream().map(row ->
            new DataPermissionPolicyResponse(row.id(), row.name(), row.description(), row.scopeType(),
                    mapper.selectPolicyDepartments(tenantId, row.id()), row.enabled(), row.version(), row.updatedAt())).toList(); }
    private DataPermissionPolicyResponse findPolicy(Long tenantId, Long id) { return policies(tenantId).stream().filter(p -> p.id().equals(id)).findFirst().orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND)); }
    private DataPermissionPolicyResponse requirePolicy(Long tenantId, Long id) { if (mapper.countPolicy(tenantId, id)==0) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND); return findPolicy(tenantId,id); }
    private DataPermissionPolicyResponse requireEnabledPolicy(Long tenantId, Long id) { if (mapper.countEnabledPolicy(tenantId,id)==0) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND); return findPolicy(tenantId,id); }
    private ResolvedUserAccess requireManager(Long userId) { ResolvedUserAccess actor=userAccessService.resolveActiveUser(userId); if(actor==null) throw new BusinessException(ErrorCode.AUTH_REQUIRED); if(!actor.permissions().contains(MANAGE)) throw new BusinessException(ErrorCode.PERMISSION_DENIED); return actor; }
    private void validateDepartments(Long tenantId, String scope, Set<Long> ids) { if ("CUSTOM_DEPARTMENTS".equals(scope) && ids.isEmpty()) throw new BusinessException(ErrorCode.REQUEST_INVALID, "error.data_permission.custom_required"); for(Long id:ids) if(accessMapper.countDepartment(tenantId,id)==0) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND); }
    private void replaceDepartments(Long tenantId, Long policyId, String scope, Set<Long> ids) { mapper.deletePolicyDepartments(tenantId,policyId); if("CUSTOM_DEPARTMENTS".equals(scope) && !ids.isEmpty()) mapper.insertPolicyDepartments(tenantId,policyId,ids); }
    private DataPermissionMapper.DataPermissionPolicyWrite write(ResolvedUserAccess actor, Long id, SaveDataPermissionPolicyRequest request) { var row=new DataPermissionMapper.DataPermissionPolicyWrite(); row.setId(id); row.setTenantId(actor.tenantId()); row.setActorId(actor.userId()); row.setName(request.name().trim()); row.setDescription(request.description()==null?null:request.description().trim()); row.setScopeType(request.scopeType()); row.setEnabled(request.enabled()); row.setVersion(request.version()==null?0:request.version()); return row; }
    private void audit(ResolvedUserAccess actor,String action,Long id,String summary){auditService.recordTransactional(actor.tenantId(),actor.userId(),"DATA_PERMISSION",String.valueOf(id),action,"SUCCESS",summary);}
    private List<Long> sortedLongs(Collection<Long> values){return values.stream().sorted().toList();}
    private List<String> sortedStrings(Collection<String> values){return values.stream().sorted().toList();}
}
