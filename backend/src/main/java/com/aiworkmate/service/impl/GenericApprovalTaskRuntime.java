package com.aiworkmate.service.impl;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.aiworkmate.entity.WorkflowActionLog;
import com.aiworkmate.entity.WorkflowTask;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Data-only runtime ordering over frozen nodes and explicitly recorded task interventions. No I/O. */
final class GenericApprovalTaskRuntime {
    static final int MAX_ADDED_PER_NODE=20;
    static final int MAX_TASKS=420;
    static final int MAX_EVENTS=14000;
    record Coordinates(long tenantId,long instanceId,long applicationId,long applicantId) { }
    record Context(GenericApprovalPlan plan,Coordinates coordinates,WorkflowTask root,WorkflowTask active,
                   List<WorkflowTask> children,List<WorkflowTask> tasks,Map<Long,Long> initialAssignees) {
        Context { children=List.copyOf(children);tasks=List.copyOf(tasks);initialAssignees=Map.copyOf(initialAssignees); }
        boolean canAddSign(){return active.getId().equals(root.getId()) && children.size()<MAX_ADDED_PER_NODE;}
        WorkflowTask nextAfterApproval(){
            if("PRE".equals(active.getAddSignMode()))return root;
            for(var child:children)if("POST".equals(child.getAddSignMode()) && child.getStatus().equals("WAITING"))return child;
            return null;
        }
        Set<Long> ineligibleAddSignTargets(){
            var result=new HashSet<Long>();result.add(coordinates.applicantId());
            plan.steps().forEach(step->result.add(step.assigneeUserId()));
            tasks.forEach(task->{result.add(task.getAssigneeUserId());result.add(initialAssignees.get(task.getId()));});
            return Set.copyOf(result);
        }
        Set<Long> restrictedTransferTargets(){
            var result=new HashSet<Long>();result.add(coordinates.applicantId());
            if(active.getParentTaskId()!=null){
                plan.steps().forEach(step->result.add(step.assigneeUserId()));
                for(var task:tasks)if(!task.getId().equals(active.getId())){result.add(task.getAssigneeUserId());result.add(initialAssignees.get(task.getId()));}
            }else{
                for(var task:tasks)if(task.getParentTaskId()!=null){result.add(task.getAssigneeUserId());result.add(initialAssignees.get(task.getId()));}
            }
            return Set.copyOf(result);
        }
    }
    private GenericApprovalTaskRuntime(){ }

    static Context resolve(GenericApprovalPlan plan,int cursor,Coordinates coordinates,List<WorkflowTask> tasks,List<WorkflowActionLog> events){
        if(plan==null || plan.version()!=1 || plan.steps().size()>20 || cursor<0 || cursor>=plan.steps().size() || tasks==null || tasks.isEmpty() || tasks.size()>MAX_TASKS
                || events==null || events.size()>MAX_EVENTS)throw invalid();
        var indexed=new HashMap<Long,WorkflowTask>();var roots=new HashMap<Integer,WorkflowTask>();
        for(var task:tasks){
            if(task==null || task.getId()==null || task.getId()<1 || indexed.put(task.getId(),task)!=null
                    || !Objects.equals(task.getTenantId(),coordinates.tenantId()) || !Objects.equals(task.getInstanceId(),coordinates.instanceId())
                    || !Objects.equals(task.getBusinessId(),coordinates.applicationId()) || !"GENERIC_APPROVAL".equals(task.getBusinessType())
                    || task.getAssigneeUserId()==null || task.getAssigneeUserId()<1 || task.getAssigneeUserId()==coordinates.applicantId()
                    || task.getVersion()==null || task.getVersion()<0 || task.getProcessNodeIndex()==null || task.getProcessNodeIndex()<0 || task.getProcessNodeIndex()>cursor
                    || !Set.of("PENDING","WAITING","APPROVED","REJECTED","CANCELLED").contains(task.getStatus()==null?"":task.getStatus()))throw invalid();
            if(task.getParentTaskId()==null){
                if(task.getAddSignMode()!=null || roots.put(task.getProcessNodeIndex(),task)!=null)throw invalid();
            }else if(!Set.of("PRE","POST").contains(task.getAddSignMode()==null?"":task.getAddSignMode()))throw invalid();
        }
        for(int index=0;index<=cursor;index++)if(!roots.containsKey(index) || index<cursor && !"APPROVED".equals(roots.get(index).getStatus()))throw invalid();
        var grouped=new HashMap<Long,List<WorkflowActionLog>>();var eventIds=new HashSet<Long>();
        for(var event:events){
            if(event==null || event.getId()==null || event.getId()<1 || !eventIds.add(event.getId()) || event.getTaskId()==null || !indexed.containsKey(event.getTaskId())
                    || !Objects.equals(event.getTenantId(),coordinates.tenantId()) || !Objects.equals(event.getInstanceId(),coordinates.instanceId())
                    || !Set.of("TRANSFER","ADD_SIGN_PRE","ADD_SIGN_POST").contains(event.getAction()==null?"":event.getAction()))throw invalid();
            grouped.computeIfAbsent(event.getTaskId(),ignored->new ArrayList<>()).add(event);
        }
        grouped.values().forEach(history->history.sort(Comparator.comparing(WorkflowActionLog::getId)));
        var initial=new HashMap<Long,Long>();
        for(var root:roots.values()){
            if(grouped.getOrDefault(root.getId(),List.of()).stream().anyMatch(event->!"TRANSFER".equals(event.getAction())))throw invalid();
            long original=plan.steps().get(root.getProcessNodeIndex()).assigneeUserId();initial.put(root.getId(),original);
            if(effective(original,grouped.getOrDefault(root.getId(),List.of()),Long.MAX_VALUE)!=root.getAssigneeUserId())throw invalid();
        }
        var allChildren=new HashMap<Long,List<WorkflowTask>>();
        var plannedAssignees=new HashSet<Long>();plan.steps().forEach(step->plannedAssignees.add(step.assigneeUserId()));
        var addedAssignees=new HashSet<Long>();
        for(var child:tasks)if(child.getParentTaskId()!=null){
            var parent=indexed.get(child.getParentTaskId());
            if(parent==null || parent.getParentTaskId()!=null || !Objects.equals(parent.getProcessNodeIndex(),child.getProcessNodeIndex()))throw invalid();
            var history=grouped.getOrDefault(child.getId(),List.of());
            var additions=history.stream().filter(event->event.getAction().startsWith("ADD_SIGN_")).toList();
            if(additions.size()!=1)throw invalid();var addition=additions.get(0);
            long parentAtCreation=effective(initial.get(parent.getId()),grouped.getOrDefault(parent.getId(),List.of()),addition.getId());
            if(!addition.getAction().equals("ADD_SIGN_"+child.getAddSignMode()) || !Objects.equals(addition.getActorUserId(),parentAtCreation)
                    || !Objects.equals(addition.getOriginalAssigneeUserId(),parentAtCreation) || addition.getTargetUserId()==null || addition.getTargetUserId()<1
                    || addition.getTargetUserId()==coordinates.applicantId() || addition.getTargetUserId()==parentAtCreation
                    || plannedAssignees.contains(addition.getTargetUserId()) || !addedAssignees.add(addition.getTargetUserId())
                    || history.stream().anyMatch(event->"TRANSFER".equals(event.getAction()) && event.getId()<addition.getId()) || !"PENDING".equals(addition.getFromStatus())
                    || !("PRE".equals(child.getAddSignMode())?"PENDING":"WAITING").equals(addition.getToStatus())
                    || addition.getComment()==null || addition.getComment().isBlank())throw invalid();
            initial.put(child.getId(),addition.getTargetUserId());
            if(effective(addition.getTargetUserId(),history,Long.MAX_VALUE)!=child.getAssigneeUserId())throw invalid();
            allChildren.computeIfAbsent(parent.getId(),ignored->new ArrayList<>()).add(child);
            if(child.getProcessNodeIndex()<cursor && !"APPROVED".equals(child.getStatus()))throw invalid();
        }
        if(allChildren.values().stream().anyMatch(children->children.size()>MAX_ADDED_PER_NODE))throw invalid();
        var root=roots.get(cursor);var children=new ArrayList<>(allChildren.getOrDefault(root.getId(),List.of()));children.sort(Comparator.comparing(WorkflowTask::getId));
        var pre=children.stream().filter(child->"PRE".equals(child.getAddSignMode())).toList();
        var post=children.stream().filter(child->"POST".equals(child.getAddSignMode())).toList();
        WorkflowTask active;
        if("WAITING".equals(root.getStatus())){
            if(pre.isEmpty() || !"PENDING".equals(pre.get(pre.size()-1).getStatus()) || pre.subList(0,pre.size()-1).stream().anyMatch(child->!"APPROVED".equals(child.getStatus()))
                    || post.stream().anyMatch(child->!"WAITING".equals(child.getStatus())))throw invalid();
            active=pre.get(pre.size()-1);
        }else{
            if(pre.stream().anyMatch(child->!"APPROVED".equals(child.getStatus())))throw invalid();
            if("PENDING".equals(root.getStatus())){
                if(post.stream().anyMatch(child->!"WAITING".equals(child.getStatus())))throw invalid();active=root;
            }else if("APPROVED".equals(root.getStatus())){
                active=null;boolean found=false;
                for(var child:post){
                    if(!found && "APPROVED".equals(child.getStatus()))continue;
                    if(!found){if(!"PENDING".equals(child.getStatus()))throw invalid();active=child;found=true;}
                    else if(!"WAITING".equals(child.getStatus()))throw invalid();
                }
                if(active==null)throw invalid();
            }else throw invalid();
        }
        if(tasks.stream().filter(task->"PENDING".equals(task.getStatus())).count()!=1)throw invalid();
        return new Context(plan,coordinates,root,active,children,tasks,initial);
    }
    private static long effective(long original,List<WorkflowActionLog> history,long beforeId){
        long current=original;int transfers=0;
        for(var event:history)if(event.getId()<beforeId && "TRANSFER".equals(event.getAction())){
            if(++transfers>32 || !Objects.equals(event.getActorUserId(),current) || !Objects.equals(event.getOriginalAssigneeUserId(),current)
                    || event.getTargetUserId()==null || event.getTargetUserId()<1 || event.getTargetUserId()==current
                    || !"PENDING".equals(event.getFromStatus()) || !"PENDING".equals(event.getToStatus()) || event.getComment()==null || event.getComment().isBlank())throw invalid();
            current=event.getTargetUserId();
        }
        return current;
    }
    private static BusinessException invalid(){return new BusinessException(ErrorCode.BUSINESS_STATE_INVALID,"validation.genericApproval.runtimeInvalid");}
}
