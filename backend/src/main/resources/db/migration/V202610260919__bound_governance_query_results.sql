-- Add explicit filtered totals/truncation metadata; preserve all permissions, switches and budgets.
WITH contracts(code,old_hash,new_hash,metadata) AS (VALUES
 ('accessGovernance.query','sha256:d622f57307336098c25b779333ff842a874adcbf1880ae687c08276d3c6bbc61','sha256:7286724aabd5e431fe911c9a9b3ec9f9984be9431fe9cf3dd436c4dea4a6b806','["roleCount","rolesTruncated"]'::jsonb),
 ('dataPermission.query','sha256:858c22c5e8947762eda39892caf2fbf962282c21d94945491daf34ebde3f6c21','sha256:e4f18dfeab7b4995534312eeef5e2aa1fbf2590fd4aa17d5299b3070aaed8b51','["policyCount","policiesTruncated"]'::jsonb),
 ('aiPermission.query','sha256:270d6da876eea0376fddf296219842f25f2bdc594ec61023ac365006cfc7ae87','sha256:d10931f47f7a8c029913766cb429a38e2caef5ad4c5d72e992ca8220632f6870','["toolCount","roleCount","toolsTruncated","rolesTruncated"]'::jsonb)
)
UPDATE agent_tool t SET
 output_schema=jsonb_set(jsonb_set(t.output_schema,'{properties}',(t.output_schema->'properties') || (
   SELECT jsonb_object_agg(field,CASE WHEN field LIKE '%Truncated' THEN '{"type":"boolean"}'::jsonb ELSE '{"type":"integer","minimum":0}'::jsonb END)
   FROM jsonb_array_elements_text(c.metadata) AS fields(field)
  )), '{required}', (t.output_schema->'required') || c.metadata),
 schema_hash=c.new_hash, updated_at=CURRENT_TIMESTAMP
FROM contracts c WHERE t.code=c.code AND t.handler_version='1.0.0' AND t.schema_hash=c.old_hash;
