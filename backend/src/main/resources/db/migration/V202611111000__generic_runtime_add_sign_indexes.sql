-- Frozen nodes stay unique roots; runtime add-sign children may share their frozen node index.
CREATE UNIQUE INDEX IF NOT EXISTS ux_generic_root_execution_node
    ON workflow_task(tenant_id,instance_id,process_node_index)
    WHERE business_type='GENERIC_APPROVAL' AND process_node_index IS NOT NULL AND parent_task_id IS NULL;

DROP INDEX IF EXISTS ux_generic_task_execution_node;

-- Legacy uncompiled tasks retain their read/reject compatibility. Every compiled runtime has one active node.
CREATE UNIQUE INDEX IF NOT EXISTS ux_generic_active_execution_task
    ON workflow_task(tenant_id,instance_id)
    WHERE business_type='GENERIC_APPROVAL' AND process_node_index IS NOT NULL AND status='PENDING';

CREATE UNIQUE INDEX IF NOT EXISTS ux_generic_child_current_assignee
    ON workflow_task(tenant_id,instance_id,parent_task_id,assignee_user_id)
    WHERE business_type='GENERIC_APPROVAL' AND parent_task_id IS NOT NULL;
