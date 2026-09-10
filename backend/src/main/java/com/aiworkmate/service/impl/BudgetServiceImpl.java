package com.aiworkmate.service.impl;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.aiworkmate.dto.*;
import com.aiworkmate.entity.BudgetPlan;
import com.aiworkmate.entity.BudgetTransaction;
import com.aiworkmate.entity.User;
import com.aiworkmate.mapper.BudgetPlanMapper;
import com.aiworkmate.mapper.BudgetTransactionMapper;
import com.aiworkmate.mapper.UserMapper;
import com.aiworkmate.service.BusinessAuditService;
import com.aiworkmate.service.BudgetService;
import com.aiworkmate.service.NotificationService;
import com.aiworkmate.service.UserAccessService;
import com.aiworkmate.service.model.ResolvedUserAccess;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class BudgetServiceImpl implements BudgetService {
    private static final String READ = "route:budget";
    private static final String MANAGE = "budget:manage";
    private static final Set<String> STATUSES = Set.of("DRAFT", "ACTIVE", "CLOSED", "CANCELLED");
    private static final Map<String,List<String>> TRANSITIONS = Map.of(
            "DRAFT", List.of("ACTIVE", "CANCELLED"), "ACTIVE", List.of("CLOSED", "CANCELLED"),
            "CLOSED", List.of(), "CANCELLED", List.of());

    private final BudgetPlanMapper planMapper;
    private final BudgetTransactionMapper transactionMapper;
    private final UserMapper userMapper;
    private final UserAccessService accessService;
    private final BusinessAuditService auditService;
    private final NotificationService notificationService;
    private final MessageSource messageSource;

    @Override @Transactional(readOnly=true)
    public BudgetPageResponse list(Long userId, String keyword, String status, Integer fiscalYear, int page, int size) {
        ResolvedUserAccess actor = requireRead(userId);
        String normalizedStatus = normalizeStatus(status);
        int safePage=Math.max(1,page), safeSize=Math.min(100,Math.max(1,size));
        LambdaQueryWrapper<BudgetPlan> query=base(actor.tenantId())
                .eq(normalizedStatus!=null, BudgetPlan::getStatus, normalizedStatus)
                .eq(fiscalYear!=null, BudgetPlan::getFiscalYear, fiscalYear)
                .and(StringUtils.hasText(keyword), q -> q.like(BudgetPlan::getBudgetCode, keyword.trim())
                        .or().like(BudgetPlan::getName, keyword.trim()).or().like(BudgetPlan::getOwnerLabel, keyword.trim()))
                .orderByDesc(BudgetPlan::getFiscalYear).orderByDesc(BudgetPlan::getUpdatedAt);
        Page<BudgetPlan> result=planMapper.selectPage(new Page<>(safePage,safeSize),query);
        List<BudgetPlan> active=planMapper.selectList(base(actor.tenantId()).eq(BudgetPlan::getStatus,"ACTIVE"));
        BigDecimal total=nvl(planMapper.totalActive(actor.tenantId())), occupied=nvl(planMapper.totalOccupied(actor.tenantId())), spent=nvl(planMapper.totalSpent(actor.tenantId()));
        BudgetPageResponse.Stats stats=new BudgetPageResponse.Stats(planMapper.selectCount(base(actor.tenantId())), active.size(),
                active.stream().filter(p -> !"NORMAL".equals(alertLevel(p))).count(), total, occupied, spent,
                total.subtract(occupied).subtract(spent));
        boolean manageable=canManage(actor);
        return new BudgetPageResponse(result.getRecords().stream().map(p->response(p,manageable)).toList(), result.getTotal(),safePage,safeSize,stats,manageable);
    }

    @Override @Transactional(readOnly=true)
    public BudgetDetailResponse detail(Long userId, Long id) {
        ResolvedUserAccess actor=requireRead(userId); BudgetPlan plan=requirePlan(actor,id);
        List<BudgetTransactionResponse> rows=transactionMapper.selectList(new LambdaQueryWrapper<BudgetTransaction>()
                .eq(BudgetTransaction::getTenantId,actor.tenantId()).eq(BudgetTransaction::getBudgetId,id)
                .orderByDesc(BudgetTransaction::getCreatedAt).orderByDesc(BudgetTransaction::getId)).stream()
                .map(this::transactionResponse).toList();
        return new BudgetDetailResponse(response(plan,canManage(actor)),rows);
    }

    @Override @Transactional(readOnly=true)
    public BudgetOptionsResponse options(Long userId) {
        ResolvedUserAccess actor=requireRead(userId);
        return new BudgetOptionsResponse(userMapper.selectList(new LambdaQueryWrapper<User>().eq(User::getTenantId,actor.tenantId())
                .eq(User::getStatus,1).orderByAsc(User::getDisplayName).orderByAsc(User::getUsername)).stream()
                .map(u->new BudgetOptionsResponse.Option(u.getId(),label(u),u.getEmail())).toList());
    }

    @Override @Transactional
    public BudgetResponse create(Long userId, BudgetPlanRequest request) {
        ResolvedUserAccess actor=requireManage(userId); User owner=requireOwner(actor,request.ownerUserId());
        BudgetPlan plan=new BudgetPlan(); plan.setTenantId(actor.tenantId()); plan.setBudgetCode(code(request.code()));
        apply(plan,request,owner); plan.setOccupiedAmount(BigDecimal.ZERO); plan.setSpentAmount(BigDecimal.ZERO);
        plan.setStatus("DRAFT"); plan.setVersion(0); plan.setDeleted(false); plan.setCreatedBy(actor.userId()); plan.setUpdatedBy(actor.userId());
        LocalDateTime now=LocalDateTime.now(); plan.setCreatedAt(now); plan.setUpdatedAt(now);
        try { planMapper.insert(plan); } catch (DuplicateKeyException ex) { throw new BusinessException(ErrorCode.REQUEST_INVALID,"validation.budget.duplicate"); }
        event(actor,plan,"CREATED",BigDecimal.ZERO,BigDecimal.ZERO,null,null,null); audit(actor,plan,"CREATE");
        return response(plan,true);
    }

    @Override @Transactional
    public BudgetResponse update(Long userId, Long id, BudgetPlanRequest request) {
        ResolvedUserAccess actor=requireManage(userId); BudgetPlan old=requirePlan(actor,id); version(request.version(),old.getVersion());
        if (!("DRAFT".equals(old.getStatus())||"ACTIVE".equals(old.getStatus()))) state("validation.budget.edit.closed");
        if (!old.getBudgetCode().equals(code(request.code()))) state("validation.budget.code.immutable");
        if (request.totalAmount().compareTo(old.getOccupiedAmount().add(old.getSpentAmount()))<0) state("validation.budget.amount.belowUsed");
        User owner=requireOwner(actor,request.ownerUserId()); BudgetPlan changed=new BudgetPlan(); apply(changed,request,owner);
        changed.setUpdatedBy(actor.userId()); changed.setUpdatedAt(LocalDateTime.now()); changed.setVersion(old.getVersion()+1);
        int count=planMapper.update(changed,update(actor,old)); if(count!=1) conflict();
        event(actor,old,"UPDATED",old.getOccupiedAmount(),old.getSpentAmount(),null,null,null); audit(actor,old,"UPDATE");
        return response(requirePlan(actor,id),true);
    }

    @Override @Transactional
    public BudgetResponse updateStatus(Long userId, Long id, BudgetStatusRequest request) {
        ResolvedUserAccess actor=requireManage(userId); BudgetPlan old=requirePlan(actor,id); version(request.version(),old.getVersion());
        String target=normalizeStatus(request.status());
        if(target==null || !TRANSITIONS.getOrDefault(old.getStatus(),List.of()).contains(target)) state("validation.budget.transition.invalid");
        if("CLOSED".equals(target)&&old.getOccupiedAmount().signum()>0) state("validation.budget.close.occupied");
        if("CANCELLED".equals(target)&&(old.getOccupiedAmount().signum()>0||old.getSpentAmount().signum()>0)) state("validation.budget.cancel.used");
        int count=planMapper.update(null,update(actor,old).set(BudgetPlan::getStatus,target).set(BudgetPlan::getUpdatedBy,actor.userId())
                .set(BudgetPlan::getUpdatedAt,LocalDateTime.now()).set(BudgetPlan::getVersion,old.getVersion()+1)); if(count!=1) conflict();
        event(actor,old,"STATUS_"+target,old.getOccupiedAmount(),old.getSpentAmount(),null,null,request.reason()); audit(actor,old,"STATUS_"+target);
        return response(requirePlan(actor,id),true);
    }

    @Override @Transactional
    public BudgetResponse operate(Long userId, Long id, BudgetOperationRequest request) {
        ResolvedUserAccess actor=requireManage(userId); BudgetPlan old=requirePlan(actor,id); version(request.version(),old.getVersion());
        if(!"ACTIVE".equals(old.getStatus())) state("validation.budget.operation.activeOnly");
        BigDecimal occupied=old.getOccupiedAmount(), spent=old.getSpentAmount(), available=old.getTotalAmount().subtract(occupied).subtract(spent);
        String type=request.type().toUpperCase(Locale.ROOT); BigDecimal nextOccupied=occupied, nextSpent=spent;
        if("OCCUPY".equals(type)) { if(request.amount().compareTo(available)>0) state("validation.budget.operation.insufficient"); nextOccupied=occupied.add(request.amount()); }
        else if("RELEASE".equals(type)) { if(request.amount().compareTo(occupied)>0) state("validation.budget.operation.releaseExceeded"); nextOccupied=occupied.subtract(request.amount()); }
        else if("SPEND".equals(type)) { if(request.amount().compareTo(occupied)>0) state("validation.budget.operation.spendExceeded"); nextOccupied=occupied.subtract(request.amount()); nextSpent=spent.add(request.amount()); }
        else throw new BusinessException(ErrorCode.REQUEST_INVALID,"validation.budget.operation.invalid");
        int count=planMapper.update(null,update(actor,old).set(BudgetPlan::getOccupiedAmount,nextOccupied).set(BudgetPlan::getSpentAmount,nextSpent)
                .set(BudgetPlan::getUpdatedBy,actor.userId()).set(BudgetPlan::getUpdatedAt,LocalDateTime.now()).set(BudgetPlan::getVersion,old.getVersion()+1)); if(count!=1) conflict();
        event(actor,old,type,nextOccupied,nextSpent,request.amount(),request.referenceCode(),request.note()); audit(actor,old,type);
        BudgetPlan current=requirePlan(actor,id);
        if("NORMAL".equals(alertLevel(old))&&!"NORMAL".equals(alertLevel(current))) notificationService.publish(actor.tenantId(),old.getOwnerUserId(),NotificationService.TYPE_ALERT,
                message("notification.budget.warning.title",old.getBudgetCode()),message("notification.budget.warning.content",old.getName(),utilization(current)),"budget",id);
        return response(current,true);
    }

    private void apply(BudgetPlan plan,BudgetPlanRequest r,User owner){ plan.setName(r.name().trim()); plan.setFiscalYear(r.fiscalYear()); plan.setOwnerUserId(owner.getId()); plan.setOwnerLabel(label(owner)); plan.setTotalAmount(r.totalAmount()); plan.setCurrency(r.currency().toUpperCase(Locale.ROOT)); plan.setWarningThreshold(r.warningThreshold()); plan.setSummary(trim(r.summary())); }
    private BudgetResponse response(BudgetPlan p,boolean manage){ BigDecimal available=p.getTotalAmount().subtract(p.getOccupiedAmount()).subtract(p.getSpentAmount()); return new BudgetResponse(p.getId(),p.getBudgetCode(),p.getName(),p.getFiscalYear(),p.getOwnerUserId(),p.getOwnerLabel(),p.getTotalAmount(),p.getOccupiedAmount(),p.getSpentAmount(),available,p.getCurrency(),p.getWarningThreshold(),utilization(p),alertLevel(p),p.getStatus(),p.getSummary(),p.getVersion(),p.getUpdatedAt(),manage,manage?TRANSITIONS.getOrDefault(p.getStatus(),List.of()):List.of()); }
    private BudgetTransactionResponse transactionResponse(BudgetTransaction t){ return new BudgetTransactionResponse(t.getId(),t.getTransactionType(),t.getAmount(),t.getOccupiedBefore(),t.getOccupiedAfter(),t.getSpentBefore(),t.getSpentAfter(),t.getReferenceCode(),t.getNote(),t.getOperatorLabel(),t.getCreatedAt()); }
    private void event(ResolvedUserAccess actor,BudgetPlan p,String type,BigDecimal occupiedAfter,BigDecimal spentAfter,BigDecimal amount,String reference,String note){ BudgetTransaction t=new BudgetTransaction();t.setTenantId(actor.tenantId());t.setBudgetId(p.getId());t.setTransactionType(type);t.setAmount(amount);t.setOccupiedBefore(p.getOccupiedAmount());t.setOccupiedAfter(occupiedAfter);t.setSpentBefore(p.getSpentAmount());t.setSpentAfter(spentAfter);t.setReferenceCode(trim(reference));t.setNote(trim(note));t.setOperatorId(actor.userId());t.setOperatorLabel(actor.username());t.setCreatedAt(LocalDateTime.now());transactionMapper.insert(t); }
    private ResolvedUserAccess requireRead(Long userId){ResolvedUserAccess a=accessService.resolveActiveUser(userId);if(a==null)throw new BusinessException(ErrorCode.AUTH_REQUIRED);if(!a.permissions().contains(READ))throw new BusinessException(ErrorCode.PERMISSION_DENIED);return a;}
    private ResolvedUserAccess requireManage(Long id){ResolvedUserAccess a=requireRead(id);if(!canManage(a))throw new BusinessException(ErrorCode.PERMISSION_DENIED);return a;}
    private boolean canManage(ResolvedUserAccess a){return a.permissions().contains(MANAGE);}
    private BudgetPlan requirePlan(ResolvedUserAccess a,Long id){BudgetPlan p=planMapper.selectOne(base(a.tenantId()).eq(BudgetPlan::getId,id));if(p==null)throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);return p;}
    private User requireOwner(ResolvedUserAccess a,Long id){User u=userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getId,id).eq(User::getTenantId,a.tenantId()).eq(User::getStatus,1));if(u==null)throw new BusinessException(ErrorCode.REQUEST_INVALID,"validation.budget.owner.invalid");return u;}
    private LambdaQueryWrapper<BudgetPlan> base(Long tenant){return new LambdaQueryWrapper<BudgetPlan>().eq(BudgetPlan::getTenantId,tenant).eq(BudgetPlan::getDeleted,false);}
    private LambdaUpdateWrapper<BudgetPlan> update(ResolvedUserAccess a,BudgetPlan p){return new LambdaUpdateWrapper<BudgetPlan>().eq(BudgetPlan::getId,p.getId()).eq(BudgetPlan::getTenantId,a.tenantId()).eq(BudgetPlan::getDeleted,false).eq(BudgetPlan::getVersion,p.getVersion());}
    private String normalizeStatus(String s){if(!StringUtils.hasText(s))return null;String n=s.trim().toUpperCase(Locale.ROOT);if(!STATUSES.contains(n))throw new BusinessException(ErrorCode.REQUEST_INVALID,"validation.budget.status.invalid");return n;}
    private int utilization(BudgetPlan p){if(p.getTotalAmount().signum()==0)return 0;return p.getOccupiedAmount().add(p.getSpentAmount()).multiply(BigDecimal.valueOf(100)).divide(p.getTotalAmount(),0,RoundingMode.HALF_UP).intValue();}
    private String alertLevel(BudgetPlan p){int value=utilization(p);if(value>=100)return "FULL";if(value>=p.getWarningThreshold())return "WARNING";return "NORMAL";}
    private void version(Integer actual,Integer expected){if(actual==null||!actual.equals(expected))conflict();} private void conflict(){throw new BusinessException(ErrorCode.VERSION_CONFLICT);} private void state(String key){throw new BusinessException(ErrorCode.BUSINESS_STATE_INVALID,key);}
    private String code(String s){return s.trim().toUpperCase(Locale.ROOT);} private String trim(String s){return StringUtils.hasText(s)?s.trim():null;} private BigDecimal nvl(BigDecimal v){return v==null?BigDecimal.ZERO:v;}
    private String label(User u){return StringUtils.hasText(u.getDisplayName())?u.getDisplayName():(StringUtils.hasText(u.getEmail())?u.getEmail():u.getUsername());}
    private void audit(ResolvedUserAccess a,BudgetPlan p,String action){auditService.recordTransactional(a.tenantId(),a.userId(),"BUDGET",String.valueOf(p.getId()),action,"SUCCESS",p.getBudgetCode());}
    private String message(String key,Object...args){return messageSource.getMessage(key,args,LocaleContextHolder.getLocale());}
}
