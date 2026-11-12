-- Keep the original single-waiting-child rule for non-generic workflows.
CREATE UNIQUE INDEX IF NOT EXISTS ux_workflow_non_generic_waiting_child
    ON workflow_task(tenant_id,parent_task_id)
    WHERE status='WAITING' AND parent_task_id IS NOT NULL AND business_type<>'GENERIC_APPROVAL';

DROP INDEX IF EXISTS ux_workflow_task_waiting_child;

-- Generic waiting children are an ordered queue. The active-task and participant indexes from
-- V202611111000 keep its execution and assignment constraints independent of queue length.
