-- Declare the existing domain read requirement; this only narrows availability and grants no permissions.
UPDATE agent_tool
SET required_permissions='["leave:read:self","leave:remind"]'::jsonb,updated_at=CURRENT_TIMESTAMP
WHERE code='leave.remind' AND handler_version='1.0.0'
  AND required_permissions='["leave:remind"]'::jsonb;
