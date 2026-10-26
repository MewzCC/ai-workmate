-- Declare the route check already enforced by the generic approval domain; never grant it to callers.
UPDATE agent_tool SET required_permissions='["approval:mine:read","route:approval-start"]'::jsonb,
    updated_at=CURRENT_TIMESTAMP
WHERE code IN ('approval.application.mine.query','approval.application.detail.query')
  AND handler_version='1.0.0' AND required_permissions='["approval:mine:read"]'::jsonb;
