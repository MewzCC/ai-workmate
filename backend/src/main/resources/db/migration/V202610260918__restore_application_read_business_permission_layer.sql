-- Route prerequisites belong to the code-owned page capability layer, not business permission lists.
-- Retain the previously exercised migration version and repair its metadata without rewriting history.
UPDATE agent_tool SET required_permissions='["approval:mine:read"]'::jsonb,
    updated_at=CURRENT_TIMESTAMP
WHERE code IN ('approval.application.mine.query','approval.application.detail.query')
  AND handler_version='1.0.0'
  AND required_permissions='["approval:mine:read","route:approval-start"]'::jsonb;
