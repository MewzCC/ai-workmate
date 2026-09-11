package com.aiworkmate.service.impl;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.aiworkmate.common.TraceContext;
import com.aiworkmate.dto.*;
import com.aiworkmate.entity.IntegrationEndpoint;
import com.aiworkmate.entity.IntegrationInvocation;
import com.aiworkmate.mapper.IntegrationEndpointMapper;
import com.aiworkmate.mapper.IntegrationInvocationMapper;
import com.aiworkmate.service.BusinessAuditService;
import com.aiworkmate.service.IntegrationEndpointService;
import com.aiworkmate.service.IntegrationSandboxClient;
import com.aiworkmate.service.UserAccessService;
import com.aiworkmate.service.model.ResolvedUserAccess;
import com.aiworkmate.service.model.SandboxCallResult;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class IntegrationEndpointServiceImpl implements IntegrationEndpointService {
    private static final String READ = "route:api-center";
    private static final String MANAGE = "integration:endpoint:manage";
    private static final String EXECUTE = "integration:endpoint:execute";
    private static final Set<String> STATUSES = Set.of("DRAFT", "ACTIVE", "DISABLED");
    private static final Set<String> SENSITIVE_KEYS = Set.of("authorization", "cookie", "password", "passwd", "secret", "token", "apikey", "api_key", "accesskey", "privatekey");
    private static final Pattern SENSITIVE_TEXT = Pattern.compile("(?i)(authorization|cookie|password|passwd|secret|token|api[_-]?key|access[_-]?key|private[_-]?key)(\\s*[:=]\\s*)([^\\s,;]+)");
    private static final Map<String,List<String>> TRANSITIONS = Map.of("DRAFT",List.of("ACTIVE","DISABLED"),"ACTIVE",List.of("DISABLED"),"DISABLED",List.of("ACTIVE"));
    private static final Duration EXECUTION_COOLDOWN = Duration.ofSeconds(3);

    private final IntegrationEndpointMapper endpointMapper;
    private final IntegrationInvocationMapper invocationMapper;
    private final IntegrationSandboxClient sandboxClient;
    private final UserAccessService accessService;
    private final BusinessAuditService auditService;
    private final ObjectMapper objectMapper;

    @Override @Transactional(readOnly=true)
    public IntegrationPageResponse list(Long userId,String keyword,String status,int page,int size){
        ResolvedUserAccess actor=requireRead(userId);String normalized=normalizeStatus(status);int safePage=Math.max(1,page),safeSize=Math.min(100,Math.max(1,size));
        LambdaQueryWrapper<IntegrationEndpoint> query=base(actor.tenantId()).eq(normalized!=null,IntegrationEndpoint::getStatus,normalized)
                .and(StringUtils.hasText(keyword),q->q.like(IntegrationEndpoint::getEndpointCode,keyword.trim()).or().like(IntegrationEndpoint::getName,keyword.trim()).or().like(IntegrationEndpoint::getUpstreamCode,keyword.trim()))
                .orderByDesc(IntegrationEndpoint::getUpdatedAt);
        Page<IntegrationEndpoint> result=endpointMapper.selectPage(new Page<>(safePage,safeSize),query);
        long success=invocationMapper.selectCount(new LambdaQueryWrapper<IntegrationInvocation>().eq(IntegrationInvocation::getTenantId,actor.tenantId()).eq(IntegrationInvocation::getOutcome,"SUCCESS"));
        long failed=invocationMapper.selectCount(new LambdaQueryWrapper<IntegrationInvocation>().eq(IntegrationInvocation::getTenantId,actor.tenantId()).eq(IntegrationInvocation::getOutcome,"FAILED"));
        IntegrationPageResponse.Stats stats=new IntegrationPageResponse.Stats(endpointMapper.selectCount(base(actor.tenantId())),count(actor.tenantId(),"ACTIVE"),count(actor.tenantId(),"DISABLED"),success,failed);
        boolean manage=canManage(actor),execute=canExecute(actor);
        return new IntegrationPageResponse(result.getRecords().stream().map(e->response(e,manage,execute)).toList(),result.getTotal(),safePage,safeSize,stats,manage,execute);
    }

    @Override @Transactional(readOnly=true)
    public IntegrationDetailResponse detail(Long userId,Long id){ResolvedUserAccess actor=requireRead(userId);IntegrationEndpoint endpoint=requireEndpoint(actor,id);
        List<IntegrationInvocationResponse> records=invocationMapper.selectList(new LambdaQueryWrapper<IntegrationInvocation>().eq(IntegrationInvocation::getTenantId,actor.tenantId()).eq(IntegrationInvocation::getEndpointId,id).orderByDesc(IntegrationInvocation::getCreatedAt).last("LIMIT 50")).stream().map(this::invocationResponse).toList();
        return new IntegrationDetailResponse(response(endpoint,canManage(actor),canExecute(actor)),records);}

    @Override @Transactional(readOnly=true)
    public IntegrationOptionsResponse options(Long userId){requireRead(userId);return new IntegrationOptionsResponse(sandboxClient.options());}

    @Override @Transactional
    public IntegrationEndpointResponse create(Long userId,IntegrationEndpointRequest request){ResolvedUserAccess actor=requireManage(userId);validate(request);
        IntegrationEndpoint endpoint=new IntegrationEndpoint();endpoint.setTenantId(actor.tenantId());endpoint.setEndpointCode(code(request.code()));apply(endpoint,request);endpoint.setStatus("DRAFT");endpoint.setVersion(0);endpoint.setDeleted(false);endpoint.setCreatedBy(actor.userId());endpoint.setUpdatedBy(actor.userId());LocalDateTime now=LocalDateTime.now();endpoint.setCreatedAt(now);endpoint.setUpdatedAt(now);
        try{endpointMapper.insert(endpoint);}catch(DuplicateKeyException ex){throw new BusinessException(ErrorCode.REQUEST_INVALID,"validation.integration.duplicate");}
        audit(actor,endpoint,"CREATE");return response(endpoint,true,canExecute(actor));}

    @Override @Transactional
    public IntegrationEndpointResponse update(Long userId,Long id,IntegrationEndpointRequest request){ResolvedUserAccess actor=requireManage(userId);IntegrationEndpoint old=requireEndpoint(actor,id);requireVersion(request.version(),old.getVersion());
        if("ACTIVE".equals(old.getStatus()))throw new BusinessException(ErrorCode.BUSINESS_STATE_INVALID,"validation.integration.edit.active");
        if(!old.getEndpointCode().equals(code(request.code())))throw new BusinessException(ErrorCode.BUSINESS_STATE_INVALID,"validation.integration.code.immutable");validate(request);
        IntegrationEndpoint changed=new IntegrationEndpoint();apply(changed,request);changed.setUpdatedBy(actor.userId());changed.setUpdatedAt(LocalDateTime.now());changed.setVersion(old.getVersion()+1);
        int updated;try{updated=endpointMapper.update(changed,update(actor,old));}catch(DuplicateKeyException ex){throw new BusinessException(ErrorCode.REQUEST_INVALID,"validation.integration.duplicate");}if(updated!=1)conflict();audit(actor,old,"UPDATE");return response(requireEndpoint(actor,id),true,canExecute(actor));}

    @Override @Transactional
    public IntegrationEndpointResponse updateStatus(Long userId,Long id,IntegrationStatusRequest request){ResolvedUserAccess actor=requireManage(userId);IntegrationEndpoint old=requireEndpoint(actor,id);requireVersion(request.version(),old.getVersion());String target=normalizeStatus(request.status());
        if(target==null||!TRANSITIONS.getOrDefault(old.getStatus(),List.of()).contains(target))throw new BusinessException(ErrorCode.BUSINESS_STATE_INVALID,"validation.integration.transition.invalid");
        if("ACTIVE".equals(target)&&!sandboxClient.isAvailable(old.getUpstreamCode()))throw new BusinessException(ErrorCode.BUSINESS_STATE_INVALID,"validation.integration.upstream.unavailable");
        int updated=endpointMapper.update(null,update(actor,old).set(IntegrationEndpoint::getStatus,target).set(IntegrationEndpoint::getUpdatedBy,actor.userId()).set(IntegrationEndpoint::getUpdatedAt,LocalDateTime.now()).set(IntegrationEndpoint::getVersion,old.getVersion()+1));if(updated!=1)conflict();audit(actor,old,"STATUS_"+target);return response(requireEndpoint(actor,id),true,canExecute(actor));}

    @Override @Transactional
    public IntegrationInvocationResponse execute(Long userId,Long id,Integer version){ResolvedUserAccess actor=requireExecute(userId);IntegrationEndpoint endpoint=requireEndpoint(actor,id);requireVersion(version,endpoint.getVersion());if(!"ACTIVE".equals(endpoint.getStatus()))throw new BusinessException(ErrorCode.BUSINESS_STATE_INVALID,"validation.integration.execute.activeOnly");
        IntegrationInvocation latest=invocationMapper.selectOne(new LambdaQueryWrapper<IntegrationInvocation>().eq(IntegrationInvocation::getTenantId,actor.tenantId()).eq(IntegrationInvocation::getEndpointId,id).eq(IntegrationInvocation::getOperatorId,actor.userId()).orderByDesc(IntegrationInvocation::getCreatedAt).last("LIMIT 1"));
        if(latest!=null&&latest.getCreatedAt().plus(EXECUTION_COOLDOWN).isAfter(LocalDateTime.now()))throw new BusinessException(ErrorCode.RATE_LIMITED,"validation.integration.execute.tooFrequent");
        SandboxCallResult call=sandboxClient.execute(endpoint.getUpstreamCode(),endpoint.getHttpMethod(),endpoint.getRelativePath(),endpoint.getRequestTemplate());
        IntegrationInvocation invocation=new IntegrationInvocation();invocation.setTenantId(actor.tenantId());invocation.setEndpointId(id);invocation.setRequestHash(hash(endpoint));invocation.setOutcome(call.outcome());invocation.setHttpStatus(call.httpStatus());invocation.setDurationMs(call.durationMs());invocation.setResponsePreview(sanitizeResponsePreview(call.responsePreview()));invocation.setErrorCode(call.errorCode());invocation.setTraceId(StringUtils.hasText(TraceContext.traceId())?TraceContext.traceId():UUID.randomUUID().toString().replace("-",""));invocation.setOperatorId(actor.userId());invocation.setOperatorLabel(actor.username());invocation.setCreatedAt(LocalDateTime.now());invocationMapper.insert(invocation);audit(actor,endpoint,"EXECUTE_"+call.outcome());return invocationResponse(invocation);}

    private void validate(IntegrationEndpointRequest request){if(!sandboxClient.isRegistered(request.upstreamCode()))throw new BusinessException(ErrorCode.REQUEST_INVALID,"validation.integration.upstream.invalid");validatePath(request.relativePath());String body=trim(request.requestTemplate());if(("GET".equals(request.method())||"DELETE".equals(request.method()))&&body!=null)throw new BusinessException(ErrorCode.REQUEST_INVALID,"validation.integration.body.notAllowed");if(body!=null){try{JsonNode node=objectMapper.readTree(body);if(!node.isObject()||containsSensitive(node))throw new BusinessException(ErrorCode.REQUEST_INVALID,"validation.integration.template.sensitive");}catch(BusinessException ex){throw ex;}catch(Exception ex){throw new BusinessException(ErrorCode.REQUEST_INVALID,"validation.integration.template.invalid");}}}
    private void validatePath(String path){String lower=path.toLowerCase(Locale.ROOT);if(!path.startsWith("/")||path.startsWith("//")||path.contains("\\")||path.contains("\r")||path.contains("\n")||lower.contains("://")||lower.contains("..")||lower.contains("%2e")||lower.contains("@"))throw new BusinessException(ErrorCode.REQUEST_INVALID,"validation.integration.path.invalid");}
    private boolean containsSensitive(JsonNode node){Iterator<String> names=node.fieldNames();while(names.hasNext())if(isSensitiveKey(names.next()))return true;for(JsonNode child:node)if(child.isContainerNode()&&containsSensitive(child))return true;return false;}
    private String sanitizeResponsePreview(String preview){if(!StringUtils.hasText(preview))return null;try{JsonNode root=objectMapper.readTree(preview);redactSensitiveValues(root);return objectMapper.writeValueAsString(root);}catch(Exception ignored){return SENSITIVE_TEXT.matcher(preview).replaceAll("$1$2[REDACTED]");}}
    private void redactSensitiveValues(JsonNode node){if(node.isObject()){List<String> names=new ArrayList<>();node.fieldNames().forEachRemaining(names::add);for(String name:names){if(isSensitiveKey(name))((com.fasterxml.jackson.databind.node.ObjectNode)node).put(name,"[REDACTED]");else redactSensitiveValues(node.get(name));}}else if(node.isArray())for(JsonNode child:node)redactSensitiveValues(child);}
    private boolean isSensitiveKey(String name){String normalized=name.replace("-","").replace("_","").toLowerCase(Locale.ROOT);return SENSITIVE_KEYS.stream().map(key->key.replace("_","")).anyMatch(normalized::equals);}
    private void apply(IntegrationEndpoint e,IntegrationEndpointRequest r){e.setName(r.name().trim());e.setUpstreamCode(r.upstreamCode().trim());e.setHttpMethod(r.method().trim().toUpperCase(Locale.ROOT));e.setRelativePath(r.relativePath().trim());e.setRequestTemplate(trim(r.requestTemplate()));e.setDescription(trim(r.description()));}
    private IntegrationEndpointResponse response(IntegrationEndpoint e,boolean manage,boolean execute){return new IntegrationEndpointResponse(e.getId(),e.getEndpointCode(),e.getName(),e.getUpstreamCode(),e.getHttpMethod(),e.getRelativePath(),e.getRequestTemplate(),e.getDescription(),e.getStatus(),e.getVersion(),e.getUpdatedAt(),manage,execute&&"ACTIVE".equals(e.getStatus()),manage?TRANSITIONS.getOrDefault(e.getStatus(),List.of()):List.of());}
    private IntegrationInvocationResponse invocationResponse(IntegrationInvocation i){return new IntegrationInvocationResponse(i.getId(),i.getOutcome(),i.getHttpStatus(),i.getDurationMs(),i.getResponsePreview(),i.getErrorCode(),i.getOperatorLabel(),i.getCreatedAt());}
    private ResolvedUserAccess requireRead(Long id){ResolvedUserAccess a=accessService.resolveActiveUser(id);if(a==null)throw new BusinessException(ErrorCode.AUTH_REQUIRED);if(!a.permissions().contains(READ))throw new BusinessException(ErrorCode.PERMISSION_DENIED);return a;}
    private ResolvedUserAccess requireManage(Long id){ResolvedUserAccess a=requireRead(id);if(!canManage(a))throw new BusinessException(ErrorCode.PERMISSION_DENIED);return a;}private ResolvedUserAccess requireExecute(Long id){ResolvedUserAccess a=requireRead(id);if(!canExecute(a))throw new BusinessException(ErrorCode.PERMISSION_DENIED);return a;}
    private boolean canManage(ResolvedUserAccess a){return a.permissions().contains(MANAGE);}private boolean canExecute(ResolvedUserAccess a){return a.permissions().contains(EXECUTE);}
    private IntegrationEndpoint requireEndpoint(ResolvedUserAccess a,Long id){IntegrationEndpoint e=endpointMapper.selectOne(base(a.tenantId()).eq(IntegrationEndpoint::getId,id));if(e==null)throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);return e;}
    private LambdaQueryWrapper<IntegrationEndpoint> base(Long tenant){return new LambdaQueryWrapper<IntegrationEndpoint>().eq(IntegrationEndpoint::getTenantId,tenant).eq(IntegrationEndpoint::getDeleted,false);}
    private LambdaUpdateWrapper<IntegrationEndpoint> update(ResolvedUserAccess a,IntegrationEndpoint e){return new LambdaUpdateWrapper<IntegrationEndpoint>().eq(IntegrationEndpoint::getId,e.getId()).eq(IntegrationEndpoint::getTenantId,a.tenantId()).eq(IntegrationEndpoint::getDeleted,false).eq(IntegrationEndpoint::getVersion,e.getVersion());}
    private long count(Long tenant,String status){return endpointMapper.selectCount(base(tenant).eq(IntegrationEndpoint::getStatus,status));}
    private String normalizeStatus(String value){if(!StringUtils.hasText(value))return null;String n=value.trim().toUpperCase(Locale.ROOT);if(!STATUSES.contains(n))throw new BusinessException(ErrorCode.REQUEST_INVALID,"validation.integration.status.invalid");return n;}
    private String code(String v){return v.trim().toUpperCase(Locale.ROOT);}private String trim(String v){return StringUtils.hasText(v)?v.trim():null;}private void requireVersion(Integer actual,Integer expected){if(actual==null||!actual.equals(expected))conflict();}private void conflict(){throw new BusinessException(ErrorCode.VERSION_CONFLICT);}
    private String hash(IntegrationEndpoint e){try{byte[] bytes=MessageDigest.getInstance("SHA-256").digest((e.getHttpMethod()+"\n"+e.getRelativePath()+"\n"+Objects.toString(e.getRequestTemplate(),"")).getBytes(StandardCharsets.UTF_8));return HexFormat.of().formatHex(bytes);}catch(Exception ex){throw new IllegalStateException(ex);}}
    private void audit(ResolvedUserAccess a,IntegrationEndpoint e,String action){auditService.recordTransactional(a.tenantId(),a.userId(),"INTEGRATION_ENDPOINT",String.valueOf(e.getId()),action,"SUCCESS",e.getEndpointCode());}
}
