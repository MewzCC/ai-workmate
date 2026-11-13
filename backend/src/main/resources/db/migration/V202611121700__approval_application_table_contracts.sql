-- Extend only known platform contracts. Preserve activation, permissions and tenant-specific overrides.
-- A table is data inside one application; these tools still execute exactly one domain write.
WITH versions(code, old_hash, new_hash) AS (VALUES
 ('approval.application.createDraft','sha256:0b91ec92030a3bdb99baac22dce2b1e7c3a239829740fe9ad174b24ecf340424','sha256:d81ae202169689c1af702ca5ef36f53a1f0908f6236c0ca44ac2be5232faaff7'),
 ('approval.application.updateDraft','sha256:a5c42c9496a21a5168c36ccda217610628fe07528ea042342fa483368e445290','sha256:5230372a18d3c297184e08e1c6877f66c327afdf923b5b623390f31a78e58504'),
 ('approval.application.apply','sha256:c614bce4890b0f2b8ae04b4b75000c73f4184fe0be2133a9fc84b4f341e5db1e','sha256:08d47dbb39aa36daa749d7e733c827fd681cf87963b4898e22d250d446250f1e')
), row_contract(schema) AS (VALUES ('{"type":"array","maxItems":20,"items":{"type":"object","additionalProperties":false,"required":["cells"],"properties":{"cells":{"type":"array","maxItems":10,"items":{"type":"object","additionalProperties":false,"required":["name","value"],"properties":{"name":{"type":"string","pattern":"^[a-z][A-Za-z0-9_-]{0,39}$","maxLength":40},"value":{"type":"string","maxLength":500}}}}}}}'::jsonb))
UPDATE agent_tool AS tool
SET parameters_schema=jsonb_set(jsonb_set(tool.parameters_schema,
      '{properties,fields,items,properties,rows}',row_contract.schema,TRUE),
      '{properties,fields,items,oneOf}','[{"required":["value"]},{"required":["values"]},{"required":["rows"]}]'::jsonb,TRUE),
    schema_hash=versions.new_hash, updated_at=CURRENT_TIMESTAMP
FROM versions, row_contract
WHERE tool.tenant_id IS NULL AND tool.code=versions.code AND tool.schema_hash=versions.old_hash
  AND tool.handler_version='1.0.0' AND tool.risk_level='L1' AND tool.side_effect='SINGLE_WRITE'
  AND tool.confirmation_policy='EXPLICIT' AND tool.data_scope_policy='SELF';

WITH versions(code, old_hash, new_hash) AS (VALUES
 ('approval.application.detail.query','sha256:ac5b12b6c1316ba942c2b8816ad1991d92f51aecb1fddd46bff79803bb456777','sha256:14cfdac4a34a1640dec04ba5469f0ffb4fdab6dfbb1b2f837cc517631c8641cf'),
 ('approval.genericTask.detail.query','sha256:c1e6afc86677babeed0109482833332693c33021e08175aa986a1973e7061035','sha256:99de6ea3587ea8dc31ef6133872480be1d4d13920612812ec1f9ec7c469a268d')
), table_contract(schema) AS (VALUES ('{"type":"object","additionalProperties":false,"required":["columns","rows"],"properties":{"columns":{"type":"array","minItems":1,"maxItems":10,"items":{"type":"object","additionalProperties":false,"required":["name","label","type"],"properties":{"name":{"type":"string","maxLength":40},"label":{"type":"string","maxLength":40},"type":{"type":"string","enum":["text","textarea","number","money","date","time","radio","select"]}}}},"rows":{"type":"array","maxItems":20,"items":{"type":"array","maxItems":10,"items":{"type":"string","maxLength":500}}}}}'::jsonb))
UPDATE agent_tool AS tool
SET output_schema=jsonb_set(tool.output_schema,'{properties,fields,items,properties,table}',table_contract.schema,TRUE),
    schema_hash=versions.new_hash, max_result_bytes=262144, updated_at=CURRENT_TIMESTAMP
FROM versions, table_contract
WHERE tool.tenant_id IS NULL AND tool.code=versions.code AND tool.schema_hash=versions.old_hash
  AND tool.handler_version='1.0.0' AND tool.risk_level='L0' AND tool.side_effect='NONE'
  AND tool.confirmation_policy='NONE';

WITH versions(code, old_hash, new_hash, location) AS (VALUES
 ('approval.form.definition.query','sha256:b91991992231bd332647b3c0942cba36cce1372e9b5d67692b2090366727e00f','sha256:5012a7e1a3c3fc0dd2d2679805d85aa3575fc244d3f3e95c4ffbb1b0bb69f7b0',ARRAY['properties','fields','items','properties','columns']),
 ('approval.application.template.query','sha256:2b42e7d61ca55522686379feee80e732096212aff579fa25c43dd806a1bef94a','sha256:258721ef458d3f19b88a1d58aced8bf7013e26c70fe64397620a33982eaba287',ARRAY['properties','items','items','properties','fields','items','properties','columns'])
), column_contract(schema) AS (VALUES ('{"type":"array","minItems":1,"maxItems":10,"items":{"type":"object","additionalProperties":false,"required":["name","label","type","required","options"],"properties":{"name":{"type":"string","pattern":"^[a-z][A-Za-z0-9_-]{0,39}$","maxLength":40},"label":{"type":"string","minLength":1,"maxLength":40},"type":{"type":"string","enum":["text","textarea","number","money","date","time","radio","select"]},"required":{"type":"boolean"},"options":{"type":"array","maxItems":10,"items":{"type":"string","minLength":1,"maxLength":80}},"min":{"type":"number","minimum":-1000000000000000,"maximum":1000000000000000}}}}'::jsonb))
UPDATE agent_tool AS tool
SET output_schema=jsonb_set(tool.output_schema,versions.location,column_contract.schema,TRUE),
    schema_hash=versions.new_hash, updated_at=CURRENT_TIMESTAMP
FROM versions, column_contract
WHERE tool.tenant_id IS NULL AND tool.code=versions.code AND tool.schema_hash=versions.old_hash
  AND tool.handler_version='1.0.0' AND tool.risk_level='L0' AND tool.side_effect='NONE'
  AND tool.confirmation_policy='NONE';
