-- Align historical Agent page-action identifiers with the enabled OA route keys.
-- Remove a legacy duplicate first so the unique tenant/page/tool constraint remains valid.
DELETE FROM agent_page_action_policy legacy
WHERE legacy.page_id IN ('todo-list', 'message-center')
  AND EXISTS (
      SELECT 1
      FROM agent_page_action_policy canonical
      WHERE canonical.tenant_id = legacy.tenant_id
        AND canonical.tool_code = legacy.tool_code
        AND canonical.page_id = CASE legacy.page_id
            WHEN 'todo-list' THEN 'todo'
            WHEN 'message-center' THEN 'messages'
        END
  );

UPDATE agent_page_action_policy
SET page_id = CASE page_id
        WHEN 'todo-list' THEN 'todo'
        WHEN 'message-center' THEN 'messages'
    END,
    updated_at = CURRENT_TIMESTAMP
WHERE page_id IN ('todo-list', 'message-center');
