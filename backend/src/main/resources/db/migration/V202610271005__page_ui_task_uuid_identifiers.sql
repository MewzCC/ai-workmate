-- Extend task-detail selection without altering published migration history or enablement.
UPDATE agent_tool
SET parameters_schema = jsonb_set(parameters_schema, '{properties,recordId}', '{"oneOf":[{"type":"integer","minimum":1},{"type":"string","pattern":"^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"}]}'::jsonb),
    output_schema = jsonb_set(output_schema, '{properties,recordId}', '{"oneOf":[{"type":"integer","minimum":1},{"type":"string","pattern":"^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"}]}'::jsonb),
    schema_hash = 'sha256:96720d47e7d6e195390015a328c245958642cb65350a12fa78fc6f71c126223d'
WHERE code = 'page.ui.prepare' AND handler_version = '1.0.0'
  AND schema_hash = 'sha256:77f6759ba3f87f05aeeba0411ca3d5a7d532415543cdd8c1536a8ae3150292de';
