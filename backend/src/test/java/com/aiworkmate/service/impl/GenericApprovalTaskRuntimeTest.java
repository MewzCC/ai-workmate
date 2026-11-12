package com.aiworkmate.service.impl;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.entity.WorkflowActionLog;
import com.aiworkmate.entity.WorkflowTask;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class GenericApprovalTaskRuntimeTest {
    private final GenericApprovalPlan plan=new GenericApprovalPlan(1,List.of(
            new GenericApprovalPlan.Step(0,"Review",10,24),new GenericApprovalPlan.Step(1,"Finance",20,24)));
    private final GenericApprovalTaskRuntime.Coordinates coordinates=new GenericApprovalTaskRuntime.Coordinates(1,2,3,99);
    @Test void plainFrozenNodeAdvancesOnlyAfterItsAssignedTask(){
        var state=resolve(List.of(task(100,null,null,10,"PENDING")),List.of());
        assertThat(state.active().getId()).isEqualTo(100);assertThat(state.nextAfterApproval()).isNull();
        assertThat(state.canAddSign()).isTrue();
    }
    @Test void preSignSuspendsTheRootAndRestoresItBeforeAnyPostSign(){
        WorkflowTask root=task(100,null,null,10,"WAITING"),pre=task(101,100L,"PRE",30,"PENDING"),post=task(102,100L,"POST",40,"WAITING");
        var logs=List.of(add(1,101,30,"PRE"),add(2,102,40,"POST"));
        var state=resolve(List.of(root,pre,post),logs);
        assertThat(state.active().getId()).isEqualTo(101);assertThat(state.nextAfterApproval().getId()).isEqualTo(100);
        assertThat(state.canAddSign()).isFalse();
        root.setStatus("PENDING");pre.setStatus("APPROVED");
        state=resolve(List.of(root,pre,post),logs);assertThat(state.nextAfterApproval().getId()).isEqualTo(102);
    }
    @Test void postSignQueueCannotSkipItsFirstSignerOrAdvanceTheFrozenCursorEarly(){
        WorkflowTask root=task(100,null,null,10,"APPROVED"),first=task(101,100L,"POST",30,"PENDING"),second=task(102,100L,"POST",40,"WAITING");
        var logs=List.of(add(1,101,30,"POST"),add(2,102,40,"POST"));
        assertThat(resolve(List.of(root,first,second),logs).nextAfterApproval().getId()).isEqualTo(102);
        first.setStatus("APPROVED");second.setStatus("PENDING");
        assertThat(resolve(List.of(root,first,second),logs).nextAfterApproval()).isNull();
        first.setStatus("WAITING");
        assertThatThrownBy(()->resolve(List.of(root,first,second),logs)).isInstanceOf(BusinessException.class);
    }
    @Test void rejectsParallelTasksMissingCreationEvidenceAndForgedFutureNodes(){
        WorkflowTask root=task(100,null,null,10,"PENDING"),child=task(101,100L,"PRE",30,"PENDING");
        assertThatThrownBy(()->resolve(List.of(root,child),List.of(add(1,101,30,"PRE")))).isInstanceOf(BusinessException.class);
        root.setStatus("WAITING");assertThatThrownBy(()->resolve(List.of(root,child),List.of())).isInstanceOf(BusinessException.class);
        var future=task(103,null,null,20,"WAITING");future.setProcessNodeIndex(1);
        assertThatThrownBy(()->resolve(List.of(root,child,future),List.of(add(1,101,30,"PRE")))).isInstanceOf(BusinessException.class);
    }
    @Test void childTransfersUseTheAddedSignerAndDoNotBorrowTheFrozenRootAssignment(){
        WorkflowTask root=task(100,null,null,10,"WAITING"),child=task(101,100L,"PRE",40,"PENDING");
        var transfer=new WorkflowActionLog();transfer.setId(2L);transfer.setTenantId(1L);transfer.setInstanceId(2L);transfer.setTaskId(101L);
        transfer.setAction("TRANSFER");transfer.setActorUserId(30L);transfer.setOriginalAssigneeUserId(30L);transfer.setTargetUserId(40L);
        transfer.setFromStatus("PENDING");transfer.setToStatus("PENDING");transfer.setComment("Explicit transfer");
        var state=resolve(List.of(root,child),List.of(add(1,101,30,"PRE"),transfer));
        assertThat(state.active().getAssigneeUserId()).isEqualTo(40);
        assertThat(state.restrictedTransferTargets()).contains(10L,20L).doesNotContain(30L);
        transfer.setOriginalAssigneeUserId(10L);
        assertThatThrownBy(()->resolve(List.of(root,child),List.of(add(1,101,30,"PRE"),transfer))).isInstanceOf(BusinessException.class);
    }
    @Test void addSignCandidatesExcludeFrozenAndAlreadyAddedReviewers(){
        WorkflowTask root=task(100,null,null,10,"PENDING"),post=task(101,100L,"POST",30,"WAITING");
        assertThat(resolve(List.of(root,post),List.of(add(1,101,30,"POST"))).ineligibleAddSignTargets()).contains(10L,20L,30L);
    }
    @Test void rejectsDuplicatedAddedSignersAndCreationEventsAttachedToTheRoot(){
        WorkflowTask root=task(100,null,null,10,"PENDING"),first=task(101,100L,"POST",30,"WAITING"),second=task(102,100L,"POST",30,"WAITING");
        assertThatThrownBy(()->resolve(List.of(root,first,second),List.of(add(1,101,30,"POST"),add(2,102,30,"POST")))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->resolve(List.of(root),List.of(add(1,100,30,"POST")))).isInstanceOf(BusinessException.class);
    }
    private GenericApprovalTaskRuntime.Context resolve(List<WorkflowTask> tasks,List<WorkflowActionLog> logs){return GenericApprovalTaskRuntime.resolve(plan,0,coordinates,tasks,logs);}
    private WorkflowTask task(long id,Long parent,String mode,long assignee,String status){
        var task=new WorkflowTask();task.setId(id);task.setTenantId(1L);task.setInstanceId(2L);task.setBusinessId(3L);task.setBusinessType("GENERIC_APPROVAL");
        task.setParentTaskId(parent);task.setAddSignMode(mode);task.setProcessNodeIndex(0);task.setProcessNodeName("Review");task.setAssigneeUserId(assignee);task.setStatus(status);task.setVersion(0);return task;
    }
    private WorkflowActionLog add(long id,long task,long signer,String mode){
        var log=new WorkflowActionLog();log.setId(id);log.setTenantId(1L);log.setInstanceId(2L);log.setTaskId(task);
        log.setAction("ADD_SIGN_"+mode);log.setActorUserId(10L);log.setOriginalAssigneeUserId(10L);log.setTargetUserId(signer);
        log.setFromStatus("PENDING");log.setToStatus(mode.equals("PRE")?"PENDING":"WAITING");log.setComment("Explicit add sign");return log;
    }
}
